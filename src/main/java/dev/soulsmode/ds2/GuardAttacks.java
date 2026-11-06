package dev.soulsmode.ds2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Attacking without lowering the guard (docs/research/weapon_arts.md s.2, s.8): with both weapons one-handed and one
 * hand guarding, the other hand's light button gives action 0x12 (slot 57) and, inside its combo window, 0x13 (58);
 * the guard (b8+0x14) stays, because FUN_14038fd30 accepts the guard request during motion types 4 / 5. Only the
 * spear (motion type 5) and the rapiers (4) have these slots; the heavy ones (59 / 60) are empty for every weapon.
 * The client lays the guard clip over the attack (weapons.json {@code guardLayer}, SM_MoveGuard).
 */
public final class GuardAttacks {
	/** A weapon's guard attacks, null when it has none. */
	public record Data(Attack light, Attack lightCombo) {
		public Attack attack(Moveset.Type type) {
			return type == Moveset.Type.GUARD_ATTACK_COMBO ? lightCombo : type == Moveset.Type.GUARD_ATTACK ? light : null;
		}
	}

	private static Map<String, Data> weapons;

	private GuardAttacks() {
	}

	public static synchronized Data data(String weapon) {
		if (weapons == null) {
			weapons = new HashMap<>();
			JsonObject raw = Weapons.raw();
			JsonObject animations = raw.getAsJsonObject("animations");
			for (Map.Entry<String, JsonElement> e : raw.getAsJsonObject("weapons").entrySet()) {
				JsonObject w = e.getValue().getAsJsonObject();
				if (!w.has("guard_attack")) {
					continue;
				}
				JsonObject hand = w.getAsJsonObject("guard_attack");
				Attack light = slot(hand, "light", animations);
				if (light != null) {
					weapons.put(e.getKey(), new Data(light, slot(hand, "light_combo", animations)));
				}
			}
		}
		return weapons.get(weapon);
	}

	private static Attack slot(JsonObject hand, String key, JsonObject animations) {
		JsonElement s = hand.get(key);
		if (s == null || s.isJsonNull()) {
			return null;
		}
		JsonElement a = animations.get(s.getAsJsonObject().get("taeId").getAsString());
		return a == null || a.isJsonNull() ? null : Weapons.attack(hand, key, animations);
	}
}
