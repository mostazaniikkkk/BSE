package dev.soulsmode.client;

import dev.soulsmode.client.camera.SoulsCamera;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * The forward push of the movement input that the jump attack and the kick need (FUN_140396e60, every frame, with
 * PlayerCommonParam row 0; docs/research/weapons.md, "Tipo 9"):
 * <ol>
 * <li>the input stays at exactly 0 for at least +0x74 = 0.1 s;</li>
 * <li>then it goes from at most 0.1 to at least +0x70 = 0.8 in under +0x6c = 0.1 s;</li>
 * <li>pointing (in camera space) within +0x78 = 45 degrees of where the character faces.</li>
 * </ol>
 * The flag then stays up for +0x68 = 0.05 s. The keyboard gives magnitudes 0 or 1, so the push is the moment movement
 * keys go down after 0.1 s without any.
 */
public final class FlickInput {
	private static final double ARM_TIME = 0.1;
	private static final double RISE_TIME = 0.1;
	private static final double FLAG_TIME = 0.05;
	private static final double MAX_ANGLE = 45;

	private static double zeroSince = Double.NaN;
	/** When the input last left zero with the arm time met (NaN: not armed). */
	private static double armedRiseAt = Double.NaN;
	private static double flagAt = Double.NaN;

	private FlickInput() {
	}

	public static void init() {
		WorldRenderEvents.START.register(context -> sample());
	}

	/** Whether the push flag is up now. */
	public static boolean active() {
		sample();
		return !Double.isNaN(flagAt) && now() - flagAt <= FLAG_TIME;
	}

	private static void sample() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		double t = now();
		// The keys themselves: the player's input is only refreshed once per tick.
		var o = mc.options;
		float forward = mc.screen != null ? 0 : axis(o.keyUp.isDown(), o.keyDown.isDown());
		float left = mc.screen != null ? 0 : axis(o.keyLeft.isDown(), o.keyRight.isDown());
		if (forward == 0 && left == 0) {
			if (Double.isNaN(zeroSince)) {
				zeroSince = t;
			}
			armedRiseAt = Double.NaN;
			return;
		}
		if (!Double.isNaN(zeroSince)) {
			armedRiseAt = t - zeroSince >= ARM_TIME ? t : Double.NaN;
			zeroSince = Double.NaN;
			// Keys are all-or-nothing: the magnitude reaches 1 in this sample, well under RISE_TIME.
			if (!Double.isNaN(armedRiseAt) && t - armedRiseAt < RISE_TIME && facing(player, forward, left)) {
				flagAt = t;
			}
		}
	}

	/** The input direction is within 45 degrees of the body's facing. */
	private static boolean facing(LocalPlayer player, float forward, float left) {
		float inputAngle = (float) Math.toDegrees(Math.atan2(-left, forward));
		float base = SoulsCamera.isActive() ? SoulsCamera.yaw() : player.getYRot();
		return Math.abs(Mth.wrapDegrees(base + inputAngle - player.getYRot())) <= MAX_ANGLE;
	}

	private static float axis(boolean positive, boolean negative) {
		return (positive ? 1 : 0) - (negative ? 1 : 0);
	}

	private static double now() {
		return System.nanoTime() / 1e9;
	}
}
