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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Last Giant (c3096, EnemyParam 309600; docs/research/last_giant.md, c3096_attacks.json): its two AI phases
 * (ai309600 / ai309610 tables, approach, after-attack leap), its actions per weapon mode (steps, hit events, root
 * motion), locomotion and damage rows.
 */
public final class C3096Attacks {
	public static final String RESOURCE = "/data/soulsmode/ds2/c3096_attacks.json";

	/** EnemyParam 309600 (last_giant.md s.2.1): HP, physical defence, magic / lightning / fire / dark absorption %. */
	public static final int HP = 2530;
	public static final float DEFENCE = 380;
	public static final float[] ABSORPTION = {30, 30, 30, 10};
	/** EnemyMoveParam 309600 +0x2c / +0x30: the turn outside an attack, 90 deg/s. */
	public static final float TURN_DEGREES_PER_SECOND = 90;

	/** A hit event: 2200 / 2202 (a hit) or 2301 (a stationary bullet, its shockwave), its window and damage row. */
	public record Hit(int type, float start, float end, int enemyDamageParam, float rate) {
	}

	/** Root motion: (left, back) m per sample, and the root's turn in degrees per sample (+ = toward the model's left). */
	public record Trajectory(float rate, float[][] keys, float[] yaw) {
		private float[] sample(float t) {
			if (keys.length == 0) {
				return new float[] {0, 0, 0};
			}
			float f = Math.max(0, t * rate);
			int i = Math.min(keys.length - 1, (int) f);
			int j = Math.min(keys.length - 1, i + 1);
			float w = Math.min(1, f - i);
			float y0 = yaw == null || yaw.length == 0 ? 0 : yaw[Math.min(yaw.length - 1, i)];
			float y1 = yaw == null || yaw.length == 0 ? 0 : yaw[Math.min(yaw.length - 1, j)];
			return new float[] {keys[i][0] + (keys[j][0] - keys[i][0]) * w, keys[i][1] + (keys[j][1] - keys[i][1]) * w,
					y0 + (y1 - y0) * w};
		}

		/** {left, back, yaw} at {@code t} seconds of the clip. */
		public float[] at(float t) {
			return sample(t);
		}
	}

	public record Step(int tae, String animation, float duration, List<Hit> hits, float hitDurationEnd,
			Trajectory trajectory, List<float[]> otherEvents) {
	}

	public record Action(int mode, int action, boolean combo, List<Step> steps) {
	}

	/** A row of a phase's attack table (last_giant.md s.4.2 / s.4.3). */
	public record Row(float angleMin, float angleMax, float distMin, float distMax, String speed, int rand, Boolean side,
			String type, int action, float continueDistance, float continueAngle, int[] cutRand) {
	}

	/** S2147483645's arguments and the after-attack / approach-end back leaps. */
	public record Phase(int phase, int mode, List<Row> rows, float stopDistance, float e0, float runAngle, float walkAngle,
			float giveUp, float walkGiveUp, int afterRand, float afterMaxDistance, int afterAction,
			float approachEndMaxDistance, int approachEndAction) {
	}

	/** An EnemyDamageParam row with its DamageCtrlParam (poise and reaction inputs). */
	public record DamageRow(float[] values, int floor, float rate, int staminaDamage, float radius, float length,
			Poise.Hit poise) {
	}

	public record Loop(int tae, String animation, float speed) {
	}

	private static List<Phase> phases;
	private static Map<Long, Action> actions;
	private static Map<Integer, Loop> loops;
	private static Map<Integer, DamageRow> rows;
	private static int phaseChangePercent;
	private static int phaseChangeAction;

	private C3096Attacks() {
	}

