package dev.soulsmode.ds2;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The damage reaction's blend of clips (docs/research/hit_reaction.md s.9 / s.11), shared by the server (root motion)
 * and the client (pose): c0001 BT_Damage (the NPC invader), c1020 state 0x61c (zombies, illagers) and c3000 state
 * 0x284 (iron golems; the same tree as c1020's, docs/research/c3000_ogre.md s.6).
 */
public final class ReactionBlend {
	/** A clip (TAE id) with its weight. */
	public record Part(int tae, float weight) {
	}

	/** One clip's root motion: samples per second and (left, back) metres per sample (nsa.trajectory). */
	private record Trajectory(float rate, float[][] keys) {
	}

	/** BlendN on DmgDirection: children BR, BL, FL, FR, BR, BL at these angles (quadrant suffixes 3, 2, 0, 1, 3, 2). */
	private static final float[] DIRECTIONS = {-225, -135, -45, 45, 135, 225};
	private static final int[] QUADRANTS = {3, 2, 0, 1, 3, 2};
	public static final float C0001_LENGTH = 1.3333f;
	public static final float C1020_LENGTH = 1.0f;
	/** c3000's six DmgKnockBack clips (.nsa +0x28). */
	public static final float C3000_LENGTH = 1.1667f;

	private static Map<Integer, Trajectory> trajectories;

	private ReactionBlend() {
	}

	/**
	 * nodeBlendNUpdateConnections (FUN_1406387e0, no wrap): clamped to the ends, else linear between the two neighbours
	 * of {@code angle} (degrees, positive = attacker on the right); clips 160000 + 10 x + quadrant.
	 */
	public static List<Part> quadrants(int x, float angle) {
		int i = 1;
		while (i < DIRECTIONS.length - 1 && angle > DIRECTIONS[i]) {
			i++;
		}
		float f = Math.max(0, Math.min(1, (angle - DIRECTIONS[i - 1]) / (DIRECTIONS[i] - DIRECTIONS[i - 1])));
		return List.of(new Part(160000 + 10 * x + QUADRANTS[i - 1], 1 - f), new Part(160000 + 10 * x + QUADRANTS[i], f));
	}

	/**
	 * The root motion's source (hit_reaction.md s.11): the FeatherBlend2's child 0 (its trajectory alpha is 0), a Blend2
	 * of the Light and Heavy branches at {@code heavy}; each branch the BlendN pair of its quadrants (c0001) or one clip
	 * (c1020 / c3000: {@code lightB} / {@code heavyB} unused, f = 0). {@code rig}: 0 c0001, 1020, 3000.
	 */
	public record Lower(int lightA, int lightB, int heavyA, int heavyB, float f, float heavy, int rig) {
	}

	/** c0001: Light set x, Heavy set x + 4, quadrants by {@code angle}. */
	public static Lower c0001Lower(int x, float angle, float heavy) {
		List<Part> light = quadrants(x, angle);
		List<Part> heavySet = quadrants(x + 4, angle);
		return new Lower(light.get(0).tae(), light.get(1).tae(), heavySet.get(0).tae(), heavySet.get(1).tae(),
				light.get(1).weight(), heavy, 0);
	}

	/**
	 * c1020 Light clip for the switches DmgDownOrUp / front-back (the _B clip serves both heights). c3000's state 0x284
	 * has the same clips under the same switches.
	 */
	public static int c1020Light(boolean up, boolean back) {
		return back ? 160016 : up ? 160025 : 160015;
	}

	public static int c1020Heavy(boolean up, boolean back) {
		return back ? 160056 : up ? 160065 : 160055;
	}

	public static Lower c1020Lower(boolean up, boolean back, float heavy) {
		int light = c1020Light(up, back);
		int h = c1020Heavy(up, back);
		return new Lower(light, light, h, h, 0, heavy, 1020);
	}

	/** c3000 (the Ogre): c1020's tree over its own clips. */
	public static Lower c3000Lower(boolean up, boolean back, float heavy) {
		int light = c1020Light(up, back);
		int h = c1020Heavy(up, back);
		return new Lower(light, light, h, h, 0, heavy, 3000);
	}

	/**
	 * The root motion from clip time {@code t0} to {@code t1}, (left, back) metres. Each clip gives a per-frame delta
	 * (FUN_140616980: positions at both times, no rotation in these clips); Blend2 / BlendN combine two children's deltas
	 * with TaskBlend2...SlerpTraj (FUN_14061af00): position by FUN_1405ea3f0 (blendPosition), and keep a single child
	 * when the weight is under 1e-4 or over 0.9999 (FUN_140636c30).
	 */
	public static float[] delta(Lower l, float t0, float t1) {
		float[] light = blend(clipDelta(l.lightA(), l.rig(), t0, t1), clipDelta(l.lightB(), l.rig(), t0, t1), l.f());
		float[] heavy = blend(clipDelta(l.heavyA(), l.rig(), t0, t1), clipDelta(l.heavyB(), l.rig(), t0, t1), l.f());
		return blend(light, heavy, l.heavy());
	}

	private static float[] blend(float[] a, float[] b, float w) {
		if (w < 1e-4f) {
			return a;
		}
		if (w > 0.9999f) {
			return b;
		}
		return blendPosition(a, b, w);
	}

	/**
	 * FUN_1405ea3f0: linear when either vector is shorter than 1e-6 or they are parallel; otherwise the first's
	 * direction turned towards the second's by acos(dot) w, with the length lerped.
	 */
	public static float[] blendPosition(float[] a, float[] b, float w) {
		double la = Math.hypot(a[0], a[1]);
		double lb = Math.hypot(b[0], b[1]);
		if (la < 1e-6 || lb < 1e-6) {
			return new float[] {a[0] + (b[0] - a[0]) * w, a[1] + (b[1] - a[1]) * w};
		}
		double dot = (a[0] * b[0] + a[1] * b[1]) / (la * lb);
		double cross = (a[0] * b[1] - a[1] * b[0]) / (la * lb);
		if (Math.abs(cross) < 1e-6) {
			return new float[] {a[0] + (b[0] - a[0]) * w, a[1] + (b[1] - a[1]) * w};
		}
		double angle = Math.acos(Math.max(-1, Math.min(1, dot))) * w * Math.signum(cross);
		double len = la + (lb - la) * w;
		double ux = a[0] / la;
		double uy = a[1] / la;
		return new float[] {(float) ((ux * Math.cos(angle) - uy * Math.sin(angle)) * len),
				(float) ((ux * Math.sin(angle) + uy * Math.cos(angle)) * len)};
	}

	/** One clip's position change between two clip times. */
	private static float[] clipDelta(int tae, int rig, float t0, float t1) {
		Trajectory tr = rig == 3000 ? c3000(tae) : trajectories().get(rig == 1020 ? -tae : tae);
		if (tr == null || tr.keys().length == 0) {
			return new float[2];
		}
		float[] p0 = sample(tr, t0);
		float[] p1 = sample(tr, t1);
		return new float[] {p1[0] - p0[0], p1[1] - p0[1]};
	}

	/** FUN_14065ebc0: f = t rate, i = trunc(f), a = f - i; at the last sample i = n - 2, a = 1; linear. */
	private static float[] sample(Trajectory tr, float t) {
		float[][] k = tr.keys();
		if (k.length == 1) {
			return k[0];
		}
		float f = Math.max(0, t * tr.rate());
		int i = (int) f;
		float a = f - i;
		if (i >= k.length - 1) {
			i = k.length - 2;
			a = 1;
		}
		return new float[] {k[i][0] + (k[i + 1][0] - k[i][0]) * a, k[i][1] + (k[i + 1][1] - k[i][1]) * a};
	}

	private static Trajectory c3000(int tae) {
		C3000Attacks.Trajectory t = C3000Attacks.damageTrajectory(tae);
		return t == null ? null : new Trajectory(t.rate(), t.keys());
	}

	/** c0001 from weapons.json {@code hitReaction}; c1020 from c1020_attacks.json {@code damage} (stored negated). */
	private static synchronized Map<Integer, Trajectory> trajectories() {
		if (trajectories != null) {
			return trajectories;
		}
		Map<Integer, Trajectory> out = new HashMap<>();
		JsonObject hr = Weapons.raw().getAsJsonObject("hitReaction");
		float rate = hr.get("trajectoryRate").getAsFloat();
		for (Map.Entry<String, JsonElement> e : hr.getAsJsonObject("trajectories").entrySet()) {
			out.put(Integer.parseInt(e.getKey()), new Trajectory(rate, keys(e.getValue().getAsJsonArray())));
		}
		try (var in = ReactionBlend.class.getResourceAsStream("/data/soulsmode/ds2/c1020_attacks.json")) {
			JsonObject c = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement d : c.getAsJsonArray("damage")) {
				JsonObject o = d.getAsJsonObject();
				out.put(-o.get("id").getAsInt(), new Trajectory(o.get("trajectoryRate").getAsFloat(),
						keys(o.getAsJsonArray("trajectory"))));
			}
		} catch (Exception e) {
			throw new IllegalStateException("c1020_attacks.json", e);
		}
		trajectories = out;
		return out;
	}

	private static float[][] keys(JsonArray a) {
		float[][] out = new float[a.size()][];
		for (int i = 0; i < out.length; i++) {
			JsonArray k = a.get(i).getAsJsonArray();
			out[i] = new float[] {k.get(0).getAsFloat(), k.get(1).getAsFloat()};
		}
		return out;
	}
}
