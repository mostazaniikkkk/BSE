package dev.soulsmode.client.camera;

import dev.soulsmode.SoulsAttack;
import dev.soulsmode.api.SoulsWeapons;
import dev.soulsmode.client.StaminaHud;
import dev.soulsmode.client.anim.GripClient;
import dev.soulsmode.ds2.Bow;
import dev.soulsmode.network.BowAimPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The bow's aim mode (docs/research/bow.md "Modo apuntar"): with the bow two-handed, holding the other hand's button
 * (here the use key, or the left light key) switches to SubjectiveCameraOperator with CameraSubjectiveParam 10: an eye
 * 1.5 m above the feet and 0.4 m forward, a 25 deg field of view, pitch -45 .. +60 deg. It starts from the follow
 * camera's direction and, on release, the follow camera takes the aim's yaw. The body faces the aim.
 * The mouse uses DS2's PC formula ({@link #turn}); whether DS2 lets the player walk while aiming is not confirmed: here
 * it can.
 */
public final class BowAim {
	private static boolean active;
	/** The weapon aiming (its CameraSubjectiveParam row). */
	private static Bow weapon = Bow.BOW;
	private static float yaw;
	private static float pitch;

	private BowAim() {
	}

	public static boolean active() {
		return active;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	/** The bow or crossbow in a hand, or null. */
	public static Bow ranged(Player player, boolean left) {
		return Bow.of(SoulsAttack.damageKey(SoulsWeapons.movesetFor(left ? player.getOffhandItem()
				: player.getMainHandItem())));
	}

	/** Which hand holds a ranged weapon: 0 none, 1 right, 2 left. */
	public static int bowHand(Player player) {
		return ranged(player, false) != null ? 1 : ranged(player, true) != null ? 2 : 0;
	}

	/** A ranged weapon held with both hands (DS2's grip 2 / 3 with its hand), or null. */
	public static Bow twoHandedRanged(Player player) {
		var grip = GripClient.grip(player);
		return grip.twoHanded() ? ranged(player, grip.leftWeapon()) : null;
	}

	public static boolean bowTwoHanded(Player player) {
		return twoHandedRanged(player) != null;
	}

	/** Each client tick: aim while the other hand's button is held. */
	public static void tick(Minecraft mc, boolean otherButtonDown) {
		LocalPlayer player = mc.player;
		boolean want = player != null && StaminaHud.soulsMode() && mc.screen == null && player.isAlive()
				&& bowTwoHanded(player) && otherButtonDown;
		if (want == active) {
			return;
		}
		active = want;
		countsX = countsY = 0;
		smoothX = smoothY = 0;
		lastFrame = 0;
		if (active) {
			weapon = twoHandedRanged(player);
			yaw = SoulsCamera.yaw();
			pitch = clampPitch(SoulsCamera.pitch());
		} else if (player != null) {
			// The follow camera takes over the aim's direction; the body already faces it.
			SoulsCamera.setYaw(yaw);
			SoulsCamera.setPitch(pitch);
		}
		if (mc.getConnection() != null) {
			ClientPlayNetworking.send(new BowAimPayload(active));
		}
	}

	private static float clampPitch(float value) {
		return Mth.clamp(value, (float) Math.toDegrees(weapon.aimPitchMin()), (float) Math.toDegrees(weapon.aimPitchMax()));
	}

	public static Bow weapon() {
		return weapon;
	}

	/** Mouse counts not consumed by a DS2 frame yet, the smoothed velocity per axis and the last DS2 frame. */
	private static double countsX;
	private static double countsY;
	private static float smoothX;
	private static float smoothY;
	private static long lastFrame;

	/**
	 * Mouse input while aiming, DS2's formula (bow.md s.2.2; FUN_1404a4050 / FUN_1404a4540) on Minecraft's raw cursor
	 * counts (Entity.turn's argument without Minecraft's sensitivity curve): once per DS2 frame (60 fps), any counts ->
	 * target = counts x 0.01745, else 0; v += (target - v) x 0.5 per axis; yaw / pitch += v x (0.1 + 0.019 x sens) x
	 * 0.24 radians, sens = DS2's default 50. Minecraft's own sensitivity slider is not used.
	 */
	public static void turn(double dx, double dy) {
		double curve = Math.pow(Minecraft.getInstance().options.sensitivity().get() * 0.6 + 0.2, 3) * 8;
		if (curve > 0) {
			countsX += dx / curve;
			countsY += dy / curve;
		}
		long now = System.nanoTime();
		long frame = (long) (1e9 / weapon.aimMouse("frameRate"));
		if (lastFrame == 0 || now - lastFrame > 10 * frame) {
			lastFrame = now - frame;
		}
		float dead = weapon.aimMouse("deadZone");
		float perUnit = weapon.aimMouse("radPerUnit");
		float smoothing = weapon.aimMouse("smoothing");
		float gain = (weapon.aimMouse("sensitivityBase") + weapon.aimMouse("sensitivityPerStep")
				* weapon.aimMouse("defaultSensitivity")) * weapon.aimMouse("scale");
		while (now - lastFrame >= frame) {
			lastFrame += frame;
			boolean moved = Math.abs(countsX) > dead || Math.abs(countsY) > dead;
			float targetX = moved ? (float) countsX * perUnit : 0;
			float targetY = moved ? (float) countsY * perUnit : 0;
			countsX = 0;
			countsY = 0;
			smoothX += (targetX - smoothX) * smoothing;
			smoothY += (targetY - smoothY) * smoothing;
			yaw += (float) Math.toDegrees(smoothX * gain);
			pitch = clampPitch(pitch + (float) Math.toDegrees(smoothY * gain));
		}
	}

	/** After the input tick: the body faces the aim. */
	public static void afterInput(LocalPlayer player) {
		// The DS2 frames since the last mouse event (the smoothing keeps turning after the mouse stops).
		turn(0, 0);
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.yHeadRot = yaw;
		player.yBodyRot = yaw;
	}
}
