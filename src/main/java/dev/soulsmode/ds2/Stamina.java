package dev.soulsmode.ds2;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The player's stamina resource (chr+0x1ac current, +0x1b0 min, +0x1b4 max) and its controller
 * (chr+0xe0 -> +0xa8). Ported from FUN_1403335d0 (consume), FUN_1403337e0 (tick), FUN_140333900 (regen)
 * and FUN_14038be90 (max update). See docs/research/stamina.md.
 */
public final class Stamina {
	public static final String RESOURCE = "/data/soulsmode/ds2/player_stamina.json";

	/** Constants from the player's ChrParam / ChrCommonParam rows and the 0.1 s global at 0x14157f380. */
	public record Params(float regenPerSecond, float exhaustedCeiling, float exhaustedFloor, float drainBase,
			float exhaustTimer, float exhaustTimer2, float regenDelay, float actionCostBase, float jumpFactor,
			float dodgeFactor, float stepFactor, float chainedMultiplier, float attackMultiplier, float sprintRate,
			float lockedSprintRate, float sprintCooldown) {
		/**
		 * FUN_140376770 for a dodge: ChrCommonParam+0x24 x factor (+0x2c roll, +0x28 backstep) x k, where k is +0x38
		 * while SM_Dodge or SM_Jump is still active (chained), else +0x3c inside an attack's AttackDuration, else 1.
		 */
		public float dodgeCost(boolean roll, boolean chained, boolean inAttack) {
			float k = chained ? chainedMultiplier : inAttack ? attackMultiplier : 1;
			return actionCostBase * (roll ? dodgeFactor : stepFactor) * k;
		}

		/**
		 * ChrJumpCtrl cost (FUN_140330370 -> FUN_140376770): ChrCommonParam+0x24 x +0x30, with the special
		 * effect factor and the 1.25 multipliers of still-unidentified chr.c0 flags taken as 1.
		 */
		public float jumpCost() {
			return actionCostBase * jumpFactor;
		}

		public static Params load() {
			try (InputStream in = Stamina.class.getResourceAsStream(RESOURCE)) {
				if (in == null) {
					throw new IllegalStateException("missing " + RESOURCE);
				}
				JsonObject j = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				return new Params(j.get("regenPerSecond").getAsFloat(), j.get("exhaustedCeiling").getAsFloat(),
						j.get("exhaustedFloor").getAsFloat(), j.get("drainBase").getAsFloat(),
						j.get("exhaustTimer").getAsFloat(), j.get("exhaustTimer2").getAsFloat(),
						j.get("regenDelay").getAsFloat(), j.get("actionCostBase").getAsFloat(),
						j.get("jumpFactor").getAsFloat(), j.get("dodgeFactor").getAsFloat(),
						j.get("dodgeAltFactor").getAsFloat(), j.get("chainedMultiplier").getAsFloat(),
						j.get("flaggedMultiplier").getAsFloat(), j.get("drainRateA").getAsFloat(),
						j.get("drainRateA2").getAsFloat(), j.get("sprintCooldown").getAsFloat());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	/** ChrEquipLoadParam curves used by stamina (data/soulsmode/ds2/equip_load_curves.json). */
	public record LoadCurves(float[] thresholds, float[] regenPenalty, float[] drainA, float[] drainB, float[] drainC) {
		public static final String RESOURCE = "/data/soulsmode/ds2/equip_load_curves.json";

		public static LoadCurves load() {
			try (InputStream in = Stamina.class.getResourceAsStream(RESOURCE)) {
				if (in == null) {
					throw new IllegalStateException("missing " + RESOURCE);
				}
				JsonObject j = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				return new LoadCurves(floats(j, "thresholds"), floats(j, "regenPenalty"), floats(j, "drainA"),
						floats(j, "drainB"), floats(j, "drainC"));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		private static float[] floats(JsonObject j, String key) {
			var array = j.getAsJsonArray(key);
			float[] out = new float[array.size()];
			for (int i = 0; i < out.length; i++) {
				out[i] = array.get(i).getAsFloat();
			}
			return out;
		}
	}

	private static final float EPSILON = 1.1920929e-7f; // DAT_1410af2e4
	/** chr+0x1b0: zeroed by the CharacterCtrl constructor (FUN_1403114f0); no other writer found. */
	private static final float MIN = 0f;

	private final Params params;
	private final LoadCurves curves;
	/** chr.b0+0x30: equipped weight / max load (FUN_14037e430). 0 until an equipment weight system exists. */
	private float equipLoadRatio;
	private float current;
	private float max;
	/** Controller +0x08: time left before regen may resume after a consumption. */
	private float regenDelay;
	/** Controller +0x0c: set on exhaustion, meaning still unknown. */
	private float exhaustTimer2;
	/** Controller +0x10: set on exhaustion while it raises chr.b8+0x5e0; meaning of that counter unknown. */
	private float exhaustTimer;
	/** chr.b8+0x558: regen runs only while this is 0. Consumption delay and continuous drains add to it. */
	private int regenBlockers;
	/** TAE 101100 of the current action (FUN_1403287b0 also counts on chr.b8+0x558), set every tick by the caller. */
	private boolean actionBlocksRegen;

	public Stamina(Params params, LoadCurves curves, float max) {
		this.params = params;
		this.curves = curves;
		this.max = max;
		this.current = max;
	}

	public Params params() {
		return params;
	}

	public float current() {
		return current;
	}

	public float max() {
		return max;
	}

	public void setEquipLoadRatio(float ratio) {
		equipLoadRatio = ratio;
	}

	/** FUN_1403337c0. */
	public boolean isExhausted() {
		return current <= 0;
	}

	/** FUN_14038be90: new max, current clamped into [min, max]. */
	public void setMax(float newMax) {
		max = newMax;
		current = clamp(current);
	}

	/**
	 * FUN_1403335d0 with type 0. Its cost multiplier chr.b0+0xc8 starts at 1.0 (constructor FUN_14031cbc0) and is
	 * changed by FUN_140385fe0 (not ported). Going to 0 or below is first bounded to [exhaustedFloor,
	 * exhaustedCeiling] and then to [MIN, max]; with MIN = 0 the player ends at 0.
	 */
	public void consume(float cost) {
		if (Math.abs(cost) <= EPSILON) {
			return;
		}
		float next = current - cost;
		if (next <= 0) {
			next = Math.max(params.exhaustedFloor(), Math.min(next, params.exhaustedCeiling()));
			current = clamp(next);
			exhaustTimer2 = params.exhaustTimer2();
			if (params.exhaustTimer() > 0) {
				exhaustTimer = params.exhaustTimer();
			}
		} else {
			current = clamp(next);
		}
		if (regenDelay <= 0 && params.regenDelay() > 0) {
			regenBlockers++;
		}
		regenDelay = params.regenDelay();
	}

	public void setActionBlocksRegen(boolean blocks) {
		actionBlocksRegen = blocks;
	}

	/** Continuous drains (FUN_140311110 and siblings) block regen while active. */
	public void beginDrain() {
		regenBlockers++;
	}

	public void endDrain() {
		if (regenBlockers > 0) {
			regenBlockers--;
		}
	}

	/**
	 * Per-second drain = ChrParam+0x3c x ChrCommonParam rate x equip-load multiplier (FUN_1403764d0 and siblings);
	 * {@code loadValues} is the matching ChrEquipLoadParam row (drainA/B/C).
	 */
	public void drain(float rate, float[] loadValues, float dt) {
		float load = EquipLoadCurve.multiplier(equipLoadRatio, curves.thresholds(), loadValues);
		consume(params.drainBase() * rate * load * dt);
	}

	public LoadCurves curves() {
		return curves;
	}

	/**
	 * FUN_140333880: (chr.b8+0x14 ? chr.b8+0x264 : 1) x (vfunc +0x130 object, 1 when absent) x load multiplier
	 * for row 0x1d. chr.b8+0x14 is 0 after its constructor (FUN_14031bcf0); the vfunc factor is taken as 1.
	 */
	public float regenMultiplier() {
		return EquipLoadCurve.multiplier(equipLoadRatio, curves.thresholds(), curves.regenPenalty());
	}

	/** FUN_1403337e0: timers count down, then FUN_140333900 regenerates. */
	public void tick(float dt) {
		if (regenDelay != 0) {
			regenDelay -= dt;
			if (regenDelay <= 0) {
				if (regenBlockers > 0) {
					regenBlockers--;
				}
				regenDelay = 0;
			}
		}
		if (exhaustTimer2 != 0) {
			exhaustTimer2 = Math.max(0, exhaustTimer2 - dt);
		}
		if (exhaustTimer != 0) {
			exhaustTimer = Math.max(0, exhaustTimer - dt);
		}
		if (regenBlockers == 0 && !actionBlocksRegen) {
			current = clamp(current + regenMultiplier() * params.regenPerSecond() * dt);
		}
	}

	/** The [min, max] clamp of FUN_1403335d0 / FUN_140333900. */
	private float clamp(float value) {
		return Math.max(MIN, Math.min(value, max));
	}
}
