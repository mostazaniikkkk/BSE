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
 * The player's poise (docs/research/poise.md, poise.json): its maximum (armour ArmorParam +0x40 with the lack-of-stats
 * penalty, PSPL +0x90 at min(Endurance, Adaptability)), regeneration, the poise damage and rule of each incoming hit
 * (DamageCtrlParam +0xa0 / +0x02) and the TAE windows that change the reaction (110500 / 110600 / 111900).
 */
public final class Poise {
	public static final String RESOURCE = "/data/soulsmode/ds2/poise.json";

	/** A Minecraft armour piece's DS2 row: poise and Str / Dex / Int / Fth requirements (ArmorParam +0x2c..+0x32). */
	public record Piece(float poise, int[] requirements) {
	}

	/**
	 * What an incoming hit's DamageCtrlParam row gives the victim: poise damage (+0xa0), rule (+0x02: 0 always, 1 only
	 * in a TAE 111900 window, 2 never), reaction (+0x10) and level (+0x0c), the reaction after DamageMotionWeightParam
	 * column 2 (the player victim's, FUN_140138a40), and the reaction inputs of the hit row (speed, DmgDistance base,
	 * thrust, DmgDownOrUp mode, near / far / falloff); NaN / -1 where poise.json has none.
	 */
	public record Hit(float poiseDamage, int rule, int reaction, int level, int remapped, float speed, float distance,
			int thrust, int downUp, float near, float far, float falloff) {
	}

	/** A stagger clip's length and its track windows (clip seconds, null when the clip has no such track). */
	public record StaggerClip(float duration, Map<String, float[]> tracks) {
		public float end(String track) {
			float[] w = tracks.get(track);
			return w == null ? 0 : w[1];
		}
	}

	private static JsonObject json;
	private static Map<String, Map<String, Piece>> armor;
	private static float[] statPoise;
	private static float[] armorLack;
	private static Map<String, Map<Integer, Hit>> enemyHits;
	private static Map<Integer, List<float[]>> superArmor;
	private static Map<Integer, List<float[]>> noReaction;
	private static Map<Integer, List<float[]>> poiseFactorWindows;
	private static Map<Integer, List<Hit>> weaponHits;
	private static Map<String, Float> attackPoiseFactor;
	private static Map<Integer, StaggerClip> staggerClips;

	private Poise() {
	}

