package dev.soulsmode.ds2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * DS2's power stance (docs/research/powerstance.md), the data side:
 * <ul>
 * <li>The pair test (FUN_14034d930 / FUN_14034ede0): DualWieldingPermissionParam row = the right weapon's category
 * (WeaponTypeParam +0x38), the byte for the left weapon's category; weapons.json {@code dualWielding} has it resolved.
 * State 6 when the left category is not above the right one, else 5.</li>
 * <li>The stat test (FUN_140350170): Strength and Dexterity at least min(99, int(1.5 x the larger WeaponParam
 * +0x18 / +0x1a of the two)).</li>
 * <li>The left buttons' attacks, slots 52 / 53 / 54 (types 0x0d / 0x0e / 0x0f), from the weapon of state 5 (left) or 6
 * (right). Both weapons hit: TAE 2200 events on slots 0..4 are the right weapon's, 5..9 the left one's, each with its
 * own weapon's PlayerDamageParam row (slot % 5) for the same slot; every event with a filled slot charges the cost.</li>
 * </ul>
 */
public final class PowerStance {
	/** One TAE 2200 event of a power stance attack: window, slot (0..9) and the PDP index (slot % 5). */
	public record Event(float start, float end, int slot) {
		public boolean leftWeapon() {
			return slot >= 5;
		}
	}

	/** A weapon's power stance data. */
	public record Data(int category, int stanceRight, int stanceLeft, Attack light, Attack lightCombo, Attack heavy,
			JsonObject slots) {
		public Attack attack(Moveset.Type type) {
			return switch (type) {
				case POWER_LIGHT -> light;
				case POWER_LIGHT_COMBO -> lightCombo;
				case POWER_HEAVY -> heavy;
				default -> null;
			};
		}
	}

	private static Map<String, Data> weapons;
	private static Map<Integer, Map<Integer, Boolean>> dual;
	private static final Map<Attack, List<Event>> EVENTS = new IdentityHashMap<>();
	public static final float STAT_FACTOR = 1.5f;

	private PowerStance() {
	}

	public static synchronized Data data(String weapon) {
		Data p = weapon == null ? null : pack.get(weapon);
		if (p != null) {
			return p;
		}
		load();
		return weapons.get(weapon);
	}

	/** Datapack weapons' power stance data by full id (SoulsContent), replaced on reload. */
	private static volatile Map<String, Data> pack = Map.of();

	public static synchronized void setPack(Map<String, Data> weapons) {
		pack = Map.copyOf(weapons);
	}

	/** Whether the pair can be power-stanced (DualWieldingPermissionParam); false when either is unknown. */
	public static synchronized boolean pairAllowed(String right, String left) {
		Data r = data(right);
		Data l = data(left);
		if (r == null || l == null) {
			return false;
		}
		Map<Integer, Boolean> row = dual.get(r.category());
		return row != null && row.getOrDefault(l.category(), false);
	}

	/** State 6 (the right weapon gives the left buttons' moves) when the left category is not above the right one. */
	public static boolean rightGivesMoves(String right, String left) {
		return data(left).category() <= data(right).category();
	}

	/** FUN_140350170: the Strength and Dexterity the pair needs. */
	public static int[] requirements(String right, String left) {
		Ds2Damage.WeaponData r = Ds2Damage.weapon(right);
		Ds2Damage.WeaponData l = Ds2Damage.weapon(left);
		if (r == null || l == null) {
			return new int[] {99, 99};
		}
		return new int[] {Math.min(99, (int) (Math.max(r.requirements()[0], l.requirements()[0]) * STAT_FACTOR)),
				Math.min(99, (int) (Math.max(r.requirements()[1], l.requirements()[1]) * STAT_FACTOR))};
	}

	public static synchronized List<Event> events(Attack attack) {
		load();
		return EVENTS.getOrDefault(attack, List.of());
	}

