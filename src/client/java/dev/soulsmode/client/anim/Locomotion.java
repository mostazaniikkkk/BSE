package dev.soulsmode.client.anim;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.animation.PlayerRawAnimationBuilder;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.enums.PlayState;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.client.StaminaHud;
import dev.soulsmode.client.camera.LockOn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Souls Mode locomotion: idle / walk / jog / dash loops whose cycle lengths and foot plants come from DS2
 * (c0001.nmb Leftsteps/Rightsteps, .nsa +0x28; see docs/research/animations.md). Poses are our own.
 * Gait mapping is a design choice: normal movement = Jog (DS2's full-stick gait), sprint = Dash, sneak = Walk.
 */
public final class Locomotion {
	/** Game-feel switch. */
	public static final boolean ENABLED = true;
	public static final ResourceLocation LAYER = SoulsMode.id("locomotion");
	/** PAL convention: 0..10 for idle/walk/run poses, below emotes and gameplay actions (the roll layer is 1500). */
	public static final int PRIORITY = 5;

	/**
	 * Built on first use: PAL looks the animations up when building, and they are only loaded with the
	 * resources, after mod init.
	 */
	private static final java.util.Map<String, RawAnimation> CACHE = new java.util.HashMap<>();
	/** Locked-on side walk (a00_06_0011..0014) and side jog (a00_07_0011..0014): forward, back, left, right. */
	private static final String[] SIDE_WALK = {"ds2_side_walk_forward", "ds2_side_walk_back", "ds2_side_walk_left",
			"ds2_side_walk_right"};
	private static final String[] SIDE_JOG = {"ds2_side_jog_forward", "ds2_side_jog_back", "ds2_side_jog_left",
			"ds2_side_jog_right"};

	private Locomotion() {
	}

	public static void init() {
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY,
				player -> new PlayerAnimationController(player, (controller, state, animSetter) -> {
					RawAnimation animation = pick(player, state.isMovingLenient());
					if (animation != null && player.onGround()) {
						remember(player, animation);
					}
					return animation == null ? PlayState.STOP : animSetter.setAnimation(animation);
				}));
	}

	private static RawAnimation pick(AbstractClientPlayer player, boolean moving) {
		// Other players' Souls Mode flag comes from the server (SoulsPresence).
		if (!ENABLED || !dev.soulsmode.client.PresenceClient.soulsMode(player)) {
			return null;
		}
		if (player.isSwimming() || player.isFallFlying() || player.isPassenger()) {
			return null;
		}
		if (!player.onGround()) {
			// In the air the jump / fall layer (AirClient) shows DS2's SM_Jump / SM_Fall over locomotion, which keeps its
			// loop so landing does not restart it.
			return LAST.get(player);
		}
		// Two-handing the right weapon: the clips with the weapon's stance layered on (AttackStance_LR, SM 0x3bc).
		String suffix = GripClient.stanceSuffix(player);
		if (!moving) {
			return loop("ds2_idle" + suffix);
		}
		if (player.isSprinting()) {
			return loop("ds2_dash" + suffix);
		}
		if (player == Minecraft.getInstance().player && LockOn.isLocked()) {
			// DS2 drives the side walk with FB_Speed / LR_Speed in the character's frame (FUN_140363a20); how its blend
			// tree mixes the four clips is not decoded, so the clip of the dominant direction plays.
			String[] set = player.isShiftKeyDown() ? SIDE_WALK : SIDE_JOG;
			double yaw = Math.toRadians(player.getYRot());
			var v = player.getDeltaMovement();
			double forward = v.x * -Math.sin(yaw) + v.z * Math.cos(yaw);
			double left = v.x * Math.cos(yaw) + v.z * Math.sin(yaw);
			if (Math.abs(forward) >= Math.abs(left)) {
				return loop((forward >= 0 ? set[0] : set[1]) + suffix);
			}
			return loop((left >= 0 ? set[2] : set[3]) + suffix);
		}
		return loop((player.isShiftKeyDown() ? "ds2_walk" : "ds2_jog") + suffix);
	}

	/** The loop each player played last on the ground, and its clip name. */
	private static final java.util.Map<AbstractClientPlayer, RawAnimation> LAST = new java.util.WeakHashMap<>();
	private static final java.util.Map<RawAnimation, String> NAMES = new java.util.HashMap<>();

	/** The locomotion clip (ds2_walk, ds2_jog_2h_..., ...) the player plays now, or null. */
	static String clip(AbstractClientPlayer player) {
		RawAnimation last = LAST.get(player);
		return last == null ? null : NAMES.get(last);
	}

	private static RawAnimation remember(AbstractClientPlayer player, RawAnimation animation) {
		LAST.put(player, animation);
		return animation;
	}

	private static RawAnimation loop(String name) {
		// A stance variant with no clip (a weapon whose stance is not exported, or a grip state lagging a weapon change)
		// falls back to the plain clip instead of crashing the renderer.
		int stance = Math.max(name.indexOf("_2h_"), name.indexOf("_2w_"));
		if (stance > 0 && !com.zigythebird.playeranim.animation.PlayerAnimResources.hasAnimation(SoulsMode.id(name))) {
			name = name.substring(0, stance);
		}
		return CACHE.computeIfAbsent(name, n -> {
			RawAnimation a = PlayerRawAnimationBuilder.begin().thenLoop(SoulsMode.id(n)).build();
			NAMES.put(a, n);
			return a;
		});
	}
}
