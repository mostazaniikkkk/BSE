package dev.soulsmode.ds2;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The player's lock-on values exported by tools/re/lockon.py (docs/research/lockon.md). Distances in metres
 * (blocks), angles in radians, times in seconds.
 *
 * @param listRadius      LockOnParam f0 x LockTargetParam f0: candidates (and the current target) must be closer
 * @param acquireDistance LockOnParam f2 x LockTargetParam f1: a new target must be this close
 * @param zoneWide        LockOnParam f3 x 0.01: screen-zone box factor (zones 1 and 2)
 * @param zoneNarrow      LockOnParam f4 x 0.01: screen-zone box factor (zone 1)
 * @param coneYaw         LockOnParam f5: max horizontal angle from the camera's forward
 * @param conePitch       LockOnParam f6: max vertical angle from the camera's forward
 * @param closeRange      LockOnParam f7: closer targets count as visible outside the cone
 * @param player          target values of a player (ChrCommonParam 100)
 * @param mob             target values of a Minecraft mob (most common EnemyCommonParam values, a choice)
 * @param camera          CameraExFollowParam row 0 fields the locked camera and the recenter use
 */
public record LockOnData(float listRadius, float acquireDistance, float zoneWide, float zoneNarrow, float coneYaw,
		float conePitch, float closeRange, Target player, Target mob, Camera camera) {
	public static final String RESOURCE = "/data/soulsmode/ds2/lockon.json";

	/** CommonParam +0x360 / +0xc4 / +0xc8 of the target. */
	public record Target(float lostSightTime, float exclusionRadius, float exclusionHeight) {
	}

	/**
	 * CameraExFollowParam: +0x78/+0x7c and +0x80/+0x84 pitch limits blended by |dy| between +0x88 and +0x8c,
	 * +0xb8 per-frame tracking factor reached over +0x118 s, +0xbc dead zone, +0xc0 aim offset (x FOV x 0.5),
	 * +0x100/+0x104/+0x108 recenter time range and ease power.
	 */
	public record Camera(float fov, float pitchMin, float pitchMax, float nearPitchMin, float nearPitchMax,
			float pitchBlendLow, float pitchBlendHigh, float trackRate, float deadZone, float aimOffset, float trackRamp,
			float recenterTimeMin, float recenterTimeMax, float recenterPower) {
	}

	public static LockOnData load() {
		try (InputStream in = LockOnData.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("missing " + RESOURCE);
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject l = json.getAsJsonObject("lockOn");
			JsonObject m = json.getAsJsonObject("lockTarget");
			JsonObject t = json.getAsJsonObject("targets");
			JsonObject c = json.getAsJsonObject("camera");
			return new LockOnData(f(l, "listRadius") * f(m, "listRadius"), f(l, "acquireDistance") * f(m, "acquireDistance"),
					f(l, "zoneWide"), f(l, "zoneNarrow"), f(l, "coneYaw"), f(l, "conePitch"), f(l, "closeRange"),
					target(t.getAsJsonObject("player")), target(t.getAsJsonObject("mob")),
					new Camera(f(c, "fov"), f(c, "pitchMin"), f(c, "pitchMax"), f(c, "nearPitchMin"), f(c, "nearPitchMax"),
							f(c, "pitchBlendLow"), f(c, "pitchBlendHigh"), f(c, "trackRate"), f(c, "deadZone"),
							f(c, "aimOffset"), f(c, "trackRamp"), f(c, "recenterTimeMin"), f(c, "recenterTimeMax"),
							f(c, "recenterPower")));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Target target(JsonObject o) {
		return new Target(f(o, "lostSightTime"), f(o, "exclusionRadius"), f(o, "exclusionHeight"));
	}

	private static float f(JsonObject o, String key) {
		return o.get(key).getAsFloat();
	}

	/** FUN_140382f40: (1000 - distance clamped to [0, 1000]) plus 3000 / 2000 / 1000 / 0 for screen zone 1 / 2 / 3 / 0. */
	public static float score(float distance, int zone) {
		float bonus = switch (zone) {
			case 1 -> 3000;
			case 2 -> 2000;
			case 3 -> 1000;
			default -> 0;
		};
		return 1000 - Math.max(0, Math.min(1000, distance)) + bonus;
	}

	/**
	 * Screen zone of a point in normalized device coordinates (FUN_140491cf0): 1 inside (zoneNarrow, zoneWide),
	 * 2 inside (1, zoneWide), 3 on screen, 0 off screen. Which factor is the horizontal one is not confirmed: here
	 * the first is x.
	 */
	public int zone(double ndcX, double ndcY) {
		double x = Math.abs(ndcX);
		double y = Math.abs(ndcY);
		if (x <= zoneNarrow && y <= zoneWide) {
			return 1;
		}
		if (x <= 1 && y <= zoneWide) {
			return 2;
		}
		return x <= 1 && y <= 1 ? 3 : 0;
	}

	/** Locked pitch limits (FUN_14049df40): the near limits blend to the full ones as |dy| goes from +0x88 to +0x8c. */
	public float[] pitchLimits(double dy) {
		float k = (float) Math.max(0, Math.min(1, (Math.abs(dy) - camera.pitchBlendLow())
				/ (camera.pitchBlendHigh() - camera.pitchBlendLow())));
		return new float[] {camera.nearPitchMin() + (camera.pitchMin() - camera.nearPitchMin()) * k,
				camera.nearPitchMax() + (camera.pitchMax() - camera.nearPitchMax()) * k};
	}
}
