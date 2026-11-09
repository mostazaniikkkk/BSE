package dev.soulsmode.client.qa;

import com.mojang.blaze3d.platform.InputConstants;
import dev.soulsmode.SoulsMode;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA only, {@code -Dsoulsmode.qa=stick} (tools/qa/stick_setup.js): a stick takes Unbreaking III from a book at the anvil,
 * then hits a zombie with the real attack key; the stick's damage is logged against the number of hits.
 */
final class StickQa {
	private static int phase;
	private static int tick = -1;
	private static int hits;
	private static int hurt;

	private StickQa() {
	}

	private static void log(String text, Object... args) {
		SoulsMode.LOGGER.info("[BSE QA] " + text, args);
	}

	static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null || mc.gameMode == null) {
			return;
		}
		if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
			mc.setScreen(null);
		}
		if (phase == 0 && tick < 0 && Boolean.getBoolean("soulsmode.qa.plain")) {
			phase = 1;
			log("NEED zombie");
			return;
		}
		if (phase == 0 && tick < 0) {
			Items.class.getName();
			net.minecraft.world.item.Item need = Boolean.getBoolean("soulsmode.qa.table") ? Items.LAPIS_LAZULI : Items.ENCHANTED_BOOK;
			{
				if (mc.player.getInventory().countItem(need) == 0) {
					if (mc.player.tickCount % 40 == 0) {
						log("NEED anvil");
					}
					return;
				}
			}
			tick = 0;
		}
		if (phase == 1 && tick < 0) {
			if (mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(5)).isEmpty()) {
				if (mc.player.tickCount % 40 == 0) {
					log("NEED zombie");
				}
				return;
			}
			tick = 0;
		}
		if (phase > 1) {
			return;
		}
		tick++;
		mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
		if (phase == 0) {
			mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
					new net.minecraft.world.phys.Vec3(0.5, 100.5, 2.5));
			switch (tick) {
				case 10 -> {
					log("anvil: aim {} block {}", mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult b0
							? b0.getBlockPos() : mc.hitResult, mc.level.getBlockState(new net.minecraft.core.BlockPos(0, 100, 2)));
					if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult b) {
						log("anvil: use {}", mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, b));
					}
				}
				case 20 -> move(mc, Items.STICK);
				case 25 -> move(mc, Boolean.getBoolean("soulsmode.qa.table") ? Items.LAPIS_LAZULI : Items.ENCHANTED_BOOK);
				case 33 -> {
					if (mc.player.containerMenu instanceof net.minecraft.world.inventory.EnchantmentMenu table) {
						log("table: costs {} clues {} levels {}", java.util.Arrays.toString(table.costs),
								java.util.Arrays.toString(table.enchantClue), java.util.Arrays.toString(table.levelClue));
						mc.gameMode.handleInventoryButtonClick(table.containerId, 0);
					}
				}
				case 35 -> {
					if (Boolean.getBoolean("soulsmode.qa.table")) {
						ItemStack enchanted = mc.player.containerMenu.getSlot(0).getItem();
						log("table: stick after enchanting {} enchantments {}", enchanted, enchanted.getEnchantments());
						log("done");
						phase = 2;
						mc.stop();
						return;
					}
					ItemStack result = mc.player.containerMenu.getSlot(2).getItem();
					log("anvil: screen {} left {} {} right {} result {} enchantments {}",
							mc.screen == null ? null : mc.screen.getClass().getSimpleName(), mc.player.containerMenu.getSlot(0).getItem(),
							mc.player.containerMenu.getSlot(0).getItem().getComponents(), mc.player.containerMenu.getSlot(1).getItem(),
							result, result.getEnchantments());
					mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 2, 0, ClickType.QUICK_MOVE,
							mc.player);
				}
				case 45 -> {
					mc.player.closeContainer();
					phase = 1;
					tick = -1;
					log("NEED zombie");
				}
				default -> {
				}
			}
			return;
		}
		if (tick == 1) {
			// The anvil's result went to the main inventory: swap it into the selected hotbar slot.
			var items = mc.player.getInventory().items;
			for (int i = 0; i < 36; i++) {
				if (!items.get(i).isEmpty()) {
					log("slot {} {} {}", i, items.get(i), items.get(i).getEnchantments());
				}
			}
			for (int i = 0; i < 9; i++) {
				if (items.get(i).is(Items.STICK)) {
					mc.player.getInventory().selected = i;
				}
			}
			for (int i = 9; i < 36; i++) {
				if (items.get(i).is(Items.STICK)) {
					mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i,
							mc.player.getInventory().selected, ClickType.SWAP, mc.player);
				}
			}
		}
		if (tick == 5) {
			log("hits: holding {} enchantments {}", mc.player.getMainHandItem(), mc.player.getMainHandItem().getEnchantments());
		}
		Zombie z = mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(5)).get(0);
		mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.position().add(0, 1, 0));
		if (z.hurtTime == 10) {
			hurt++;
		}
		// One light attack every 50 ticks (the stick's swing is under 2.5 s).
		if (tick % 50 == 10 && hits < 24) {
			hits++;
			InputConstants.Key k = InputConstants.getKey(mc.options.keyAttack.saveString());
			KeyMapping.click(k);
		}
		if (tick == 50 * 24 + 40) {
			ItemStack stick = mc.player.getMainHandItem();
			log("hits: {} attacks, zombie hurt {} times (health {}), stick {} damage {} / {} enchantments {}", hits, hurt,
					z.getHealth(), stick, stick.getDamageValue(), stick.getMaxDamage(), stick.getEnchantments());
			log("done");
			phase = 2;
			mc.stop();
		}
	}

	private static void move(Minecraft mc, net.minecraft.world.item.Item item) {
		var menu = mc.player.containerMenu;
		for (int i = 0; i < menu.slots.size(); i++) {
			if (menu.slots.get(i).container == mc.player.getInventory() && menu.slots.get(i).getItem().is(item)) {
				mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, mc.player);
				return;
			}
		}
	}
}
