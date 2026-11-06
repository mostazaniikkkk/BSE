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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DS2 attack rating and damage (docs/research/damage.md).
 * <ul>
 * <li>Per damage type i (0 physical, 1 magic, 2 lightning, 3 fire, 4 dark, 5 poison): {@code val[i] = max(0, (S[i] +
 * B[i]) x M[i]) x k} (FUN_140391fe0). B = lerp(base +0, base +max, level / max) x scale; M = the split (+ a resin's
 * 0.2); S = the stat bonuses x the weapon's coefficients for that level (FUN_1403903b0), plus a resin's +40.</li>
 * <li>k = (1 - lack penalty) x the hand factor (WeaponTypeParam +0x60 right / +0x54 left). Lack f = clamp(sum over
 * Str, Dex, Int, Fth of max(0, 1 - stat / max(req, 1)), 0, 1) with the Strength requirement halved two-handed
 * (FUN_14034d3c0); penalty 0.01 x (30 + 50 f) when f > 0, plus 0.01 x 10 g for a stamina deficit g
 * (PlayerLackOfStatsParam row 0).</li>
 * <li>Victim (an enemy, FUN_140138d50): d = max(floor, 6 A - 6 def) / 6 x PDP rate x WeaponParam +0x80 per type, with
 * the enemy multiplier and divisor 6 / 6 (DamageAdjustParam +0x114 / +0x14c, elements +0x118.. / +0x150..).</li>
 * </ul>
 */
public final class Ds2Damage {
	public static final String RESOURCE = Weapons.RESOURCE;
	public static final int TYPES = 6;
	/** Enemy victim: attack multiplier and divisor (DamageAdjustParam), the same for every type. */
	private static final float ENEMY_MULTIPLIER = 6;
	private static final float ENEMY_DIVISOR = 6;

	public record WeaponData(int maxLevel, float[] base0, float[] baseMax, float[] split, float scale, float[][] coef,
			int[] requirements, float weaponFactor, float[] handFactor) {
	}

	private static Map<String, WeaponData> weapons;
	private static Map<String, float[]> bonus;
	private static Map<String, Piece[]> armor;

	/** An armour piece's DS2 data (docs/research/damage.md "Defensa del jugador"): defStat factor, physical defence per
	 * subtype, absorption magic / lightning / fire / dark. */
	public record Piece(float factor, float[] defence, float[] absorption) {
	}

	private Ds2Damage() {
	}

	public static synchronized Map<String, WeaponData> weapons() {
		if (weapons == null) {
			load();
		}
		return weapons;
	}

