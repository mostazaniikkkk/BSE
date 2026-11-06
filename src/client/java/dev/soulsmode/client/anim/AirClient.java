package dev.soulsmode.client.anim;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.animation.PlayerRawAnimationBuilder;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import dev.soulsmode.SoulsMode;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * SM_Jump 0x17ec and SM_Fall 0xd3 of c0001.nmb (docs/research/movement.md "Salto y caída: animación") over
 * locomotion, which keeps playing underneath. Every blend weighs its destination (1 - cos(pi t / D)) / 2.
 * <ul>
 * <li>Jump: JumpTakeoff from 0 over 0.1 s (0x2080; StandJumpTakeoff is unreachable in c0001). Landed from 0.4333 s on
 * (StateControl1): RollLanding with a horizontal speed >= 4.0 m/s (ChrMoveParam 100 +0x160), else back to locomotion
 * at once (0x20a6, 0.0001 s). Still in the air at its end (1.6667 s, 0x20a3): FallLoop over 0.2 s.</li>
 * <li>Fall (IsFall: descending at least 0.5 m below the apex, ChrMoveParam +0x1f0): FallLoop over 0.7 s (0x207e).
 * Landing: RollLanding when the drop is >= 5.0 m and the horizontal speed >= 4.5 m/s (+0x154 / +0x158; after a jump
 * 0 m / 4.0 m/s), else locomotion at once (0x20ad).</li>
 * <li>RollLanding: back to locomotion at 0.5667 s over 0.2 s when moving (0x20e6), else at 0.6333 s over 0.3 s
 * (0x20a8).</li>
 * </ul>
 * A walk-off fall starts FallLoop at the locomotion's Footsteps phase ({@link #fallStart}, start method 3). Every
 * landing out of JumpTakeoff or FallLoop starts the landing shake ({@link LandingShakeClient}) with its drop.
 */
public final class AirClient {
	public static final ResourceLocation LAYER = SoulsMode.id("air");
	/** Above locomotion (5), below the grip change (20) and the actions. */
	private static final int PRIORITY = 6;
	private static final float TAKEOFF_LAND_FROM = 0.4333f;
	private static final float TAKEOFF_LENGTH = 1.6667f;
	private static final double FALL_DROP = 0.5;
	private static final float ROLL_EXIT_MOVING = 0.5667f;
	private static final float ROLL_EXIT = 0.6333f;

	private enum Phase { NONE, TAKEOFF, FALL, ROLL }

	private static final class State {
		Phase phase = Phase.NONE;
		long start;
		boolean afterJump;
		double apexY;
		boolean wasOnGround = true;
	}

	private static final Map<AbstractClientPlayer, State> STATES = new WeakHashMap<>();

	private AirClient() {
	}