	/**
	 * A weapon entry's power stance ({@code powerStanceCategory}, {@code powerStanceStance}, {@code power_stance}), or
	 * null when it has none; built-in weapons and datapack weapons alike.
	 */
	public static synchronized Data parse(JsonObject w, JsonObject animations) {
		if (!w.has("powerStanceCategory") || !w.has("power_stance")) {
			return null;
		}
		JsonObject hand = w.getAsJsonObject("power_stance");
		JsonObject stance = w.getAsJsonObject("powerStanceStance");
		Attack[] attacks = new Attack[3];
		String[] slots = {"light", "light_combo", "heavy"};
		for (int i = 0; i < 3; i++) {
			JsonElement s = hand.get(slots[i]);
			if (s == null || s.isJsonNull()) {
				continue;
			}
			String tae = s.getAsJsonObject().get("taeId").getAsString();
			JsonElement a = animations.get(tae);
			if (a == null || a.isJsonNull()) {
				continue;
			}
			attacks[i] = Weapons.attack(hand, slots[i], animations);
			List<Event> events = new ArrayList<>();
			JsonObject slot = s.getAsJsonObject();
			for (JsonElement h : a.getAsJsonObject().getAsJsonArray("hits")) {
				var arr = h.getAsJsonArray();
				int index = arr.get(2).getAsInt();
				// Only events whose PDP slot is filled hit (and charge).
				var rates = slot.getAsJsonArray("damageRates");
				JsonElement rate = index % 5 < rates.size() ? rates.get(index % 5) : null;
				if (rate != null && !rate.isJsonNull()) {
					events.add(new Event(arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), index));
				}
			}
			EVENTS.put(attacks[i], List.copyOf(events));
		}
		return new Data(w.get("powerStanceCategory").getAsInt(), stance.get("right").getAsInt(),
				stance.get("left").getAsInt(), attacks[0], attacks[1], attacks[2], hand);
	}

	/** The damage rate and floor of slot {@code type}'s PDP index {@code pdp} of a weapon (its own row). */
	public static synchronized float[] rate(String weapon, Moveset.Type type, int pdp) {
		Data d = data(weapon);
		String key = switch (type) {
			case POWER_LIGHT -> "light";
			case POWER_LIGHT_COMBO -> "light_combo";
			default -> "heavy";
		};
		if (d == null || !d.slots().has(key) || d.slots().get(key).isJsonNull()) {
			return null;
		}
		JsonObject s = d.slots().getAsJsonObject(key);
		if (pdp >= s.getAsJsonArray("damageRates").size()) {
			return null;
		}
		JsonElement rate = s.getAsJsonArray("damageRates").get(pdp);
		JsonElement floor = s.getAsJsonArray("damageFloors").get(pdp);
		JsonElement speed = s.has("reactionSpeeds") ? s.getAsJsonArray("reactionSpeeds").get(pdp) : null;
		if (rate.isJsonNull()) {
			return null;
		}
		// {rate, floor, speed, then the hit's reaction motion (Weapons.withMotion, Attack.reactionMotion)}.
		float[] hit = Weapons.withMotion(new float[] {0, 0, 0, 0, 0, Float.NaN}, s.getAsJsonArray("reactionMotions"), pdp);
		float[] out = new float[3 + Attack.MOTION_FIELDS];
		out[0] = rate.getAsFloat();
		out[1] = floor.isJsonNull() ? 0 : floor.getAsFloat();
		out[2] = speed == null || speed.isJsonNull() ? Float.NaN : speed.getAsFloat();
		System.arraycopy(hit, 6, out, 3, Attack.MOTION_FIELDS);
		return out;
	}

	/** PlayerDamageParam +0x20 of slot {@code type}'s PDP index {@code pdp} of a weapon, NaN when unknown. */
	public static synchronized float counterRate(String weapon, Moveset.Type type, int pdp) {
		Data d = data(weapon);
		String key = switch (type) {
			case POWER_LIGHT -> "light";
			case POWER_LIGHT_COMBO -> "light_combo";
			default -> "heavy";
		};
		if (d == null || !d.slots().has(key) || d.slots().get(key).isJsonNull()) {
			return Float.NaN;
		}
		JsonObject s = d.slots().getAsJsonObject(key);
		if (!s.has("counterRates") || pdp >= s.getAsJsonArray("counterRates").size()) {
			return Float.NaN;
		}
		JsonElement r = s.getAsJsonArray("counterRates").get(pdp);
		return r.isJsonNull() ? Float.NaN : r.getAsFloat();
	}

	/** Stamina for the time going from {@code from} (exclusive) to {@code to}: the cost per event with a filled slot. */
	public static float staminaDue(Attack attack, float from, float to) {
		float due = 0;
		for (Event e : events(attack)) {
			if (e.start() > from && e.start() <= to) {
				due += attack.staminaCost();
			}
		}
		return due;
	}

	private static void load() {
		if (weapons != null) {
			return;
		}
		JsonObject raw = Weapons.raw();
		JsonObject animations = raw.getAsJsonObject("animations");
		weapons = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : raw.getAsJsonObject("weapons").entrySet()) {
			Data d = parse(e.getValue().getAsJsonObject(), animations);
			if (d != null) {
				weapons.put(e.getKey(), d);
			}
		}
		dual = new HashMap<>();
		for (Map.Entry<String, JsonElement> r : raw.getAsJsonObject("dualWielding").entrySet()) {
			Map<Integer, Boolean> row = new HashMap<>();
			for (Map.Entry<String, JsonElement> l : r.getValue().getAsJsonObject().entrySet()) {
				row.put(Integer.parseInt(l.getKey()), l.getValue().getAsBoolean());
			}
			dual.put(Integer.parseInt(r.getKey()), row);
		}
	}
}
