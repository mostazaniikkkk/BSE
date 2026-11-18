package dev.soulsmode.client.qa;

import com.mojang.blaze3d.platform.InputConstants;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.client.anim.SwingClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * QA (tools/qa/water_setup.js): a light attack on land, then one standing in shallow water; logs how long each swing
 * runs (ticks) and its clip rate, and a screenshot mid-swing in the water.
 */
public final class WaterQa {
	private static int tick = -1;
	private static int swingStart = -1;

	private WaterQa() {
	}

	private static void log(String text, Object... args) {
		SoulsMode.LOGGER.info("[BSE QA] " + text, args);
	}

	private static void attack(Minecraft mc, boolean down) {
		InputConstants.Key k = InputConstants.getKey(mc.options.keyAttack.saveString());
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
		if (tick < 0 && (mc.player.tickCount < 80 || !mc.player.getMainHandItem().is(net.minecraft.world.item.Items.IRON_SWORD))) {
			if (mc.player.tickCount % 20 == 0) {
				log("NEED sword");
			}
			return;
		}
		tick++;
		mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
		int id = mc.player.getId();
		boolean swinging = SwingClient.swinging(id);
		if (swinging && swingStart < 0) {
			swingStart = tick;
		}
		if (!swinging && swingStart >= 0) {
			log("swing ended after {} ticks (inWater={} clipRate seen={})", tick - swingStart, mc.player.isInWater(), last);
			swingStart = -1;
		}
		if (swinging) {
			last = SwingClient.clipRate(id);
			String anim = SwingClient.animation(id);
			if (!anim.equals(lastAnim)) {
				log("t={} swing {}", tick, anim);
				lastAnim = anim;
			}
		} else {
			lastAnim = "";
		}
		switch (tick) {
			case 40 -> log("land: inWater={}", mc.player.isInWater());
			case 41 -> attack(mc, true);
			case 43 -> attack(mc, false);
			case 140 -> log("WATER please");
			case 200 -> log("water: inWater={} underWater={}", mc.player.isInWater(), mc.player.isUnderWater());
			case 201 -> attack(mc, true);
			case 203 -> attack(mc, false);
			case 215 -> net.minecraft.client.Screenshot.grab(mc.gameDirectory, "qa_water_swing.png", mc.getMainRenderTarget(),
					m -> log("screenshot"));
			case 320 -> {
				log("done");
				mc.stop();
			}
			default -> {
			}
		}
	}

	private static float last = 1;
	private static String lastAnim = "";
}
