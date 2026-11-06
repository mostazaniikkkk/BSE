package dev.soulsmode.ds2;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DS2's ranged weapons (docs/research/bow.md, crossbow.md; weapons.json {@code short_bow} / {@code light_crossbow},
 * {@code bow} / {@code crossbow}, {@code bowAnimations} / {@code crossbowAnimations}):
 * <ul>
 * <li>Short Bow 4200000 with Wood Arrow 60760000: only two-handed fires (one-handed slots are motion type 15).</li>
 * <li>Light Crossbow 4600000 with Wood Bolt 60910000: also fires one-handed from either hand (type 10), with its own
 * clips per grip, a 0.4 s Prepare held at 95 %, and a long CF (the reload is inside it).</li>
 * </ul>
 * Two-handed, the other hand's buttons aim while held (type 16, CameraSubjectiveParam 10 / 30, identical rows). The
 * shot: Prepare (stamina at ShootStateControl), held while the button is, then the CF whose TAE 2300 spawns the
 * grip's bullet; without ammo, the grip's NoBullet clip.
 */
public final class Bow {
	public static final Bow BOW = new Bow("short_bow", "bow", "bowAnimations", false);
	public static final Bow CROSSBOW = new Bow("light_crossbow", "crossbow", "crossbowAnimations", true);

	/** Grip keys of the crossbow's per-grip blocks; the bow only has two-handed shots. */
	public static final String ONE_HAND_RIGHT = "oneHandRight";
	public static final String ONE_HAND_LEFT = "oneHandLeft";
	public static final String TWO_HAND = "twoHand";

	/** One grip's shot: clips, timings, stamina, bullet and spawn point. */
	public record Shot(Attack prepare, Attack cf, Attack noBullet, float shootAt, float bulletAt, float staminaCost,
			String bullet, float[] originLeft, float[] originRight) {
		/** The spawn point in model space (left, up, forward, metres) for the weapon's hand. */
		public float[] origin(boolean weaponLeft) {
			return weaponLeft ? originLeft : originRight;
		}
	}

	public final String weapon;
	private final String block;
	private final String animationsBlock;
	/** Fires one-handed (the crossbow; the bow's one-handed slots do nothing). */
	public final boolean oneHanded;
	private Map<String, Shot> shots;
	private Map<String, Bullets.Def> bullets;
	private JsonObject camera;
	/** Every clip of this weapon by TAE id (clients resolve the Start payload with it). */
	private final Map<Integer, Attack> clips = new HashMap<>();

	private Bow(String weapon, String block, String animationsBlock, boolean oneHanded) {
		this.weapon = weapon;
		this.block = block;
		this.animationsBlock = animationsBlock;
		this.oneHanded = oneHanded;
	}

	public static Bow of(String weaponKey) {
		return BOW.weapon.equals(weaponKey) ? BOW : CROSSBOW.weapon.equals(weaponKey) ? CROSSBOW : null;
	}

	private synchronized void load() {
		if (shots != null) {
			return;
		}
		JsonObject raw = Weapons.raw();
		JsonObject root = raw.getAsJsonObject(block);
		JsonObject anims = raw.getAsJsonObject(animationsBlock);
		JsonObject ids = root.getAsJsonObject("animations");
		JsonObject arrow = root.getAsJsonObject("arrow");
		bullets = Bullets.parse(arrow.getAsJsonObject("bullets"));
		camera = root.getAsJsonObject("aimCamera");
		JsonObject w = raw.getAsJsonObject("weapons").getAsJsonObject(weapon);
		String origin = arrow.get("originDummy").getAsString();
		float[] dummyLeft = floats(w.getAsJsonObject("dummyPolysLeft").getAsJsonObject(origin).getAsJsonArray("pos"));
		float[] dummyRight = floats(w.getAsJsonObject("dummyPolys").getAsJsonObject(origin).getAsJsonArray("pos"));
		shots = new HashMap<>();
		for (String grip : List.of(ONE_HAND_RIGHT, ONE_HAND_LEFT, TWO_HAND)) {
			// The crossbow has a block per grip; the bow's top-level ids are its two-handed shot.
			JsonObject g = ids.has(grip) ? ids.getAsJsonObject(grip) : grip.equals(TWO_HAND) ? ids : null;
			if (g == null) {
				continue;
			}
			JsonObject prepareJson = anims.getAsJsonObject(g.get("prepare").getAsString());
			JsonObject cfJson = anims.getAsJsonObject(g.get("cf").getAsString());
			float stamina = root.get("staminaCost").isJsonObject()
					? root.getAsJsonObject("staminaCost").get(grip).getAsFloat() : root.get("staminaCost").getAsFloat();
			String bullet = arrow.has("bulletByGrip")
					? arrow.getAsJsonObject("bulletByGrip").getAsJsonObject(gripCode(grip)).get("bullet").getAsString()
					: arrow.get("bullet").getAsString();
			// The CF's bone sample at TAE 2300 is of its authored side; the other side is the mirrored export.
			boolean cfLeft = cfJson.get("leftAuthored").getAsBoolean();
			float[] left = origin(cfJson.getAsJsonArray(cfLeft ? "bulletBones" : "bulletBonesMirror"), dummyLeft);
			float[] right = origin(cfJson.getAsJsonArray(cfLeft ? "bulletBonesMirror" : "bulletBones"), dummyRight);
			shots.put(grip, new Shot(clip(g.get("prepare").getAsInt(), anims), clip(g.get("cf").getAsInt(), anims),
					clip(g.get("noBullet").getAsInt(), anims), prepareJson.getAsJsonArray("shootState").get(0).getAsFloat(),
					cfJson.getAsJsonArray("bullets").get(0).getAsJsonArray().get(0).getAsFloat(), stamina, bullet, left, right));
		}
	}

