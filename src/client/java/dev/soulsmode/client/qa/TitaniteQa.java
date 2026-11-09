package dev.soulsmode.client.qa;

import dev.soulsmode.SoulsMode;
import dev.soulsmode.SoulsUpgrade;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA only, {@code -Dsoulsmode.qa=titanite} (tools/qa/titanite_setup.js answers the NEED lines through RCON): the
 * titanite reinforcement at the smithing table, the anvil's highest-level rule, an infusion's attack rating and the
 * smiths' trades, driven through the real menus.
 */
final class TitaniteQa {
	private static int phase;
	private static int tick = -1;

	private TitaniteQa() {
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
		String[] needs = {"smithing", "anvil", "infusion", "trades"};
		if (phase >= needs.length) {
			return;
		}
		if (tick < 0) {
			if (!ready(mc, phase)) {
				if (mc.player.tickCount % 40 == 0) {
					log("NEED {}", needs[phase]);
				}
				return;
			}
			tick = 0;
		}
		tick++;
		mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
		switch (phase) {
			case 0 -> smithing(mc);
			case 1 -> anvil(mc);
			case 2 -> infusion(mc);
			case 3 -> trades(mc);
			default -> {
			}
		}
	}

	private static boolean ready(Minecraft mc, int p) {
		return switch (p) {
			case 0 -> mc.player.getInventory().countItem(dev.soulsmode.item.SoulsItems.TITANITE_SHARD) == 5;
			case 1 -> count(mc, Items.IRON_SWORD) == 2;
			case 2 -> SoulsUpgrade.infusion(mc.player.getMainHandItem()) > 0;
			case 3 -> !mc.level.getEntitiesOfClass(AbstractVillager.class, mc.player.getBoundingBox().inflate(6)).isEmpty();
			default -> false;
		};
	}

	private static int count(Minecraft mc, Item item) {
		return mc.player.getInventory().countItem(item);
	}

