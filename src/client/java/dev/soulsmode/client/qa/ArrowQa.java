package dev.soulsmode.client.qa;

import com.mojang.blaze3d.platform.InputConstants;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.network.AttackPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Items;

/** QA (tools/qa/arrow_setup.js): fires the bow two-handed at a wall and takes screenshots of the arrow. */
public final class ArrowQa {
	private static int tick = -1;

	private ArrowQa() {
	}

	private static void log(String text, Object... args) {
		SoulsMode.LOGGER.info("[BSE QA] " + text, args);
	}

	private static void key(KeyMapping key, boolean down) {
		InputConstants.Key k = InputConstants.getKey(key.saveString());
		if (down) {
			KeyMapping.click(k);
		}
		KeyMapping.set(k, down);
	}

	private static void shot(Minecraft mc, String name) {
		net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), m -> log("screenshot {}", name));
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
			mc.setScreen(null);
		}
		if (tick < 0 && !mc.player.getMainHandItem().is(Items.BOW)) {
			if (mc.player.tickCount % 40 == 0) {
				log("NEED bow");
			}
			return;
		}
		mc.options.hideGui = tick < 240;
		mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		tick++;
		int arrows = 0;
		for (var e : mc.level.entitiesForRendering()) {
			if (e instanceof AbstractArrow) {
				arrows++;
			}
		}
		if (tick % 10 == 0) {
			log("t={} arrow entities={} arrows in inventory={}", tick, arrows, mc.player.getInventory().countItem(Items.ARROW));
		}
		switch (tick) {
			case 40 -> ClientPlayNetworking.send(new AttackPayloads.Grip(false));
			case 80 -> key(mc.options.keyAttack, true);
			case 115 -> key(mc.options.keyAttack, false);
			case 101, 103, 105, 107, 109, 111, 113, 117, 119, 121 -> shot(mc, "qa_arrow_t" + tick + ".png");
			case 200 -> shot(mc, "qa_arrow_stuck.png");
			case 230 -> key(mc.options.keyUse, true);
			case 260 -> shot(mc, "qa_bow_sights.png");
			case 270 -> key(mc.options.keyUse, false);
			case 280 -> log("done");
			case 300 -> mc.stop();
			default -> {
			}
		}
		if (tick > 230 && tick < 270) {
			KeyMapping.set(InputConstants.getKey(mc.options.keyUse.saveString()), true);
		}
		if (tick > 80 && tick < 115) {
			KeyMapping.set(InputConstants.getKey(mc.options.keyAttack.saveString()), true);
		}
	}
}
