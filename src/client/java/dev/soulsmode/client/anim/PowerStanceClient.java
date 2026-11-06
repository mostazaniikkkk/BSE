package dev.soulsmode.client.anim;

import com.google.gson.JsonObject;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.animation.PlayerRawAnimationBuilder;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.enums.PlayState;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.api.SoulsWeapons;
import dev.soulsmode.ds2.Moveset;
import dev.soulsmode.ds2.Weapons;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * SM 0x400, the power stance over locomotion (docs/research/powerstance.md s.4, animations.md "Condición 0x262";
 * weapons.json {@code powerStanceClips}). States 0x401 (one-handed locomotion), 0x442 (right weapon) and 0x46a (left,
 * mirrored); every transition between them lasts {@code blendIn} (0.2 s: 0x4a1 / 0x4a6 in, 0x4a3 / 0x4a4 out,
 * 0x4a7 / 0x4a8 from one side to the other), weighs its destination (1 - cos(pi t / D)) / 2 (FUN_1406334f0) and
 * keeps updating both ends. 0x442 / 0x46a enter on 0x443: a FeatherBlend2 of locomotion with the whole stance clip
 * (clipBones); condition 0x262 on track 80 / event 42 (0..1 of the clip, threshold 0.1) starts the blend (holdBlend:
 * 0.2 s right, 0.1 s left) into 0x450, the clip's last frame (SingleFrame_1) with holdBones, at 0.9 of the clip while
 * the clip keeps playing.
 * <p>
 * Once a side has reached its hold and no transition runs, locomotion switches to the {@code _2w_} clips (the hold
 * baked in) and the layers stop. Until then locomotion stays one-handed and these layers rebuild the states over it:
 * two slots (the transition's two ends), each with a sampler of its clip and one of its held frame, and {@link #LAYER},
 * which mixes per bone: locomotion + w x delta(to) + (1 - w) x delta(from), with delta(side) = (clip - locomotion) x
 * clipBones x (1 - h) + (hold - locomotion) x holdBones x h, w the SM 0x400 transition and h the side's clip -> hold
 * blend. The side last entered is kept, so leaving still fades after the weapon that gave it is gone.
 */
public final class PowerStanceClient {
	public static final ResourceLocation LAYER = SoulsMode.id("power_stance");
	/** Above locomotion and the grip change, below the attacks and the guard; the samplers first. */
	private static final int PRIORITY = 22;
	private static final int SAMPLER_PRIORITY = 21;
	/** Condition 0x262: event 42 spans the whole clip, threshold 0.1 of it left. */
	private static final float HOLD_START = 0.9f;

	/** One end of the SM 0x400 transition: a stance side (0x442 or 0x46a) from when its clip started. */
	private static final class Side {
		ResourceLocation clip;
		float duration;
		float blendIn;
		float holdBlend;
		Map<String, Float> clipBones;
		Map<String, Float> holdBones;
		long start;
		/** The clip's and the held frame's pose per bone, sampled this frame. */
		final Map<String, PlayerAnimBone> clipPose = new HashMap<>();
		final Map<String, PlayerAnimBone> holdPose = new HashMap<>();

		/** Weight of 0x450 (the hold) inside the side. */
		float toHold(long now) {
			return transition(seconds(now - start) - HOLD_START * duration, holdBlend);
		}
	}

	private static final class State {
		final Side[] slots = new Side[2];
		/** Slot of the destination (-1: one-handed locomotion) and of the source (-1: one-handed locomotion). */
		int to = -1;
		int from = -1;
		long since;
		float length;
		/** {@code to} is in its hold with no transition: the {@code _2w_} locomotion carries it, the layers are off. */
		boolean baked;
	}

	private static final Map<AbstractClientPlayer, State> STATES = new WeakHashMap<>();

	private PowerStanceClient() {
	}

	private static ResourceLocation sampler(int slot, boolean hold) {
		return SoulsMode.id("power_stance_" + slot + (hold ? "_hold" : "_clip"));
	}

	public static void init() {
		for (int slot = 0; slot < 2; slot++) {
			for (boolean hold : new boolean[] {false, true}) {
				int s = slot;
				PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(sampler(slot, hold), SAMPLER_PRIORITY,
						player -> {
							PlayerAnimationController controller = new PlayerAnimationController(player,
									(c, state, animSetter) -> PlayState.STOP);
							controller.addModifierLast(new Sampler(player, s, hold));
							return controller;
						});
			}
		}
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> {
			PlayerAnimationController controller = new PlayerAnimationController(player,
					(c, state, animSetter) -> PlayState.STOP);
			controller.addModifierLast(new Mixed(player));
			return controller;
		});
	}

	/** GripClient: the grip of {@code player} changed from {@code before} to {@code after}. */
	static void onGrip(AbstractClientPlayer player, Moveset.Grip before, Moveset.Grip after) {
		long now = System.nanoTime();
		State s = STATES.get(player);
		if (after.powerStance() && before != after) {
			JsonObject json = clip(player, after);
			if (json == null) {
				return;
			}
			Side side = side(json, now);
			int shown = s == null ? -1 : s.to >= 0 ? s.to : s.from;
			if (shown < 0) {
				// 0x401 -> 0x442 / 0x46a.
				s = new State();
				STATES.put(player, s);
				s.to = 0;
				s.from = -1;
			} else {
				// 0x442 <-> 0x46a (or back in while leaving): the side shown so far is the source.
				int other = 1 - shown;
				stopSlot(player, other);
				s.from = shown;
				s.to = other;
				resume(player, s, s.from);
			}
			s.slots[s.to] = side;
			s.since = now;
			s.length = side.blendIn;
			s.baked = false;
			play(player, sampler(s.to, false), PlayerRawAnimationBuilder.begin().thenPlay(side.clip)
					.thenLoop(hold(side.clip)).build());
			play(player, sampler(s.to, true), PlayerRawAnimationBuilder.begin().thenLoop(hold(side.clip)).build());
			play(player, LAYER, PlayerRawAnimationBuilder.begin().thenLoop(hold(side.clip)).build());
		} else if (before.powerStance() && !after.powerStance() && s != null && s.to >= 0) {
			// 0x442 / 0x46a -> 0x401, from the side last entered (the weapon may already be gone).
			s.from = s.to;
			s.to = -1;
			s.since = now;
			s.length = s.slots[s.from].blendIn;
			s.baked = false;
			resume(player, s, s.from);
			play(player, LAYER, PlayerRawAnimationBuilder.begin().thenLoop(hold(s.slots[s.from].clip)).build());
		}
	}

	/** Locomotion stays one-handed while these layers show the stance. */
	static boolean entering(AbstractClientPlayer player) {
		State s = STATES.get(player);
		return s != null && s.to >= 0 && !s.baked;
	}

	/** A side's held frame is sampled again when its layers were off (it was baked into locomotion). */
	private static void resume(AbstractClientPlayer player, State s, int slot) {
		if (s.baked) {
			play(player, sampler(slot, true), PlayerRawAnimationBuilder.begin().thenLoop(hold(s.slots[slot].clip))
					.build());
		}
	}

	private static JsonObject clip(AbstractClientPlayer player, Moveset.Grip grip) {
		boolean left = grip == Moveset.Grip.POWER_STANCE_LEFT;
		var data = dev.soulsmode.ds2.PowerStance.data(dev.soulsmode.SoulsAttack.damageKey(
				SoulsWeapons.movesetFor(left ? player.getOffhandItem() : player.getMainHandItem())));
		if (data == null) {
			return null;
		}
		JsonObject clips = Weapons.raw().getAsJsonObject("powerStanceClips");
		String stance = String.valueOf(left ? data.stanceLeft() : data.stanceRight());
		return clips == null || !clips.has(stance) ? null
				: clips.getAsJsonObject(stance).getAsJsonObject(left ? "left" : "right");
	}

	private static Side side(JsonObject json, long now) {
		Side s = new Side();
		s.start = now;
		s.duration = json.get("duration").getAsFloat();
		s.blendIn = json.get("blendIn").getAsFloat();
		s.holdBlend = json.get("holdBlend").getAsFloat();
		s.clipBones = bones(json.getAsJsonObject("clipBones"));
		s.holdBones = bones(json.getAsJsonObject("holdBones"));
		s.clip = ResourceLocation.parse(json.get("animation").getAsString());
		return s;
	}

	private static Map<String, Float> bones(JsonObject o) {
		Map<String, Float> m = new HashMap<>();
		o.entrySet().forEach(e -> m.put(e.getKey(), e.getValue().getAsFloat()));
		return m;
	}

	private static ResourceLocation hold(ResourceLocation clip) {
		return ResourceLocation.fromNamespaceAndPath(clip.getNamespace(), clip.getPath() + "_hold");
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

	private static void stopSlot(AbstractClientPlayer player, int slot) {
		stop(player, sampler(slot, false));
		stop(player, sampler(slot, true));
	}

	private static void stopAll(AbstractClientPlayer player) {
		stopSlot(player, 0);
		stopSlot(player, 1);
		stop(player, LAYER);
	}

	private static float seconds(long nanos) {
		return nanos / 1e9f;
	}

	/** Destination weight of a Morpheme transition t seconds into its length (FUN_1406334f0). */
	private static float transition(float t, float length) {
		if (t <= 0) {
			return 0;
		}
		return length <= 0 || t >= length ? 1 : (1 - (float) Math.cos(Math.PI * t / length)) / 2;
	}

	/** Samples a slot's clip or held frame for {@link Mixed} and leaves the bone as it was. */
	private static final class Sampler extends AbstractModifier {
		private final AbstractClientPlayer player;
		private final int slot;
		private final boolean hold;

		Sampler(AbstractClientPlayer player, int slot, boolean hold) {
			this.player = player;
			this.slot = slot;
			this.hold = hold;
		}

		@Override
		public PlayerAnimBone get3DTransform(PlayerAnimBone bone) {
			State s = STATES.get(player);
			Side side = s == null ? null : s.slots[slot];
			if (side != null) {
				(hold ? side.holdPose : side.clipPose).put(bone.getName(),
						super.get3DTransform(new PlayerAnimBone(bone.getName()).copyOtherBone(bone)));
			}
			return bone;
		}
	}

	/** SM 0x400's output over one-handed locomotion. */
	private static final class Mixed extends AbstractModifier {
		private final AbstractClientPlayer player;

		Mixed(AbstractClientPlayer player) {
			this.player = player;
		}

		@Override
		public PlayerAnimBone get3DTransform(PlayerAnimBone bone) {
			State s = STATES.get(player);
			if (s == null || s.baked) {
				return bone;
			}
			long now = System.nanoTime();
			float w = transition(seconds(now - s.since), s.length);
			if (w >= 1) {
				if (s.from >= 0) {
					stopSlot(player, s.from);
					s.slots[s.from] = null;
					s.from = -1;
				}
				if (s.to < 0) {
					STATES.remove(player);
					stopAll(player);
					return bone;
				}
				if (s.slots[s.to].toHold(now) >= 1) {
					// In 0x450 with nothing blending: the _2w_ locomotion has it baked.
					s.baked = true;
					stopAll(player);
					return bone;
				}
			}
			float[] base = {bone.rotX, bone.rotY, bone.rotZ, bone.positionX, bone.positionY, bone.positionZ};
			float[] out = base.clone();
			add(out, base, s.to < 0 ? null : s.slots[s.to], bone.getName(), w, now);
			add(out, base, s.from < 0 ? null : s.slots[s.from], bone.getName(), 1 - w, now);
			bone.rotX = out[0];
			bone.rotY = out[1];
			bone.rotZ = out[2];
			// Body position is the pelvis, alpha 0.
			if (!bone.getName().equals("body")) {
				bone.positionX = out[3];
				bone.positionY = out[4];
				bone.positionZ = out[5];
			}
			return bone;
		}

		/** out += weight x delta(side): (clip - base) x clipBones x (1 - h) + (hold - base) x holdBones x h. */
		private static void add(float[] out, float[] base, Side side, String name, float weight, long now) {
			if (side == null || weight <= 0) {
				return;
			}
			float h = side.toHold(now);
			add(out, base, side.clipPose.get(name), side.clipBones.getOrDefault(name, 0f) * (1 - h) * weight);
			add(out, base, side.holdPose.get(name), side.holdBones.getOrDefault(name, 0f) * h * weight);
		}

		private static void add(float[] out, float[] base, PlayerAnimBone pose, float a) {
			if (pose == null || a <= 0) {
				return;
			}
			float[] p = {pose.rotX, pose.rotY, pose.rotZ, pose.positionX, pose.positionY, pose.positionZ};
			for (int i = 0; i < 6; i++) {
				out[i] += (p[i] - base[i]) * a;
			}
		}
	}
}
