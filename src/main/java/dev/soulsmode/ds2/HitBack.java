package dev.soulsmode.ds2;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * DS2's HitBack, the attack bouncing off the map (docs/research/wall_bounce.md): the attack's hit volume (PlayerDamageParam
 * capsule on the weapon's dummy) touching map collision bounces when PDP +0x65's bit for that contact is clear (0x08
 * on walls, |n.y| <= cos 45 deg, or contacts more than PlayerCommonParam +0x134 = 1 m above the attacker; 0x04
 * elsewhere), only for hits with PDP +0x40 & 0x40. The bounce plays SM_HitBack's animation (a29_01_0100 / 0110 one
 * hand, a29_03_0100 / 0110 two hands, mirrored for the left hand) and cuts the attack.
 * <p>
 * Data from weapons.json (tools/re/weapons.py, cast_origins.py): each slot's {@code hitBack} / {@code hitVolume}, each
 * weapon's {@code hitDummies} in its R_Weapon frame, each attack animation's {@code weaponBones} (R_Weapon samples in
 * model space: left / up / forward, metres) and the top-level {@code hitBack} animations.
 */
public final class HitBack {
	/** One attack slot: PDP flags, HitBackParam level (distance, speed) and the PDP hit volume. */
	public record Slot(boolean map, boolean noFloor, boolean noWall, float distance, float speed, int dummy, float radius,
			float length, String source) {
	}

	/** A weapon dummy in its R_Weapon frame. */
	public record Dummy(float[] pos, float[] fwd) {
	}

	/** An R_Weapon sample: time, position and the 3x3 rotation (rows) from the bone's axes to model space. */
	public record Bone(float time, float[] pos, float[][] rot) {
	}

	/** PlayerCommonParam +0x134: contacts higher than this above the attacker use the wall bit. */
	public static final float HIGH_CONTACT = 1.0f;

	private static final Map<Attack, Slot> SLOTS = Collections.synchronizedMap(new IdentityHashMap<>());
	private static Map<Integer, List<Bone>> bones;
	private static Map<Integer, Integer> events;
	private static Map<String, Map<Integer, Dummy>> dummies;
	private static Map<String, Attack> bounces;
	/** Per bounce key: DmgHitBackDistance -> the SM_HitBack blend baked for it (tools/re/hitback.py). */
	private static Map<String, Map<Float, String>> clips;

	private HitBack() {
	}

	/**
	 * Records a slot's data for its attack (Weapons). A slot whose hits use different PDP rows has lists: the first
	 * entry with data stands for the whole attack.
	 */
	static void register(Attack attack, JsonObject slot) {
		JsonObject hb = first(slot.get("hitBack"));
		JsonObject hv = first(slot.get("hitVolume"));
		if (attack == null || hb == null || hv == null) {
			return;
		}
		SLOTS.put(attack, new Slot(hb.get("map").getAsBoolean(), hb.get("noFloor").getAsBoolean(),
				hb.get("noWall").getAsBoolean(), hb.get("distance").getAsFloat(), hb.get("speed").getAsFloat(),
				hv.get("dummy").getAsInt(), hv.get("radius").getAsFloat(), hv.get("length").getAsFloat(),
				hv.get("source").getAsString()));
	}

	private static JsonObject first(JsonElement e) {
		if (e == null || e.isJsonNull()) {
			return null;
		}
		if (e.isJsonObject()) {
			return e.getAsJsonObject();
		}
		for (JsonElement x : e.getAsJsonArray()) {
			if (x.isJsonObject()) {
				return x.getAsJsonObject();
			}
		}
		return null;
	}

	public static Slot slot(Attack attack) {
		return SLOTS.get(attack);
	}

	/** The R_Weapon sample nearest to {@code t} of an attack animation, or null without data. */
	public static synchronized Bone bone(int taeId, float t) {
		load();
		List<Bone> list = bones.get(taeId);
		if (list == null || list.isEmpty()) {
			return null;
		}
		Bone best = list.get(0);
		for (Bone b : list) {
			if (Math.abs(b.time() - t) < Math.abs(best.time() - t)) {
				best = b;
			}
		}
		return Math.abs(best.time() - t) <= 0.05f ? best : null;
	}

	/**
	 * The R_Weapon samples of an attack animation in ({@code from}, {@code to}] (exported every 1/30 s), or the one
	 * nearest to {@code to} when none falls inside; empty without data.
	 */
	public static synchronized List<Bone> bones(int taeId, float from, float to) {
		load();
		List<Bone> list = bones.get(taeId);
		if (list == null || list.isEmpty()) {
			return List.of();
		}
		List<Bone> out = new ArrayList<>();
		for (Bone b : list) {
			if (b.time() > from && b.time() <= to) {
				out.add(b);
			}
		}
		if (out.isEmpty()) {
			Bone nearest = bone(taeId, to);
			return nearest == null ? List.of() : List.of(nearest);
		}
		return out;
	}

	/** animations[tae].hitBackEvent: 280 Upper_R, 281 Upper_L (AttackHitBackControl), 0 if none. */
	public static synchronized int event(int taeId) {
		load();
		return events.getOrDefault(taeId, 0);
	}

	public static synchronized Dummy dummy(String weapon, int id) {
		load();
		Map<Integer, Dummy> m = dummies.get(weapon);
		return m == null ? null : m.get(id);
	}

	/** The bounce animation: {@code twoHanded}, {@code upperL} (event 281) or Upper_R. */
	public static synchronized Attack bounce(boolean twoHanded, boolean upperL) {
		load();
		return bounces.get((twoHanded ? "twoHanded" : "oneHanded") + (upperL ? "upperL" : "upperR"));
	}

