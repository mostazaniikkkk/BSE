package dev.soulsmode.ds2;

/**
 * Player body turn rate, ported from PlayerMovementCtrl::vfunc+0x68 (FUN_140387f40) with ChrMoveParam row 100.
 * The base movement update (FUN_140335fd0) turns the facing towards the wanted direction by this rate (rad/s).
 * See docs/research/movement.md.
 */
public final class Movement {
	/** Normal movement: ChrMoveParam +0x2c..+0x30 rad/s, lerped by speed between +0x34 and +0x38 m/s. */
	public static final float TURN_SLOW = 12.566f;
	public static final float TURN_FAST = 18.850f;
	public static final float TURN_SPEED_LOW = 1.5f;
	public static final float TURN_SPEED_HIGH = 4.0f;
	/** Mode 3 (probably dash: its speed band is the only one above jog): +0x54..+0x58 by +0x5c..+0x60. */
	public static final float DASH_TURN_SLOW = 9.076f;
	public static final float DASH_TURN_FAST = 11.170f;
	public static final float DASH_SPEED_LOW = 5.35f;
	public static final float DASH_SPEED_HIGH = 6.5f;

	private Movement() {
	}

	/** Turn rate in rad/s for the current ground speed (m/s). */
	public static float turnRate(float speed, boolean dashing) {
		return dashing
				? lerpBand(speed, DASH_SPEED_LOW, DASH_SPEED_HIGH, DASH_TURN_SLOW, DASH_TURN_FAST)
				: lerpBand(speed, TURN_SPEED_LOW, TURN_SPEED_HIGH, TURN_SLOW, TURN_FAST);
	}

	/** Same clamp-and-lerp the game uses: below low gives a, above high gives b. */
	private static float lerpBand(float x, float low, float high, float a, float b) {
		float t = x <= low ? 0f : (x < high ? (x - low) / (high - low) : 1f);
		return (b - a) * t + a;
	}
}
