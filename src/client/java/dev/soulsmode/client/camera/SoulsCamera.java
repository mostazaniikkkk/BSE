package dev.soulsmode.client.camera;

import dev.soulsmode.client.AttackClient;
import dev.soulsmode.client.StaminaHud;
import dev.soulsmode.ds2.Movement;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * DS2-style third-person camera for Souls Mode (see docs/research/camera.md): the mouse orbits the camera
 * around the player independently of the body, and the body turns towards where the player moves.
 * Values come from CameraExFollowParam row 0 unless marked otherwise.
 */
public final class SoulsCamera {
	/** Game-feel switch. */
	public static final boolean ENABLED = true;
	/** +0x0c..+0x14: pivot 1.42 m above the feet. */
	public static final double PIVOT_HEIGHT = 1.42;
	/** +0x18: DS2's camera arm length. */
	public static final double DS2_DISTANCE = 3.6;
	/** Arm length used: farther than DS2's, at the user's request (2026-10-30). */
	public static final double DISTANCE = 4.5;
	/**
	 * +0x78 / +0x7c: pitch range -40 deg .. +70 deg. Which sign is "camera above" is not confirmed yet;
	 * here +70 is looking down from above (Minecraft pitch, positive = down).
	 */
	public static final float PITCH_MIN = -40f;
	public static final float PITCH_MAX = 70f;
	/** +0x04: vertical FOV 0.768 rad. Set USE_DS2_FOV to false to keep the player's own FOV setting. */
	public static final boolean USE_DS2_FOV = true;
	public static final double FOV_DEGREES = Math.toDegrees(0.7679);

	private static float yaw;
	/** Last movement direction in world space (x, z), before the input is rewritten; zero when not moving. */
	private static double moveX;
	private static double moveZ;
	private static float pitch;
	private static boolean wasActive;

	private SoulsCamera() {
	}

	public static boolean isActive() {
		Minecraft mc = Minecraft.getInstance();
		return ENABLED && mc.player != null && StaminaHud.soulsMode()
				&& mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	/** World-space movement direction the player is pressing (camera-relative), or null without input. */
	public static double[] moveDirection() {
		return moveX == 0 && moveZ == 0 ? null : new double[] {moveX, moveZ};
	}

	public static void setYaw(float value) {
		yaw = value;
	}

	public static void setPitch(float value) {
		pitch = Mth.clamp(value, PITCH_MIN, PITCH_MAX);
	}

	/** Mouse input: rotates the camera only (same scaling as Entity.turn). */
	public static void turn(double dx, double dy) {
		yaw += (float) dx * 0.15f;
		pitch = Mth.clamp(pitch + (float) dy * 0.15f, PITCH_MIN, PITCH_MAX);
	}

	/** Angle of the input relative to the camera, in Minecraft yaw degrees. */
	private static float inputAngleOf(float forward, float left) {
		return (float) Math.toDegrees(Math.atan2(-left, forward));
	}

	/**
	 * Runs after the vanilla input tick: turns movement keys into a world direction relative to the camera,
	 * swings the body towards it and makes the player walk forward along its facing.
	 */
	public static void afterInput(LocalPlayer player) {
		boolean active = isActive();
		if (active && !wasActive) {
			yaw = player.getYRot();
			pitch = Mth.clamp(player.getXRot(), PITCH_MIN, PITCH_MAX);
		}
		wasActive = active;
		moveX = 0;
		moveZ = 0;
		if (BowAim.active()) {
			// Aiming the bow: the body faces the aim and the keys move relative to it. In any camera: BowAim takes the
			// mouse in first person too, so without this the view and the shot stayed frozen there.
			BowAim.afterInput(player);
			return;
		}
		if (!active) {
			return;
		}
		float forward = player.input.forwardImpulse;
		float left = player.input.leftImpulse;
		float magnitude = Math.min(1f, (float) Math.sqrt(forward * forward + left * left));
		if (magnitude > 1e-3f) {
			double rad = Math.toRadians(yaw + inputAngleOf(forward, left));
			moveX = -Math.sin(rad);
			moveZ = Math.cos(rad);
		}
		if (LockOn.afterInput(player, moveX, moveZ)) {
			player.setXRot(pitch);
			return;
		}
		if (magnitude > 1e-3f) {
			if (AttackClient.movementLocked()) {
				// The direction is still recorded for a dodge out of the attack, but the body stays put.
				player.setXRot(pitch);
				return;
			}
			float delta = Mth.wrapDegrees(yaw + inputAngleOf(forward, left) - player.getYRot());
			// DS2 body turn rate (Movement.turnRate, rad/s) applied over one 0.05 s tick.
			var motion = player.getDeltaMovement();
			float speed = (float) Math.sqrt(motion.x * motion.x + motion.z * motion.z) * 20f;
			float maxStep = (float) Math.toDegrees(Movement.turnRate(speed, player.isSprinting()))
					* dev.soulsmode.client.InventoryStats.turnFactor(player.isSprinting(), false) * 0.05f;
			float step = Mth.clamp(delta, -maxStep, maxStep);
			player.setYRot(player.getYRot() + step);
			player.yHeadRot = player.getYRot();
			player.yBodyRot = player.getYRot();
			player.input.forwardImpulse = magnitude;
			player.input.leftImpulse = 0;
		}
		player.setXRot(pitch);
	}
}