	/** crossbow.md: bulletByGrip keys are the hand states (2 right one-handed, 3 two-handed, 6 left one-handed). */
	private static String gripCode(String grip) {
		return switch (grip) {
			case ONE_HAND_LEFT -> "6";
			case TWO_HAND -> "3";
			default -> "2";
		};
	}

	private Attack clip(int tae, JsonObject anims) {
		return clips.computeIfAbsent(tae, t -> {
			JsonObject a = anims.getAsJsonObject(String.valueOf(t));
			float duration = a.get("duration").getAsFloat();
			float[] avoid = a.get("avoidLock").isJsonNull() ? new float[] {0, 0} : pair(a.getAsJsonArray("avoidLock"));
			float move = a.get("moveLock").isJsonNull() ? 0 : a.get("moveLock").getAsFloat();
			float light = lock(a, "lightLock");
			float heavy = lock(a, "heavyLock");
			float leftLight = lock(a, "leftLightLock");
			float leftHeavy = lock(a, "leftHeavyLock");
			List<float[]> regen = new ArrayList<>();
			for (JsonElement r : a.getAsJsonArray("regenBlock")) {
				regen.add(pair(r.getAsJsonArray()));
			}
			return new Attack("soulsmode:ds2_" + t, t, duration, List.of(), avoid[0], avoid[1], move, light, heavy, leftLight,
					leftHeavy, 0, 0, false, a.get("leftAuthored").getAsBoolean(), List.of(), List.of(), 0, List.of(),
					List.copyOf(regen));
		});
	}

	/** A cancel lock, or attackLock when the clip has no per-button one (NoBullet), else 0. */
	private static float lock(JsonObject a, String key) {
		if (!a.get(key).isJsonNull()) {
			return a.get(key).getAsFloat();
		}
		return a.get("attackLock").isJsonNull() ? 0 : a.get("attackLock").getAsFloat();
	}

	private static float[] origin(JsonArray samples, float[] dummy) {
		JsonObject o = samples.get(0).getAsJsonObject();
		float[] pos = floats(o.getAsJsonArray("pos"));
		float[] out = new float[3];
		for (int i = 0; i < 3; i++) {
			float[] r = floats(o.getAsJsonArray("rot").get(i).getAsJsonArray());
			out[i] = pos[i] + r[0] * dummy[0] + r[1] * dummy[1] + r[2] * dummy[2];
		}
		return out;
	}

	private static float[] floats(JsonArray a) {
		float[] out = new float[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsFloat();
		}
		return out;
	}

	private static float[] pair(JsonArray a) {
		return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat()};
	}

	/** The shot of a grip key, or null when this weapon has none (the bow one-handed). */
	public Shot shot(String grip) {
		load();
		return shots.get(grip);
	}

	/** A clip of this weapon by TAE id, or null. */
	public Attack clip(int tae) {
		load();
		return clips.get(tae);
	}

	public Map<String, Bullets.Def> bullets() {
		load();
		return bullets;
	}

	/** CameraSubjectiveParam 10 / 30 (identical rows; bow.md "Modo apuntar"). */
	public float aimFov() {
		load();
		return camera.get("fov").getAsFloat();
	}

	public float aimEyeUp() {
		load();
		return camera.getAsJsonObject("eye").get("up").getAsFloat();
	}

	public float aimEyeForward() {
		load();
		return camera.getAsJsonObject("eye").get("forward").getAsFloat();
	}

	/** Pitch limits in radians, positive = looking down (Minecraft's sign). */
	public float aimPitchMin() {
		load();
		return camera.get("pitchMin").getAsFloat();
	}

	public float aimPitchMax() {
		load();
		return camera.get("pitchMax").getAsFloat();
	}

	/** The aim camera's mouse formula field (weapons.json {@code aimCamera.mouse}). */
	public float aimMouse(String field) {
		load();
		return camera.getAsJsonObject("mouse").get(field).getAsFloat();
	}

	public float aimRayLength() {
		load();
		return camera.get("rayLength").getAsFloat();
	}
}
