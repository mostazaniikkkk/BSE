package dev.soulsmode;

import dev.soulsmode.network.ClassPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * A "Souls-like" world (the game mode picked on the world creation screen, after Hardcore): every player who joins it
 * for the first time enters Souls Mode, and a player in Souls Mode without a class gets the class selection screen.
 */
public final class SoulsWorld extends SavedData {
	private static final String ID = "soulsmode_world";
	private boolean soulsLike;

	private static final SavedData.Factory<SoulsWorld> FACTORY = new SavedData.Factory<>(SoulsWorld::new,
			SoulsWorld::load, null);

	private static SoulsWorld load(CompoundTag tag, HolderLookup.Provider registries) {
		SoulsWorld world = new SoulsWorld();
		world.soulsLike = tag.getBoolean("soulsLike");
		return world;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putBoolean("soulsLike", soulsLike);
		return tag;
	}

	private static SoulsWorld get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
	}

	public static boolean isSoulsLike(MinecraftServer server) {
		return get(server).soulsLike;
	}

	/** Marks the world as Souls-like (called once, when a world created in that mode starts). */
	public static void markSoulsLike(MinecraftServer server) {
		SoulsWorld world = get(server);
		world.soulsLike = true;
		world.setDirty();
	}

	/** On join: a Souls-like world puts a new player in Souls Mode; a Souls Mode player without a class picks one. */
	public static void onJoin(ServerPlayer player) {
		if (isSoulsLike(player.server) && !player.getAttachedOrElse(SoulsAttachments.SOULS_LIKE_JOINED, false)) {
			player.setAttached(SoulsAttachments.SOULS_LIKE_JOINED, true);
			SoulsGameMode.enter(player);
		}
		openClassSelectionIfNeeded(player);
	}

	/** Only in a Souls-like world: the screen comes up until a class is picked (also after leaving without one). */
	public static void openClassSelectionIfNeeded(ServerPlayer player) {
		if (isSoulsLike(player.server) && SoulsGameMode.isActive(player) && SoulsPlayer.data(player).isEmpty()) {
			ServerPlayNetworking.send(player, new ClassPayloads.Open(true));
		}
	}

	/** The class picked on the selection screen; only while the player has none. */
	public static void onChoose(ServerPlayer player, int index) {
		if (!isSoulsLike(player.server) || !SoulsGameMode.isActive(player) || SoulsPlayer.data(player).isPresent()) {
			return;
		}
		dev.soulsmode.ds2.Ds2Data.startingClasses().byIndex(index).ifPresent(c -> {
			SoulsPlayer.chooseClass(player, c);
			giveStartingEquipment(player, c.index());
		});
		player.setHealth(player.getMaxHealth());
	}

	/**
	 * Starting equipment: each class's PlayerStatusParam gear (docs/research/classes.md, FUN_140155b90) in Minecraft
	 * items, the user's mapping (2026-10-31). Not given: spell tools (no spells yet), Lifegems and the other DS2
	 * consumables (no equivalent), the Explorer's bolts (no crossbow class gear).
	 */
	private static void giveStartingEquipment(ServerPlayer player, int classIndex) {
		java.util.List<net.minecraft.world.item.ItemStack> items = new java.util.ArrayList<>();
		net.minecraft.world.item.ItemStack offhand = net.minecraft.world.item.ItemStack.EMPTY;
		net.minecraft.world.item.ItemStack head = net.minecraft.world.item.ItemStack.EMPTY;
		net.minecraft.world.item.ItemStack chest = net.minecraft.world.item.ItemStack.EMPTY;
		net.minecraft.world.item.ItemStack legs = net.minecraft.world.item.ItemStack.EMPTY;
		net.minecraft.world.item.ItemStack feet = net.minecraft.world.item.ItemStack.EMPTY;
		switch (classIndex) {
			case 1 -> { // Warrior: Broken Straight Sword, Iron Parma, Standard Helm, Hard Leather set
				items.add(stack(net.minecraft.world.item.Items.STONE_SWORD, 1));
				offhand = stack(net.minecraft.world.item.Items.SHIELD, 1);
				head = stack(net.minecraft.world.item.Items.IRON_HELMET, 1);
				chest = stack(net.minecraft.world.item.Items.LEATHER_CHESTPLATE, 1);
				legs = stack(net.minecraft.world.item.Items.LEATHER_LEGGINGS, 1);
				feet = stack(net.minecraft.world.item.Items.LEATHER_BOOTS, 1);
			}
			case 2 -> { // Knight: Broadsword, Falconer set
				items.add(stack(net.minecraft.world.item.Items.IRON_SWORD, 1));
				chest = stack(net.minecraft.world.item.Items.CHAINMAIL_CHESTPLATE, 1);
				legs = stack(net.minecraft.world.item.Items.CHAINMAIL_LEGGINGS, 1);
				feet = stack(net.minecraft.world.item.Items.CHAINMAIL_BOOTS, 1);
			}
			case 9 -> { // Swordsman: Scimitar and Shortsword, Wanderer set
				items.add(stack(net.minecraft.world.item.Items.IRON_SWORD, 1));
				offhand = stack(net.minecraft.world.item.Items.STONE_SWORD, 1);
				head = stack(net.minecraft.world.item.Items.LEATHER_HELMET, 1);
				chest = stack(net.minecraft.world.item.Items.LEATHER_CHESTPLATE, 1);
				legs = stack(net.minecraft.world.item.Items.LEATHER_LEGGINGS, 1);
				feet = stack(net.minecraft.world.item.Items.LEATHER_BOOTS, 1);
			}
			case 4 -> { // Bandit: Hand Axe, Short Bow, 25 Wood Arrows, Bandit set
				items.add(stack(net.minecraft.world.item.Items.STONE_AXE, 1));
				items.add(stack(net.minecraft.world.item.Items.BOW, 1));
				items.add(stack(net.minecraft.world.item.Items.ARROW, 25));
				head = stack(net.minecraft.world.item.Items.LEATHER_HELMET, 1);
				chest = stack(net.minecraft.world.item.Items.LEATHER_CHESTPLATE, 1);
				legs = stack(net.minecraft.world.item.Items.LEATHER_LEGGINGS, 1);
				feet = stack(net.minecraft.world.item.Items.LEATHER_BOOTS, 1);
			}
			case 6 -> { // Cleric: Mace (club), Archdrake Robes
				items.add(stack(net.minecraft.world.item.Items.STICK, 1));
				chest = stack(net.minecraft.world.item.Items.LEATHER_CHESTPLATE, 1);
			}
			case 7 -> { // Sorcerer: Dagger, Black Hollow Mage Robe, Imported Trousers
				items.add(stack(net.minecraft.world.item.Items.WOODEN_SWORD, 1));
				chest = stack(net.minecraft.world.item.Items.LEATHER_CHESTPLATE, 1);
				legs = stack(net.minecraft.world.item.Items.LEATHER_LEGGINGS, 1);
			}
			case 8 -> { // Explorer: Dagger, Traveling Merchant set
				items.add(stack(net.minecraft.world.item.Items.WOODEN_SWORD, 1));
				head = stack(net.minecraft.world.item.Items.LEATHER_HELMET, 1);
				chest = stack(net.minecraft.world.item.Items.LEATHER_CHESTPLATE, 1);
				legs = stack(net.minecraft.world.item.Items.LEATHER_LEGGINGS, 1);
				feet = stack(net.minecraft.world.item.Items.LEATHER_BOOTS, 1);
			}
			default -> { // Deprived: nothing
			}
		}
		setIfEmpty(player, net.minecraft.world.entity.EquipmentSlot.OFFHAND, offhand);
		setIfEmpty(player, net.minecraft.world.entity.EquipmentSlot.HEAD, head);
		setIfEmpty(player, net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
		setIfEmpty(player, net.minecraft.world.entity.EquipmentSlot.LEGS, legs);
		setIfEmpty(player, net.minecraft.world.entity.EquipmentSlot.FEET, feet);
		for (net.minecraft.world.item.ItemStack item : items) {
			if (!player.getInventory().add(item)) {
				player.drop(item, false);
			}
		}
	}

	private static net.minecraft.world.item.ItemStack stack(net.minecraft.world.item.Item item, int count) {
		return new net.minecraft.world.item.ItemStack(item, count);
	}

	/** Equips into an empty slot; otherwise the item goes to the inventory. */
	private static void setIfEmpty(ServerPlayer player, net.minecraft.world.entity.EquipmentSlot slot,
			net.minecraft.world.item.ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (player.getItemBySlot(slot).isEmpty()) {
			player.setItemSlot(slot, stack);
		} else if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}
}
