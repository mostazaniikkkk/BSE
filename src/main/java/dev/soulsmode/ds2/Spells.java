package dev.soulsmode.ds2;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * DS2 spells and attunement (docs/research/spells.md, casting.md). A spell is a SpellParam row in JSON; the mod ships
 * the engine and the cast animations, the spells themselves come from datapacks (docs/datapacks.md).
 */
public final class Spells {
	/** Attuned spell array: 14 entries (inv+0x25910..+0x25980). */
	public static final int MAX_ATTUNED = 14;
	/** a38_11_5030 StickSpellNoBullet_R: every failed cast check plays it (FUN_140395930, 0x24596d6). */
	public static final String FAILED_CAST = "38115030";
	/** SpellParam +0x05 target conditions the mod can run: 0 always, 1 / 2 enchantable weapon / shield, 5 never (FUN_1401a46a0). */
	private static final List<Integer> TARGETS = List.of(0, 1, 2, 5);

	/**
	 * A SpellParam row: school +0x00 (0 sorcery, 1 miracle, 2 pyromancy, 3 / 4 hexes), Int / Faith requirements
	 * +0x08 / +0x0a, attunement slots +0xf0, uses per uses level 1..10 +0xf1..+0xfa, target condition +0x05, and the
	 * cast block's prepare / cast animations (block +0x08 / +0x0c; the 8 blocks are identical for every player spell)
	 * and its stamina factor (block +0x10, multiplies the catalyst slot's cost; FUN_1403936a0 at 0x1403941e4).
	 * {@code bullet} is the BulletParam row of the spell (+0x0c, with its PlayerDamageParam +0x10; FUN_140390050) among
	 * {@code bullets}, which also holds its child bullets; {@code rangeFactor} is SpellParam +0xb4 (homing range).
	 */
	public record Spell(int school, int requiredIntelligence, int requiredFaith, int slots, int[] uses, int target,
			String prepare, String cast, float staminaFactor, float castTime, float rangeFactor, String bullet,
			java.util.Map<String, Bullets.Def> bullets, java.util.Map<Integer, List<JsonObject>> spEffects,
			net.minecraft.resources.ResourceLocation script) {
	}

	private Spells() {
	}

	public static Spell parse(JsonObject json) {
		int school = json.get("school").getAsInt();
		if (school < 0 || school > 4) {
			throw new IllegalArgumentException("school must be 0..4");
		}
		JsonArray req = json.getAsJsonArray("requirements");
		JsonArray usesJson = json.getAsJsonArray("uses");
		if (usesJson.size() != 10) {
			throw new IllegalArgumentException("uses needs 10 values (uses levels 1..10)");
		}
		int[] uses = new int[10];
		for (int i = 0; i < 10; i++) {
			uses[i] = usesJson.get(i).getAsInt();
		}
		int target = json.has("target") ? json.get("target").getAsInt() : 0;
		if (!TARGETS.contains(target)) {
			throw new IllegalArgumentException("target " + target + " is not supported (only " + TARGETS + ")");
		}
		int slots = json.get("slots").getAsInt();
		if (slots < 1 || slots > MAX_ATTUNED) {
			throw new IllegalArgumentException("slots must be 1.." + MAX_ATTUNED);
		}
		return new Spell(school, req.get(0).getAsInt(), req.get(1).getAsInt(), slots, uses, target,
				json.get("prepare").getAsString(), json.get("cast").getAsString(), json.get("staminaFactor").getAsFloat(),
				json.get("castTime").getAsFloat(), json.has("rangeFactor") ? json.get("rangeFactor").getAsFloat() : 1, bulletId(json), bullets(json),
				spEffects(json), json.has("script")
						? net.minecraft.resources.ResourceLocation.parse(json.get("script").getAsString()) : null);
	}

	/** The SpEffect instructions the mod runs (docs/research/speffects.md); others are ignored (logged by the pack's export). */
	private static final List<String> SP_EFFECT_TYPES = List.of("statusSelectBullet", "statMultiplier", "heal",
			"durationScales", "split", "attack", "defence", "movementRate", "statRate", "statAdd", "statAddLarge");

