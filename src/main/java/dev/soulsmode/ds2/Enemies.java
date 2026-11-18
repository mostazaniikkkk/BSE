package dev.soulsmode.ds2;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DS2 enemies (docs/research/enemies.md), the engine side of datapack enemies: an EnemyParam-like definition, its
 * attacks (EnemyDamageParam rows on animation hit windows) and the formulas of both directions of a hit. The model,
 * animations, attack timings and AI come from the pack (docs/datapacks.md, "Enemigos").
 */
public final class Enemies {
	private Enemies() {
	}

	/**
	 * One EnemyDamageParam row (enemies.md s.4.1): attack value per type (0 physical, 1 magic, 2 lightning, 3 fire,
	 * 4 dark; +0x0c/+0x10/+0x14 into +0x19/+0x1a/+0x1b), floor +0x18, rate +0x1c, guard stamina damage u16 +0x24.
	 */
	/**
	 * An EnemyDamageParam row: damages, floor, rate, guard stamina damage and the status pairs the mod reads, type 12
	 * petrification and type 9 curse (FUN_140170710, docs/research/petrification.md section 1.1).
	 */
	public record Damage(float[] values, int floor, float rate, int staminaDamage, float petrify, float curse) {
		public Damage(float[] values, int floor, float rate, int staminaDamage) {
			this(values, floor, rate, staminaDamage, 0, 0);
		}
	}

	/**
	 * An attack (an EnemyBehavior entry, enemy_ai.md s.7): its animation, length and hit windows {start, end} with the
	 * damage row they deal; the arguments of the attack subroutine S2147483595 (reach in blocks, angle in degrees, s.9.2);
	 * and for the declarative table its distance rule (f7018 min / max) and the width of its RandInt interval (weight).
	 */
	public record Attack(String name, String animation, float duration, List<float[]> hits, Damage damage, float reach,
			float minRange, float maxRange, float angle, int weight, List<Damage> hitDamages) {
		/** The row of hit window i: its own (a TAE event names its EDP row, enemy_ai.md s.8) or the attack's. */
		public Damage damage(int i) {
			Damage own = i >= 0 && i < hitDamages.size() ? hitDamages.get(i) : null;
			return own != null ? own : damage;
		}
	}

	private static Damage damage(JsonObject d) {
		float[] values = new float[5];
		String[] types = {"physical", "magic", "lightning", "fire", "dark"};
		for (int i = 0; i < 5; i++) {
			values[i] = d.has(types[i]) ? d.get(types[i]).getAsFloat() : 0;
		}
		return new Damage(values, d.has("floor") ? d.get("floor").getAsInt() : 0, d.has("rate") ? d.get("rate").getAsFloat() : 1,
				d.has("staminaDamage") ? d.get("staminaDamage").getAsInt() : 0,
				d.has("petrify") ? d.get("petrify").getAsFloat() : 0, d.has("curse") ? d.get("curse").getAsFloat() : 0);
	}

	/** One ItemLotParam2_Chr slot (s.6): item, count, chance in % per roll. */
	public record Drop(String item, int count, float chance) {
	}

	/**
	 * EnemyParam fields (s.2): HP +0x28, stamina +0x2c, poise +0x118 and its regen per s +0x11c, physical defence
	 * +0x9c, absorption magic / lightning / fire / dark +0xa0..+0xac (%), guard stability +0x74 and % blocked
	 * +0x78..+0x88, attack multiplier +0xd8, souls +0xd0; drop slots and rolls (s.6); critical limits of
	 * EnemyCommonParam +0x100..+0x12c are the mod's mob row.
	 */
	public record Def(int hp, float stamina, float poise, float poiseRegen, int defence, float[] absorption,
			float guardStability, float[] guardBlocked, float attackMultiplier, int souls, int rolls, List<Drop> drops,
			List<Attack> attacks, float walkSpeed, float runSpeed, float width, float height, float followRange,
			Map<String, String> animations, Map<String, Float> durations, String model, String texture,
			String animationFile, net.minecraft.resources.ResourceLocation script, boolean boss, float[] turn,
			float bleedResistance, Targets targets) {
		/**
		 * The turn rate in degrees per tick outside attacks (FUN_1403a28b0): lerp(EnemyMoveParam +0x2c, +0x30) by
		 * speed / +0x20, rad/s; {@code turn} = {+0x2c, +0x30, +0x20}. Null: Minecraft's mob turn.
		 */
		public float turnDegreesPerTick(double speed) {
			if (turn == null) {
				return 30;
			}
			double f = Math.max(0, Math.min(1, speed / turn[2]));
			return (float) Math.toDegrees(turn[0] + (turn[1] - turn[0]) * f) / 20f;
		}

		public Attack attack(String name) {
			for (Attack a : attacks) {
				if (a.name().equals(name)) {
					return a;
				}
			}
			return null;
		}
	}

