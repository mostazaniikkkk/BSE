package dev.soulsmode.client.qa;

import com.mojang.blaze3d.platform.InputConstants;
import dev.soulsmode.SoulsMode;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * QA (tools/qa/animate_setup.js): the server forces clips on this player with /souls animate; this holds the forward
 * key all the time and logs the position (it must not move while held) and takes screenshots.
 */
public final class AnimateQa {
	private static int tick = -1;

	private AnimateQa() {
	}

	private static void log(String text, Object... args) {
		SoulsMode.LOGGER.info("[BSE QA] " + text, args);
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
			mc.setScreen(null);
		}
		if (mc.player.isDeadOrDying()) {
			if (tick < 0) {
				mc.player.respawn();
				return;
			}
			if (tick % 20 == 0) {
				log("t={} dead", tick);
			}
			tick++;
			if (tick > 400) {
				mc.stop();
			}
			return;
		}
		tick++;
		mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
		KeyMapping.set(InputConstants.getKey(mc.options.keyUp.saveString()), tick > 60);
		if (tick == 20) {
			log("READY");
		}
		if (tick % 10 == 0) {
			log("t={} pos={} {} {}", tick, String.format("%.2f", mc.player.getX()), String.format("%.2f", mc.player.getY()),
					String.format("%.2f", mc.player.getZ()));
		}
		if (tick == 100 || tick == 170) {
			String name = "qa_animate_t" + tick + ".png";
			net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), m -> log("screenshot {}", name));
		}
		if (tick > 600) {
			mc.stop();
		}
	}
}
