package dev.soulsmode.client.anim;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.animation.PlayerRawAnimationBuilder;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.enums.PlayState;
import dev.soulsmode.SoulsMode;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * BT_Move's additive landing layer (docs/research/movement.md "Salto y caída: animación"), weighed by LandingRate r:
 * a pulse that a flagged landing starts (AirClient: after a jump, or a walk-off fall of at least 0.5 m), rising over
 * 0.1 s as A sin(phi pi / 2) and decaying over D as A (1 - sin(phi pi / 2)), A = max(base, min(R, 1)),
 * R = lerp(0.8, 1.25, t) clamp(drop / 4.0, 0, 1), t = clamp(equip load % / 200, 0, 1); a fall: base 0.10,
 * D = lerp(0.5, 1.5, t); a jump: base 0.70, D = lerp(0.7, 1.0, t). The layer enters over 0.001 s and leaves over 0.1 s
 * once r is back at 0, both weighing the destination (1 - cos(pi t / T)) / 2.
 * <p>
 * Over locomotion, per bone: the crouch additive (AdditiveStand -> AdditiveCrouch at r / 10), HipsIK (pelvis r (0, 0.1,
 * -0.4) m and slerp(I, Euler(25, 0, 0), r) in model space, legs kept on the feet) and the shake additive
 * (AdditiveShakeLanding at per-bone alphas, slow at <= 3.4 m/s horizontal speed or fast; slow -> fast over 0.1 s,
 * fast -> slow over 0.3 s). tools/re/ds2_anims.py bakes each at r = 1 over AdditiveStand through the retarget
 * (ds2_landing_stand / _crouch / _hips / _shake_slow / _shake_fast); here each Minecraft bone takes their rotation
 * deltas from ds2_landing_stand as quaternions in the bones' own space (R = Rz Ry Rx of the PAL angles), scaled by r
 * (crouch, hips), and lays them on the left of locomotion in DS2's order (shake o hips o crouch o locomotion); the body
 * position adds r times the pelvis offsets. The deltas are taken over AdditiveStand, not over the locomotion pose they
 * land on (the retarget composes several DS2 bones per Minecraft bone): an approximation.
 */
public final class LandingShakeClient {
	public static final ResourceLocation LAYER = SoulsMode.id("landing_shake");
	/** Above locomotion (5) and the air layer (6), below the grip change (20); the samplers first. */
	private static final int PRIORITY = 8;
	private static final int SAMPLER_PRIORITY = 7;
	private static final float RISE = 0.1f;
	private static final float ENTER = 0.001f;
	private static final float EXIT = 0.1f;
	private static final float TO_FAST = 0.1f;
	private static final float TO_SLOW = 0.3f;
	private static final double SLOW_SPEED = 3.4;
	private static final double DROP_FULL = 4.0;

	private enum Pose {
		STAND("ds2_landing_stand", true), CROUCH("ds2_landing_crouch", true), HIPS("ds2_landing_hips", true),
		SLOW("ds2_landing_shake_slow", false), FAST("ds2_landing_shake_fast", false);

		final String clip;
		final boolean loop;

		Pose(String clip, boolean loop) {
			this.clip = clip;
			this.loop = loop;
		}