	private static synchronized void load() {
		if (phases != null) {
			return;
		}
		try (InputStream in = C3096Attacks.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("missing " + RESOURCE);
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			List<Phase> ph = new ArrayList<>();
			for (JsonElement e : json.getAsJsonArray("phases")) {
				JsonObject p = e.getAsJsonObject();
				List<Row> table = new ArrayList<>();
				for (JsonElement re : p.getAsJsonArray("rows")) {
					JsonObject r = re.getAsJsonObject();
					JsonObject call = r.getAsJsonObject("call");
					JsonArray angle = r.getAsJsonArray("angle");
					JsonArray dist = r.getAsJsonArray("dist");
					int[] cut = {0, 0};
					if (call.has("cutRand")) {
						cut = new int[] {call.getAsJsonArray("cutRand").get(0).getAsInt(),
								call.getAsJsonArray("cutRand").get(1).getAsInt()};
					}
					table.add(new Row(angle.get(0).getAsFloat(), angle.get(1).getAsFloat(), dist.get(0).getAsFloat(),
							dist.get(1).getAsFloat(), r.has("speed") ? r.get("speed").getAsString() : null,
							r.has("rand") ? r.get("rand").getAsInt() : 0, r.has("side") ? r.get("side").getAsBoolean() : null,
							call.get("type").getAsString(), call.get("action").getAsInt(),
							call.has("continueDistance") ? call.get("continueDistance").getAsFloat() : Float.MAX_VALUE,
							call.has("continueAngle") ? call.get("continueAngle").getAsFloat() : 360, cut));
				}
				JsonObject ap = p.getAsJsonObject("approach");
				JsonObject after = p.getAsJsonObject("afterAttack");
				JsonObject end = p.getAsJsonObject("onApproachEnd");
				ph.add(new Phase(p.get("phase").getAsInt(), p.get("mode").getAsInt(), List.copyOf(table),
						ap.get("stopDistance").getAsFloat(), ap.getAsJsonArray("env").get(0).getAsFloat(),
						ap.get("runAngle").getAsFloat(), ap.get("walkAngle").getAsFloat(), ap.get("giveUp").getAsFloat(),
						ap.get("walkGiveUp").getAsFloat(), after.get("rand").getAsInt(), after.get("maxDistance").getAsFloat(),
						after.get("action").getAsInt(), end.get("maxDistance").getAsFloat(), end.get("action").getAsInt()));
			}
			Map<Long, Action> acts = new HashMap<>();
			for (JsonElement e : json.getAsJsonArray("actions")) {
				JsonObject a = e.getAsJsonObject();
				List<Step> steps = new ArrayList<>();
				for (JsonElement se : a.getAsJsonArray("steps")) {
					JsonObject s = se.getAsJsonObject();
					List<Hit> hits = new ArrayList<>();
					for (JsonElement he : s.getAsJsonArray("hits")) {
						JsonObject h = he.getAsJsonObject();
						hits.add(new Hit(h.get("type").getAsInt(), h.get("start").getAsFloat(), h.get("end").getAsFloat(),
								h.get("enemyDamageParam").getAsInt(), h.get("rate").getAsFloat()));
					}
					List<float[]> other = new ArrayList<>();
					if (s.has("otherEvents")) {
						for (JsonElement oe : s.getAsJsonArray("otherEvents")) {
							JsonObject o = oe.getAsJsonObject();
							other.add(new float[] {o.get("type").getAsFloat(), o.get("start").getAsFloat(),
									o.get("end").getAsFloat()});
						}
					}
					steps.add(new Step(s.get("id").getAsInt(), s.get("animation").getAsString(),
							s.get("duration").getAsFloat(), List.copyOf(hits),
							s.has("hitDurationEnd") && !s.get("hitDurationEnd").isJsonNull()
									? s.get("hitDurationEnd").getAsFloat() : s.get("duration").getAsFloat(),
							trajectory(s), List.copyOf(other)));
				}
				int mode = a.get("mode").getAsInt();
				int action = a.get("action").getAsInt();
				acts.put(key(mode, action), new Action(mode, action, a.get("combo").getAsBoolean(), List.copyOf(steps)));
			}
			Map<Integer, Loop> loco = new HashMap<>();
			for (JsonElement e : json.getAsJsonArray("locomotion")) {
				JsonObject l = e.getAsJsonObject();
				loco.put(l.get("id").getAsInt(), new Loop(l.get("id").getAsInt(), l.get("animation").getAsString(),
						l.get("speed").getAsFloat()));
			}
			Map<Integer, DamageRow> dmg = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("damageRows").entrySet()) {
				JsonObject r = e.getValue().getAsJsonObject();
				float[] values = new float[5];
				for (JsonElement d : r.getAsJsonArray("damage")) {
					JsonArray pair = d.getAsJsonArray();
					int type = pair.get(0).getAsInt();
					if (type >= 0 && type < 5) {
						values[type] = pair.get(1).getAsFloat();
					}
				}
				Poise.Hit poise = new Poise.Hit(r.get("poiseDamage").getAsFloat(), r.get("poiseRule").getAsInt(),
						r.get("reaction").getAsInt(), r.get("level").getAsInt(), r.get("reaction").getAsInt(),
						r.get("speed").getAsFloat(), r.get("distance").getAsFloat(), -1, r.get("downUp").getAsInt(),
						0, 0, 0);
				dmg.put(Integer.parseInt(e.getKey()), new DamageRow(values, r.get("floor").getAsInt(),
						r.get("rate").getAsFloat(), r.get("staminaDamage").getAsInt(), r.get("radius").getAsFloat(),
						r.get("length").getAsFloat(), poise));
			}
			JsonObject change = json.getAsJsonObject("phaseChange");
			phaseChangePercent = change.get("hpPercentBelow").getAsInt();
			phaseChangeAction = change.get("action").getAsInt();
			actions = acts;
			loops = loco;
			rows = dmg;
			phases = List.copyOf(ph);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static Trajectory trajectory(JsonObject s) {
		float rate = s.has("trajectoryRate") ? s.get("trajectoryRate").getAsFloat() : 30;
		float[][] keys = new float[0][];
		if (s.has("trajectory")) {
			JsonArray t = s.getAsJsonArray("trajectory");
			keys = new float[t.size()][];
			for (int i = 0; i < keys.length; i++) {
				keys[i] = new float[] {t.get(i).getAsJsonArray().get(0).getAsFloat(), t.get(i).getAsJsonArray().get(1).getAsFloat()};
			}
		}
		float[] yaw = null;
		if (s.has("trajectoryYaw") && s.get("trajectoryYaw").isJsonArray()) {
			JsonArray y = s.getAsJsonArray("trajectoryYaw");
			yaw = new float[y.size()];
			for (int i = 0; i < yaw.length; i++) {
				yaw[i] = y.get(i).getAsFloat();
			}
		}
		return new Trajectory(rate, keys, yaw);
	}

	private static long key(int mode, int action) {
		return ((long) mode << 32) | (action & 0xffffffffL);
	}

	/** Phase 1 (mode 0) or 2 (mode 1). */
	public static Phase phase(int phase) {
		load();
		return phases.get(Math.max(0, Math.min(phases.size() - 1, phase - 1)));
	}

	public static Action action(int mode, int action) {
		load();
		return actions.get(key(mode, action));
	}

	public static Loop loop(int tae) {
		load();
		return loops.get(tae);
	}

	public static DamageRow damageRow(int enemyDamageParam) {
		load();
		return rows.get(enemyDamageParam);
	}

	/** f5200() < this (HP %) starts the phase change (5300 in ai309600 map 20). */
	public static int phaseChangePercent() {
		load();
		return phaseChangePercent;
	}

	public static int phaseChangeAction() {
		load();
		return phaseChangeAction;
	}
}