	/**
	 * The bounce at DmgHitBackDuration {@code speed} (HitBackParam, the slot's {@code hitBack.speed}): SM_HitBack's 0x7d
	 * node plays the clip at that speed, so its length and every track window are divided by it.
	 */
	public static Attack bounce(boolean twoHanded, boolean upperL, float speed, float distance) {
		Attack a = bounce(twoHanded, upperL);
		if (a == null) {
			return null;
		}
		String clip;
		synchronized (HitBack.class) {
			clip = clips.getOrDefault((twoHanded ? "twoHanded" : "oneHanded") + (upperL ? "upperL" : "upperR"), Map.of())
					.get(distance);
		}
		String animation = clip != null ? clip : a.animation();
		if (speed <= 0) {
			speed = 1;
		}
		List<float[]> regen = new ArrayList<>();
		for (float[] w : a.regenBlock()) {
			regen.add(new float[] {w[0] / speed, w[1] / speed});
		}
		return new Attack(animation, a.taeId(), a.duration() / speed, List.of(), a.avoidLockStart() / speed,
				a.avoidLockEnd() / speed, a.moveLock() / speed, a.lightLock() / speed, a.heavyLock() / speed,
				a.leftLightLock() / speed, a.leftHeavyLock() / speed, 0, 0, false, false, List.of(), List.of(), 0,
				List.of(), List.copyOf(regen));
	}

	/** The bounce animation by its animation id (clients resolve the Start payload with it). */
	public static synchronized Attack bounceByAnimation(String animation) {
		load();
		for (Attack a : bounces.values()) {
			if (a.animation().equals(animation)) {
				return a;
			}
		}
		return null;
	}

	private static void load() {
		if (bones != null) {
			return;
		}
		JsonObject raw = Weapons.raw();
		bones = new HashMap<>();
		events = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : raw.getAsJsonObject("animations").entrySet()) {
			if (!e.getKey().chars().allMatch(Character::isDigit) || !e.getValue().isJsonObject()) {
				continue;
			}
			JsonObject a = e.getValue().getAsJsonObject();
			int tae = Integer.parseInt(e.getKey());
			if (a.has("hitBackEvent") && !a.get("hitBackEvent").isJsonNull()) {
				events.put(tae, a.get("hitBackEvent").getAsInt());
			}
			if (a.has("weaponBones") && a.get("weaponBones").isJsonArray()) {
				List<Bone> list = new ArrayList<>();
				for (JsonElement s : a.getAsJsonArray("weaponBones")) {
					JsonObject o = s.getAsJsonObject();
					float[][] rot = new float[3][];
					for (int i = 0; i < 3; i++) {
						rot[i] = floats(o.getAsJsonArray("rot").get(i).getAsJsonArray());
					}
					list.add(new Bone(o.get("time").getAsFloat(), floats(o.getAsJsonArray("pos")), rot));
				}
				bones.put(tae, List.copyOf(list));
			}
		}
		dummies = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : raw.getAsJsonObject("weapons").entrySet()) {
			JsonObject w = e.getValue().getAsJsonObject();
			if (!w.has("hitDummies")) {
				continue;
			}
			Map<Integer, Dummy> m = new HashMap<>();
			for (Map.Entry<String, JsonElement> d : w.getAsJsonObject("hitDummies").entrySet()) {
				JsonObject o = d.getValue().getAsJsonObject();
				m.put(Integer.parseInt(d.getKey()), new Dummy(floats(o.getAsJsonArray("pos")), floats(o.getAsJsonArray("fwd"))));
			}
			dummies.put(e.getKey(), m);
		}
		bounces = new HashMap<>();
		clips = new HashMap<>();
		for (Map.Entry<String, JsonElement> g : raw.getAsJsonObject("hitBack").entrySet()) {
			for (Map.Entry<String, JsonElement> side : g.getValue().getAsJsonObject().entrySet()) {
				JsonObject a = side.getValue().getAsJsonObject();
				int tae = a.get("taeId").getAsInt();
				float duration = a.get("duration").getAsFloat();
				float[] avoid = pair(a.getAsJsonArray("avoidLock"));
				float move = pair(a.getAsJsonArray("moveLock"))[1];
				float attack = pair(a.getAsJsonArray("attackLock"))[1];
				List<float[]> regen = new ArrayList<>();
				if (a.has("regenBlock")) {
					for (JsonElement r : a.getAsJsonArray("regenBlock")) {
						regen.add(pair(r.getAsJsonArray()));
					}
				}
				if (a.has("clips")) {
					// SM_HitBack's FeatherBlend2 / Blend2 by DmgHitBackDistance, baked per distance (wall_bounce.md s.3).
					Map<Float, String> byDistance = new HashMap<>();
					for (Map.Entry<String, JsonElement> c : a.getAsJsonObject("clips").entrySet()) {
						byDistance.put(Float.parseFloat(c.getKey()), c.getValue().getAsString());
					}
					clips.put(g.getKey() + side.getKey(), byDistance);
				}
				bounces.put(g.getKey() + side.getKey(), new Attack("soulsmode:ds2_" + tae, tae, duration, List.of(),
						avoid[0], avoid[1], move, attack, attack, attack, attack, 0, 0, false, false, List.of(), List.of(),
						0, List.of(), List.copyOf(regen)));
			}
		}
	}

	private static float[] floats(JsonArray a) {
		float[] out = new float[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsFloat();
		}
		return out;
	}

	private static float[] pair(JsonArray a) {
		return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat()};
	}
}