	private static java.util.Map<Integer, List<JsonObject>> spEffects(JsonObject json) {
		java.util.Map<Integer, List<JsonObject>> out = new java.util.HashMap<>();
		if (!json.has("spEffects")) {
			return java.util.Map.of();
		}
		for (var e : json.getAsJsonObject("spEffects").entrySet()) {
			List<JsonObject> list = new ArrayList<>();
			for (JsonElement i : e.getValue().getAsJsonObject().getAsJsonArray("instructions")) {
				String type = i.getAsJsonObject().get("type").getAsString();
				if (!SP_EFFECT_TYPES.contains(type)) {
					throw new IllegalArgumentException("SpEffect " + e.getKey() + ": unknown instruction " + type);
				}
				list.add(i.getAsJsonObject());
			}
			out.put(Integer.parseInt(e.getKey()), List.copyOf(list));
		}
		return java.util.Map.copyOf(out);
	}

	private static String bulletId(JsonObject json) {
		return json.has("bullet") ? json.get("bullet").getAsString() : null;
	}

	private static java.util.Map<String, Bullets.Def> bullets(JsonObject json) {
		if (!json.has("bullets")) {
			return java.util.Map.of();
		}
		java.util.Map<String, Bullets.Def> out = Bullets.parse(json.getAsJsonObject("bullets"));
		if (json.has("bullet") && !out.containsKey(json.get("bullet").getAsString())) {
			throw new IllegalArgumentException("bullet " + json.get("bullet").getAsString() + " is not in bullets");
		}
		return java.util.Map.copyOf(out);
	}

	/**
	 * The wait from the start of ShootStateControl to the cast animation (FUN_1403972d0 counts down b8+0x1b8, written
	 * by FUN_1403936a0; docs/research/casting.md section 6): block +0x00 x ChrCommonParam factor (1.0 for every grip)
	 * x WeaponTypeParam +0x10c x derived +0x0c (row ((2 Att + Int + Fth) / 4) +0x34) x the lack factor
	 * (FUN_14038f9d0) x b8+0x488 (1.0: the ctor's value, no SpEffect changes it in the mod).
	 */
	public static float castWait(Spell spell, float catalystCastTime, Attributes stats, PhysicalStatsTable table,
			float requirementLack, float staminaLack) {
		int row = (2 * stats.get(Attributes.ATTUNEMENT) + stats.get(Attributes.INTELLIGENCE) + stats.get(Attributes.FAITH)) / 4;
		float f = Math.max(0, Math.min(1, requirementLack));
		float g = Math.max(0, Math.min(1, staminaLack));
		float lack = 1 + Math.max(0, Math.min(1, (f > 0 ? 0.01f * (50 + 50 * f) : 0) + (g > 0 ? 0.01f * 50 * g : 0)));
		return spell.castTime() * catalystCastTime * table.row(row).castTime() * lack;
	}

