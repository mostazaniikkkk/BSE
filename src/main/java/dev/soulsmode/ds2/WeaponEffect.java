package dev.soulsmode.ds2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A weapon's own SpEffect (docs/research/weapons.md "Efecto del arma"; DarkTwo docs/espadones.md section 12): the
 * SpEffect of ItemParam +0x04 of the weapon's ItemParam row (FUN_1401a6890), applied while the weapon is the active
 * one of a hand (FUN_140349d30 -> FUN_14034b6b0 -> FUN_14022ed30 mode 2, slot 0xfc left / 0xfd right, no duration).
 * Weapon entry field {@code spEffect}: {@code {"instructions": [...]}}. Instructions:
 * <ul>
 * <li>{@code atkRateBySoul} (1000[14], FUN_14021f570): a rate from the souls held, {@code low} / {@code high} (i32
 * +0x04 / +0x08) and {@code rateLow} / {@code rateHigh} (f32 +0x0c / +0x10): rateLow below low, linear to rateHigh
 * at high, rateHigh above. Selector (byte +0x00) 0 = the souls held, the only one seen.</li>
 * <li>{@code atkRateByHollowCount} (1000[15], FUN_14021f470): the same curve over a counter, {@code low} / {@code high}
 * (u8 +0x01 / +0x02), {@code rateLow} / {@code rateHigh} (f32 +0x04 / +0x08); {@code counter} (byte +0x00) 1 =
 * hollowing (status +0x1ac, vt+0x18), 2 = sin (byte +0x1d7, vt+0x20).</li>
 * <li>{@code changeWeaponParam} (100110[04]): in the left hand the weapon uses another weapon row ({@code weapon}, a
 * weapon key or datapack id) for WeaponParam, WRP, the action category and WTP (FUN_14034a390).</li>
 * </ul>
 * The two rates are attack actions (code 8): they apply only to the hits of the hand of their slot (FUN_14021f230,
 * mask 5 + (hand != 0)) and multiply S only (FUN_140391020).
 */
public record WeaponEffect(List<Rate> rates, String leftWeapon) {
	/** One rate curve: {@code source} 0 souls held, 1 hollowing, 2 sin. */
	public record Rate(int source, float low, float high, float rateLow, float rateHigh) {
		/** FUN_14021f570 / FUN_14021f470. */
		public float at(float value) {
			if (high == low || rateHigh == rateLow) {
				return rateLow;
			}
			if (value < low) {
				return rateLow;
			}
			if (value <= high) {
				return rateLow + (value - low) / (high - low) * (rateHigh - rateLow);
			}
			return rateHigh;
		}
	}

	public static final int SOULS = 0;
	public static final int HOLLOWING = 1;
	public static final int SIN = 2;

	private static volatile Map<String, WeaponEffect> pack = Map.of();

	/** A weapon entry's {@code spEffect}, or null without one. */
	public static WeaponEffect parse(JsonObject weapon) {
		if (!weapon.has("spEffect") || !weapon.get("spEffect").isJsonObject()) {
			return null;
		}
		List<Rate> rates = new ArrayList<>();
		String left = null;
		for (JsonElement e : weapon.getAsJsonObject("spEffect").getAsJsonArray("instructions")) {
			JsonObject i = e.getAsJsonObject();
			String type = i.get("type").getAsString();
			switch (type) {
				case "atkRateBySoul" -> rates.add(new Rate(SOULS, i.get("low").getAsFloat(), i.get("high").getAsFloat(),
						i.get("rateLow").getAsFloat(), i.get("rateHigh").getAsFloat()));
				case "atkRateByHollowCount" -> {
					String counter = i.get("counter").getAsString();
					int source = switch (counter) {
						case "hollow" -> HOLLOWING;
						case "sin" -> SIN;
						default -> throw new IllegalArgumentException("atkRateByHollowCount: unknown counter " + counter);
					};
					rates.add(new Rate(source, i.get("low").getAsFloat(), i.get("high").getAsFloat(),
							i.get("rateLow").getAsFloat(), i.get("rateHigh").getAsFloat()));
				}
				case "changeWeaponParam" -> left = i.get("weapon").getAsString();
				default -> throw new IllegalArgumentException("weapon spEffect: unknown instruction " + type);
			}
		}
		return new WeaponEffect(List.copyOf(rates), left);
	}

	public static void setPack(Map<String, WeaponEffect> effects) {
		pack = Map.copyOf(effects);
	}

	private static Map<String, WeaponEffect> builtIn;

	/** A weapon's own SpEffect (datapack id or built-in key), or null. */
	public static WeaponEffect of(String key) {
		if (key == null) {
			return null;
		}
		WeaponEffect p = pack.get(key);
		if (p != null) {
			return p;
		}
		synchronized (WeaponEffect.class) {
			if (builtIn == null) {
				Map<String, WeaponEffect> m = new java.util.HashMap<>();
				for (Map.Entry<String, JsonElement> w : Weapons.raw().getAsJsonObject("weapons").entrySet()) {
					WeaponEffect e = parse(w.getValue().getAsJsonObject());
					if (e != null) {
						m.put(w.getKey(), e);
					}
				}
				builtIn = m;
			}
			return builtIn.get(key);
		}
	}
}