	private static void look(Minecraft mc, double x, double y, double z) {
		mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
				new net.minecraft.world.phys.Vec3(x, y, z));
	}

	/** Shift-clicks the first inventory slot of the open menu holding {@code item}. */
	private static void quickMove(Minecraft mc, Item item) {
		AbstractContainerMenu menu = mc.player.containerMenu;
		for (int i = 0; i < menu.slots.size(); i++) {
			var slot = menu.slots.get(i);
			if (slot.container == mc.player.getInventory() && slot.getItem().is(item)) {
				mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, mc.player);
				return;
			}
		}
		log("no {} to move", item);
	}

	private static void use(Minecraft mc) {
		var hit = mc.hitResult;
		if (hit instanceof net.minecraft.world.phys.BlockHitResult block) {
			mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, block);
		}
	}

	private static void smithing(Minecraft mc) {
		look(mc, 0.5, 100.5, 2.5);
		switch (tick) {
			case 10 -> {
				log("smithing: before xp {} shards {} sword level {}", mc.player.experienceLevel,
						count(mc, dev.soulsmode.item.SoulsItems.TITANITE_SHARD), SoulsUpgrade.level(find(mc, Items.WOODEN_SWORD)));
				use(mc);
			}
			case 20 -> {
				log("smithing: screen {}", mc.screen == null ? null : mc.screen.getClass().getSimpleName());
				quickMove(mc, Items.WOODEN_SWORD);
			}
			case 25 -> quickMove(mc, dev.soulsmode.item.SoulsItems.TITANITE_SHARD);
			case 35 -> {
				ItemStack result = mc.player.containerMenu.getSlot(3).getItem();
				log("smithing: result {} level {}", result, SoulsUpgrade.level(result));
				net.minecraft.client.Screenshot.grab(mc.gameDirectory, "qa_smithing.png", mc.getMainRenderTarget(),
						message -> log("screenshot smithing"));
			}
			case 40 -> mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 3, 0, ClickType.QUICK_MOVE,
					mc.player);
			case 50 -> mc.player.closeContainer();
			case 70 -> {
				ItemStack sword = find(mc, Items.WOODEN_SWORD);
				log("smithing: after xp {} shards {} sword level {} ds2 level {} durability {} (base {})",
						mc.player.experienceLevel, count(mc, dev.soulsmode.item.SoulsItems.TITANITE_SHARD), SoulsUpgrade.level(sword),
						SoulsUpgrade.ds2Level(sword), sword.getMaxDamage(), Items.WOODEN_SWORD.getDefaultInstance().getMaxDamage());
				next();
			}
			default -> {
			}
		}
	}

	private static void anvil(Minecraft mc) {
		look(mc, 0.5, 100.5, 2.5);
		switch (tick) {
			case 10 -> use(mc);
			case 20 -> {
				log("anvil: screen {}", mc.screen == null ? null : mc.screen.getClass().getSimpleName());
				quickMove(mc, Items.IRON_SWORD);
			}
			case 25 -> quickMove(mc, Items.IRON_SWORD);
			case 35 -> {
				var menu = mc.player.containerMenu;
				log("anvil: left level {} right level {} result level {} result durability {}/{}",
						SoulsUpgrade.level(menu.getSlot(0).getItem()), SoulsUpgrade.level(menu.getSlot(1).getItem()),
						SoulsUpgrade.level(menu.getSlot(2).getItem()), menu.getSlot(2).getItem().getDamageValue(),
						menu.getSlot(2).getItem().getMaxDamage());
				mc.player.closeContainer();
				next();
			}
			default -> {
			}
		}
	}

	private static void infusion(Minecraft mc) {
		if (tick == 5) {
			ItemStack held = mc.player.getMainHandItem();
			var stats = new dev.soulsmode.ds2.Attributes(filled(20));
			String key = SoulsUpgrade.weaponKey(held);
			float[] plain = dev.soulsmode.ds2.Ds2Damage.attackRating(key, 0, 0, stats, false, false, -1, 0, null, null);
			float[] infused = dev.soulsmode.ds2.Ds2Damage.attackRating(key, 0, SoulsUpgrade.infusion(held), stats, false,
					false, -1, 0, null, null);
			log("infusion: {} index {} plain {} infused {}", key, SoulsUpgrade.infusion(held),
					java.util.Arrays.toString(plain), java.util.Arrays.toString(infused));
			next();
		}
	}

	private static short[] filled(int value) {
		short[] s = new short[dev.soulsmode.ds2.Attributes.SLOT_COUNT];
		java.util.Arrays.fill(s, (short) value);
		return s;
	}

	private static void trades(Minecraft mc) {
		var villagers = mc.level.getEntitiesOfClass(AbstractVillager.class, mc.player.getBoundingBox().inflate(6));
		AbstractVillager v = villagers.get(0);
		if (tick == 10) {
			look(mc, v.getX(), v.getEyeY(), v.getZ());
			mc.gameMode.interact(mc.player, v, net.minecraft.world.InteractionHand.MAIN_HAND);
		}
		if (tick == 30) {
			if (mc.player.containerMenu instanceof MerchantMenu merchant) {
				StringBuilder out = new StringBuilder();
				for (var offer : merchant.getOffers()) {
					out.append(offer.getCostA().getCount()).append(' ').append(offer.getCostA().getItem()).append(" -> ")
							.append(offer.getResult()).append("; ");
				}
				log("trades {}: {}", v.getType().toShortString(), out);
			} else {
				log("trades: no merchant screen ({})", mc.screen);
			}
			mc.player.closeContainer();
			next();
		}
	}

	private static ItemStack find(Minecraft mc, Item item) {
		for (ItemStack s : mc.player.getInventory().items) {
			if (s.is(item)) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	private static void next() {
		phase++;
		tick = -1;
		if (phase == 4) {
			log("done");
			Minecraft.getInstance().stop();
		}
	}
}
