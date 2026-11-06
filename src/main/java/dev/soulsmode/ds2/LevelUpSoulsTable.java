package dev.soulsmode.ds2;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * PlayerLevelUpSoulsParam and its lookup, ported from FUN_14038d140 (see docs/research/stats.md).
 * Rows are addressed by index, exactly like the game's accessor FUN_140358b90.
 */
public final class LevelUpSoulsTable {
	public static final String RESOURCE = "/data/soulsmode/ds2/player_level_up_souls.json";

	public record Row(int level, int slope, int base) {
	}

	private final List<Row> rows;

	/**
	 * The user's exception (2026-10-31): levelling up spends Minecraft experience levels instead of DS2's
	 * PlayerLevelUpSoulsParam souls (500 souls are not 500 experience points); going up to soul level N costs N
	 * experience levels (from 12 to 13: 13). One row, slope 1, base 1: cost(level) = level + 1. See SoulsPlayer.
	 */
	public static final LevelUpSoulsTable EXPERIENCE_LEVELS = new LevelUpSoulsTable(List.of(new Row(0, 1, 1)));

	public LevelUpSoulsTable(List<Row> rows) {
		this.rows = List.copyOf(rows);
	}

	public static LevelUpSoulsTable load() {
		try (InputStream in = LevelUpSoulsTable.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("missing " + RESOURCE);
			}
			JsonArray json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonArray("rows");
			List<Row> rows = new ArrayList<>(json.size());
			for (JsonElement e : json) {
				JsonArray r = e.getAsJsonArray();
				rows.add(new Row(r.get(0).getAsInt(), r.get(1).getAsInt(), r.get(2).getAsInt()));
			}
			return new LevelUpSoulsTable(rows);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private Row rowAt(long index) {
		return index >= 0 && index < rows.size() ? rows.get((int) index) : null;
	}

	/**
	 * Souls needed to level up while at {@code level}. FUN_1401fb970 charges cost(current level)
	 * and then increments the level.
	 */
	public int cost(int level) {
		long index = Integer.toUnsignedLong(level);
		Row row = rowAt(index);
		if (row != null && row.level() == level) {
			return row.base();
		}
		// Halve the index until we land on a row at or below the target level.
		while (row == null || level < row.level()) {
			index >>= 1;
			row = rowAt(index);
		}
		// Walk forward to the last row whose level is below the target.
		Row previous = row;
		Row current = row;
		while (current != null) {
			if (level <= current.level()) {
				break;
			}
			previous = current;
			index++;
			current = rowAt(index);
		}
		return (level - previous.level()) * previous.slope() + previous.base();
	}
}
