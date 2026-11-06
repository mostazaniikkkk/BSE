package dev.soulsmode.ds2;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * ChrEquipLoadParam rows for the equip load's effects (docs/research/equipload.md), exported by tools/re/weapons.py:
 * thresholds (row 1), speed rows 3 walk / 4 jog / 5 dash (FUN_14031c990: speed x (1 - row x 0.01), x 0.3 above 120 %),
 * dodge distance rows 0x19 / 0x1b / 0x17 / 0x26, Tired dodge playback speed rows 0x1a / 0x25 (raw).
 */
public record EquipLoadRows(float[] thresholds, float[] walkSpeed, float[] jogSpeed, float[] dashSpeed,
		float[] rollDistance, float[] rollTiredDistance, float[] stepDistance, float[] stepTiredDistance,
		float[] rollTiredSpeed, float[] stepTiredSpeed, float[] turn, float[] lockedTurn, float[] dashTurn,
		float[] lockedDashTurn) {
	/** movectrl+0x168 (FUN_140385b40): speed factor while overloaded (> 120 %). */
	public static final float OVERLOAD_SPEED = 0.3f;
	public static final float OVERLOAD_PERCENT = 120f;

	public static EquipLoadRows load() {
		try (InputStream in = EquipLoadRows.class.getResourceAsStream(Weapons.RESOURCE)) {
			JsonObject r = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonObject("equipLoadRows");
			return new EquipLoadRows(row(r, "thresholds"), row(r, "walkSpeed"), row(r, "jogSpeed"), row(r, "dashSpeed"),
					row(r, "rollDistance"), row(r, "rollTiredDistance"), row(r, "stepDistance"), row(r, "stepTiredDistance"),
					row(r, "rollTiredSpeed"), row(r, "stepTiredSpeed"), row(r, "turn"), row(r, "lockedTurn"),
					row(r, "dashTurn"), row(r, "lockedDashTurn"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static float[] row(JsonObject r, String name) {
		JsonArray a = r.getAsJsonArray(name);
		float[] out = new float[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsFloat();
		}
		return out;
	}

	public static boolean overloaded(float loadRatio) {
		return loadRatio * 100f > OVERLOAD_PERCENT;
	}

	/**
	 * b8+0x138 (FUN_14031c970, read by FUN_140387f40): turn rate multiplier, built like the speed one: row 0x10
	 * (locked 0xf) walking / jogging, 0x11 (locked 0x12) dashing.
	 */
	public float turn(float loadRatio, boolean dashing, boolean locked) {
		float[] row = dashing ? (locked ? lockedDashTurn : dashTurn) : (locked ? lockedTurn : turn);
		return speed(loadRatio, row);
	}

	/** b8+0x11c: speed multiplier for a movement row. */
	public float speed(float loadRatio, float[] row) {
		return (1 - EquipLoadCurve.value(loadRatio, thresholds, row) * 0.01f) * (overloaded(loadRatio) ? OVERLOAD_SPEED : 1);
	}
}
