package dev.soulsmode.client.anim;

import com.google.gson.JsonObject;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.animation.PlayerRawAnimationBuilder;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.SpeedModifier;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.enums.PlayState;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.ds2.Attack;
import dev.soulsmode.ds2.Weapons;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * SM 0x210e, the guard (docs/research/weapon_arts.md s.8): a layer over the whole body network, locomotion and attacks
 * included. State 0x2110 SM_MoveGuard picks, by GuardStance (the guarding hand) and GuardID_Number (the guard TAE: 31 /
 * 32 one-handed, 33 / 34 two-handed), BT_Guard1Hand_L / _R or BT_Guard2Hand_L / _R. Each FeatherBlend2s the body with
 * the guard clip (switch on GuardID_Category, weapons.json {@code guardLayer.clips}) through a PlaySpeed node at
 * GuardSpeed, then, by condition 0x262 at 0.95 of the clip, blends {@code holdBlend} into a SingleFrame of the clip at
 * {@code holdFrame} (the exported {@code _hold}) while the clip keeps playing (held on its last frame, {@code _end},
 * past its end). Per-bone alphas: {@code bones} (one-handed, the same in clip and hold) or {@code bones2h} (two-handed,
 * clip and hold differ).
 * <p>
 * SM 0x210e enters 0x2110 over {@code enterBlend} (0x232a..0x232f) and leaves it with the exit blend of the guard's
 * GuardCancelSpeedCategory (0x2329 / 0x232c / 0x232d / 0x2330), both weighing the destination (1 - cos(pi t / D)) / 2
 * (FUN_1406334f0); the server sends that blend and GuardSpeed (AttackPayloads.GuardLayer). A guard attack runs in the
 * body network under the layer, so the guard stays up through it.
 * <p>
 * {@link #HOLD_LAYER} samples the held frame without applying it; {@link #LAYER} plays the clip and mixes, per bone,
 * base + ((clip - base) x clipBones x (1 - toHold) + (hold - base) x holdBones x toHold) x envelope.
 */
public final class GuardLayerClient {
	public static final ResourceLocation LAYER = SoulsMode.id("guard_layer");
	public static final ResourceLocation HOLD_LAYER = SoulsMode.id("guard_layer_hold");
	/** Above the action layer (SoulsAnimations, 1500): 0x210e is above the attacks; the hold sampler first. */
	private static final int PRIORITY = 1600;
	private static final int HOLD_PRIORITY = 1599;
	/** Condition 0x262: GuardDuration spans 0.5..1.0 of the clip, threshold 0.1 of it left. */
	private static final float HOLD_START = 0.95f;

	private static final Map<String, String> MIRROR = Map.of("left_arm", "right_arm", "right_arm", "left_arm",
			"left_leg", "right_leg", "right_leg", "left_leg", "left_item", "right_item", "right_item", "left_item");

	private static JsonObject layer;

	/** Per player: the guard on the layer (null fields until a guard went up). */
	private static final class State {
		boolean active;
		long raised;
		long exitStart;
		/** From the server (AttackPayloads.GuardLayer), for the next guard. */
		float exitBlend;
		float speed = 1;
		/** GuardSpeed of the guard on the layer. */
		float playing = 1;
		float duration;
		boolean mirror;
		Map<String, Float> clipBones = Map.of();
		Map<String, Float> holdBones = Map.of();
		/** The held frame per bone, sampled by the hold layer this frame. */
		final Map<String, PlayerAnimBone> hold = new HashMap<>();
		/** Exit blend of the exit running (exitBlend may already hold the next guard's). */
		float leaving;
		/** The layer's output per bone on the last frame (SM 0x210e's): what a dead blend starts from. */
		final Map<String, Pose> last = new HashMap<>();
		/** The exit cut by a re-raise, as a dead blend (null when none). */
		Dead dead;
	}

	/** rotX, rotY, rotZ, positionX, positionY, positionZ of a bone at a time. */
	private record Pose(float[] v, long at) {
	}

	/**
	 * The interrupted exit 0x2110 -> 0x210f after a breakout (weapon_arts.md s.10): its source 0x2110 is deleted and
	 * replaced by a dead pose that the exit keeps blending with 0x210f on its own clock and length (task 0xad,
	 * FUN_140632820; pose task 0xb4). FUN_140634ba0 misses the transition's own cache (exact-frame lookup) and takes
	 * SM 0x210e's output of the last frame, already blended with w0. Whether the frame before it is still there (then
	 * the rotations keep turning at its angular velocity, FUN_140635610 / FUN_140631d20) is not established: the pose
	 * is kept as it was.
	 */
	private static final class Dead {
		final Map<String, float[]> pose = new HashMap<>();
		long exitStart;
		float exitBlend;
	}

	private static final Map<AbstractClientPlayer, State> STATES = new WeakHashMap<>();
	private static final Map<PlayerAnimationController, SpeedModifier> SPEEDS = new WeakHashMap<>();

	private GuardLayerClient() {
	}

	public static void init() {
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(HOLD_LAYER, HOLD_PRIORITY, player -> {
			PlayerAnimationController controller = new PlayerAnimationController(player,
					(c, state, animSetter) -> PlayState.STOP);
			controller.addModifierLast(new HoldSampler(player));
			return controller;
		});
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> {
			PlayerAnimationController controller = new PlayerAnimationController(player,
					(c, state, animSetter) -> PlayState.STOP);
			SpeedModifier speed = new SpeedModifier(1);
			controller.addModifierLast(new Weighted(player));
			controller.addModifierLast(speed);
			SPEEDS.put(controller, speed);
			return controller;
		});
	}

	private static synchronized JsonObject layer() {
		if (layer == null) {
			layer = Weapons.raw().getAsJsonObject("guardLayer");
		}
		return layer;
	}

	/** The server's values for this player's next guard (AttackPayloads.GuardLayer). */
	public static void values(AbstractClientPlayer player, float exitBlend, float speed) {
		State s = STATES.computeIfAbsent(player, p -> new State());
		s.exitBlend = exitBlend;
		s.speed = speed;
	}

	/** The layer holds a guard and is not leaving (a guard Start then changes nothing: 0x2110 never left). */
	public static boolean continuing(AbstractClientPlayer player) {
		State s = STATES.get(player);
		return s != null && s.active && s.exitStart == 0;
	}

	/**
	 * A guard went up: its clip from the start, entering the layer. {@code weaponLeft} is the guarding hand (GuardStance
	 * -1). A DS2 guard plays the clip its BT's switch picks (weapons.json, nearest category: the torch in the right hand
	 * gets a44); a datapack guard ({@code <animation>_hold} its held pose) takes the BT of its grip at speed 1.
	 */
	public static void raise(AbstractClientPlayer player, Attack guard, boolean weaponLeft, boolean twoHanded) {
		JsonObject l = layer();
		String side = weaponLeft ? "left" : "right";
		ResourceLocation clip;
		ResourceLocation end;
		boolean mirror;
		boolean twoHandedBt;
		float duration;
		JsonObject sides = l.getAsJsonObject("clips").getAsJsonObject(String.valueOf(guard.taeId()));
		if (guard.taeId() != 0) {
			JsonObject entry = sides == null ? null : sides.getAsJsonObject(side);
			if (entry == null) {
				stopNow(player);
				return;
			}
			clip = ResourceLocation.parse(entry.get("animation").getAsString());
			mirror = entry.get("mirror").getAsBoolean();
			twoHandedBt = entry.get("twoHanded").getAsBoolean();
			// Condition 0x262 reads the picked clip's own GuardDuration, so its length (not the slot's TAE).
			duration = entry.get("duration").getAsFloat();
			end = suffixed(clip, "_end");
		} else {
			clip = ResourceLocation.parse(guard.animation());
			mirror = guard.mirroredFor(weaponLeft);
			twoHandedBt = twoHanded;
			end = suffixed(clip, "_hold");
			duration = guard.duration();
		}
		State s = STATES.computeIfAbsent(player, p -> new State());
		if (s.active && s.exitStart != 0) {
			// Breakout (FUN_1405fb3f0, 0x210f's +0x20 and the transitions' flag 0x200): the exit becomes the source of
			// a new 0.1 s entry, and 0x2110 restarts from its default state (FUN_1405fa200 deletes it).
			s.dead = dead(s);
		} else {
			s.dead = null;
		}
		s.active = true;
		s.raised = System.nanoTime();
		s.exitStart = 0;
		s.playing = guard.taeId() != 0 && s.speed > 0 ? s.speed : 1;
		s.duration = duration;
		s.mirror = mirror;
		s.hold.clear();
		if (twoHandedBt) {
			JsonObject bones = l.getAsJsonObject("bones2h").getAsJsonObject(side);
			s.clipBones = bones(bones.getAsJsonObject("clip"));
			s.holdBones = bones(bones.getAsJsonObject("hold"));
		} else {
			s.clipBones = bones(l.getAsJsonObject("bones").getAsJsonObject(side));
			s.holdBones = s.clipBones;
		}
		if (controller(player, LAYER) instanceof PlayerAnimationController c) {
			SpeedModifier speed = SPEEDS.get(c);
			if (speed != null) {
				speed.speed = s.playing;
			}
			c.triggerAnimation(PlayerRawAnimationBuilder.begin().thenPlay(clip).thenLoop(end).build());
		}
		if (controller(player, HOLD_LAYER) instanceof PlayerAnimationController c) {
			c.triggerAnimation(PlayerRawAnimationBuilder.begin().thenLoop(suffixed(clip, "_hold")).build());
		}
	}

	/** The guard dropped (GuardStance 0): leave with its exit blend, at once without one. */
	public static void stop(AbstractClientPlayer player) {
		State s = STATES.get(player);
		if (s == null || !s.active) {
			return;
		}
		if (s.exitBlend <= 0) {
			stopNow(player);
		} else if (s.exitStart == 0) {
			s.exitStart = System.nanoTime();
			s.leaving = s.exitBlend;
		}
	}

	public static void stopNow(AbstractClientPlayer player) {
		State s = STATES.get(player);
		if (s != null) {
			s.active = false;
			s.exitStart = 0;
			s.dead = null;
			s.last.clear();
		}
		for (ResourceLocation id : new ResourceLocation[] {LAYER, HOLD_LAYER}) {
			if (controller(player, id) instanceof PlayerAnimationController c) {
				c.stop();
			}
		}
	}

	private static Object controller(AbstractClientPlayer player, ResourceLocation id) {
		return PlayerAnimationAccess.getPlayerAnimationLayer(player, id);
	}

	private static ResourceLocation suffixed(ResourceLocation id, String suffix) {
		return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + suffix);
	}

	private static Map<String, Float> bones(JsonObject o) {
		Map<String, Float> m = new HashMap<>();
		o.entrySet().forEach(e -> m.put(e.getKey(), e.getValue().getAsFloat()));
		return m;
	}

	/** SM 0x210e's weight on 0x2110 now: the enter blend times what is left of the exit blend; 0 once it ended. */
	private static float envelope(AbstractClientPlayer player, State s, long now) {
		float w = transition((now - s.raised) / 1e9f, layer().get("enterBlend").getAsFloat());
		if (s.exitStart != 0) {
			float out = transition((now - s.exitStart) / 1e9f, s.leaving);
			if (out >= 1) {
				stopNow(player);
				return 0;
			}
			w *= 1 - out;
		}
		return w;
	}

	/** The dead blend of the exit running now, from the layer's output of the last frame. */
	private static Dead dead(State s) {
		Dead d = new Dead();
		d.exitStart = s.exitStart;
		d.exitBlend = s.leaving;
		s.last.forEach((bone, pose) -> d.pose.put(bone, pose.v().clone()));
		return d;
	}

	/** Destination weight of a Morpheme transition t seconds into its length (FUN_1406334f0). */
	private static float transition(float t, float length) {
		if (t <= 0) {
			return 0;
		}
		return length <= 0 || t >= length ? 1 : (1 - (float) Math.cos(Math.PI * t / length)) / 2;
	}

	/** The animation's pose for {@code bone}, through Morpheme's mirror node (0x87) when {@code mirror}. */
	private static PlayerAnimBone sample(PlayerAnimBone bone, boolean mirror,
			java.util.function.UnaryOperator<PlayerAnimBone> next) {
		String name = bone.getName();
		if (!mirror) {
			return next.apply(new PlayerAnimBone(name).copyOtherBone(bone));
		}
		PlayerAnimBone source = new PlayerAnimBone(MIRROR.getOrDefault(name, name)).copyOtherBone(bone);
		flip(source);
		PlayerAnimBone animated = next.apply(source);
		flip(animated);
		return animated;
	}

	/** PAL's MirrorModifier transform. */
	private static void flip(PlayerAnimBone bone) {
		bone.positionX = -bone.positionX;
		bone.rotY = -bone.rotY;
		bone.rotZ = -bone.rotZ;
		bone.bend = -bone.bend;
	}

	/** Samples the held frame for {@link Weighted} and leaves the bone as it was. */
	private static final class HoldSampler extends AbstractModifier {
		private final AbstractClientPlayer player;

		HoldSampler(AbstractClientPlayer player) {
			this.player = player;
		}

		@Override
		public PlayerAnimBone get3DTransform(PlayerAnimBone bone) {
			State s = STATES.get(player);
			if (s != null && s.active) {
				s.hold.put(bone.getName(), sample(bone, s.mirror, super::get3DTransform));
			}
			return bone;
		}
	}

	/** The BT's clip and hold states over what is below, weighed by SM 0x210e. */
	private static final class Weighted extends AbstractModifier {
		private final AbstractClientPlayer player;

		Weighted(AbstractClientPlayer player) {
			this.player = player;
		}

		@Override
		public PlayerAnimBone get3DTransform(PlayerAnimBone bone) {
			State s = STATES.get(player);
			if (s == null || !s.active) {
				return bone;
			}
			long now = System.nanoTime();
			float envelope = envelope(player, s, now);
			if (!s.active) {
				return bone;
			}
			String name = bone.getName();
			// Condition 0x262 reads the clip's own clock, which runs at GuardSpeed; the blend itself is real time.
			float toHold = transition((now - s.raised) / 1e9f - HOLD_START * s.duration / s.playing,
					layer().get("holdBlend").getAsFloat());
			float a = s.clipBones.getOrDefault(name, 0f) * (1 - toHold);
			float b = s.holdBones.getOrDefault(name, 0f) * toHold;
			PlayerAnimBone hold = s.hold.get(name);
			if (hold == null) {
				b = 0;
				hold = bone;
			}
			PlayerAnimBone clip = a > 0 ? sample(bone, s.mirror, super::get3DTransform) : bone;
			float[] base = {bone.rotX, bone.rotY, bone.rotZ, bone.positionX, bone.positionY, bone.positionZ};
			float[] c = {clip.rotX, clip.rotY, clip.rotZ, clip.positionX, clip.positionY, clip.positionZ};
			float[] h = {hold.rotX, hold.rotY, hold.rotZ, hold.positionX, hold.positionY, hold.positionZ};
			// 0x2110: the BT over what is below. Body position is the pelvis, alpha 0.
			float[] guard = new float[6];
			for (int i = 0; i < 6; i++) {
				boolean pelvis = i >= 3 && name.equals("body");
				guard[i] = pelvis ? base[i] : base[i] + (c[i] - base[i]) * a + (h[i] - base[i]) * b;
			}
			float[] out = new float[6];
			Dead d = s.dead;
			float enter = transition((now - s.raised) / 1e9f, layer().get("enterBlend").getAsFloat());
			float oldExit = d == null ? 1 : transition((now - d.exitStart) / 1e9f, d.exitBlend);
			if (d != null && (enter >= 1 || oldExit >= 1)) {
				s.dead = null;
				d = null;
			}
			float[] dp = d == null ? null : d.pose.get(name);
			if (dp == null) {
				for (int i = 0; i < 6; i++) {
					out[i] = base[i] + (guard[i] - base[i]) * envelope;
				}
			} else {
				// Blend2(dead pose, 0x210f, w of the old exit), then the new entry towards the fresh 0x2110.
				float exit = s.exitStart == 0 ? 1 : 1 - transition((now - s.exitStart) / 1e9f, s.leaving);
				for (int i = 0; i < 6; i++) {
					float old = dp[i] + (base[i] - dp[i]) * oldExit;
					float in = old + (guard[i] - old) * enter;
					// A new exit during this entry fades the lot (not traced).
					out[i] = base[i] + (in - base[i]) * exit;
				}
			}
			s.last.put(name, new Pose(out, now));
			bone.rotX = out[0];
			bone.rotY = out[1];
			bone.rotZ = out[2];
			bone.positionX = out[3];
			bone.positionY = out[4];
			bone.positionZ = out[5];
			return bone;
		}
	}
}