	public static void init() {
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY,
				player -> new PlayerAnimationController(player, (c, state, animSetter) -> PlayState.STOP));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				return;
			}
			for (var p : client.level.players()) {
				if (p instanceof AbstractClientPlayer player) {
					tick(player);
				}
			}
		});
	}

	private static void tick(AbstractClientPlayer player) {
		State s = STATES.computeIfAbsent(player, p -> new State());
		boolean souls = Locomotion.ENABLED && dev.soulsmode.client.PresenceClient.soulsMode(player)
				&& !player.isSwimming() && !player.isFallFlying() && !player.isPassenger();
		if (!souls) {
			if (s.phase != Phase.NONE) {
				leave(player, s, 0);
			}
			LandingShakeClient.stop(player);
			s.wasOnGround = player.onGround();
			return;
		}
		boolean ground = player.onGround();
		double vy = player.getY() - player.yo;
		// The drop from the apex, read before the apex follows the character onto the ground.
		double drop = s.apexY - player.getY();
		if (ground && !s.wasOnGround && (s.phase == Phase.TAKEOFF || s.phase == Phase.FALL)) {
			// A flagged landing (IsLanding after SM_Jump or IsFall): LandingRate's pulse, at the contact.
			LandingShakeClient.land(player, drop, s.phase == Phase.TAKEOFF || s.afterJump);
		}
		// The apex follows the character while rising or grounded (FUN_140372620).
		if (ground || vy > 0) {
			s.apexY = player.getY();
		}
		double speed = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) * 20;
		float t = (System.nanoTime() - s.start) / 1e9f;
		switch (s.phase) {
			case NONE -> {
				if (s.wasOnGround && !ground && vy > 0) {
					enter(player, s, Phase.TAKEOFF, "ds2_jump_takeoff", 2);
					s.afterJump = true;
				} else if (!ground && vy < 0 && s.apexY - player.getY() >= FALL_DROP) {
					enter(player, s, Phase.FALL, "ds2_fall_loop", 14, fallStart(player));
					s.afterJump = false;
				}
			}
			case TAKEOFF -> {
				if (ground && t >= TAKEOFF_LAND_FROM) {
					land(player, s, true, 0, speed);
				} else if (!ground && t >= TAKEOFF_LENGTH) {
					enter(player, s, Phase.FALL, "ds2_fall_loop", 4);
				}
			}
			case FALL -> {
				if (ground) {
					land(player, s, s.afterJump, drop, speed);
				}
			}
			case ROLL -> {
				boolean moving = speed > 1e-3;
				if (moving && t >= ROLL_EXIT_MOVING) {
					leave(player, s, 4);
				} else if (t >= ROLL_EXIT) {
					leave(player, s, 6);
				}
			}
		}
		s.wasOnGround = ground;
	}

	/** LandingCategory 1 (RollLanding) or 0 (straight back to locomotion). */
	private static void land(AbstractClientPlayer player, State s, boolean afterJump, double drop, double speed) {
		double height = afterJump ? 0 : 5.0;
		double minSpeed = afterJump ? 4.0 : 4.5;
		if (drop >= height && speed >= minSpeed) {
			enter(player, s, Phase.ROLL, "ds2_roll_landing", 0);
		} else {
			leave(player, s, 0);
		}
	}

	/**
	 * Footsteps sync track (the clip's discrete track at attrib 0x15 +0x8c, FUN_140610c40 -> FUN_1405fd130) of each
	 * locomotion clip: duration and event start fractions (c0001.nmb).
	 */
	private static final Map<String, float[]> FOOTSTEPS = Map.ofEntries(
			Map.entry("ds2_idle", new float[] {2.0f, 0.0019f}),
			Map.entry("ds2_walk", new float[] {1.0667f, 0.0019f, 0.5063f}),
			Map.entry("ds2_jog", new float[] {0.7667f, 0.0f, 0.4783f}),
			Map.entry("ds2_dash", new float[] {0.6f, 0.0001f, 0.4444f}),
			Map.entry("ds2_side_walk_forward", new float[] {1.3f, 0.0015f, 0.3846f}),
			Map.entry("ds2_side_walk_back", new float[] {1.3f, 0.0015f, 0.5641f}),
			Map.entry("ds2_side_walk_left", new float[] {0.7667f, 0.0026f, 0.5011f}),
			Map.entry("ds2_side_walk_right", new float[] {0.7667f, 0.0026f, 0.5148f}),
			Map.entry("ds2_side_jog_forward", new float[] {0.7667f, 0.0019f, 0.4783f}),
			Map.entry("ds2_side_jog_back", new float[] {0.7333f, 0.0019f, 0.4053f}),
			Map.entry("ds2_side_jog_left", new float[] {0.7333f, 0.0019f, 0.546f}),
			Map.entry("ds2_side_jog_right", new float[] {0.7333f, 0.0019f, 0.5f}));
	private static final float FALL_LOOP_LENGTH = 0.6667f;

	/**
	 * Transition 0x207e's start method 3 (FUN_14064fa60): the destination starts at the source's sync position (event
	 * index + fraction) in its own sync track. FallLoop's track has one event spanning the cycle, so it starts at the
	 * fraction of the locomotion's current Footsteps event; without a sync source, at 0. Seconds into FallLoop.
	 */
	private static float fallStart(AbstractClientPlayer player) {
		String clip = Locomotion.clip(player);
		if (clip == null) {
			return 0;
		}
		int suffix = clip.indexOf("_2");
		float[] track = FOOTSTEPS.get(suffix > 0 ? clip.substring(0, suffix) : clip);
		if (track == null || !(PlayerAnimationAccess.getPlayerAnimationLayer(player, Locomotion.LAYER)
				instanceof PlayerAnimationController loco)) {
			return 0;
		}
		float length = track[0];
		float f = (loco.getAnimationTime() % length) / length;
		int n = track.length - 1;
		int i = n - 1;
		for (int k = 0; k < n; k++) {
			if (track[1 + k] <= f) {
				i = k;
			}
		}
		float start = track[1 + i];
		float next = i + 1 < n ? track[2 + i] : track[1] + 1;
		float frac = (((f - start) % 1) + 1) % 1 / (next - start);
		return Math.max(0, Math.min(1, frac)) * FALL_LOOP_LENGTH;
	}

	private static void enter(AbstractClientPlayer player, State s, Phase phase, String clip, int fadeTicks) {
		enter(player, s, phase, clip, fadeTicks, 0);
	}

	private static void enter(AbstractClientPlayer player, State s, Phase phase, String clip, int fadeTicks,
			float startSeconds) {
		s.phase = phase;
		s.start = System.nanoTime();
		RawAnimation anim = phase == Phase.FALL
				? PlayerRawAnimationBuilder.begin().thenLoop(SoulsMode.id(clip)).build()
				: PlayerRawAnimationBuilder.begin().thenPlayAndHold(SoulsMode.id(clip)).build();
		if (PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof PlayerAnimationController c) {
			if (startSeconds > 0) {
				// replaceAnimationWithFade has no start offset: its fade modifier, then the clip from that time.
				if (fadeTicks > 0) {
					c.addModifierLast(AbstractFadeModifier.standardFadeIn(fadeTicks, EasingType.EASE_IN_OUT_SINE));
				}
				c.triggerAnimation(anim, startSeconds * 20);
			} else if (fadeTicks > 0) {
				c.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fadeTicks, EasingType.EASE_IN_OUT_SINE),
						anim);
			} else {
				c.triggerAnimation(anim);
			}
		}
	}

	private static void leave(AbstractClientPlayer player, State s, int fadeTicks) {
		s.phase = Phase.NONE;
		if (PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof PlayerAnimationController c) {
			if (fadeTicks > 0) {
				c.replaceAnimationWithFade(AbstractFadeModifier.standardFadeOut(fadeTicks, EasingType.EASE_IN_OUT_SINE),
						(RawAnimation) null);
			} else {
				c.stop();
			}
		}
	}

	/** Whether the air layer shows a clip (locomotion then keeps its current loop instead of switching). */
	static boolean airborne(AbstractClientPlayer player) {
		State s = STATES.get(player);
		return s != null && s.phase != Phase.NONE;
	}
}
