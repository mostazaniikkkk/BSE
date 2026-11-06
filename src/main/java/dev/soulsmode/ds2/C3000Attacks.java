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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Ogre's attacks (docs/research/c3000_ogre.md, data/soulsmode/ds2/c3000_attacks.json, tools/re/ogre_anims.py):
 * per ai300000 action its steps (animation, length, TAE 2200 hit windows with the EnemyDamageParam rate, the
 * HitDuration end where a combo's next step starts, root motion), the reach / angle of its attack subroutine and a
 * combo's continuation limits; the locomotion loops and the damage clips.
 */
public final class C3000Attacks {
	public static final String RESOURCE = "/data/soulsmode/ds2/c3000_attacks.json";

	/** A hit window: start / end (s), EnemyDamageParam +0x1c rate (0 = a row without damage). */
	public record Hit(float start, float end, float rate, int enemyDamageParam) {
	}

	/** Root motion: samples per second and (left, back) metres per sample. */
	public record Trajectory(float rate, float[][] keys) {
		/** FUN_14065ebc0: f = t rate, i = trunc(f), a = f - i; at the last sample i = n - 2, a = 1; linear. */
		public float[] at(float t) {
			if (keys.length == 0) {
				return new float[2];
			}
			if (keys.length == 1) {
				return keys[0];
			}
			float f = Math.max(0, t * rate);
			int i = (int) f;
			float a = f - i;
			if (i >= keys.length - 1) {
				i = keys.length - 2;
				a = 1;
			}
			return new float[] {keys[i][0] + (keys[i + 1][0] - keys[i][0]) * a,
					keys[i][1] + (keys[i + 1][1] - keys[i][1]) * a};
		}
	}

	public record Step(int tae, String animation, float duration, float hitDurationEnd, List<Hit> hits,
			Trajectory trajectory) {
	}

	/**
	 * An action of ai300000. {@code reach} / {@code angle}: launch limits of its subroutine (NaN = launched where it
	 * stands, combo 52); {@code continueDistance} / {@code continueAngle}: S2147483628's cut.
	 */
	public record Action(int action, boolean combo, float reach, float angle, float continueDistance,
			float continueAngle, List<Step> steps) {
	}

	public record Loop(int tae, String animation, float duration, float speed) {
	}

	private static Map<Integer, Action> actions;
	private static Map<Integer, Loop> locomotion;
	private static Map<Integer, Trajectory> damage;
	private static float damageDuration = Float.NaN;

	private C3000Attacks() {
	}

	/** The action of ai300000 (14, 15, 50, 51, 52), or null. */
	public static Action action(int action) {
		load();
		return actions.get(action);
	}

	/** A locomotion loop by TAE id (7001 idle, 37001 walk, 47001 jog), or null. */
	public static Loop locomotion(int tae) {
		load();
		return locomotion.get(tae);
	}

	/** A damage clip's root motion (160015..160065), or null. */
	public static Trajectory damageTrajectory(int tae) {
		load();
		return damage.get(tae);
	}

	/** DamageDuration of the reaction clips (all six: 0..0.5 s at speed 1). */
	public static float damageDuration() {
		load();
		return damageDuration;
	}

	/**
	 * The attack table S2147483609 of ai300000 (map 10; r0 = RandInt(0, 20), first match): d = f7001() (m), angle =
	 * f7002() (deg). Not ported: the f5018() turn-around (61) and the grab (action 13), so their rows fall through;
	 * f5901() == 10 holds (the table runs in map 10).
	 */
	public static int table(double d, double angle, int r0) {
		if (d < 4.5 && angle >= 110) {
			return 52;
		}
		if (d < 2.5 && r0 > 0 && r0 < 5) {
			return 14;
		}
		if (d < 2.5 && r0 > 0 && r0 < 10) {
			return 15;
		}
		if (r0 > 0 && r0 < 15) {
			return 51;
		}
		return 50;
	}

	private static synchronized void load() {
		if (actions != null) {
			return;
		}
		Map<Integer, Action> a = new LinkedHashMap<>();
		Map<Integer, Loop> l = new LinkedHashMap<>();
		Map<Integer, Trajectory> dmg = new LinkedHashMap<>();
		try (InputStream in = C3000Attacks.class.getResourceAsStream(RESOURCE)) {
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement e : json.getAsJsonArray("actions")) {
				JsonObject o = e.getAsJsonObject();
				List<Step> steps = new ArrayList<>();
				for (JsonElement s : o.getAsJsonArray("steps")) {
					JsonObject so = s.getAsJsonObject();
					List<Hit> hits = new ArrayList<>();
					for (JsonElement h : so.getAsJsonArray("hits")) {
						JsonObject ho = h.getAsJsonObject();
						hits.add(new Hit(ho.get("start").getAsFloat(), ho.get("end").getAsFloat(), ho.get("rate").getAsFloat(),
								ho.get("enemyDamageParam").getAsInt()));
					}
					steps.add(new Step(so.get("id").getAsInt(), so.get("animation").getAsString(),
							so.get("duration").getAsFloat(), so.get("hitDurationEnd").getAsFloat(), List.copyOf(hits),
							trajectory(so)));
				}
				int id = o.get("action").getAsInt();
				a.put(id, new Action(id, o.get("combo").getAsBoolean(), f(o, "reach"), f(o, "angle"),
						f(o, "continueDistance"), f(o, "continueAngle"), List.copyOf(steps)));
			}
			for (JsonElement e : json.getAsJsonArray("locomotion")) {
				JsonObject o = e.getAsJsonObject();
				l.put(o.get("id").getAsInt(), new Loop(o.get("id").getAsInt(), o.get("animation").getAsString(),
						o.get("duration").getAsFloat(), o.get("speed").getAsFloat()));
			}
			for (JsonElement e : json.getAsJsonArray("damage")) {
				JsonObject o = e.getAsJsonObject();
				dmg.put(o.get("id").getAsInt(), trajectory(o));
				damageDuration = o.get("damageDuration").getAsFloat();
			}
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		locomotion = l;
		damage = dmg;
		actions = a;
	}

	private static float f(JsonObject o, String key) {
		return o.has(key) ? o.get(key).getAsFloat() : Float.NaN;
	}

	private static Trajectory trajectory(JsonObject o) {
		JsonArray k = o.getAsJsonArray("trajectory");
		float[][] keys = new float[k.size()][];
		for (int i = 0; i < keys.length; i++) {
			JsonArray p = k.get(i).getAsJsonArray();
			keys[i] = new float[] {p.get(0).getAsFloat(), p.get(1).getAsFloat()};
		}
		return new Trajectory(o.get("trajectoryRate").getAsFloat(), keys);
	}
}
