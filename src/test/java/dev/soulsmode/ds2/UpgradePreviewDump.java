package dev.soulsmode.ds2;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Not a check: writes build/upgrade_preview.json, the attack rating of every weapon the mod gives to a Minecraft item
 * at each reinforcement level (Ds2Damage, one-handed, no buffs), for a few stat sets. Used for the titanite preview.
 */
class UpgradePreviewDump {
	/** Item -> DS2 weapon (SoulsWeapons' table) and the material it is made of. */
	private static final String[][] ITEMS = {
			{"wooden_sword", "broken_straight_sword", "wood"}, {"stone_sword", "puzzling_stone_sword", "stone"},
			{"iron_sword", "longsword", "iron"}, {"golden_sword", "shortsword", "gold"},
			{"diamond_sword", "drakekeepers_sword", "diamond"}, {"netherite_sword", "red_rust_sword", "netherite"},
			{"wooden_axe", "hand_axe", "wood"}, {"stone_axe", "infantry_axe", "stone"}, {"iron_axe", "battle_axe", "iron"},
			{"golden_axe", "bandit_axe", "gold"}, {"diamond_axe", "dragonslayers_crescent_axe", "diamond"},
			{"netherite_axe", "gyrm_axe", "netherite"},
			{"wooden_pickaxe", "pickaxe", "wood"}, {"stone_pickaxe", "pickaxe", "stone"}, {"iron_pickaxe", "pickaxe", "iron"},
			{"golden_pickaxe", "pickaxe", "gold"}, {"diamond_pickaxe", "pickaxe", "diamond"},
			{"netherite_pickaxe", "pickaxe", "netherite"},
			{"mace", "mace", "-"}, {"stick", "club", "-"}, {"trident", "spear", "-"}, {"bow", "short_bow", "-"},
			{"crossbow", "light_crossbow", "-"},
	};

	private static Attributes stats(int value) {
		short[] s = new short[Attributes.SLOT_COUNT];
		Arrays.fill(s, (short) value);
		return new Attributes(s);
	}

	@Test
	void dump() throws IOException {
		Map<String, Attributes> presets = new LinkedHashMap<>();
		presets.put("10", stats(10));
		presets.put("20", stats(20));
		presets.put("40", stats(40));
		JsonArray items = new JsonArray();
		for (String[] item : ITEMS) {
			Ds2Damage.WeaponData w = Ds2Damage.weapon(item[1]);
			JsonObject o = new JsonObject();
			o.addProperty("item", item[0]);
			o.addProperty("weapon", item[1]);
			o.addProperty("material", item[2]);
			o.addProperty("maxLevel", w == null ? 0 : w.maxLevel());
			JsonObject byStats = new JsonObject();
			for (var p : presets.entrySet()) {
				JsonArray levels = new JsonArray();
				for (int level = 0; w != null && level <= w.maxLevel(); level++) {
					float[] ar = Ds2Damage.attackRating(item[1], level, p.getValue(), false, false, -1);
					JsonArray parts = new JsonArray();
					for (float v : ar) {
						parts.add(Math.round(v));
					}
					levels.add(parts);
				}
				byStats.add(p.getKey(), levels);
			}
			o.add("attack", byStats);
			// Minecraft damage of the first light attack's first hit on a mob without DS2 defence (Ds2Damage.damageToEnemy
			// / 40 HP per health point, as SoulsAttack does), per level and stat set.
			Moveset moveset = Weapons.load().get(item[1]);
			Attack light = moveset == null ? null : moveset.attack(false, false, Moveset.Type.LIGHT);
			JsonObject dmg = new JsonObject();
			for (var p : presets.entrySet()) {
				JsonArray levels = new JsonArray();
				for (int level = 0; w != null && level <= w.maxLevel(); level++) {
					float[] ar = Ds2Damage.attackRating(item[1], level, p.getValue(), false, false, -1);
					float rate = light == null || light.hits().isEmpty() ? 1 : light.damageRate(0);
					int floor = light == null || light.hits().isEmpty() ? 0 : light.damageFloor(0);
					levels.add(Math.round(Ds2Damage.damageToEnemy(item[1], ar, rate, floor) / 40.0 * 100) / 100.0);
				}
				dmg.add(p.getKey(), levels);
			}
			o.add("mcDamage", dmg);
			o.addProperty("lightRate", light == null || light.hits().isEmpty() ? 1 : light.damageRate(0));
			o.addProperty("lightFloor", light == null || light.hits().isEmpty() ? 0 : light.damageFloor(0));
			items.add(o);
		}
		try (FileWriter f = new FileWriter("build/upgrade_preview.json")) {
			new GsonBuilder().setPrettyPrinting().create().toJson(items, f);
		}
	}
}
