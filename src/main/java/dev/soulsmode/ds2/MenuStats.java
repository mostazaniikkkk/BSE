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
import java.util.HashMap;
import java.util.Map;

/**
 * DS2's level-up screen values (docs/research/stats.md "Pantalla de subir nivel"; FUN_1400cc620 with the display struct
 * of 0x14038c1f0): MenuStatsParam columns by attribute index (weapons.json menuStats, rows 1..99; an index outside 1..99
 * uses row 1). Columns 0 and 1 of the screen only depend on the attributes; column 2 (weapons, physical defence with
 * armour, poise with armour) is computed by the client with the equipment.
 */
public final class MenuStats {
	private static final String RESOURCE = "/data/soulsmode/ds2/weapons.json";
	private static Map<String, float[]> columns;

	private MenuStats() {
	}

	private static synchronized Map<String, float[]> columns() {
		if (columns == null) {
			try (InputStream in = MenuStats.class.getResourceAsStream(RESOURCE)) {
				JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
						.getAsJsonObject("menuStats");
				Map<String, float[]> out = new HashMap<>();
				for (Map.Entry<String, JsonElement> e : json.entrySet()) {
					JsonArray a = e.getValue().getAsJsonArray();
					float[] v = new float[a.size()];
					for (int i = 0; i < v.length; i++) {
						v[i] = a.get(i).getAsFloat();
					}
					out.put(e.getKey(), v);
				}
				columns = out;
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return columns;
	}

	/** MenuStatsParam[index] column; rows 1..99, row 1 otherwise. */
	public static float value(String column, int index) {
		float[] v = columns().get(column);
		return v[index >= 1 && index <= 99 ? index - 1 : 0];
	}

	/** One cell of the grid: its common.fmg label and its value for these attributes; NaN = equipment (column 2). */
	public record Cell(int label, boolean decimal) {
	}

	private static int s(Attributes a, int slot) {
		return a.get(slot);
	}

	/** Column 0, rows 0..12 (FUN_1400cc620; struct +0x00..+0x20). */
	public static final Cell[] COLUMN0 = {new Cell(909014, false), new Cell(909015, false), new Cell(909016, true),
			new Cell(909017, false), new Cell(909018, false), new Cell(909019, false), new Cell(909020, false),
			new Cell(909021, false), new Cell(909022, false), new Cell(909023, false), new Cell(909024, false),
			new Cell(909025, false), new Cell(909026, false)};
	/**
	 * Column 1, rows 0..12 (struct +0x22..+0x40). Rows 0 and 8 have no label (+0x22 MSP[STR] +0x1a, +0x32 MSP[ADP] +0x2a):
	 * label 0, not drawn (whether DS2 shows them is not confirmed).
	 */
	public static final Cell[] COLUMN1 = {new Cell(0, false), new Cell(909028, false), new Cell(909029, false),
			new Cell(909030, false), new Cell(909031, false), new Cell(909032, false), new Cell(909033, false),
			new Cell(909034, false), new Cell(0, false), new Cell(909036, false), new Cell(909051, false),
			new Cell(909038, false), new Cell(909049, true)};

	/** Column 0 value of {@code row} for {@code a}; {@code physical} gives HP (PSPL) and the equip load's factor. */
	public static float column0(int row, Attributes a, PhysicalStatsTable physical) {
		int intel = s(a, Attributes.INTELLIGENCE);
		int faith = s(a, Attributes.FAITH);
		int dex = s(a, Attributes.DEXTERITY);
		return switch (row) {
			case 0 -> DerivedStats.compute(a, physical).maxHp();
			case 1 -> value("stamina", s(a, Attributes.ENDURANCE));
			case 2 -> value("equipLoad", s(a, Attributes.VITALITY));
			case 3 -> Math.max(0, Math.min(14, value("slots", s(a, Attributes.ATTUNEMENT))));
			// The adjustment of FUN_14014d130 is not decoded: the base value.
			case 4 -> value("castSpeed", (2 * s(a, Attributes.ATTUNEMENT) + intel + faith) / 4);
			case 5 -> value("atkStr", s(a, Attributes.STRENGTH));
			case 6 -> value("atkDex", dex);
			case 7 -> value("bonusMagic", intel);
			case 8 -> value("bonusFire", (intel + faith) / 2);
			case 9 -> value("bonusLightning", faith);
			case 10 -> value("bonusDark", Math.min(intel, faith));
			case 11 -> value("bonusPoison", (3 * dex + s(a, Attributes.ADAPTABILITY)) / 4);
			case 12 -> value("bonusBleed", (3 * dex + faith) / 4);
			default -> Float.NaN;
		};
	}

	/** Column 1 value of {@code row}; row 12 (poise) is {@code statPoise}. */
	public static float column1(int row, Attributes a, float statPoise) {
		int intel = s(a, Attributes.INTELLIGENCE);
		int faith = s(a, Attributes.FAITH);
		int adp = s(a, Attributes.ADAPTABILITY);
		return switch (row) {
			case 1 -> physicalDefence(a);
			case 2 -> value("defMagic", intel);
			case 3 -> value("defFire", (intel + faith) / 2);
			case 4 -> value("defLightning", faith);
			case 5 -> value("defDark", Math.min(intel, faith));
			case 6 -> value("resPoison", (3 * adp + s(a, Attributes.VITALITY)) / 4);
			case 7 -> value("resBleed", (3 * adp + faith) / 4);
			case 9 -> value("resPetrify", (3 * adp + s(a, Attributes.VIGOR)) / 4);
			case 10 -> value("resCurse", (3 * adp + s(a, Attributes.ATTUNEMENT)) / 4);
			case 11 -> value("agility", (3 * adp + s(a, Attributes.ATTUNEMENT)) / 4);
			case 12 -> statPoise;
			default -> Float.NaN;
		};
	}

	/** Phys DEF of the stats (struct +0x24): MSP[(END + VIT + STR + DEX) / 4] +0x1c. */
	public static float physicalDefence(Attributes a) {
		return value("defPhysical", (s(a, Attributes.ENDURANCE) + s(a, Attributes.VITALITY) + s(a, Attributes.STRENGTH)
				+ s(a, Attributes.DEXTERITY)) / 4);
	}
}
