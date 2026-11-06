package dev.soulsmode.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.network.ZombieAttackPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Plays the Hollow Soldier's attack animations on zombies (SoulsZombies; docs/research/c1020_anims.md): the c1020
 * clips decoded from DS2's .nsa and retargeted to Minecraft's humanoid model (assets/soulsmode/enemy_animations).
 * Limb rotations replace the model's own (degrees, ModelPart xRot / yRot / zRot); {@code body} turns and moves the
 * whole model about the hip, like Player Animation Library's body bone.
 */
public final class ZombieAnimClient {
	/**
	 * One clip: per bone, time -> {x, y, z}. {@code hips}: a constant rotation of the hips in the renderer's space
	 * (HipsIK, reactions), the legs turned back so the feet stay; null without one. {@code rig}: "c3000" for the Ogre's
	 * clips on the iron golem (hip pivot 11 px, arms about the shoulders), else null.
	 */
	private record Clip(float length, Map<String, NavigableMap<Float, float[]>> rotations,
			NavigableMap<Float, float[]> bodyPosition, org.joml.Quaternionf hips, String rig) {
		Clip(float length, Map<String, NavigableMap<Float, float[]>> rotations, NavigableMap<Float, float[]> bodyPosition) {
			this(length, rotations, bodyPosition, null, null);
		}
	}

	/** The Ogre rig's tag (ogre_anims.py). */
	private static final String C3000 = "c3000";

	/** A clip from {@code from} seconds on, looped while {@code loop} until the next stage. */
	private record Stage(Clip clip, float from, boolean loop) {
	}

	private record Playing(java.util.List<Stage> stages, float end, long startTick) {
		Clip clip() {
			return stages.get(0).clip();
		}
	}

	/** The payload's name for the parried sequence of a humanoid mob (SoulsAttack.onParried). */
	public static final String PARRIED = "parried";
	private static final Map<String, Clip> CLIPS = new HashMap<>();
	private static final Map<Integer, Playing> PLAYING = new HashMap<>();
	/** The Last Giant's weapon mode per entity (SoulsGiants: "mode:1" shows the log). */
	private static final Map<Integer, Integer> MODES = new HashMap<>();
	/** held_arm's position channel of the clips that have one (c3096 mode-1 clips, last_giant.md s.3). */
	private static final Map<Clip, NavigableMap<Float, float[]>> HELD_POSITIONS = new java.util.IdentityHashMap<>();

