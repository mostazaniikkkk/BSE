package dev.soulsmode.ds2;

/**
 * ChrEquipLoadParam curve lookup, ported from FUN_140320230 / FUN_1403203e0: the equip load ratio
 * (equipped weight / max load) is scaled to percent and interpolated between five thresholds.
 */
public final class EquipLoadCurve {
	private EquipLoadCurve() {
	}

	/**
	 * @param loadRatio  chr.b0+0x30, written by FUN_14037e430 as weight / (multiplier x max load)
	 * @param thresholds row 1 of the param (load percentages)
	 * @param values     the row for the queried effect
	 */
	public static float value(float loadRatio, float[] thresholds, float[] values) {
		float percent = loadRatio * 100f;
		if (percent <= thresholds[0]) {
			return values[0];
		}
		for (int i = 1; i < thresholds.length; i++) {
			if (percent <= thresholds[i]) {
				return lerp(percent, thresholds[i - 1], thresholds[i], values[i - 1], values[i]);
			}
		}
		return values[values.length - 1];
	}

	/** FUN_1403203e0. */
	private static float lerp(float x, float x0, float x1, float y0, float y1) {
		float t = x <= x0 ? 0f : (x < x1 ? (x - x0) / (x1 - x0) : 1f);
		return (y1 - y0) * t + y0;
	}

	/** FUN_14031e640 over FUN_140320130: the multiplier applied to regen and drains is 1 - value / 100. */
	public static float multiplier(float loadRatio, float[] thresholds, float[] values) {
		return 1f - value(loadRatio, thresholds, values) * 0.01f;
	}
}
