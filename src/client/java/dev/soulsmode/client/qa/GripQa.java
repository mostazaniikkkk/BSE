package dev.soulsmode.client.qa;

import com.mojang.blaze3d.platform.InputConstants;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.client.anim.GripClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * QA (tools/qa/grip_setup.js): two stone swords; shift + the grip key (power stance), logs the grip, a screenshot,
 * then the grip key again (back to one hand).
 */
public final class GripQa {
	private static int tick = -1;

	private GripQa() {
	}

	private static void log(String text, Object... args) {
		SoulsMode.LOGGER.info("[BSE QA] " + text, args);
	}

	private static KeyMapping gripKey(Minecraft mc) {
		for (KeyMapping k : mc.options.keyMappings) {
			if (k.getName().equals("key.soulsmode.two_hand")) {
				return k;
			}
		}
		throw new IllegalStateException("no grip key");
	}

	private static void press(KeyMapping key, boolean down) {
		InputConstants.Key k = InputConstants.getKey(key.saveString());
		if (down) {
			KeyMapping.click(k);
		}
		KeyMapping.set(k, down);
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
			mc.setScreen(null);
		}
		if (mc.player.isDeadOrDying()) {
			mc.player.respawn();
			return;
		}
		if (tick < 0 && (mc.player.tickCount < 80
				|| !mc.player.getOffhandItem().is(net.minecraft.world.item.Items.STONE_SWORD))) {
			if (mc.player.tickCount % 20 == 0) {
				log("NEED swords");
			}
			return;
		}
		tick++;
		mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
		if (tick % 10 == 0) {
			log("t={} grip={} shift={}", tick, GripClient.grip(mc.player), mc.player.isShiftKeyDown());
		}
		switch (tick) {
			case 20 -> KeyMapping.set(InputConstants.getKey(mc.options.keyShift.saveString()), true);
			case 30 -> press(gripKey(mc), true);
			case 32 -> press(gripKey(mc), false);
			case 40 -> KeyMapping.set(InputConstants.getKey(mc.options.keyShift.saveString()), false);
			case 70 -> net.minecraft.client.Screenshot.grab(mc.gameDirectory, "qa_power_stance.png",
					mc.getMainRenderTarget(), m -> log("screenshot"));
			case 100 -> press(gripKey(mc), true);
			case 102 -> press(gripKey(mc), false);
			case 140 -> {
				log("done");
				mc.stop();
			}
			default -> {
			}
		}
	}
}