		ResourceLocation layer() {
			return SoulsMode.id("landing_shake_" + name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	private static final Set<String> BONES = Set.of("body", "head", "right_arm", "left_arm", "right_leg", "left_leg",
			"right_item", "left_item");

	private static final class State {
		boolean active;
		/** Start of the pulse (the landing) and of the layer's entry (the shake clip's time 0). */
		long pulse;
		long entered;
		float amplitude;
		float decay;
		/** Slow / fast switch: the fast weight when the last switch started, its target and length. */
		boolean fast;
		long switched;
		float switchFrom;
		float switchLength;
		final Map<Pose, Map<String, PlayerAnimBone>> samples = new java.util.EnumMap<>(Pose.class);
	}

	private static final Map<AbstractClientPlayer, State> STATES = new WeakHashMap<>();

	private LandingShakeClient() {
	}

	public static void init() {
		for (Pose pose : Pose.values()) {
			PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(pose.layer(), SAMPLER_PRIORITY, player -> {
				PlayerAnimationController controller = new PlayerAnimationController(player,
						(c, state, animSetter) -> PlayState.STOP);
				controller.addModifierLast(new Sampler(player, pose));
				return controller;
			});
		}
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> {
			PlayerAnimationController controller = new PlayerAnimationController(player,
					(c, state, animSetter) -> PlayState.STOP);
			controller.addModifierLast(new Mixed(player));
			return controller;
		});
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

	/**
	 * AirClient: a flagged landing, {@code drop} metres below the apex, after a jump or a fall. The pulse restarts; the
	 * layer (and the shake clip) restarts unless r was still above 0.
	 */
	static void land(AbstractClientPlayer player, double drop, boolean afterJump) {
		// Equip load % / 200: the server sends only the local player's (InventoryStats); others are taken as 0.
		float t = player == Minecraft.getInstance().player
				? clamp(dev.soulsmode.client.InventoryStats.loadRatio() * 100 / 200f) : 0;
		float rate = lerp(0.8f, 1.25f, t) * clamp((float) (drop / DROP_FULL));
		float base = afterJump ? 0.70f : 0.10f;
		State s = STATES.computeIfAbsent(player, p -> new State());
		long now = System.nanoTime();
		boolean running = s.active && rate(s, now) > 0;
		s.amplitude = Math.max(base, Math.min(rate, 1));
		s.decay = afterJump ? lerp(0.7f, 1.0f, t) : lerp(0.5f, 1.5f, t);
		s.pulse = now;
		if (running) {
			return;
		}
		s.active = true;
		s.entered = now;
		s.fast = fast(player);
		s.switchFrom = s.fast ? 1 : 0;
		s.switched = now;
		s.switchLength = 0;
		s.samples.clear();
		for (Pose pose : Pose.values()) {
			ResourceLocation id = SoulsMode.id(pose.clip);
			play(player, pose.layer(), pose.loop ? PlayerRawAnimationBuilder.begin().thenLoop(id).build()
					: PlayerRawAnimationBuilder.begin().thenPlayAndHold(id).build());
		}
		play(player, LAYER, PlayerRawAnimationBuilder.begin().thenLoop(SoulsMode.id(Pose.STAND.clip)).build());
	}

	/** Off at once (Souls Mode off, swimming, ...). */
	static void stop(AbstractClientPlayer player) {
		State s = STATES.get(player);
		if (s != null && s.active) {
			s.active = false;
			stopLayers(player);
		}
	}

	private static void tick(AbstractClientPlayer player) {
		State s = STATES.get(player);
		if (s == null || !s.active) {
			return;
		}
		boolean fast = fast(player);
		if (fast != s.fast) {
			long now = System.nanoTime();
			s.switchFrom = fastWeight(s, now);
			s.fast = fast;
			s.switched = now;
			s.switchLength = fast ? TO_FAST : TO_SLOW;
		}
	}

	private static boolean fast(AbstractClientPlayer player) {
		return Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) * 20 > SLOW_SPEED;
	}

	/** LandingRate now. */
	private static float rate(State s, long now) {
		float t = seconds(now - s.pulse);
		if (t <= 0) {
			return 0;
		}
		if (t < RISE) {
			return s.amplitude * (float) Math.sin(t / RISE * Math.PI / 2);
		}
		if (t < RISE + s.decay) {
			return s.amplitude * (1 - (float) Math.sin((t - RISE) / s.decay * Math.PI / 2));
		}
		return 0;
	}

	/** The layer's weight: its entry, times what is left of its exit once r is back at 0; 0 once it left. */
	private static float weight(State s, long now) {
		float w = transition(seconds(now - s.entered), ENTER);
		float out = seconds(now - s.pulse) - RISE - s.decay;
		return out <= 0 ? w : w * (1 - transition(out, EXIT));
	}

	private static float fastWeight(State s, long now) {
		float to = s.fast ? 1 : 0;
		return s.switchFrom + (to - s.switchFrom) * transition(seconds(now - s.switched), s.switchLength);
	}

	private static void stopLayers(AbstractClientPlayer player) {
		for (Pose pose : Pose.values()) {
			stop(player, pose.layer());
		}
		stop(player, LAYER);
	}

	private static void play(AbstractClientPlayer player, ResourceLocation layer, RawAnimation anim) {
		if (PlayerAnimationAccess.getPlayerAnimationLayer(player, layer) instanceof PlayerAnimationController c) {
			c.triggerAnimation(anim);
		}
	}

	private static void stop(AbstractClientPlayer player, ResourceLocation layer) {
		if (PlayerAnimationAccess.getPlayerAnimationLayer(player, layer) instanceof PlayerAnimationController c) {
			c.stop();
		}
	}

	private static float seconds(long nanos) {
		return nanos / 1e9f;
	}

	private static float clamp(float v) {
		return Math.max(0, Math.min(1, v));
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** Destination weight of a Morpheme transition t seconds into its length (FUN_1406334f0). */
	private static float transition(float t, float length) {
		if (t <= 0) {
			return length <= 0 ? 1 : 0;
		}
		return length <= 0 || t >= length ? 1 : (1 - (float) Math.cos(Math.PI * t / length)) / 2;
	}

	/** R = Rz Ry Rx of the bone's angles (ModelPart / PAL order). */
	private static Quaternionf rotation(PlayerAnimBone bone) {
		return new Quaternionf().rotationZYX(bone.rotZ, bone.rotY, bone.rotX);
	}

	/** {@code pose} relative to {@code stand}, on the left: pose stand^-1. */
	private static Quaternionf delta(PlayerAnimBone pose, Quaternionf standInverse) {
		return rotation(pose).mul(standInverse);
	}

	/** Samples a pose for {@link Mixed} and leaves the bone as it was. */
	private static final class Sampler extends AbstractModifier {
		private final AbstractClientPlayer player;
		private final Pose pose;

		Sampler(AbstractClientPlayer player, Pose pose) {
			this.player = player;
			this.pose = pose;
		}

		@Override
		public PlayerAnimBone get3DTransform(PlayerAnimBone bone) {
			State s = STATES.get(player);
			if (s != null && s.active && BONES.contains(bone.getName())) {
				s.samples.computeIfAbsent(pose, p -> new HashMap<>()).put(bone.getName(),
						super.get3DTransform(new PlayerAnimBone(bone.getName()).copyOtherBone(bone)));
			}
			return bone;
		}
	}

	/** The landing layer's output over locomotion. */
	private static final class Mixed extends AbstractModifier {
		private final AbstractClientPlayer player;

		Mixed(AbstractClientPlayer player) {
			this.player = player;
		}

		@Override
		public PlayerAnimBone get3DTransform(PlayerAnimBone bone) {
			State s = STATES.get(player);
			if (s == null || !s.active) {
				return bone;
			}
			long now = System.nanoTime();
			float w = weight(s, now);
			if (w <= 0 && seconds(now - s.pulse) > RISE + s.decay) {
				s.active = false;
				stopLayers(player);
				return bone;
			}
			String name = bone.getName();
			if (!BONES.contains(name)) {
				return bone;
			}
			PlayerAnimBone stand = sample(s, Pose.STAND, name);
			PlayerAnimBone crouch = sample(s, Pose.CROUCH, name);
			PlayerAnimBone hips = sample(s, Pose.HIPS, name);
			PlayerAnimBone slow = sample(s, Pose.SLOW, name);
			PlayerAnimBone fastPose = sample(s, Pose.FAST, name);
			if (stand == null || crouch == null || hips == null || slow == null || fastPose == null) {
				return bone;
			}
			float r = rate(s, now);
			Quaternionf standInverse = rotation(stand).conjugate();
			Quaternionf base = rotation(bone);
			Quaternionf crouchDelta = new Quaternionf().slerp(delta(crouch, standInverse), r);
			Quaternionf hipsDelta = new Quaternionf().slerp(delta(hips, standInverse), r);
			Quaternionf shake = delta(slow, standInverse).slerp(delta(fastPose, standInverse), fastWeight(s, now));
			Quaternionf out = shake.mul(hipsDelta).mul(crouchDelta).mul(base);
			Quaternionf blended = new Quaternionf(base).slerp(out, w);
			float[] e = closest(blended.getEulerAnglesZYX(new Vector3f()), bone);
			// Positions: only the crouch's and HipsIK's (the shake adds none).
			float k = w * r;
			bone.positionX += k * (crouch.positionX + hips.positionX - 2 * stand.positionX);
			bone.positionY += k * (crouch.positionY + hips.positionY - 2 * stand.positionY);
			bone.positionZ += k * (crouch.positionZ + hips.positionZ - 2 * stand.positionZ);
			bone.rotX = e[0];
			bone.rotY = e[1];
			bone.rotZ = e[2];
			return bone;
		}

		private static PlayerAnimBone sample(State s, Pose pose, String name) {
			Map<String, PlayerAnimBone> m = s.samples.get(pose);
			return m == null ? null : m.get(name);
		}

		/**
		 * Of the two Euler triples for the rotation, (x, y, z) and (x + pi, pi - y, z + pi), the one closest to the bone's
		 * angles, unwrapped by whole turns: the layers above blend the angles linearly.
		 */
		private static float[] closest(Vector3f e, PlayerAnimBone bone) {
			float[] prev = {bone.rotX, bone.rotY, bone.rotZ};
			float pi = (float) Math.PI;
			float[][] candidates = {{e.x, e.y, e.z}, {e.x + pi, pi - e.y, e.z + pi}};
			float[] best = null;
			float bestDistance = Float.MAX_VALUE;
			for (float[] c : candidates) {
				float distance = 0;
				for (int i = 0; i < 3; i++) {
					c[i] += 2 * pi * Math.round((prev[i] - c[i]) / (2 * pi));
					distance += (prev[i] - c[i]) * (prev[i] - c[i]);
				}
				if (distance < bestDistance) {
					bestDistance = distance;
					best = c;
				}
			}
			return best;
		}
	}
}