	private ZombieAnimClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ZombieAttackPayload.TYPE, (payload, context) -> {
			if (payload.animation().isEmpty() || context.client().level == null) {
				PLAYING.remove(payload.entityId());
				return;
			}
			long now = context.client().level.getGameTime();
			if (payload.animation().startsWith(dev.soulsmode.SoulsGiants.MODE_MESSAGE)) {
				MODES.put(payload.entityId(), Integer.parseInt(payload.animation()
						.substring(dev.soulsmode.SoulsGiants.MODE_MESSAGE.length())));
				return;
			}
			if (PARRIED.equals(payload.animation())) {
				// The parried sequence (SoulsAttack.onParried): DmgParryBreak Start, the loop until the riposte window
				// closes, then End, the same clips and times as a parried player (VictimPayload).
				var times = dev.soulsmode.ds2.Weapons.parried();
				Clip start = clip("player:ds2_guard_break");
				Clip loop = clip("player:ds2_parried_loop");
				Clip end = clip("player:ds2_parried_end");
				if (start != null && loop != null && end != null) {
					PLAYING.put(payload.entityId(), new Playing(java.util.List.of(new Stage(start, 0, false),
							new Stage(loop, start.length(), true), new Stage(end, times.riposteUntil(), false)),
							Math.max(times.moveAt(), times.attackAt()), now));
				}
				return;
			}
			if (payload.animation().startsWith("react:")) {
				Clip clip = reaction(payload.animation().substring(6).split(":"));
				if (clip != null) {
					PLAYING.put(payload.entityId(), new Playing(java.util.List.of(new Stage(clip, 0, false)), clip.length(),
							now));
				}
				return;
			}
			// "loop:<clip>": a locomotion loop until replaced (SoulsInvader).
			boolean loop = payload.animation().startsWith("loop:");
			Clip clip = clip(loop ? payload.animation().substring(5) : payload.animation());
			if (clip != null) {
				PLAYING.put(payload.entityId(), new Playing(java.util.List.of(new Stage(clip, 0, loop)),
						loop ? Float.MAX_VALUE : clip.length(), now));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			PLAYING.clear();
			MODES.clear();
		});
	}

	/** Minecraft bones the upper / lower body of a damage FeatherBlend2 drives (body rotation = Spine1: upper). */
	private static final String[] UPPER = {"head", "right_arm", "left_arm", "body"};
	private static final String[] LOWER = {"right_leg", "left_leg"};
	/** Sampling step of a built reaction, in clip time. */
	private static final float STEP = 1 / 60f;

	/** One clip with a weight. */
	private record Part(Clip clip, float weight) {
	}

	/**
	 * A damage reaction (SoulsHitReaction, docs/research/hit_reaction.md s.9) built from DS2's clips the way the
	 * network blends them, played at the hit's speed:
	 * <ul>
	 * <li>{@code c1020:<up>:<back>:<heavy>:<speed>}: Hollow Soldier state 0x61c; upper body the Light clip, lower body
	 * Light <-> Heavy at {@code heavy} (DmgDistance / 4); switches DmgDownOrUp and front / back.</li>
	 * <li>{@code c3000:...}: the same for the Ogre's state 0x284 (the same tree over c3000's clips).</li>
	 * <li>{@code c0001:<category>:<thrust>:<up>:<angle>:<heavy>:<speed>}: BT_Damage 0x1924 / 0x1a92; upper body the
	 * pure Light (30) or Heavy (40) set, lower body Light <-> Heavy at {@code heavy} (DmgDistance / 5); each set the
	 * BlendN of its four quadrants by DmgDirection {@code angle} (degrees, positive = attacker on the right).</li>
	 * </ul>
	 */
	private static Clip reaction(String[] a) {
		try {
			List<Part> upper;
			List<Part> lightSet;
			List<Part> heavySet;
			org.joml.Quaternionf hips = null;
			float heavy;
			float speed;
			if (a[0].equals("c1020") || a[0].equals(C3000)) {
				boolean up = a[1].equals("1");
				boolean back = a[2].equals("1");
				heavy = Float.parseFloat(a[3]);
				speed = Float.parseFloat(a[4]);
				String prefix = a[0] + "_";
				lightSet = List.of(new Part(clip(prefix + dev.soulsmode.ds2.ReactionBlend.c1020Light(up, back)), 1));
				heavySet = List.of(new Part(clip(prefix + dev.soulsmode.ds2.ReactionBlend.c1020Heavy(up, back)), 1));
				upper = lightSet;
			} else {
				boolean heavyCategory = a[1].equals("40");
				int x = 1 + (a[2].equals("1") ? 2 : 0) + (a[3].equals("1") ? 1 : 0);
				float angle = Float.parseFloat(a[4]);
				heavy = Float.parseFloat(a[5]);
				speed = Float.parseFloat(a[6]);
				if (a.length >= 10) {
					hips = hips(Float.parseFloat(a[7]), Float.parseFloat(a[8]), Float.parseFloat(a[9]));
				}
				lightSet = quadrants(x, angle);
				heavySet = quadrants(x + 4, angle);
				upper = heavyCategory ? heavySet : lightSet;
			}
			List<Part> lower = new ArrayList<>();
			lightSet.forEach(p -> lower.add(new Part(p.clip(), p.weight() * (1 - heavy))));
			heavySet.forEach(p -> lower.add(new Part(p.clip(), p.weight() * heavy)));
			if (!(speed > 0) || upper.stream().anyMatch(p -> p.clip() == null) || lower.stream().anyMatch(p -> p.clip() == null)) {
				return null;
			}
			float length = upper.get(0).clip().length();
			Map<String, NavigableMap<Float, float[]>> rotations = new HashMap<>();
			NavigableMap<Float, float[]> position = new TreeMap<>();
			for (float t = 0; ; t = Math.min(length, t + STEP)) {
				float at = t / speed;
				for (String bone : UPPER) {
					put(rotations, bone, at, mix(upper, p -> p.clip().rotations().get(bone), t));
				}
				for (String bone : LOWER) {
					put(rotations, bone, at, mix(lower, p -> p.clip().rotations().get(bone), t));
				}
				float[] pelvis = mix(lower, p -> p.clip().bodyPosition(), t);
				if (pelvis != null) {
					position.put(at, pelvis);
				}
				if (t >= length) {
					break;
				}
			}
			return new Clip(length / speed, rotations, position.isEmpty() ? null : position, hips,
					a[0].equals(C3000) ? C3000 : null);
		} catch (RuntimeException e) {
			SoulsMode.LOGGER.warn("[BSE] bad reaction {}: {}", String.join(":", a), e.toString());
			return null;
		}
	}

	/**
	 * A c0001 reaction ({@link #reaction} spec without the hips) as Player Animation Library JSON for the player's own
	 * rig: the same blended keyframes (body / head / arms / legs rotation, body position), one animation named
	 * {@code name}; null when a clip is missing.
	 */
	public static JsonObject playerReaction(String name, String spec) {
		Clip clip = reaction(spec.split(":"));
		if (clip == null) {
			return null;
		}
		JsonObject bones = new JsonObject();
		clip.rotations().forEach((bone, keys) -> {
			JsonObject channel = new JsonObject();
			channel.add("rotation", keyframes(keys));
			bones.add(bone, channel);
		});
		if (clip.bodyPosition() != null) {
			JsonObject body = bones.has("body") ? bones.getAsJsonObject("body") : new JsonObject();
			body.add("position", keyframes(clip.bodyPosition()));
			bones.add("body", body);
		}
		JsonObject animation = new JsonObject();
		animation.addProperty("loop", false);
		animation.addProperty("animation_length", clip.length());
		animation.add("bones", bones);
		JsonObject animations = new JsonObject();
		animations.add(name, animation);
		JsonObject root = new JsonObject();
		root.addProperty("format_version", "1.8.0");
		root.add("animations", animations);
		return root;
	}

	private static JsonObject keyframes(NavigableMap<Float, float[]> keys) {
		JsonObject out = new JsonObject();
		keys.forEach((t, v) -> {
			com.google.gson.JsonArray a = new com.google.gson.JsonArray();
			for (float f : v) {
				a.add(f);
			}
			out.add(String.format(java.util.Locale.ROOT, "%.4f", t), a);
		});
		return out;
	}

	/**
	 * HipsIK (task 0x47, FUN_14062bd20): Dmg_Rotation as Euler degrees (FUN_14062d040: q = q_y q_z q_x, half angles
	 * deg pi / 360) applied to the Pelvis in DS2's model space (X left, Y back, Z up) after its animated rotation,
	 * weight BlendWeight_1 = 1; then a two-bone leg IK puts the feet back. DS2 model -> Minecraft model (x, -z, y), then
	 * the renderer's space before its (-1, -1, 1) flip negates x and y: (-x, z, y).
	 */
	private static org.joml.Quaternionf hips(float x, float y, float z) {
		org.joml.Quaternionf qx = new org.joml.Quaternionf().rotationX((float) Math.toRadians(x));
		org.joml.Quaternionf qy = new org.joml.Quaternionf().rotationY((float) Math.toRadians(y));
		org.joml.Quaternionf qz = new org.joml.Quaternionf().rotationZ((float) Math.toRadians(z));
		org.joml.Quaternionf q = new org.joml.Quaternionf(qy).mul(qz).mul(qx);
		return new org.joml.Quaternionf(-q.x, q.z, q.y, q.w);
	}

	/**
	 * BlendN on DmgDirection (nodeBlendNUpdateConnections FUN_1406387e0, no wrap): children BR, BL, FL, FR, BR, BL at
	 * -225, -135, -45, 45, 135, 225; clamped to the ends, else linear between the two neighbours.
	 */
	private static List<Part> quadrants(int x, float angle) {
		List<Part> out = new ArrayList<>();
		for (dev.soulsmode.ds2.ReactionBlend.Part p : dev.soulsmode.ds2.ReactionBlend.quadrants(x, angle)) {
			out.add(new Part(clip("player:ds2_" + p.tae()), p.weight()));
		}
		return out;
	}

	private static void put(Map<String, NavigableMap<Float, float[]>> rotations, String bone, float t, float[] v) {
		if (v != null) {
			rotations.computeIfAbsent(bone, b -> new TreeMap<>()).put(t, v);
		}
	}

	/** The weighted sum of the parts' channel at {@code t} (Euler degrees, the clips' own space). */
	private static float[] mix(List<Part> parts, java.util.function.Function<Part, NavigableMap<Float, float[]>> channel,
			float t) {
		float[] out = null;
		float total = 0;
		for (Part p : parts) {
			if (p.weight() <= 0) {
				continue;
			}
			float[] v = sample(channel.apply(p), Math.min(t, p.clip().length()));
			if (v == null) {
				continue;
			}
			if (out == null) {
				out = new float[3];
			}
			for (int k = 0; k < 3; k++) {
				out[k] += v[k] * p.weight();
			}
			total += p.weight();
		}
		if (out != null && total > 0) {
			for (int k = 0; k < 3; k++) {
				out[k] /= total;
			}
		}
		return out;
	}

	/** The stage playing and the time into its clip, or null when the entity plays none. */
	private record At(Clip clip, float t) {
	}

	private static At at(Entity entity, float partialTick) {
		Playing p = PLAYING.get(entity.getId());
		if (p == null || entity.level() == null) {
			return null;
		}
		float t = (entity.level().getGameTime() - p.startTick() + partialTick) / 20f;
		if (t >= p.end() || !entity.isAlive()) {
			PLAYING.remove(entity.getId());
			return null;
		}
		Stage stage = p.stages().get(0);
		for (Stage s : p.stages()) {
			if (t >= s.from()) {
				stage = s;
			}
		}
		float local = t - stage.from();
		if (stage.loop()) {
			local %= stage.clip().length();
		}
		return new At(stage.clip(), Math.min(local, stage.clip().length()));
	}

	/** Whether the entity plays a clip now. */
	/** The Last Giant's weapon mode (0 until SoulsGiants says otherwise). */
	public static int mode(Entity entity) {
		return MODES.getOrDefault(entity.getId(), 0);
	}

	/**
	 * The clip's held_arm now: {xRot, yRot, zRot} degrees and the grip's {x, y, z} px from the right shoulder (chest
	 * frame, Minecraft model axes); null when the clip has none.
	 */
	public static float[] heldArm(Entity entity, float partialTick) {
		At at = at(entity, partialTick);
		if (at == null) {
			return null;
		}
		float[] r = sample(at.clip().rotations().get("held_arm"), at.t());
		float[] p = sample(HELD_POSITIONS.get(at.clip()), at.t());
		return r == null || p == null ? null : new float[] {r[0], r[1], r[2], p[0], p[1], p[2]};
	}

	public static boolean playing(Entity entity) {
		return PLAYING.containsKey(entity.getId());
	}

	/** After the zombie model's own setupAnim: the clip's limb rotations. */
	public static void applyLimbs(Entity entity, float partialTick, ModelPart head, ModelPart hat, ModelPart body,
			ModelPart rightArm, ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {
		At at = at(entity, partialTick);
		if (at == null) {
			return;
		}
		Clip clip = at.clip();
		float t = at.t();
		set(clip, "head", t, head);
		hat.copyFrom(head);
		// The body part stays at rest: the body bone moves the whole model (applyBody).
		body.xRot = 0;
		body.yRot = 0;
		body.zRot = 0;
		set(clip, "right_arm", t, rightArm);
		set(clip, "left_arm", t, leftArm);
		set(clip, "right_leg", t, rightLeg);
		set(clip, "left_leg", t, leftLeg);
		if (clip.hips() != null) {
			// The leg IK puts the feet back: Minecraft's rigid legs turn back by the hips' rotation about their hip
			// (the knees cannot bend). Model space = the renderer's with x and y negated.
			org.joml.Quaternionf h = clip.hips();
			org.joml.Quaternionf back = new org.joml.Quaternionf(-h.x, -h.y, h.z, h.w).conjugate();
			counter(back, rightLeg);
			counter(back, leftLeg);
		}
	}

	/**
	 * The body bone, in the renderer's space before its (-1, -1, 1) flip: about the hip (model (0, 12, 0) px, 0.751
	 * blocks above the feet; the iron golem's (0, 11, 0) for c3000 clips), rotation Rz * Ry(-y) * Rx(-x) and the offset
	 * (-left, up, back) / 16.
	 */
	public static void applyBody(Entity entity, float partialTick, com.mojang.blaze3d.vertex.PoseStack pose, float scale) {
		At at = at(entity, partialTick);
		if (at == null) {
			return;
		}
		Clip clip = at.clip();
		float t = at.t();
		float[] r = sample(clip.rotations().get("body"), t);
		float[] p = sample(clip.bodyPosition(), t);
		float pivot = (1.501f - (C3000.equals(clip.rig()) ? 11 : 12) / 16f) * scale;
		if (p != null) {
			pose.translate(-p[0] / 16f * scale, p[1] / 16f * scale, p[2] / 16f * scale);
		}
		if (r != null) {
			pose.translate(0, pivot, 0);
			pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(r[2]));
			pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-r[1]));
			pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-r[0]));
			pose.translate(0, -pivot, 0);
		}
		if (clip.hips() != null) {
			pose.translate(0, pivot, 0);
			pose.mulPose(clip.hips());
			pose.translate(0, -pivot, 0);
		}
	}

	/**
	 * Iron golem arms (after applyLimbs): vanilla pivots, or, while a c3000 clip plays, turned about the shoulder S =
	 * the vanilla pivot P + (-+11, 0, 0) px (the arm boxes span x -13..-9 / 9..13, IronGolemModel.createBodyLayer)
	 * instead of P: the part keeps its cubes, so its pivot goes to P + d - R d (R = Rz Ry Rx, ModelPart.translateAndRotate).
	 */
	public static void golemShoulders(Entity entity, float partialTick, ModelPart rightArm, ModelPart leftArm) {
		At at = at(entity, partialTick);
		boolean ogre = at != null && C3000.equals(at.clip().rig());
		shoulder(rightArm, ogre ? -11 : 0);
		shoulder(leftArm, ogre ? 11 : 0);
	}

	private static void shoulder(ModelPart arm, float dx) {
		var p = arm.getInitialPose();
		org.joml.Vector3f rd = new org.joml.Matrix3f().rotationZYX(arm.zRot, arm.yRot, arm.xRot)
				.transform(new org.joml.Vector3f(dx, 0, 0));
		arm.x = p.x + dx - rd.x;
		arm.y = p.y - rd.y;
		arm.z = p.z - rd.z;
	}

	private static void counter(org.joml.Quaternionf back, ModelPart part) {
		org.joml.Quaternionf q = new org.joml.Quaternionf(back).mul(
				new org.joml.Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot));
		org.joml.Vector3f e = q.getEulerAnglesZYX(new org.joml.Vector3f());
		part.xRot = e.x;
		part.yRot = e.y;
		part.zRot = e.z;
	}

	private static void set(Clip clip, String bone, float t, ModelPart part) {
		float[] v = sample(clip.rotations().get(bone), t);
		if (v != null) {
			part.xRot = (float) Math.toRadians(v[0]);
			part.yRot = (float) Math.toRadians(v[1]);
			part.zRot = (float) Math.toRadians(v[2]);
		}
	}

	/** Linear interpolation between the keys around {@code t}. */
	private static float[] sample(NavigableMap<Float, float[]> keys, float t) {
		if (keys == null || keys.isEmpty()) {
			return null;
		}
		Map.Entry<Float, float[]> a = keys.floorEntry(t);
		Map.Entry<Float, float[]> b = keys.ceilingEntry(t);
		if (a == null) {
			return b.getValue();
		}
		if (b == null || b.getKey().equals(a.getKey())) {
			return a.getValue();
		}
		float f = (t - a.getKey()) / (b.getKey() - a.getKey());
		float[] out = new float[3];
		for (int i = 0; i < 3; i++) {
			out[i] = a.getValue()[i] + (b.getValue()[i] - a.getValue()[i]) * f;
		}
		return out;
	}

	private static Clip clip(String name) {
		return CLIPS.computeIfAbsent(name, n -> {
			try {
				// "player:<name>": a c0001 clip of the player rig (player_animations), else an enemy clip.
				boolean player = n.startsWith("player:");
				String file = player ? "player_animations/" + n.substring(7) : "enemy_animations/" + n;
				var resource = Minecraft.getInstance().getResourceManager()
						.getResource(SoulsMode.id(file + ".json")).orElse(null);
				if (resource == null) {
					SoulsMode.LOGGER.warn("[BSE] missing enemy animation {}", n);
					return null;
				}
				try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
					JsonObject all = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
					JsonObject a = all.entrySet().iterator().next().getValue().getAsJsonObject();
					Map<String, NavigableMap<Float, float[]>> rotations = new HashMap<>();
					NavigableMap<Float, float[]> position = null;
					NavigableMap<Float, float[]> held = null;
					for (Map.Entry<String, JsonElement> bone : a.getAsJsonObject("bones").entrySet()) {
						JsonObject channels = bone.getValue().getAsJsonObject();
						if (channels.has("rotation")) {
							rotations.put(bone.getKey(), keys(channels.getAsJsonObject("rotation")));
						}
						if (bone.getKey().equals("body") && channels.has("position")) {
							position = keys(channels.getAsJsonObject("position"));
						}
						if (bone.getKey().equals("held_arm") && channels.has("position")) {
							held = keys(channels.getAsJsonObject("position"));
						}
					}
					Clip clip = new Clip(a.get("animation_length").getAsFloat(), rotations, position, null,
							n.startsWith(C3000 + "_") ? C3000 : null);
					if (held != null) {
						HELD_POSITIONS.put(clip, held);
					}
					return clip;
				}
			} catch (Exception e) {
				SoulsMode.LOGGER.error("[BSE] enemy animation {}: {}", n, e.toString());
				return null;
			}
		});
	}

	private static NavigableMap<Float, float[]> keys(JsonObject json) {
		NavigableMap<Float, float[]> out = new TreeMap<>();
		for (Map.Entry<String, JsonElement> e : json.entrySet()) {
			var v = e.getValue().getAsJsonArray();
			out.put(Float.parseFloat(e.getKey()), new float[] {v.get(0).getAsFloat(), v.get(1).getAsFloat(),
					v.get(2).getAsFloat()});
		}
		return out;
	}
}
