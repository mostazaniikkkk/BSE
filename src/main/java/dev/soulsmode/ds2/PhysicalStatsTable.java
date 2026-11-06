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

/**
 * PhysicalStatsPerLevelStatValuesParam (one row per attribute value 1..99) plus the single row of
 * RelatePhysicalStatToLevelStatParam. See docs/research/stats.md for the field evidence.
 */
public final class PhysicalStatsTable {
	public static final String RESOURCE = "/data/soulsmode/ds2/physical_stats_per_level.json";

	public record Row(int hp, int hpBonus, int stamina, float equipLoad, int attunementSlots, int spellUsesLevel,
			float castTime) {
	}

	private final Row[] rows;
	private final int[] relate;

	public PhysicalStatsTable(Row[] rows, int[] relate) {
		this.rows = rows.clone();
		this.relate = relate.clone();
	}

	public static PhysicalStatsTable load() {
		try (InputStream in = PhysicalStatsTable.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("missing " + RESOURCE);
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonArray jsonRows = json.getAsJsonArray("rows");
			Row[] rows = new Row[jsonRows.size()];
			for (int i = 0; i < rows.length; i++) {
				JsonObject r = jsonRows.get(i).getAsJsonObject();
				if (r.get("level").getAsInt() != i + 1) {
					throw new IllegalStateException("rows must be levels 1..99 in order");
				}
				rows[i] = new Row(r.get("hp").getAsInt(), r.get("hpBonus").getAsInt(), r.get("stamina").getAsInt(),
						r.get("equipLoad").getAsFloat(), r.get("attunementSlots").getAsInt(),
						r.get("spellUsesLevel").getAsInt(), r.get("castTime").getAsFloat());
			}
			JsonArray jsonRelate = json.getAsJsonArray("relate");
			int[] relate = new int[jsonRelate.size()];
			int i = 0;
			for (JsonElement e : jsonRelate) {
				relate[i++] = e.getAsInt();
			}
			return new PhysicalStatsTable(rows, relate);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Row lookup as in FUN_14038d790: values outside 1..99 fall back to row 1. */
	public Row row(int statValue) {
		if (statValue < 1 || statValue > 99) {
			statValue = 1;
		}
		return rows[statValue - 1];
	}

	/** Relate byte {@code index} resolved through FUN_14038e280: codes 1..11 pick slot code-1, anything else is 0. */
	public int relatedValue(int index, Attributes attributes) {
		int code = relate[index];
		return code >= 1 && code <= Attributes.SLOT_COUNT ? attributes.get(code - 1) : 0;
	}
}
