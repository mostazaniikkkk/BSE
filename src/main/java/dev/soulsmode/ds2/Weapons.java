package dev.soulsmode.ds2;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The DS2 weapons exported by tools/re/weapons.py (WeaponParam -> WeaponActionCategoryParam ->
 * WeaponAttackMotionParam, timings from the TAE and c0001.nmb), keyed by name. Animations are
 * {@code soulsmode:ds2_<taeId>}.
 */
public final class Weapons {
	public static final String RESOURCE = "/data/soulsmode/ds2/weapons.json";

	private Weapons() {
	}

	private static JsonObject raw;

	/** The whole exported file, parsed once (read only: callers must copy before changing it). */
	public static synchronized JsonObject raw() {
		if (raw == null) {
			try (InputStream in = Weapons.class.getResourceAsStream(RESOURCE)) {
				if (in == null) {
					throw new IllegalStateException("missing " + RESOURCE);
				}
				raw = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return raw;
	}

	/** Datapack weapons' resolved entries (extends applied) by full id, and the merged animation table (SoulsContent). */
	private static volatile Map<String, JsonObject> packEntries = Map.of();
	private static volatile JsonObject packAnimations = null;
	private static volatile JsonObject packStanceClips = null;

	public static void setPack(Map<String, JsonObject> entries, JsonObject animations, JsonObject powerStanceClips) {
		packEntries = Map.copyOf(entries);
		packAnimations = animations;
		packStanceClips = powerStanceClips;
	}

	/** A datapack weapon's resolved entry by full id, or null. */
	public static JsonObject packEntry(String key) {
		return key == null ? null : packEntries.get(key);
	}

	/** A weapon's entry: a datapack weapon's (full id) or a built-in one's (name); null when unknown. */
	public static JsonObject entry(String key) {
		if (key == null) {
			return null;
		}
		JsonObject p = packEntries.get(key);
		return p != null ? p : raw().getAsJsonObject("weapons").getAsJsonObject(key);
	}

	/** The animation table: the mod's plus every datapack's {@code animations}. */
	public static JsonObject animations() {
		JsonObject p = packAnimations;
		return p != null ? p : raw().getAsJsonObject("animations");
	}

	/**
	 * The power stance locomotion clips by stance id (weapons.json {@code powerStanceClips}) plus a datapack weapon
	 * file's own {@code powerStanceClips}.
	 */
	public static JsonObject powerStanceClips() {
		JsonObject p = packStanceClips;
		return p != null ? p : raw().getAsJsonObject("powerStanceClips");
	}

	/** WeaponTypeParam +0x6c of a weapon (weapons.json {@code counterRate}), NaN when it has none. */
	public static float counterRate(String key) {
		JsonObject w = entry(key);
		return w != null && w.has("counterRate") ? w.get("counterRate").getAsFloat() : Float.NaN;
	}

	public static Map<String, Moveset> load() {
		JsonObject json = raw();
		JsonObject animations = json.getAsJsonObject("animations");
		Map<String, Moveset> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("weapons").entrySet()) {
			out.put(e.getKey(), moveset(e.getValue().getAsJsonObject(), animations));
		}
		return out;
	}

	/**
	 * Everything the mod reads from one weapon entry (same schema as the entries of {@link #RESOURCE}), for weapons
	 * added by datapacks (dev.soulsmode.content.SoulsContent). Critical attacks and victims are null when the weapon
	 * has none.
	 */
	public record Def(Moveset moveset, GripData grip, GuardData guard, float weight, Ds2Damage.WeaponData attack,
			Attack riposte, Attack guardBreakStab, BackstabVictim backstabVictim, BackstabVictim riposteVictim,
			BackstabVictim guardBreakVictim, int[] castSchools, float castTime, float bulletRange,
			Map<Integer, float[]> dummyPolys, int weaponClass, boolean enchantable) {
	}

	public static Def parse(JsonObject w, JsonObject animations) {
		return new Def(moveset(w, animations), new GripData(w.get("twoHandStance").getAsInt(), w.get("storage").getAsInt()),
				new GuardData(rates(w.getAsJsonArray("guardStability")), rates(w.getAsJsonArray("guardStaminaRates")),
						w.has("guardStabilityByLevel") ? rates(w.getAsJsonArray("guardStabilityByLevel")) : null),
				w.get("weight").getAsFloat(), Ds2Damage.parseWeapon(w.getAsJsonObject("attack")),
				critical(w, "riposte", animations), critical(w, "guard_break", animations),
				victim(w, "backstab", animations), victim(w, "riposte", animations), victim(w, "guard_break", animations),
				castSchools(w), w.has("castTime") ? w.get("castTime").getAsFloat() : 1,
				w.has("bulletRange") ? w.get("bulletRange").getAsFloat() : 1, dummyPolys(w),
				w.has("weaponClass") ? w.get("weaponClass").getAsInt() : 0,
				w.has("enchantable") && w.get("enchantable").getAsBoolean());
	}

	/**
	 * The weapons.json slot of a built-in weapon's guard (motion type 13) for a request resolved like
	 * Moveset.attack: the other hand's buttons of a two-handed weapon use {@code two_handed_other}, the rest the
	 * grip's block. Null without one.
	 */
	public static JsonObject guardSlot(String weapon, boolean weaponLeft, boolean twoHanded, Moveset.Type type) {
		JsonObject w = entry(weapon);
		if (w == null) {
			return null;
		}
		boolean other = type == Moveset.Type.OTHER_LIGHT || type == Moveset.Type.OTHER_HEAVY;
		JsonObject block = w.getAsJsonObject(other ? "two_handed_other" : twoHanded ? "two_handed"
				: weaponLeft ? "left" : "right");
		String key = other ? (type == Moveset.Type.OTHER_LIGHT ? "light" : "heavy")
				: type.name().toLowerCase(java.util.Locale.ROOT);
		JsonElement slot = block == null ? null : block.get(key);
		return slot == null || !slot.isJsonObject() || !slot.getAsJsonObject().has("guardSpeed") ? null
				: slot.getAsJsonObject();
	}

	/**
	 * GuardCancelSpeedCategory of a guard slot (FUN_14038ffb0): WeaponTypeParam u8 +0x51, one step up with a Str / Dex
	 * lack (weapons.json {@code guardCancelCategory} / {@code guardCancelCategoryLacking}).
	 */
	public static int guardCancelCategory(JsonObject slot, boolean lacking) {
		return slot.get(lacking ? "guardCancelCategoryLacking" : "guardCancelCategory").getAsInt();
	}

	/**
	 * GuardSpeed of a guard slot (FUN_14038f870, weapon_arts.md s.8.5): WAMP +0x08 x
	 * PhysicalStatsPerLevelStatValuesParam[(Attunement + 3 Adaptability) / 4] +0x7c (index outside 1..99 -> 1.0) x
	 * (1 - pen), pen = clamp(0.01 (r0 + (r1 - r0) f), 0, 1) with PlayerLackOfStatsParam row 9 when the Str / Dex lack f
	 * is above 0 (its stamina columns are 0).
	 */
	public static float guardSpeed(JsonObject slot, int attunement, int adaptability, float strDexLack) {
		JsonObject layer = raw().getAsJsonObject("guardLayer");
		JsonArray stat = layer.getAsJsonArray("guardSpeedStat");
		int index = (attunement + 3 * adaptability) / 4;
		float derived = index >= 1 && index <= stat.size() ? stat.get(index - 1).getAsFloat() : 1;
		float pen = 0;
		if (strDexLack > 0) {
			JsonArray row = layer.getAsJsonArray("guardSpeedLackRow");
			float r0 = row.get(0).getAsFloat();
			pen = Math.max(0, Math.min(1, 0.01f * (r0 + (row.get(1).getAsFloat() - r0) * strDexLack)));
		}
		return slot.get("guardSpeed").getAsFloat() * derived * (1 - pen);
	}

	/** SM 0x210e's exit blend for a GuardCancelSpeedCategory (conditions 0x271 on CP 0x8c, ranges inclusive), 0 if none. */
	public static float guardExitBlend(int category) {
		JsonObject blends = raw().getAsJsonObject("guardLayer").getAsJsonObject("exitBlend");
		if (category >= 0 && category <= 10) {
			return blends.get("10").getAsFloat();
		}
		if (category == 20 || category == 30) {
			return blends.get(String.valueOf(category)).getAsFloat();
		}
		return category >= 40 && category <= 100 ? blends.get("40-100").getAsFloat() : 0;
	}

	/** A catalyst's dummy polys in its R_Weapon frame (local rig axes, m; docs/research/dummypoly.md). */
	public static Map<Integer, float[]> dummyPolys(JsonObject w) {
		Map<Integer, float[]> out = new LinkedHashMap<>();
		if (w.has("dummyPolys")) {
			for (Map.Entry<String, JsonElement> e : w.getAsJsonObject("dummyPolys").entrySet()) {
				// Catalysts: a position array; bows / crossbows: {pos, fwd} (cast_origins.py hand_dummies, read by Bow).
				JsonElement v = e.getValue();
				out.put(Integer.parseInt(e.getKey()),
						rates(v.isJsonObject() ? v.getAsJsonObject().getAsJsonArray("pos") : v.getAsJsonArray()));
			}
		}
		return Map.copyOf(out);
	}

	public static Map<String, Map<Integer, float[]>> dummyPolys() {
		Map<String, Map<Integer, float[]>> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : raw().getAsJsonObject("weapons").entrySet()) {
			out.put(e.getKey(), dummyPolys(e.getValue().getAsJsonObject()));
		}
		return out;
	}

