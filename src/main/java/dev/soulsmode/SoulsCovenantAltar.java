package dev.soulsmode;

import dev.soulsmode.item.SoulsComponents;
import dev.soulsmode.item.SoulsItems;
import dev.soulsmode.network.CovenantPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * The Way of Blue's altar, in place of Saulden (talk_m10_04 group 7410; docs/research/covenants.md): talking first gives
 * the rank rewards owed (machine 2147483591 -> 2147483608: only while the active covenant is 4; rank >= 1 Bloodbite
 * Ring, >= 2 Hush, >= 3 Blue Tearstone Ring, each once, MapEvent 1308), then the menu. Joining (2147483612 / 611):
 * no requirement; 1305 if already in it; not during an invasion (1311, "with a phantom present"); 130381(4), the Blue
 * Seal once (lot 2001000, flag 102381), 1301. Abandoning is the user's rule (2026-11-10; in DS2 another NPC does it):
 * 1304 without a covenant, 1315 with a phantom present, 130381(0), 1303. Points and rank stay (FUN_14038bd80).
 * The rings are armour enchantments (the user's rule): enchanted books; Hush is BSE's spell soulsmode:hush.
 */
public final class SoulsCovenantAltar {
	/** COVENANT_REWARDS entry of the joining lot: covenant x 10 + 0. */
	private static final int JOIN_LOT = 0;

	private SoulsCovenantAltar() {
	}

	/** Right click on the altar: rewards owed, then the menu. */
	public static void open(ServerPlayer player, int covenant) {
		if (!SoulsGameMode.isActive(player)) {
			return;
		}
		if (SoulsCovenants.active(player) == covenant) {
			giveRankRewards(player, covenant);
			// 2147483591 state 8: the joining lot again if it was never given (a full inventory).
			give(player, covenant, JOIN_LOT, joinLot(player, covenant));
		}
		sync(player, covenant);
	}

	public static void sync(ServerPlayer player, int covenant) {
		ServerPlayNetworking.send(player, new CovenantPayloads.Open(covenant, SoulsCovenants.active(player),
				SoulsCovenants.rank(player, covenant), SoulsCovenants.points(player, covenant)));
	}

	public static void onAction(ServerPlayer player, CovenantPayloads.Action action) {
		int covenant = action.covenant();
		if (covenant != SoulsPvp.WAY_OF_BLUE || !SoulsGameMode.isActive(player)) {
			return;
		}
		if (action.action() == 0) {
			if (SoulsCovenants.active(player) == covenant) {
				player.sendSystemMessage(Component.translatable("message.soulsmode.covenant.already"));
			} else if (SoulsPvp.inInvasion(player)) {
				player.sendSystemMessage(Component.translatable("message.soulsmode.covenant.phantom_join"));
			} else {
				SoulsCovenants.setActive(player, covenant);
				player.sendSystemMessage(Component.translatable("message.soulsmode.covenant.joined"));
				give(player, covenant, JOIN_LOT, joinLot(player, covenant));
				giveRankRewards(player, covenant);
			}
		} else if (action.action() == 1) {
			if (SoulsCovenants.active(player) == 0) {
				player.sendSystemMessage(Component.translatable("message.soulsmode.covenant.none"));
			} else if (SoulsPvp.inInvasion(player)) {
				player.sendSystemMessage(Component.translatable("message.soulsmode.covenant.phantom_leave"));
			} else {
				SoulsCovenants.setActive(player, 0);
				player.sendSystemMessage(Component.translatable("message.soulsmode.covenant.abandoned"));
			}
		}
		sync(player, covenant);
	}

	/** Lot 2001000: the Blue Seal (40740000), as its armour enchantment book. */
	private static ItemStack joinLot(ServerPlayer player, int covenant) {
		return book(player, "blue_seal", 1);
	}

	/** Lots 2001011 / 2001012 / 2001013 by rank. */
	private static void giveRankRewards(ServerPlayer player, int covenant) {
		int rank = SoulsCovenants.rank(player, covenant);
		List<ItemStack> owed = new ArrayList<>();
		if (rank >= 1 && give(player, covenant, 1, book(player, "bloodbite", 1))) {
			owed.add(ItemStack.EMPTY);
		}
		if (rank >= 2) {
			ItemStack hush = new ItemStack(SoulsItems.SPELL);
			hush.set(SoulsComponents.SPELL, SoulsMode.id("hush"));
			if (give(player, covenant, 2, hush)) {
				owed.add(ItemStack.EMPTY);
			}
		}
		if (rank >= 3 && give(player, covenant, 3, book(player, "blue_tearstone", 1))) {
			owed.add(ItemStack.EMPTY);
		}
		if (!owed.isEmpty()) {
			// talk 74100610 -> talk/m10_04_00_00.fmg 74101700 "I'll always be here... watching over you."
			player.sendSystemMessage(Component.translatable("message.soulsmode.covenant.saulden_reward"));
		}
	}

	/** Gives a reward once (its flag); a full inventory keeps it owed (MapEvent 1011). Returns whether it was given. */
	private static boolean give(ServerPlayer player, int covenant, int slot, ItemStack stack) {
		int flag = covenant * 10 + slot;
		List<Integer> given = player.getAttachedOrElse(SoulsAttachments.COVENANT_REWARDS, List.of());
		if (given.contains(flag) || stack.isEmpty()) {
			return false;
		}
		if (!player.getInventory().add(stack)) {
			player.sendSystemMessage(Component.translatable("message.soulsmode.covenant.inventory_full"));
			return false;
		}
		List<Integer> next = new ArrayList<>(given);
		next.add(flag);
		player.setAttached(SoulsAttachments.COVENANT_REWARDS, List.copyOf(next));
		return true;
	}

	private static ItemStack book(ServerPlayer player, String enchantment, int level) {
		var holder = player.server.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
				.getHolder(ResourceKey.create(Registries.ENCHANTMENT, SoulsMode.id(enchantment)));
		return holder.map(h -> EnchantedBookItem.createForEnchantment(new EnchantmentInstance(h, level)))
				.orElse(ItemStack.EMPTY);
	}
}
