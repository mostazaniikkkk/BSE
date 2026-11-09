package dev.soulsmode.client.qa;

import com.mojang.blaze3d.platform.InputConstants;
import dev.soulsmode.SoulsMode;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.monster.Zombie;

/**
 * QA only, {@code -Dsoulsmode.qa=hits} (tools/qa/hits_setup.js): swings the held weapon at a zombie with the real attack
 * key, one light attack every 60 ticks, {@code soulsmode.qa.swings} times (default 30). The server logs the effects
 * (run it with -Dsoulsmode.qa.log=true): bleed build-up and bursts (SoulsBleed), stick wear (SoulsStick).
 */
final class HitsQa {
	private static int tick = -1;
	private static int swings;

	private HitsQa() {
	}

	static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
			mc.setScreen(null);
		}
		// The setup's target only (named QaTarget): night zombies may spawn around.
		var zombies = mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(4),
				z -> z.hasCustomName() && "QaTarget".equals(z.getCustomName().getString()));
		if (tick < 0) {
			if (zombies.isEmpty()) {
				if (mc.player.tickCount % 40 == 0) {
					SoulsMode.LOGGER.info("[BSE QA] NEED zombie");
				}
				return;
			}
			tick = 0;
		}
		tick++;
		mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
		if (!zombies.isEmpty()) {
			mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
					zombies.get(0).position().add(0, 1.2, 0));
		}
		InputConstants.Key key = InputConstants.getKey(mc.options.keyAttack.saveString());
		int total = Integer.getInteger("soulsmode.qa.swings", 30);
		if (tick % 60 == 20 && swings < total) {
			swings++;
			KeyMapping.click(key);
			KeyMapping.set(key, true);
		}
		if (tick % 60 == 22) {
			KeyMapping.set(key, false);
		}
		if (tick == 60 * total + 80) {
			SoulsMode.LOGGER.info("[BSE QA] hits: {} swings, held {} damage {} / {}", swings, mc.player.getMainHandItem(),
					mc.player.getMainHandItem().getDamageValue(), mc.player.getMainHandItem().getMaxDamage());
			SoulsMode.LOGGER.info("[BSE QA] done");
			mc.stop();
		}
	}
}
