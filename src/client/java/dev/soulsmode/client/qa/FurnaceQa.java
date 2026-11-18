package dev.soulsmode.client.qa;

import dev.soulsmode.SoulsMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (tools/qa/furnace_setup.js): smelts 2 raw iron, then takes the ingots out of a furnace as a Souls Mode player (half with a right click to the
 * cursor, the rest with shift-click), logs the cursor / inventory / health and takes screenshots.
 */
public final class FurnaceQa {
	private static final BlockPos FURNACE = new BlockPos(0, 200, 2);
	private static int tick = -1;

	private FurnaceQa() {
	}

	private static void log(String text, Object... args) {
		SoulsMode.LOGGER.info("[BSE QA] " + text, args);
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
		if (mc.player.isDeadOrDying()) {
			mc.player.respawn();
			return;
		}
		if (tick < 0 && !mc.level.getBlockState(FURNACE).is(net.minecraft.world.level.block.Blocks.FURNACE)) {
			if (mc.player.tickCount % 40 == 0) {
				log("NEED furnace");
			}
			return;
		}
		tick++;
		var menu = mc.player.containerMenu;
		if (tick % 20 == 0) {
			log("t={} xp={} carried={} hot={} raw={} iron={} health={}", tick, mc.player.totalExperience, menu.getCarried(),
					mc.player.getInventory().countItem(dev.soulsmode.item.SoulsItems.INCANDESCENT_IRON_INGOT),
					mc.player.getInventory().countItem(net.minecraft.world.item.Items.RAW_IRON),
					mc.player.getInventory().countItem(net.minecraft.world.item.Items.IRON_INGOT), mc.player.getHealth());
		}
		switch (tick) {
			case 40 -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(FURNACE), Direction.NORTH, FURNACE, false));
			case 460 -> {
				log("menu {}", menu.getClass().getSimpleName());
				if (menu instanceof FurnaceMenu) {
					mc.gameMode.handleInventoryMouseClick(menu.containerId, 2, 1, ClickType.PICKUP, mc.player);
				}
			}
			case 470 -> shot(mc, "qa_furnace_cursor_hot.png");
			case 575 -> shot(mc, "qa_furnace_cursor_cooled.png");
			case 580 -> mc.gameMode.handleInventoryMouseClick(menu.containerId, 2, 0, ClickType.QUICK_MOVE, mc.player);
			case 600 -> shot(mc, "qa_furnace_inventory_hot.png");
			case 700 -> mc.player.closeContainer();
			// Souls Mode recipe (tools/qa/furnace_setup.js gives dirt and a stick; bseqa:souls_diamond): 2x2 grid.
			case 720 -> mc.gameMode.handleInventoryMouseClick(0, 36, 0, ClickType.PICKUP, mc.player);
			case 725 -> mc.gameMode.handleInventoryMouseClick(0, 1, 0, ClickType.PICKUP, mc.player);
			case 730 -> mc.gameMode.handleInventoryMouseClick(0, 37, 0, ClickType.PICKUP, mc.player);
			case 735 -> mc.gameMode.handleInventoryMouseClick(0, 2, 0, ClickType.PICKUP, mc.player);
			case 750 -> log("CRAFT souls result={}", mc.player.inventoryMenu.getSlot(0).getItem());
			// The setup script takes the player out of Souls Mode on CRAFT; then the grid is changed again.
			case 800 -> mc.gameMode.handleInventoryMouseClick(0, 2, 0, ClickType.PICKUP, mc.player);
			case 805 -> mc.gameMode.handleInventoryMouseClick(0, 2, 0, ClickType.PICKUP, mc.player);
			case 820 -> log("NOCRAFT outside result={}", mc.player.inventoryMenu.getSlot(0).getItem());
			case 830 -> log("done");
			case 900 -> mc.stop();
			default -> {
			}
		}
	}
}