	private static void load() {
		try (InputStream in = Ds2Damage.class.getResourceAsStream(RESOURCE)) {
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			weapons = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("weapons").entrySet()) {
				weapons.put(e.getKey(), parseWeapon(e.getValue().getAsJsonObject().getAsJsonObject("attack")));
			}
			bonus = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("statBonus").entrySet()) {
				bonus.put(e.getKey(), floats(e.getValue().getAsJsonArray()));
			}
			armor = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("armor").entrySet()) {
				JsonArray pieces = e.getValue().getAsJsonArray();
				Piece[] out = new Piece[pieces.size()];
				for (int i = 0; i < out.length; i++) {
					if (!pieces.get(i).isJsonNull()) {
						JsonObject p = pieces.get(i).getAsJsonObject();
						out[i] = new Piece(p.get("factor").getAsFloat(), floats(p.getAsJsonArray("defence")),
								floats(p.getAsJsonArray("absorption")));
					}
				}
				armor.put(e.getKey(), out);
			}
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** A weapon entry's {@code attack} block. */
	public static WeaponData parseWeapon(JsonObject a) {
		JsonArray coefJson = a.getAsJsonArray("coef");
		float[][] coef = new float[coefJson.size()][];
		for (int i = 0; i < coef.length; i++) {
			coef[i] = floats(coefJson.get(i).getAsJsonArray());
		}
		JsonArray req = a.getAsJsonArray("requirements");
		return new WeaponData(a.get("maxLevel").getAsInt(), floats(a.getAsJsonArray("base0")),
				floats(a.getAsJsonArray("baseMax")), floats(a.getAsJsonArray("split")), a.get("scale").getAsFloat(),
				coef, new int[] {req.get(0).getAsInt(), req.get(1).getAsInt(), req.get(2).getAsInt(), req.get(3).getAsInt()},
				a.get("weaponFactor").getAsFloat(), floats(a.getAsJsonArray("handFactor")));
	}

	/** Weapons added by datapacks, keyed by their full id (dev.soulsmode.content.SoulsContent); replaced on reload. */
	private static volatile Map<String, WeaponData> pack = Map.of();

	public static void setPack(Map<String, WeaponData> weapons) {
		pack = Map.copyOf(weapons);
	}

	/** WeaponParam +0x80 of a weapon (1.0 when unknown). */
	public static float weaponFactor(String key) {
		WeaponData w = weapon(key);
		return w == null ? 1 : w.weaponFactor();
	}

	/** A weapon by key: a built-in weapon's name, or a datapack weapon's full id. */
	public static WeaponData weapon(String key) {
		WeaponData w = pack.get(key);
		return w != null ? w : weapons().get(key);
	}

	private static float[] floats(JsonArray a) {
		float[] out = new float[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsFloat();
		}
		return out;
	}

	/**
	 * The weapon's stat lack f (FUN_14034d3c0): clamp(sum over Str, Dex, Int, Fth of max(0, 1 - stat / max(req, 1)), 0,
	 * 1), the Strength requirement halved two-handed. The attack rating's penalty and the cast wait use it.
	 */
	public static float requirementLack(String weapon, Attributes stats, boolean twoHanded) {
		WeaponData w = weapon(weapon);
		if (w == null) {
			return 0;
		}
		int[] req = {twoHanded ? w.requirements()[0] / 2 : w.requirements()[0], w.requirements()[1], w.requirements()[2],
				w.requirements()[3]};
		int[] have = {stats.get(Attributes.STRENGTH), stats.get(Attributes.DEXTERITY), stats.get(Attributes.INTELLIGENCE),
				stats.get(Attributes.FAITH)};
		float f = 0;
		for (int i = 0; i < 4; i++) {
			f += Math.max(0, 1 - have[i] / (float) Math.max(req[i], 1));
		}
		return Math.max(0, Math.min(1, f));
	}

	/**
	 * The weapon's Strength / Dexterity lack (FUN_14034ce60, slot +0x8c): the same as {@link #requirementLack} over Str
	 * and Dex only, the Strength requirement halved two-handed. GuardSpeed and GuardCancelSpeedCategory use it.
	 */
	public static float strDexLack(String weapon, Attributes stats, boolean twoHanded) {
		WeaponData w = weapon(weapon);
		if (w == null) {
			return 0;
		}
		int str = twoHanded ? w.requirements()[0] / 2 : w.requirements()[0];
		float f = Math.max(0, 1 - stats.get(Attributes.STRENGTH) / (float) Math.max(str, 1))
				+ Math.max(0, 1 - stats.get(Attributes.DEXTERITY) / (float) Math.max(w.requirements()[1], 1));
		return Math.max(0, Math.min(1, f));
	}

	/** FeWeaponThreshold rows 1 (Str) and 2 (Dex), 3..6 (Magic, Fire, Lightning, Dark): S, A, B, C, D (scaling_letters.md). */
	private static final float[] STR_LETTERS = {1.0f, 0.8f, 0.55f, 0.35f, 0.2f};
	private static final float[] DEX_LETTERS = {0.6f, 0.45f, 0.35f, 0.25f, 0.15f};
	private static final String[] LETTERS = {"S", "A", "B", "C", "D"};

	/**
	 * The weapon menu's scaling letters (FUN_1401b8ea0 / FUN_1400404d0, docs/research/scaling_letters.md) for Str, Dex,
	 * Magic, Lightning, Fire, Dark: the WeaponStatsAffectParam coefficient at the weapon's level, 0 when the weapon has
	 * none of that damage (split <= 0.01); not above 0 = "-", else the first of S..D whose threshold it reaches, else E.
	 */
	public static String[] scalingLetters(String weapon, int level) {
		WeaponData w = weapon(weapon);
		String[] out = {"-", "-", "-", "-", "-", "-"};
		if (w == null) {
			return out;
		}
		float[] c = w.coef()[Math.max(0, Math.min(Math.min(level, w.maxLevel()), w.coef().length - 1))];
		// coef index -> damage type of its split: Str / Dex physical, then magic, lightning, fire, dark.
		int[] type = {0, 0, 1, 2, 3, 4};
		for (int k = 0; k < 6; k++) {
			float value = Math.abs(w.split()[type[k]]) <= 0.01f ? 0 : c[k];
			if (value <= 0) {
				continue;
			}
			float[] thresholds = k == 1 ? DEX_LETTERS : STR_LETTERS;
			out[k] = "E";
			for (int i = 0; i < thresholds.length; i++) {
				if (value >= thresholds[i]) {
					out[k] = LETTERS[i];
					break;
				}
			}
		}
		return out;
	}

	/** Stat bonus for a stat value (PhysicalStatsPerLevelStatValuesParam row = value, 1..99). */
	private static float bonus(String column, int stat) {
		weapons();
		float[] values = bonus.get(column);
		return values[Math.max(1, Math.min(values.length, stat)) - 1];
	}

	/**
	 * Attack rating per type. {@code resinType}: the damage type a pine resin adds (2 lightning, 3 fire, 4 dark, 5
	 * poison), or -1.
	 */
	public static float[] attackRating(String weapon, int level, Attributes stats, boolean twoHanded, boolean leftHand,
			int resinType) {
		return attackRating(weapon, level, stats, twoHanded, leftHand, resinType, 0);
	}

	/**
	 * {@code staminaLack}: g = (cost - stamina) / cost when the attack was paid with too little stamina
	 * (FUN_140376510), which adds 0.01 x 10 g to the penalty (PlayerLackOfStatsParam row 0 +0x0c = 10).
	 */
	public static float[] attackRating(String weapon, int level, Attributes stats, boolean twoHanded, boolean leftHand,
			int resinType, float staminaLack) {
		return attackRating(weapon, level, stats, twoHanded, leftHand, resinType, staminaLack, null, null);
	}

	/**
	 * With the weapon buffs of the hand (speffects.md s.11.2 / 11.4): {@code buffAttack[i]} = Σ b of 100090[02]/[03]
	 * added to S (FUN_14038f3b0), {@code buffSplit[i]} = 0.01 Σ v of 100080[01] added to M (FUN_14038f440); null = none.
	 */
	public static float[] attackRating(String weapon, int level, Attributes stats, boolean twoHanded, boolean leftHand,
			int resinType, float staminaLack, float[] buffAttack, float[] buffSplit) {
		float[][] parts = attackParts(weapon, level, stats, twoHanded, leftHand, resinType, staminaLack, buffAttack, buffSplit);
		float[] out = new float[TYPES];
		for (int i = 0; i < TYPES; i++) {
			out[i] = Math.max(0, parts[0][i] + parts[1][i]);
		}
		return out;
	}

	/**
	 * The attack rating split as DS2's weapon menu shows it, per type: {base, stat bonus} = {B x M x k, S x M x k}
	 * (their sum, at least 0, is the attack rating).
	 */
	public static float[][] attackParts(String weapon, int level, Attributes stats, boolean twoHanded, boolean leftHand,
			int resinType, float staminaLack, float[] buffAttack, float[] buffSplit) {
		WeaponData w = weapon(weapon);
		float[][] out = new float[2][TYPES];
		if (w == null) {
			return out;
		}
		int lvl = Math.max(0, Math.min(w.maxLevel(), level));
		float t = w.maxLevel() > 0 ? lvl / (float) w.maxLevel() : 0;
		float[] c = w.coef()[Math.min(lvl, w.coef().length - 1)];
		int str = stats.get(Attributes.STRENGTH);
		int dex = stats.get(Attributes.DEXTERITY);
		int intel = stats.get(Attributes.INTELLIGENCE);
		int faith = stats.get(Attributes.FAITH);
		int adp = stats.get(Attributes.ADAPTABILITY);
		float[] s = {
				bonus("str", str) * c[0] + bonus("dex", dex) * c[1],
				bonus("magic", intel) * c[2],
				bonus("lightning", faith) * c[3],
				bonus("fire", (intel + faith) / 2) * c[4],
				bonus("dark", Math.min(intel, faith)) * c[5],
				bonus("poison", (3 * dex + adp) / 4) * c[6]};
		float[] m = new float[TYPES];
		for (int i = 0; i < TYPES; i++) {
			m[i] = w.split()[i];
		}
		if (resinType > 0 && resinType < TYPES) {
			// SpEffect 6025..6028 0000: split + 0.2 (0.5 poison), attack + 40 (100 poison) on the resin's type.
			boolean poison = resinType == 5;
			m[resinType] += poison ? 0.5f : 0.2f;
			s[resinType] += poison ? 100 : 40;
		}
		for (int i = 0; i < TYPES; i++) {
			if (buffAttack != null) {
				s[i] += buffAttack[i];
			}
			if (buffSplit != null) {
				m[i] += buffSplit[i];
			}
		}
		// Requirements (Strength halved two-handed) and the lack penalty.
		int reqStr = twoHanded ? w.requirements()[0] / 2 : w.requirements()[0];
		int[] req = {reqStr, w.requirements()[1], w.requirements()[2], w.requirements()[3]};
		int[] have = {str, dex, intel, faith};
		float f = 0;
		for (int i = 0; i < 4; i++) {
			f += Math.max(0, 1 - have[i] / (float) Math.max(req[i], 1));
		}
		f = Math.max(0, Math.min(1, f));
		float g = Math.max(0, Math.min(1, staminaLack));
		float penalty = (f > 0 ? 0.01f * (30 + 50 * f) : 0) + (g > 0 ? 0.01f * 10 * g : 0);
		float k = (1 - Math.max(0, Math.min(1, penalty))) * w.handFactor()[leftHand ? 1 : 0];
		for (int i = 0; i < TYPES; i++) {
			float b = (w.base0()[i] + (w.baseMax()[i] - w.base0()[i]) * t) * w.scale();
			out[0][i] = b * m[i] * k;
			out[1][i] = s[i] * m[i] * k;
		}
		return out;
	}

	/**
	 * The armour piece of a DS2 set for a body slot (0 head, 1 chest, 2 arms, 3 legs), or the naked piece when the set
	 * has none (ArmorParam rows 11001100..03).
	 */
	public static Piece piece(String set, int slot) {
		weapons();
		Piece[] pieces = set == null ? null : armor.get(set);
		Piece p = pieces == null ? null : pieces[slot];
		return p != null ? p : armor.get("naked")[slot];
	}

	/** A player victim's defence (FUN_140380070) and absorption (FUN_140381350) from its stats and armour pieces. */
	public record Defence(float physical, float[] absorption) {
	}

	/**
	 * def = sum of the pieces' physical defence (subtype 0) + (sum of their ArmorParam +0x1c) x defStat + the buffs' flat
	 * defence (vt+0x248 = FUN_140320380; speffects.md s.11.3 corrects the old + 5.5, which is vt+0x240), defStat
	 * = stat bonus +0x80 of row floor((End + Str + Vit + Dex) / 4); absorption per element = clamp(sum of the pieces' +
	 * the stat part, 0, 1). The armour stat lack factor is taken as 1 (its writer is not traced).
	 */
	public static Defence playerDefence(Attributes stats, Piece[] pieces) {
		return playerDefence(stats, pieces, 0, new float[4]);
	}

	/**
	 * With the defence buffs (100090[04]/[05], FUN_14031fe20): {@code buffDefence} = Σ b of type 0 (flat physical),
	 * {@code buffAbsorption} = 0.01 Σ b of types 1..4 (magic, lightning, fire, dark), added before the clamp.
	 */
	public static Defence playerDefence(Attributes stats, Piece[] pieces, float buffDefence, float[] buffAbsorption) {
		weapons();
		int defRow = (stats.get(Attributes.ENDURANCE) + stats.get(Attributes.STRENGTH) + stats.get(Attributes.VITALITY)
				+ stats.get(Attributes.DEXTERITY)) / 4;
		float factor = 0;
		float physical = 0;
		float[] absorption = new float[4];
		for (Piece p : pieces) {
			factor += p.factor();
			physical += p.defence()[0];
			for (int i = 0; i < 4; i++) {
				absorption[i] += p.absorption()[i];
			}
		}
		physical += factor * bonus("defStat", defRow) + buffDefence;
		int intel = stats.get(Attributes.INTELLIGENCE);
		int faith = stats.get(Attributes.FAITH);
		float[] stat = {bonus("absorbMagic", intel), bonus("absorbLightning", faith), bonus("absorbFire", (intel + faith) / 2),
				bonus("absorbDark", Math.min(intel, faith))};
		for (int i = 0; i < 4; i++) {
			absorption[i] = Math.max(0, Math.min(1, absorption[i] + stat[i] * 0.01f + buffAbsorption[i]));
		}
		return new Defence(physical, absorption);
	}

	/**
	 * Damage to a player (FUN_140138d50 with DamageAdjustParam +0x158 / +0x15c = 10 / 12 for physical, 6 / 6 for the
	 * elements): physical max(floor, 10 A - def) / 12, element t max(floor, 6 A) / 6 x (1 - absorption), each x rate x
	 * WeaponParam +0x80.
	 */
	public static float damageToPlayer(String weapon, float[] attack, float rate, int floor, Defence defence) {
		WeaponData w = weapon(weapon);
		float factor = w == null ? 1 : w.weaponFactor();
		float total = 0;
		if (attack[0] > 0) {
			total += Math.max(floor, attack[0] * 10 - defence.physical()) / 12 * rate * factor;
		}
		for (int i = 1; i < 5; i++) {
			if (attack[i] > 0) {
				total += Math.max(floor, attack[i] * 6) / 6 * (1 - defence.absorption()[i - 1]) * rate * factor;
			}
		}
		return total;
	}

	/**
	 * Damage to an enemy with zero defence for one hit (DS2 HP): per type max(floor, A) x rate x WeaponParam +0x80
	 * (A x 6 / 6); poison is a build-up, not damage, so it is left out.
	 */
	public static float damageToEnemy(String weapon, float[] attack, float rate, int floor) {
		WeaponData w = weapon(weapon);
		float factor = w == null ? 1 : w.weaponFactor();
		float total = 0;
		for (int i = 0; i < 5; i++) {
			if (attack[i] <= 0) {
				continue;
			}
			float a = attack[i] * ENEMY_MULTIPLIER;
			total += Math.max(floor, a) / ENEMY_DIVISOR * rate * factor;
		}
		return total;
	}
}
