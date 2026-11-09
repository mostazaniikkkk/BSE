package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

/** Not a check: build/current_damage.txt, today's damage of each attack (material level, SoulsAttack's formula). */
class CurrentDamageDump {
	private static final Object[][] ITEMS = {{"stick", "club", 0}, {"wooden_sword", "broken_straight_sword", 0},
			{"stone_sword", "puzzling_stone_sword", 1}, {"iron_sword", "longsword", 3}, {"diamond_sword", "drakekeepers_sword", 6},
			{"netherite_sword", "red_rust_sword", 10}, {"iron_axe", "battle_axe", 3}, {"netherite_axe", "gyrm_axe", 10},
			{"iron_pickaxe", "pickaxe", 3}, {"mace", "mace", 0}};
	private static final Moveset.Type[] TYPES = {Moveset.Type.LIGHT, Moveset.Type.LIGHT_COMBO, Moveset.Type.HEAVY,
			Moveset.Type.HEAVY_COMBO, Moveset.Type.DASH, Moveset.Type.ROLLING, Moveset.Type.BACKSTAB};

	@Test
	void dump() throws IOException {
		StringBuilder out = new StringBuilder();
		for (int statValue : new int[] {10, 20, 40}) {
			short[] s = new short[Attributes.SLOT_COUNT];
			Arrays.fill(s, (short) statValue);
			Attributes stats = new Attributes(s);
			for (Object[] item : ITEMS) {
				String weapon = (String) item[1];
				Moveset moveset = Weapons.load().get(weapon);
				for (boolean two : new boolean[] {false, true}) {
					float[] ar = Ds2Damage.attackRating(weapon, (int) item[2], stats, two, false, -1);
					out.append(statValue).append('\t').append(item[0]).append('\t').append(two ? "2H" : "1H");
					for (Moveset.Type type : TYPES) {
						Attack a = moveset == null ? null : moveset.attack(false, two, type);
						if (a == null || a.hits().isEmpty()) {
							out.append("\t").append(type).append("=-");
							continue;
						}
						StringBuilder hits = new StringBuilder();
						for (int i = 0; i < a.hits().size(); i++) {
							float d = Ds2Damage.damageToEnemy(weapon, ar, a.damageRate(i), a.damageFloor(i)) / 40f;
							hits.append(i == 0 ? "" : "+").append(String.format("%.2f", d));
						}
						out.append("\t").append(type).append('=').append(hits).append(String.format("@%.2fs", a.duration()));
					}
					out.append('\n');
				}
			}
		}
		try (FileWriter f = new FileWriter("build/current_damage.txt")) {
			f.write(out.toString());
		}
	}
}