	/** Built-in weapons' WeaponTypeParam +0x10c / WeaponParam +0x40 (cast wait / bullet range factors). */
	/** Built-in weapons' {WeaponParam +0x15, ItemParam +0x50 bit 1 (enchantable) as 0/1} (spells.md, target 1/2). */
	public static Map<String, int[]> buffTargets() {
		Map<String, int[]> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : raw().getAsJsonObject("weapons").entrySet()) {
			JsonObject w = e.getValue().getAsJsonObject();
			out.put(e.getKey(), new int[] {w.has("weaponClass") ? w.get("weaponClass").getAsInt() : 0,
					w.has("enchantable") && w.get("enchantable").getAsBoolean() ? 1 : 0});
		}
		return out;
	}

	public static Map<String, float[]> castFactors() {
		Map<String, float[]> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : raw().getAsJsonObject("weapons").entrySet()) {
			JsonObject w = e.getValue().getAsJsonObject();
			out.put(e.getKey(), new float[] {w.has("castTime") ? w.get("castTime").getAsFloat() : 1,
					w.has("bulletRange") ? w.get("bulletRange").getAsFloat() : 1});
		}
		return out;
	}

	/**
	 * The schools a weapon can cast (FUN_140397a30): WeaponTypeParam bytes +0x00 / +0x01 / +0x02 / +0x03 / +0x53 for
	 * SpellParam +0x00 = 0..4, non-zero = allowed. Weapons exported before this field cast nothing.
	 */
	public static int[] castSchools(JsonObject w) {
		int[] out = new int[5];
		if (w.has("castSchools")) {
			JsonArray a = w.getAsJsonArray("castSchools");
			for (int i = 0; i < 5; i++) {
				out[i] = a.get(i).getAsInt();
			}
		}
		return out;
	}

	public static Map<String, int[]> castSchools() {
		Map<String, int[]> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : raw().getAsJsonObject("weapons").entrySet()) {
			out.put(e.getKey(), castSchools(e.getValue().getAsJsonObject()));
		}
		return out;
	}

	private static Moveset moveset(JsonObject w, JsonObject animations) {
		JsonObject other = w.getAsJsonObject("two_handed_other");
		return new Moveset(hand(w.getAsJsonObject("right"), animations),
				hand(w.getAsJsonObject("left"), animations), hand(w.getAsJsonObject("two_handed"), animations),
				new Moveset.OtherHand(attack(other, "light", animations), attack(other, "heavy", animations)),
				rates(w.getAsJsonArray("guardRates")), critical(w, "backstab", animations),
				w.getAsJsonArray("criticalAngleBonus").get(2).getAsFloat(), w.get("parryRate").getAsFloat(),
				new Moveset.Kick(optional(w.getAsJsonObject("kick"), "one_handed", animations),
						optional(w.getAsJsonObject("kick"), "two_handed", animations)));
	}

	/** A critical slot; null when its motion type (WeaponAttackMotionParam +0x04) is 0, as FUN_140390a50 requires. */
	private static Attack critical(JsonObject w, String kind, JsonObject animations) {
		JsonObject critical = w.getAsJsonObject("critical");
		return critical.getAsJsonObject(kind).get("motionType").getAsInt() == 0 ? null : attack(critical, kind, animations);
	}

	private static BackstabVictim victim(JsonObject w, String kind, JsonObject animations) {
		JsonObject slot = w.getAsJsonObject("critical").getAsJsonObject(kind);
		JsonElement a = animations.get(slot.get("taeId").getAsString());
		if (a == null || a.isJsonNull() || !a.getAsJsonObject().has("victim")) {
			return null;
		}
		JsonObject v = a.getAsJsonObject().getAsJsonObject("victim");
		return new BackstabVictim(v.get("moveAt").getAsFloat(), v.get("attackAt").getAsFloat(),
				v.get("noCriticalUntil").getAsFloat(), windows(v.getAsJsonArray("invulnerable")),
				v.get("taeId").getAsInt(), v.get("getUpAt").getAsFloat());
	}

	/**
	 * An animation key: a number is a DS2 TAE id, played as {@code soulsmode:ds2_<id>}; anything else is the Player
	 * Animation Library id itself (animations added by resource packs).
	 */
	public static String animationName(String key) {
		return isTae(key) ? "soulsmode:ds2_" + key : key;
	}

	private static boolean isTae(String key) {
		return !key.isEmpty() && key.chars().allMatch(Character::isDigit);
	}

	private static int taeNumber(String key) {
		return isTae(key) ? Integer.parseInt(key) : 0;
	}

	private static Moveset.Hand hand(JsonObject hand, JsonObject animations) {
		return new Moveset.Hand(attack(hand, "light", animations), attack(hand, "light_combo", animations),
				attack(hand, "heavy", animations), attack(hand, "heavy_combo", animations),
				optional(hand, "dash", animations), optional(hand, "rolling", animations),
				optional(hand, "backstep", animations), optional(hand, "jump", animations), fall(hand, animations));
	}

	/** Longest a plunge's airborne part is kept (a fall this long ends it); a bound, not DS2 data. */
	private static final float MAX_FALL_SECONDS = 30;

	/** The plunging attack of a hand (slot +8), or null when the slot is not one (guards, empty, no animation). */
	private static Moveset.Fall fall(JsonObject hand, JsonObject animations) {
		if (!hand.has("fall") || hand.get("fall").isJsonNull()) {
			return null;
		}
		JsonObject slot = hand.getAsJsonObject("fall");
		String key = slot.get("taeId").getAsString();
		JsonElement element = animations.get(key);
		if (element == null || element.isJsonNull() || !element.getAsJsonObject().has("fall")) {
			return null;
		}
		JsonObject a = element.getAsJsonObject();
		int taeId = taeNumber(key);
		String animation = animationName(key);
		boolean left = a.get("leftAuthored").getAsBoolean();
		float cost = slot.get("staminaCost").getAsFloat();
		// Airborne: the loop's 2200 covers the whole descent (one hit window, one charge); no cancel while falling.
		List<float[]> airHits = new ArrayList<>();
		float airRate = Float.NaN;
		float airSpeed = Float.NaN;
		JsonArray rates = slot.getAsJsonArray("damageRates");
		JsonArray speeds = slot.getAsJsonArray("reactionSpeeds");
		JsonArray motions = slot.getAsJsonArray("reactionMotions");
		JsonArray stamina = slot.getAsJsonArray("staminaDamages");
		int airIndex = -1;
		for (JsonElement h : a.getAsJsonArray("airHits")) {
			JsonElement rate = rates.get(h.getAsJsonArray().get(2).getAsInt());
			if (!rate.isJsonNull()) {
				airRate = rate.getAsFloat();
				airSpeed = reactionSpeed(speeds, h.getAsJsonArray().get(2).getAsInt());
				airIndex = h.getAsJsonArray().get(2).getAsInt();
			}
		}
		if (!Float.isNaN(airRate)) {
			JsonElement st = stamina.get(0);
			airHits.add(withMotion(new float[] {0, MAX_FALL_SECONDS, airRate, st.isJsonNull() ? Float.NaN : st.getAsFloat(), 0,
					airSpeed}, motions, airIndex));
		}
		float d = MAX_FALL_SECONDS;
		Attack air = new Attack(animation, taeId, d, airHits, 0, d, d, d, d, d, d, 0, 0, false, left, List.of(),
				List.of(), cost, charges(airHits), List.of(new float[] {0, d}));
		List<float[]> landingHits = new ArrayList<>();
		for (JsonElement h : a.getAsJsonArray("landingHits")) {
			JsonArray e = h.getAsJsonArray();
			JsonElement rate = rates.get(e.get(2).getAsInt());
			JsonElement st = stamina.get(e.get(2).getAsInt());
			landingHits.add(withMotion(new float[] {e.get(0).getAsFloat(), e.get(1).getAsFloat(),
					rate.isJsonNull() ? Float.NaN : rate.getAsFloat(), st.isJsonNull() ? Float.NaN : st.getAsFloat(), 0,
					reactionSpeed(speeds, e.get(2).getAsInt())}, motions, e.get(2).getAsInt()));
		}
		float[] avoid = pair(a.getAsJsonArray("avoidLock"));
		Attack landing = new Attack(animation + "_landing", taeId == 0 ? 0 : taeId + 10, a.get("landingDuration").getAsFloat(),
				landingHits, avoid[0], avoid[1], a.get("moveLock").getAsFloat(), a.get("lightLock").getAsFloat(),
				a.get("heavyLock").getAsFloat(), a.get("leftLightLock").getAsFloat(), a.get("leftHeavyLock").getAsFloat(),
				0, 0, false, left, List.of(), List.of(), cost, charges(landingHits), windows(a.getAsJsonArray("regenBlock")));
		return new Moveset.Fall(air, landing, animation + "_start");
	}

	/** A slot that may be empty (id 1) or have no animation in c0001: null then. */
	private static Attack optional(JsonObject hand, String slot, JsonObject animations) {
		if (hand == null || !hand.has(slot) || hand.get(slot).isJsonNull()) {
			return null;
		}
		if (hand.getAsJsonObject(slot).has("cast")) {
			return Attack.cast(hand.getAsJsonObject(slot).get("staminaCost").getAsFloat());
		}
		JsonElement a = animations.get(hand.getAsJsonObject(slot).get("taeId").getAsString());
		return a == null || a.isJsonNull() ? null : attack(hand, slot, animations);
	}

	static Attack attack(JsonObject hand, String slot, JsonObject animations) {
		JsonObject s = hand.getAsJsonObject(slot);
		if (s.has("bowAction")) {
			// The bow's motion types 8 / 15 / 16 (docs/research/bow.md): handled by SoulsBow before any attack starts;
			// a placeholder without hits keeps the moveset whole.
			return Attack.cast(s.get("staminaCost").getAsFloat());
		}
		if (s.has("cast")) {
			return Attack.cast(s.get("staminaCost").getAsFloat());
		}
		String key = s.get("taeId").getAsString();
		int taeId = taeNumber(key);
		JsonObject a = animations.getAsJsonObject(key);
		if (a == null) {
			throw new IllegalArgumentException("no animation data for " + key);
		}
		String animation = animationName(key);
		if (a.has("committed")) {
			List<float[]> hits = hits(a, s);
			List<float[]> inv = new ArrayList<>();
			for (JsonElement h : a.getAsJsonArray("invulnerable")) {
				inv.add(pair(h.getAsJsonArray()));
			}
			float duration = a.get("duration").getAsFloat();
			Attack critical = Attack.critical(animation, taeId, duration, hits, inv,
					a.get("leftAuthored").getAsBoolean(), s.get("staminaCost").getAsFloat(), charges(hits),
					windows(a.getAsJsonArray("regenBlock")),
					a.has("avoidLock") ? pair(a.getAsJsonArray("avoidLock"))[1] : duration,
					a.has("moveLock") ? a.get("moveLock").getAsFloat() : duration,
					a.has("attackLock") ? a.get("attackLock").getAsFloat() : duration);
			SwingSpeed.register(critical, s, a);
			AttackData.register(critical, s, a);
			return critical;
		}
		if (a.has("guard")) {
			return Attack.guard(animation, taeId, a.get("duration").getAsFloat(), a.get("leftAuthored").getAsBoolean());
		}
		List<float[]> hits = hits(a, s);
		float[] avoid = pair(a.getAsJsonArray("avoidLock"));
		float[] combo = pair(a.getAsJsonArray("combo"));
		Attack attack = new Attack(animation, taeId, a.get("duration").getAsFloat(), hits, avoid[0], avoid[1],
				a.get("moveLock").getAsFloat(), a.get("lightLock").getAsFloat(), a.get("heavyLock").getAsFloat(),
				a.get("leftLightLock").getAsFloat(), a.get("leftHeavyLock").getAsFloat(), combo[0], combo[1], false,
				a.get("leftAuthored").getAsBoolean(), List.of(), windows(a.getAsJsonArray("parry")),
				s.get("staminaCost").getAsFloat(), charges(hits), windows(a.getAsJsonArray("regenBlock")));
		HitBack.register(attack, s);
		SwingSpeed.register(attack, s, a);
		AttackData.register(attack, s, a);
		return attack;
	}

	/** The start of every hit event with a filled attack slot: each one charges the attack's stamina cost. */
	private static List<Float> charges(List<float[]> hits) {
		List<Float> out = new ArrayList<>();
		for (float[] h : hits) {
			if (!Float.isNaN(h[2])) {
				out.add(h[0]);
			}
		}
		return List.copyOf(out);
	}

	/** TAE 101100 windows of each dodge animation, in {@link Dodge} order. */
	public static List<List<float[]>> dodgeRegenBlock() {
		try (InputStream in = Weapons.class.getResourceAsStream(RESOURCE)) {
			JsonObject d = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonObject("dodgeRegenBlock");
			List<List<float[]>> out = new ArrayList<>();
			for (Dodge dodge : Dodge.values()) {
				out.add(windows(d.getAsJsonArray(dodge.animation())));
			}
			return List.copyOf(out);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Hit events {start, end, damage rate, guard stamina damage}: each event's attack slot (TAE param 0) picks a
	 * PlayerDamageParam of the slot's WeaponAttackMotionParam, whose +0x1c multiplies the damage (FUN_140138d50) and
	 * u16 +0x24 is the base stamina damage on guard (FUN_140139210). NaN = the slot is empty.
	 */
	private static List<float[]> hits(JsonObject animation, JsonObject slot) {
		JsonArray rates = slot.getAsJsonArray("damageRates");
		JsonArray stamina = slot.getAsJsonArray("staminaDamages");
		JsonArray floors = slot.getAsJsonArray("damageFloors");
		JsonArray speeds = slot.getAsJsonArray("reactionSpeeds");
		JsonArray motions = slot.getAsJsonArray("reactionMotions");
		List<float[]> out = new ArrayList<>();
		for (JsonElement h : animation.getAsJsonArray("hits")) {
			JsonArray e = h.getAsJsonArray();
			// Slots 5..9 are the other hand's descriptor (power stance), with the same PDP index (slot % 5).
			int index = e.get(2).getAsInt() % 5;
			if (index >= rates.size()) {
				out.add(new float[] {e.get(0).getAsFloat(), e.get(1).getAsFloat(), Float.NaN, Float.NaN, 0});
				continue;
			}
			JsonElement rate = rates.get(index);
			JsonElement st = stamina.get(index);
			JsonElement floor = floors.get(index);
			out.add(withMotion(new float[] {e.get(0).getAsFloat(), e.get(1).getAsFloat(),
					rate.isJsonNull() ? Float.NaN : rate.getAsFloat(), st.isJsonNull() ? Float.NaN : st.getAsFloat(),
					floor.isJsonNull() ? 0 : floor.getAsFloat(), reactionSpeed(speeds, index)}, motions, index));
		}
		return out;
	}

	/**
	 * A hit array {start, end, rate, stamina, floor, speed} with its {@code reactionMotions} entry appended at index 6
	 * (Attack.reactionMotion): reaction, thrust, DmgDownOrUp mode, DmgDistance base, near, far, falloff; NaN when empty.
	 * The PDP slot index follows (Attack.hitSlot).
	 */
	public static float[] withMotion(float[] hit, JsonArray motions, int index) {
		float[] out = java.util.Arrays.copyOf(hit, 6 + Attack.MOTION_FIELDS + 1);
		java.util.Arrays.fill(out, 6, out.length, Float.NaN);
		out[6 + Attack.MOTION_FIELDS] = index;
		if (motions != null && index < motions.size() && motions.get(index).isJsonArray()) {
			JsonArray m = motions.get(index).getAsJsonArray();
			for (int i = 0; i < Attack.MOTION_FIELDS && i < m.size(); i++) {
				out[6 + i] = m.get(i).getAsFloat();
			}
		}
		return out;
	}

	/** PlayerDamageParam +0x2c of PDP {@code index} (the victim's reaction speed, HitReaction), NaN when empty. */
	private static float reactionSpeed(JsonArray speeds, int index) {
		if (speeds == null || index >= speeds.size() || speeds.get(index).isJsonNull()) {
			return Float.NaN;
		}
		return speeds.get(index).getAsFloat();
	}

	/**
	 * Guard data of a weapon (docs/research/stamina.md, "Guardia"): stability one-handed / two-handed (WeaponReinforceParam
	 * +0x74 + WeaponTypeParam +0xd0 / +0xd4, level 0) and, as attacker, the stamina damage rate right / left hand
	 * (WeaponParam +0x84 x WeaponTypeParam +0x64 / +0x58).
	 */
	/**
	 * Guard data: stability one-handed / two-handed at +0, stamina rates, and the reinforcement part of the stability
	 * per DS2 level (WRP +0x74 + 4 x level; null when not exported).
	 */
	public record GuardData(float[] stability, float[] staminaRates, float[] stabilityByLevel) {
		/** The stability of a grip at a DS2 level (fractional levels interpolate; the mod's tiers rescale them). */
		public float stability(int grip, float level) {
			float base = stability[grip];
			if (stabilityByLevel == null || stabilityByLevel.length == 0 || level <= 0) {
				return base;
			}
			float l = Math.min(level, stabilityByLevel.length - 1);
			int lo = (int) Math.floor(l);
			int hi = Math.min(lo + 1, stabilityByLevel.length - 1);
			float at = stabilityByLevel[lo] + (stabilityByLevel[hi] - stabilityByLevel[lo]) * (l - lo);
			return base + at - stabilityByLevel[0];
		}
	}

	public static Map<String, GuardData> guards() {
		try (InputStream in = Weapons.class.getResourceAsStream(RESOURCE)) {
			Map<String, GuardData> out = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("weapons").entrySet()) {
				JsonObject w = e.getValue().getAsJsonObject();
				out.put(e.getKey(), new GuardData(rates(w.getAsJsonArray("guardStability")),
						rates(w.getAsJsonArray("guardStaminaRates")),
						w.has("guardStabilityByLevel") ? rates(w.getAsJsonArray("guardStabilityByLevel")) : null));
			}
			return out;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Guard break (reaction 10): a00_16_1010 DmgParryBreak_Start with its locks and TAE 101100, run like an item use. The
	 * animation is the user's choice among the candidates: how BT_Damage picks it is not decoded.
	 */
	public static Attack guardBreak() {
		try (InputStream in = Weapons.class.getResourceAsStream(RESOURCE)) {
			JsonObject g = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonObject("guardBreak");
			float duration = g.get("duration").getAsFloat();
			float[] avoid = pair(g.getAsJsonArray("avoidLock"));
			float attackLock = pair(g.getAsJsonArray("attackLock"))[1];
			return new Attack("soulsmode:ds2_guard_break", 161010, duration, List.of(), avoid[0], avoid[1],
					pair(g.getAsJsonArray("moveLock"))[1], attackLock, attackLock, attackLock, attackLock, 0, 0, false, false,
					List.of(), List.of(), 0, List.of(), windows(g.getAsJsonArray("regenBlock")));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Victim limits for a backstab (FUN_140390a50): CommonParam +0x100..+0x10c and class +0x00. */
	public record CriticalRange(int victimClass, float facing, float slope, float height, float distance) {
	}

	public static CriticalRange backstabRange(boolean playerVictim) {
		return criticalRange("backstab", playerVictim);
	}

	/** Victim limits of a critical kind: backstab (FUN_140390a50), riposte (FUN_1403908a0), guard_break (FUN_140390700). */
	public static CriticalRange criticalRange(String kind, boolean playerVictim) {
		try (InputStream in = Weapons.class.getResourceAsStream(RESOURCE)) {
			JsonObject t = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonObject("criticalTargets").getAsJsonObject(playerVictim ? "player" : "mob");
			JsonObject b = t.getAsJsonObject(kind);
			return new CriticalRange(t.get("class").getAsInt(), b.get("facing").getAsFloat(), b.get("slope").getAsFloat(),
					b.get("height").getAsFloat(), b.get("distance").getAsFloat());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * The DS2 torch (docs/research/weapons.md): torch time set by a torch picked up with none left ({@code firstAdd},
	 * PlayerCommonParam +0xc8), added otherwise ({@code add}, +0xca), its limit (+0xcc, FUN_1401bbd70), and the
	 * lighting animation a00_18_0220 ItemUseTorchMake with its TAE 210099 (the torch goes into the left hand) and
	 * 210100 (it is lit) times.
	 */
	public record Torch(float firstAdd, float add, float limit, Attack make, float equipAt, float lightAt) {
	}

	public static Torch torch() {
		try (InputStream in = Weapons.class.getResourceAsStream(RESOURCE)) {
			JsonObject t = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonObject("torch");
			JsonObject m = t.getAsJsonObject("make");
			float duration = m.get("duration").getAsFloat();
			float[] avoid = pair(m.getAsJsonArray("avoidLock"));
			float attackLock = m.get("attackLock").getAsFloat();
			// No CancelControl_Move track: walking is not locked. CancelControl_AttackAll locks every attack hand.
			Attack make = new Attack("soulsmode:ds2_180220", 180220, duration, List.of(), avoid[0], avoid[1], 0,
					attackLock, attackLock, attackLock, attackLock, 0, 0, false, m.get("leftAuthored").getAsBoolean(),
					List.of(), List.of(), 0, List.of(), List.of());
			JsonObject events = m.getAsJsonObject("events");
			return new Torch(t.get("firstAdd").getAsFloat(), t.get("add").getAsFloat(), t.get("limit").getAsFloat(), make,
					events.get("210099").getAsFloat(), events.get("210100").getAsFloat());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Two-handing data of a weapon's WeaponTypeParam (docs/research/weapons.md, "Agarre a dos manos: cambio y postura"):
	 * the stance id of the weapon two-handed in the right hand (+0x0c, AttackStance_LR) and where it is put away while
	 * the other weapon is two-handed (+0x24, WeaponStorage: 10 back top, 20 back bottom, 30 hip, 0 none).
	 */
	public record GripData(int twoHandStance, int storage) {
	}

	public static Map<String, GripData> grips() {
		try (InputStream in = Weapons.class.getResourceAsStream(RESOURCE)) {
			Map<String, GripData> out = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("weapons").entrySet()) {
				JsonObject w = e.getValue().getAsJsonObject();
				out.put(e.getKey(), new GripData(w.get("twoHandStance").getAsInt(), w.get("storage").getAsInt()));
			}
			return out;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Human Effigy use (docs/research/hollowing.md): a00_18_0190 ItemUseAbsorb with its CancelControl_Avoid / AttackAll
	 * windows (no CancelControl_Move: the player can walk), and the time of TAE 220000, when the item's SpEffect applies.
	 */
	public record Effigy(Attack use, float effectAt) {
	}

	public static Effigy effigy() {
		return itemUse("effigy", 180190);
	}

	/** Pine resin use (docs/research/resins.md): a00_18_0060 ItemUseEnchantWeapon, SpEffect at TAE 220000 (0.667 s). */
	public static Effigy resin() {
		return itemUse("resin", 180060);
	}

	/** Soul item use (docs/research/soul_items.md): a00_18_0090 ItemUseDeamonsSoul, SpEffect at TAE 220000 (1.7 s). */
	public static Effigy soulItem() {
		return itemUse("soul_item", 180090);
	}

	/**
	 * Lloyd's Talisman (docs/research/thrown_items.md): a00_18_0100 ItemUseThrowOverhand, the item leaves the hand at
	 * TAE 220000 (0.5333 s).
	 */
	public static Effigy lloydsTalisman() {
		return itemUse("lloyds_talisman", 180100);
	}

	/**
	 * Firebomb (docs/research/thrown_items.md): ItemTypeParam 1000 -> a00_18_0110 ItemUseThrowHeavyItem, the bomb
	 * leaving the hand at TAE 220000 (0.8667 s).
	 */
	public static Effigy firebomb() {
		return itemUse("firebomb", 180110);
	}

	/**
	 * Homeward Bone (docs/research/bonfire.md "Hueso de regreso"): ItemTypeParam 250 -> a00_18_0080 ItemUsePray, the warp
	 * at TAE 220000 (2.333 s).
	 */
	public static Effigy homewardBone() {
		return itemUse("homeward_bone", 180080);
	}

	private static Effigy itemUse(String key, int taeId) {
		try (InputStream in = Weapons.class.getResourceAsStream(RESOURCE)) {
			JsonObject e = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonObject(key);
			float duration = e.get("duration").getAsFloat();
			float[] avoid = pair(e.getAsJsonArray("avoidLock"));
			float attackLock = e.get("attackLock").getAsFloat();
			// The throws have a CancelControl_Move track (thrown_items.md); the other item uses walk.
			float moveLock = e.has("moveLock") ? e.get("moveLock").getAsFloat() : 0;
			Attack use = new Attack("soulsmode:ds2_" + taeId, taeId, duration, List.of(), avoid[0], avoid[1], moveLock,
					attackLock, attackLock, attackLock, attackLock, 0, 0, false, e.get("leftAuthored").getAsBoolean(),
					List.of(), List.of(), 0, List.of(), List.of());
			return new Effigy(use, e.getAsJsonObject("events").get("220000").getAsFloat());
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** Equip load weight of each weapon (WeaponParam +0x20). */
	public static Map<String, Float> weights() {
		try (InputStream in = Weapons.class.getResourceAsStream(RESOURCE)) {
			Map<String, Float> out = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("weapons").entrySet()) {
				out.put(e.getKey(), e.getValue().getAsJsonObject().get("weight").getAsFloat());
			}
			return out;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * The victim side of each weapon's backstab (docs/research/weapons.md, "Lado de la víctima"; player victim, c0001):
	 * when it can move and attack again, and until when it cannot be critted again, from the backstab's start.
	 */
	public record BackstabVictim(float moveAt, float attackAt, float noCriticalUntil, List<float[]> invulnerable,
			int catchTae, float getUpAt) {
	}

	public static Map<String, BackstabVictim> backstabVictims() {
		return criticalVictims("backstab");
	}

	/** Each weapon's critical attack of a kind (slot 61 riposte, 62 guard_break, 63 backstab); none if its motion type is 0. */
	public static Map<String, Attack> criticals(String kind) {
		JsonObject json = raw();
		JsonObject animations = json.getAsJsonObject("animations");
		Map<String, Attack> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("weapons").entrySet()) {
			Attack a = critical(e.getValue().getAsJsonObject(), kind, animations);
			if (a != null) {
				out.put(e.getKey(), a);
			}
		}
		return out;
	}

	/**
	 * A parried attacker (docs/research/weapons.md "Riposte"; player victim c0001, used for mobs by the user's choice):
	 * the riposte window and when it can move / attack again, from the parry; its TAE 110100 windows.
	 */
	public record Parried(float riposteFrom, float riposteUntil, float moveAt, float attackAt, List<float[]> invulnerable) {
	}

	public static Parried parried() {
		try (InputStream in = Weapons.class.getResourceAsStream(RESOURCE)) {
			JsonObject p = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonObject("parried");
			return new Parried(p.get("riposteFrom").getAsFloat(), p.get("riposteUntil").getAsFloat(),
					p.get("moveAt").getAsFloat(), p.get("attackAt").getAsFloat(), windows(p.getAsJsonArray("invulnerable")));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static Map<String, BackstabVictim> criticalVictims(String kind) {
		JsonObject json = raw();
		JsonObject animations = json.getAsJsonObject("animations");
		Map<String, BackstabVictim> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("weapons").entrySet()) {
			BackstabVictim v = victim(e.getValue().getAsJsonObject(), kind, animations);
			if (v != null) {
				out.put(e.getKey(), v);
			}
		}
		return out;
	}

	/** Parry angle limits of a player parrier, {horizontal, vertical} (ChrCommonParam 100 +0x9c / +0x98). */
	public static float[] parryAngles() {
		try (InputStream in = Weapons.class.getResourceAsStream(RESOURCE)) {
			return rates(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonArray("parryAngles"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static List<float[]> windows(JsonArray json) {
		List<float[]> out = new ArrayList<>();
		for (JsonElement w : json) {
			out.add(pair(w.getAsJsonArray()));
		}
		return out;
	}

	private static float[] rates(JsonArray json) {
		float[] out = new float[json.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = json.get(i).getAsFloat();
		}
		return out;
	}

	private static float[] pair(JsonArray json) {
		return new float[] {json.get(0).getAsFloat(), json.get(1).getAsFloat()};
	}
}