	private static synchronized void load() {
		if (json != null) {
			return;
		}
		try (InputStream in = Poise.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("missing " + RESOURCE);
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			Map<String, Map<String, Piece>> pieces = new HashMap<>();
			for (Map.Entry<String, JsonElement> m : root.getAsJsonObject("armor").entrySet()) {
				Map<String, Piece> set = new HashMap<>();
				for (Map.Entry<String, JsonElement> p : m.getValue().getAsJsonObject().entrySet()) {
					JsonObject o = p.getValue().getAsJsonObject();
					JsonArray r = o.getAsJsonArray("requirements");
					set.put(p.getKey(), new Piece(o.get("poise").getAsFloat(), new int[] {r.get(0).getAsInt(),
							r.get(1).getAsInt(), r.get(2).getAsInt(), r.get(3).getAsInt()}));
				}
				pieces.put(m.getKey(), set);
			}
			statPoise = floats(root.getAsJsonObject("statPoise").getAsJsonArray("values"));
			armorLack = floats(root.getAsJsonObject("armorLackOfStats").getAsJsonArray("values"));
			Map<String, Map<Integer, Hit>> enemies = new HashMap<>();
			for (Map.Entry<String, JsonElement> rig : root.getAsJsonObject("enemyHits").entrySet()) {
				Map<Integer, Hit> rows = new HashMap<>();
				for (Map.Entry<String, JsonElement> e : rig.getValue().getAsJsonObject().entrySet()) {
					rows.put(Integer.parseInt(e.getKey()), hit(e.getValue().getAsJsonObject()));
				}
				enemies.put(rig.getKey(), rows);
			}
			JsonObject tae = root.getAsJsonObject("taeWindows");
			superArmor = windows(tae.getAsJsonArray("110500"));
			noReaction = windows(tae.getAsJsonArray("110600"));
			poiseFactorWindows = windows(tae.getAsJsonArray("111900"));
			Map<Integer, List<Hit>> byTae = new HashMap<>();
			Map<String, Float> factors = new HashMap<>();
			for (Map.Entry<String, JsonElement> w : root.getAsJsonObject("playerWeapons").entrySet()) {
				JsonObject weapon = w.getValue().getAsJsonObject();
				factors.put(w.getKey(), weapon.get("attackPoiseFactor").getAsFloat());
				for (Map.Entry<String, JsonElement> a : weapon.getAsJsonObject("attacks").entrySet()) {
					JsonObject attack = a.getValue().getAsJsonObject();
					List<Hit> hits = new ArrayList<>();
					for (JsonElement h : attack.getAsJsonArray("hits")) {
						hits.add(h.isJsonNull() ? null : hit(h.getAsJsonObject()));
					}
					byTae.putIfAbsent(attack.get("taeId").getAsInt(), hits);
				}
			}
			Map<Integer, StaggerClip> clips = new HashMap<>();
			for (Map.Entry<String, JsonElement> c : root.getAsJsonObject("staggerClips").entrySet()) {
				JsonObject o = c.getValue().getAsJsonObject();
				Map<String, float[]> tracks = new HashMap<>();
				for (Map.Entry<String, JsonElement> t : o.getAsJsonObject("tracks").entrySet()) {
					if (t.getValue().isJsonArray()) {
						tracks.put(t.getKey(), floats(t.getValue().getAsJsonArray()));
					}
				}
				clips.put(Integer.parseInt(c.getKey()), new StaggerClip(o.get("duration").getAsFloat(), tracks));
			}
			armor = pieces;
			enemyHits = enemies;
			weaponHits = byTae;
			attackPoiseFactor = factors;
			staggerClips = clips;
			json = root;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static float get(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsFloat() : Float.NaN;
	}

	private static Hit hit(JsonObject o) {
		return new Hit(o.get("poiseDamage").getAsFloat(), o.get("poiseRule").getAsInt(), o.get("reaction").getAsInt(),
				o.get("level").getAsInt(), o.has("remapped") ? o.get("remapped").getAsInt() : -1,
				get(o, "speed"), get(o, "distance"),
				o.has("thrust") ? o.get("thrust").getAsInt() : -1, o.has("downUp") ? o.get("downUp").getAsInt() : -1,
				get(o, "near"), get(o, "far"), get(o, "falloff"));
	}

	private static float[] floats(JsonArray a) {
		float[] out = new float[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsFloat();
		}
		return out;
	}

	/** [taeId, start, end] rows -> per TAE id its windows. */
	private static Map<Integer, List<float[]>> windows(JsonArray rows) {
		Map<Integer, List<float[]>> out = new HashMap<>();
		if (rows == null) {
			return out;
		}
		for (JsonElement e : rows) {
			JsonArray r = e.getAsJsonArray();
			out.computeIfAbsent(r.get(0).getAsInt(), k -> new ArrayList<>())
					.add(new float[] {r.get(1).getAsFloat(), r.get(2).getAsFloat()});
		}
		return out;
	}

	private static boolean inside(Map<Integer, List<float[]>> windows, int taeId, float t) {
		List<float[]> list = windows.get(taeId);
		if (list == null) {
			return false;
		}
		for (float[] w : list) {
			if (t >= w[0] && t < w[1]) {
				return true;
			}
		}
		return false;
	}

	/** The DS2 row of a Minecraft armour piece ("iron" / "helmet"...), or null. */
	public static Piece piece(String material, String slot) {
		load();
		Map<String, Piece> set = armor.get(material);
		return set == null ? null : set.get(slot);
	}

	/**
	 * A piece's poise with FUN_14034d150's lack f = clamp(sum of max(0, 1 - stat / req) over Str, Dex, Int, Fth with
	 * req > 0, 0, 1) and PlayerLackOfStatsParam row 0x1c: x (1 - clamp(0.01 (30 + 50 f), 0, 1)) when f > 0.
	 */
	public static float piecePoise(Piece piece, Attributes stats) {
		load();
		float f = 0;
		int[] slots = {Attributes.STRENGTH, Attributes.DEXTERITY, Attributes.INTELLIGENCE, Attributes.FAITH};
		for (int i = 0; i < 4; i++) {
			int req = piece.requirements()[i];
			if (req > 0) {
				f += Math.max(0, 1 - stats.get(slots[i]) / (float) req);
			}
		}
		f = Math.max(0, Math.min(1, f));
		if (f <= 0) {
			return piece.poise();
		}
		float pen = Math.max(0, Math.min(1, 0.01f * (armorLack[0] + (armorLack[1] - armorLack[0]) * f)));
		return piece.poise() * (1 - pen);
	}

	/** PSPL +0x90 at min(Endurance, Adaptability) (FUN_14038d790 derived +0x20; outside 1..99 -> 1). */
	public static float statPoise(Attributes stats) {
		load();
		int row = Math.min(stats.get(Attributes.ENDURANCE), stats.get(Attributes.ADAPTABILITY));
		if (row < 1 || row > statPoise.length) {
			row = 1;
		}
		return statPoise[row - 1];
	}

	/** ChrParam 100 +0x118. */
	public static float base() {
		load();
		return json.get("base").getAsFloat();
	}

	/** ChrParam 100 +0x11c, per second, outside a reaction. */
	public static float regenPerSecond() {
		load();
		return json.get("regenPerSecond").getAsFloat();
	}

	/** An enemy hit row ("c1020" / "c3000", EnemyDamageParam id), or null. */
	public static Hit enemyHit(String rig, int enemyDamageParam) {
		load();
		Map<Integer, Hit> rows = enemyHits.get(rig);
		return rows == null ? null : rows.get(enemyDamageParam);
	}

	/** Slot {@code slot} (0..2) of a player-weapon attack clip (its PDP's DCP row), by TAE id; null when unknown. */
	public static Hit weaponHit(int taeId, int slot) {
		load();
		List<Hit> hits = weaponHits.get(taeId);
		return hits == null || slot < 0 || slot >= hits.size() ? null : hits.get(slot);
	}

	/** ChrCommonParam 100 +0x30c..+0x318: D, A, B, C of the player's random Dmg_Rotation (HipsIK), degrees. */
	public static float[] hipsRotation() {
		load();
		return floats(json.getAsJsonArray("hipsRotation"));
	}

	/** WeaponParam +0xb8 of a weapon (weapons.json key): b8+0x7d0 while one of its TAE 111900 windows runs. */
	public static float attackPoiseFactor(String weapon) {
		load();
		return attackPoiseFactor.getOrDefault(weapon, 0f);
	}

	/** TAE 110500 (b8+0x5e8) at {@code t} of the clip. */
	public static boolean superArmor(int taeId, float t) {
		load();
		return inside(superArmor, taeId, t);
	}

	/** TAE 110600 (b8+0x5ec) at {@code t} of the clip. */
	public static boolean noReaction(int taeId, float t) {
		load();
		return inside(noReaction, taeId, t);
	}

	/** TAE 111900 at {@code t} of the clip. */
	public static boolean poiseFactorWindow(int taeId, float t) {
		load();
		return inside(poiseFactorWindows, taeId, t);
	}

	/** A stagger clip (160010 Light, 160050 Heavy, 160110 / 160150 ExtraHeavy, 160510 Tumble...), or null. */
	public static StaggerClip staggerClip(int tae) {
		load();
		return staggerClips.get(tae);
	}

	/** poise.json's reaction1 block (the overlay when poise holds). */
	public static JsonObject reaction1() {
		load();
		return json.getAsJsonObject("reaction1");
	}
}