	/**
	 * Who the enemy goes after (the mod's own field: DS2 decides it by faction in the ESD, which a pack writes as this
	 * filter). {@code players}: false = no player; {@code playerPredicate}: a datapack predicate the player must pass
	 * (null = all). {@code entities}: entity type ids or {@code #tags} that are targets too (empty = none).
	 */
	public record Targets(boolean players, net.minecraft.resources.ResourceLocation playerPredicate,
			List<String> entities) {
		public static final Targets DEFAULT = new Targets(true, null, List.of());

		static Targets parse(JsonObject t) {
			boolean players = true;
			net.minecraft.resources.ResourceLocation predicate = null;
			if (t.has("players")) {
				JsonElement p = t.get("players");
				if (p.isJsonPrimitive() && p.getAsJsonPrimitive().isBoolean()) {
					players = p.getAsBoolean();
				} else {
					predicate = net.minecraft.resources.ResourceLocation.parse(p.getAsString());
				}
			}
			List<String> entities = new ArrayList<>();
			if (t.has("entities")) {
				for (JsonElement e : t.getAsJsonArray("entities")) {
					String s = e.getAsString();
					// Validated here so a typo fails the load, not every tick.
					net.minecraft.resources.ResourceLocation.parse(s.startsWith("#") ? s.substring(1) : s);
					entities.add(s);
				}
			}
			return new Targets(players, predicate, List.copyOf(entities));
		}
	}

	public static Def parse(JsonObject j) {
		JsonArray abs = j.getAsJsonArray("absorption");
		float[] absorption = new float[4];
		for (int i = 0; i < 4; i++) {
			absorption[i] = abs.get(i).getAsFloat();
		}
		JsonObject guard = j.has("guard") ? j.getAsJsonObject("guard") : null;
		float[] blocked = new float[5];
		if (guard != null) {
			JsonArray b = guard.getAsJsonArray("blocked");
			for (int i = 0; i < 5; i++) {
				blocked[i] = b.get(i).getAsFloat();
			}
		}
		List<Drop> drops = new ArrayList<>();
		if (j.has("drops")) {
			for (JsonElement e : j.getAsJsonArray("drops")) {
				JsonObject d = e.getAsJsonObject();
				drops.add(new Drop(d.get("item").getAsString(), d.has("count") ? d.get("count").getAsInt() : 1,
						d.get("chance").getAsFloat()));
			}
		}
		List<Attack> attacks = new ArrayList<>();
		for (JsonElement e : j.getAsJsonArray("attacks")) {
			JsonObject a = e.getAsJsonObject();
			List<float[]> hits = new ArrayList<>();
			List<Damage> hitDamages = new ArrayList<>();
			for (JsonElement h : a.getAsJsonArray("hits")) {
				// [start, end] with the attack's row, or {"window": [start, end], "damage": {...}} with its own.
				JsonArray w = h.isJsonObject() ? h.getAsJsonObject().getAsJsonArray("window") : h.getAsJsonArray();
				hits.add(new float[] {w.get(0).getAsFloat(), w.get(1).getAsFloat()});
				hitDamages.add(h.isJsonObject() && h.getAsJsonObject().has("damage")
						? damage(h.getAsJsonObject().getAsJsonObject("damage")) : null);
			}
			JsonArray range = a.has("range") ? a.getAsJsonArray("range") : null;
			attacks.add(new Attack(a.get("name").getAsString(), a.get("animation").getAsString(),
					a.get("duration").getAsFloat(), List.copyOf(hits),
					a.has("damage") ? damage(a.getAsJsonObject("damage")) : new Damage(new float[5], 0, 1, 0),
					a.get("reach").getAsFloat(), range == null ? 0 : range.get(0).getAsFloat(),
					range == null ? Float.MAX_VALUE : range.get(1).getAsFloat(),
					a.has("angle") ? a.get("angle").getAsFloat() : 60, a.has("weight") ? a.get("weight").getAsInt() : 1,
					java.util.Collections.unmodifiableList(hitDamages)));
		}
		JsonObject anims = j.getAsJsonObject("animations");
		Map<String, String> names = new java.util.HashMap<>();
		Map<String, Float> durations = new java.util.HashMap<>();
		for (String key : List.of("idle", "walk", "run", "hurt", "death")) {
			if (anims.has(key)) {
				JsonElement e = anims.get(key);
				if (e.isJsonObject()) {
					names.put(key, e.getAsJsonObject().get("name").getAsString());
					durations.put(key, e.getAsJsonObject().get("duration").getAsFloat());
				} else {
					names.put(key, e.getAsString());
				}
			}
		}
		if (!durations.containsKey("hurt") || !durations.containsKey("death")) {
			throw new IllegalArgumentException("animations.hurt and animations.death need a name and a duration");
		}
		JsonArray size = j.getAsJsonArray("size");
		return new Def(j.get("hp").getAsInt(), j.get("stamina").getAsFloat(),
				j.has("poise") ? j.get("poise").getAsFloat() : 0, j.has("poiseRegen") ? j.get("poiseRegen").getAsFloat() : 0,
				j.get("defence").getAsInt(), absorption, guard == null ? 0 : guard.get("stability").getAsFloat(), blocked,
				j.has("attackMultiplier") ? j.get("attackMultiplier").getAsFloat() : 1, j.get("souls").getAsInt(),
				j.has("rolls") ? j.get("rolls").getAsInt() : 1, List.copyOf(drops), List.copyOf(attacks),
				j.get("walkSpeed").getAsFloat(), j.get("runSpeed").getAsFloat(), size.get(0).getAsFloat(),
				size.get(1).getAsFloat(), j.has("followRange") ? j.get("followRange").getAsFloat() : 16,
				Map.copyOf(names), Map.copyOf(durations), j.get("model").getAsString(), j.get("texture").getAsString(),
				j.get("animationFile").getAsString(),
				j.has("script") ? net.minecraft.resources.ResourceLocation.parse(j.get("script").getAsString()) : null,
				j.has("boss") && j.get("boss").getAsBoolean(), j.has("turn") ? new float[] {
						j.getAsJsonArray("turn").get(0).getAsFloat(), j.getAsJsonArray("turn").get(1).getAsFloat(),
						j.getAsJsonArray("turn").get(2).getAsFloat()} : null,
				// Bleed resistance (EnemyParam +0xb4 x 0.01, bleed.md section 2); 0.30, the most common in DS2, by
				// default (the user's choice for creatures without DS2 data).
				j.has("bleedResistance") ? j.get("bleedResistance").getAsFloat() : 0.30f,
				j.has("targets") ? Targets.parse(j.getAsJsonObject("targets")) : Targets.DEFAULT);
	}