	/** LockOnParam f1 (PlayerLockTargetCtrl+0x24): with WeaponParam +0x40 x SpellParam +0xb4, the homing range. */
	public static float lockOnBulletRange() {
		try (java.io.InputStream in = Spells.class.getResourceAsStream(LockOnData.RESOURCE)) {
			return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,
					java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("lockOn")
					.get("bulletRange").getAsFloat();
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	/** FUN_1401b40f0: attunement capacity, min(14, derived +0x02 + bonus). The bonus source is not traced: 0. */
	public static int capacity(int attunementSlots) {
		return Math.min(MAX_ATTUNED, attunementSlots);
	}

	/**
	 * FUN_1401ab750: max uses = ceil(SpellParam[+0xf0 + uses level] x chr.b8+0x484). The multiplier is 1.0 unless a
	 * SpEffect changes it (rings, hoods...), none of which exist in the mod.
	 */
	public static int maxUses(Spell spell, int usesLevel) {
		int level = Math.max(1, Math.min(10, usesLevel));
		return (int) Math.ceil(spell.uses()[level - 1] * 1.0f);
	}

	/** FUN_1401ab700: uses given back by a restore of {@code percent} (all DS2 sources round up). */
	public static int restored(int max, float percent) {
		return (int) (Math.ceil(max * percent * 0.01f) + 0.1f);
	}

	/**
	 * How many attuned entries stay (FUN_1401b40f0): the running slot sum is not lowered when an entry is removed, so
	 * from the first entry that goes over the capacity, every later one is removed too.
	 */
	public static int keptWithin(List<Integer> slots, int capacity) {
		int sum = 0;
		for (int i = 0; i < slots.size(); i++) {
			sum += slots.get(i);
			if (sum > capacity) {
				return i;
			}
		}
		return slots.size();
	}

	/**
	 * A cast animation's timing (docs/research/casting.md section 2, weapons.json {@code castAnimations}): length,
	 * TAE 2300 bullet events {time, slot}, regen block and the CancelControl_* windows (NaN: no track, not locked).
	 */
	public record CastAnimation(String animation, float duration, List<float[]> bullets, List<float[]> regenBlock,
			float avoidStart, float avoidEnd, float moveLock, float lightLock, float heavyLock, float leftLightLock,
			float leftHeavyLock, float guardLock, float attackLock, boolean leftAuthored, float shootState,
			List<float[]> bulletBones) {
		/**
		 * The R_Weapon bone at a bullet time (cast_origins.py): {time, position left / up / forward (m), rotation 3x3
		 * from the bone's local rig axes to left / up / forward, row-major}; null if not exported.
		 */
		public float[] bulletBone(float time) {
			for (float[] b : bulletBones) {
				if (Math.abs(b[0] - time) < 1e-3f) {
					return b;
				}
			}
			return null;
		}
	}

	public static CastAnimation castAnimation(String key, JsonObject a) {
		List<float[]> bullets = new ArrayList<>();
		for (JsonElement b : a.getAsJsonArray("bullets")) {
			bullets.add(new float[] {b.getAsJsonArray().get(0).getAsFloat(), b.getAsJsonArray().get(1).getAsFloat()});
		}
		List<float[]> regen = new ArrayList<>();
		for (JsonElement w : a.getAsJsonArray("regenBlock")) {
			regen.add(new float[] {w.getAsJsonArray().get(0).getAsFloat(), w.getAsJsonArray().get(1).getAsFloat()});
		}
		JsonElement avoid = a.get("avoidLock");
		boolean hasAvoid = avoid != null && !avoid.isJsonNull();
		return new CastAnimation(Weapons.animationName(key), a.get("duration").getAsFloat(), List.copyOf(bullets),
				List.copyOf(regen), hasAvoid ? avoid.getAsJsonArray().get(0).getAsFloat() : Float.NaN,
				hasAvoid ? avoid.getAsJsonArray().get(1).getAsFloat() : Float.NaN, opt(a, "moveLock"), opt(a, "lightLock"),
				opt(a, "heavyLock"), opt(a, "leftLightLock"), opt(a, "leftHeavyLock"), opt(a, "guardLock"),
				opt(a, "attackLock"), a.get("leftAuthored").getAsBoolean(),
				a.has("shootState") && !a.get("shootState").isJsonNull()
						? a.getAsJsonArray("shootState").get(0).getAsFloat() : Float.NaN, bulletBones(a));
	}

	private static List<float[]> bulletBones(JsonObject a) {
		List<float[]> out = new ArrayList<>();
		if (a.has("bulletBones")) {
			for (JsonElement e : a.getAsJsonArray("bulletBones")) {
				JsonObject b = e.getAsJsonObject();
				float[] v = new float[13];
				v[0] = b.get("time").getAsFloat();
				for (int i = 0; i < 3; i++) {
					v[1 + i] = b.getAsJsonArray("pos").get(i).getAsFloat();
					for (int j = 0; j < 3; j++) {
						v[4 + i * 3 + j] = b.getAsJsonArray("rot").get(i).getAsJsonArray().get(j).getAsFloat();
					}
				}
				out.add(v);
			}
		}
		return List.copyOf(out);
	}

	private static float opt(JsonObject a, String key) {
		JsonElement e = a.get(key);
		return e == null || e.isJsonNull() ? Float.NaN : e.getAsFloat();
	}

	/** The mod's DS2 cast animations by TAE id (weapons.json {@code castAnimations}). */
	public static CastAnimation builtInCastAnimation(String key) {
		JsonObject all = Weapons.raw().getAsJsonObject("castAnimations");
		return all.has(key) ? castAnimation(key, all.getAsJsonObject(key)) : null;
	}
}
