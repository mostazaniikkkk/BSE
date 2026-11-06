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
 * DS2's Agility (docs/research/agility.md, agility.json): the index {@code (Attunement + 3 Adaptability) / 4}
 * (FUN_14038d790) picks a row of PhysicalStatsPerLevelStatValuesParam columns +0x68..+0x78; the menu shows
 * MenuStatsParam[index] +0x32 instead, which no formula uses.
 */
public final class Agility {
	public static final String RESOURCE = "/data/soulsmode/ds2/agility.json";

	/** One index: the menu value and the derived columns used by the player. */
	public record Row(int shown, float stepIframeFraction, float rollIframeFraction, float weaponChangeSpeed) {
	}

	/** TAE 110100 / 110300 windows of a dodge clip (seconds on the clip). */
	public record Windows(float[][] full, float[][] partial) {
	}

	private static Row[] rows;
	private static float[] dodgeLackRow;
	private static Map<Integer, Windows> tae;

	private Agility() {
	}

	private static synchronized void load() {
		if (rows != null) {
			return;
		}
		try (InputStream in = Agility.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("missing " + RESOURCE);
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonArray jsonRows = json.getAsJsonArray("rows");
			Row[] loaded = new Row[jsonRows.size()];
			for (int i = 0; i < loaded.length; i++) {
				JsonObject r = jsonRows.get(i).getAsJsonObject();
				if (r.get("index").getAsInt() != i + 1) {
					throw new IllegalStateException("agility rows must be indices 1..99 in order");
				}
				loaded[i] = new Row(r.get("shown").getAsInt(), r.get("stepIframeFraction").getAsFloat(),
						r.get("rollIframeFraction").getAsFloat(), r.get("weaponChangeSpeed").getAsFloat());
			}
			JsonArray lack = json.getAsJsonArray("dodgeLackRow");
			float[] lackRow = new float[lack.size()];
			for (int i = 0; i < lackRow.length; i++) {
				lackRow[i] = lack.get(i).getAsFloat();
			}
			Map<Integer, Windows> windows = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("tae").entrySet()) {
				JsonObject w = e.getValue().getAsJsonObject();
				windows.put(Integer.parseInt(e.getKey()), new Windows(spans(w.getAsJsonArray("110100")),
						spans(w.getAsJsonArray("110300"))));
			}
			dodgeLackRow = lackRow;
			tae = windows;
			rows = loaded;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static float[][] spans(JsonArray array) {
		float[][] out = new float[array.size()][];
		for (int i = 0; i < out.length; i++) {
			JsonArray s = array.get(i).getAsJsonArray();
			out[i] = new float[] {s.get(0).getAsFloat(), s.get(1).getAsFloat()};
		}
		return out;
	}

	/** FUN_14038d790: (Attunement + 3 Adaptability) / 4, clamped to the table's 1..99. */
	public static int index(Attributes stats) {
		int i = (stats.get(Attributes.ATTUNEMENT) + 3 * stats.get(Attributes.ADAPTABILITY)) / 4;
		return Math.max(1, Math.min(99, i));
	}

	public static Row row(Attributes stats) {
		load();
		return rows[index(stats) - 1];
	}

	/** The Agility the menu shows (MenuStatsParam +0x32). */
	public static int shown(Attributes stats) {
		return row(stats).shown();
	}

	/** WeaponChangeSpeed (FUN_14035dfe0, column +0x74): playback speed of the a00_08_00x0 grip change. */
	public static float weaponChangeSpeed(Attributes stats) {
		return row(stats).weaponChangeSpeed();
	}

	/**
	 * chr.b8+0xa4 written at the dodge (FUN_14038e440 roll / FUN_14038e5d0 backstep -> FUN_140381d10): the column x
	 * (1 - pen), pen = clamp(0.01 [30 + 20 f if f > 0] + 0.01 [50 g if g > 0], 0, 1) from PlayerLackOfStatsParam row 18.
	 * {@code equipmentLack} is f (FUN_14034a830), {@code staminaLack} is g, (cost - stamina) / cost (FUN_140376510).
	 */
	public static float iframeFraction(boolean roll, Attributes stats, float equipmentLack, float staminaLack) {
		load();
		Row r = row(stats);
		float column = roll ? r.rollIframeFraction() : r.stepIframeFraction();
		float pen = 0;
		// FUN_1403811f0: each lack goes from its row's first value to its second (30 -> 50 for f, 0 -> 50 for g).
		if (equipmentLack > 0) {
			pen += 0.01f * (dodgeLackRow[0] + (dodgeLackRow[1] - dodgeLackRow[0]) * equipmentLack);
		}
		if (staminaLack > 0) {
			pen += 0.01f * (dodgeLackRow[2] + (dodgeLackRow[3] - dodgeLackRow[2]) * staminaLack);
		}
		pen = Math.max(0, Math.min(1, pen));
		return column * (1 - pen);
	}

	/** FUN_140376510: the share of the cost the stamina does not cover (0 with enough, 1 with none). */
	public static float staminaLack(float stamina, float cost) {
		if (cost <= 0 || stamina >= cost) {
			return 0;
		}
		return stamina <= 0 ? 1 : (cost - stamina) / cost;
	}

	/**
	 * Whether the clip is invulnerable at {@code t} (FUN_140326240): any 110100 window, or the first {@code fraction}
	 * of a 110300 window, [s, s + a (e - s)) with s = max(0, start).
	 */
	public static boolean invulnerable(int taeId, float t, float fraction) {
		load();
		Windows w = tae.get(taeId);
		if (w == null) {
			return false;
		}
		for (float[] s : w.full()) {
			if (t >= Math.max(0, s[0]) && t < s[1]) {
				return true;
			}
		}
		for (float[] s : w.partial()) {
			float start = Math.max(0, s[0]);
			if (t >= start && t < start + fraction * (s[1] - start)) {
				return true;
			}
		}
		return false;
	}
}