	/**
	 * A player hit on an enemy (damage.md "Defensa del enemigo"): physical max(floor, 6 AR - def) / 6, each element
	 * max(floor, 6 AR) / 6 x (1 - abs), all x rate x WeaponParam +0x80 (passed in {@code weaponFactor}); poison is
	 * left out, as for mobs.
	 */
	public static float damageToEnemy(float[] attack, float rate, int floor, float weaponFactor, Def enemy) {
		float[] absorption = new float[4];
		for (int i = 0; i < 4; i++) {
			absorption[i] = enemy.absorption()[i];
		}
		return damageToEnemy(attack, rate, floor, weaponFactor, enemy.defence(), absorption);
	}

	/** The same with an EnemyParam's defence (+0x9c) and absorption % (+0xa0..+0xac) given directly. */
	public static float damageToEnemy(float[] attack, float rate, int floor, float weaponFactor, float defence,
			float[] absorptionPercent) {
		float total = 0;
		if (attack[0] > 0) {
			total += Math.max(floor, 6 * attack[0] - defence) / 6f * rate * weaponFactor;
		}
		for (int i = 1; i < 5; i++) {
			if (attack[i] > 0) {
				float abs = Math.max(0, Math.min(1, absorptionPercent[i - 1] * 0.01f));
				total += Math.max(floor, 6 * attack[i]) / 6f * (1 - abs) * rate * weaponFactor;
			}
		}
		return total;
	}

	/**
	 * An enemy hit (s.4.1): the row's values x EP +0xd8, then the player victim formula (damage.md s.5): physical
	 * max(floor, 10 V - def) / 12 x rate, element max(floor, 6 V) / 6 x (1 - abs) x rate. {@code defence} null =
	 * a victim without DS2 defence (0, no absorption).
	 */
	public static float damageToPlayer(Damage d, float attackMultiplier, Ds2Damage.Defence defence) {
		float total = 0;
		float v0 = d.values()[0] * attackMultiplier;
		if (v0 > 0) {
			total += Math.max(d.floor(), 10 * v0 - (defence == null ? 0 : defence.physical())) / 12f * d.rate();
		}
		for (int i = 1; i < 5; i++) {
			float v = d.values()[i] * attackMultiplier;
			if (v > 0) {
				float abs = defence == null ? 0 : defence.absorption()[i - 1];
				total += Math.max(d.floor(), 6 * v) / 6f * (1 - abs) * d.rate();
			}
		}
		return total;
	}
}
