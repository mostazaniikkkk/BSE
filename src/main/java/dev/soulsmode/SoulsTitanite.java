package dev.soulsmode;

import dev.soulsmode.item.SoulsItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.List;
import java.util.Map;

/**
 * Where titanite comes from (the user's design, 2026-11-09; CHANGELOG "Implementación de la titanita"):
 * <ul>
 * <li>Mobs, killed by a player, with DS2's drop rates (titanite.md section 7.2): bat a shard 20 %, phantom a large
 * shard 15 %, vex a chunk 2 %, ravager a slab 2 % and twinkling titanite 5 %.</li>
 * <li>Chests: 25 % of them, 1 to 3 of one titanite of the structure's list (equal weights).</li>
 * <li>Trades: the weaponsmith and the armorer sell a shard (novice), a large shard (apprentice), a chunk
 * (journeyman) and a slab (expert) at DS2's price (ItemParam +0x30) at 500 souls per emerald: 2, 5, 12, 26. The
 * wandering trader sells one twinkling titanite (15000 souls: 30 emeralds).</li>
 * </ul>
 */
public final class SoulsTitanite {
	private static final float CHEST_CHANCE = 0.25f;
	/** Vanilla's usual offer: 12 uses, 5 % price multiplier; experience by level as vanilla's sells (1, 5, 10, 15). */
	private static final int USES = 12;
	private static final float PRICE_MULTIPLIER = 0.05f;

	private SoulsTitanite() {
	}

	public static void init() {
		Map<ResourceKey<LootTable>, List<Item>> chests = Map.ofEntries(
				Map.entry(BuiltInLootTables.SIMPLE_DUNGEON, List.of(SoulsItems.TITANITE_SHARD, SoulsItems.LARGE_TITANITE_SHARD)),
				Map.entry(BuiltInLootTables.ABANDONED_MINESHAFT, List.of(SoulsItems.TITANITE_SHARD, SoulsItems.LARGE_TITANITE_SHARD)),
				Map.entry(BuiltInLootTables.PILLAGER_OUTPOST,
						List.of(SoulsItems.TITANITE_SHARD, SoulsItems.LARGE_TITANITE_SHARD, SoulsItems.TITANITE_CHUNK)),
				Map.entry(BuiltInLootTables.WOODLAND_MANSION,
						List.of(SoulsItems.TITANITE_SHARD, SoulsItems.LARGE_TITANITE_SHARD, SoulsItems.TITANITE_CHUNK)),
				Map.entry(BuiltInLootTables.STRONGHOLD_CORRIDOR, List.of(SoulsItems.TITANITE_CHUNK, SoulsItems.TITANITE_SLAB)),
				Map.entry(BuiltInLootTables.STRONGHOLD_CROSSING, List.of(SoulsItems.TITANITE_CHUNK, SoulsItems.TITANITE_SLAB)),
				Map.entry(BuiltInLootTables.STRONGHOLD_LIBRARY, List.of(SoulsItems.TITANITE_CHUNK, SoulsItems.TITANITE_SLAB)),
				Map.entry(BuiltInLootTables.BASTION_TREASURE, List.of(SoulsItems.TITANITE_CHUNK, SoulsItems.TITANITE_SLAB)),
				Map.entry(BuiltInLootTables.BASTION_OTHER, List.of(SoulsItems.TITANITE_CHUNK, SoulsItems.TITANITE_SLAB)),
				Map.entry(BuiltInLootTables.BASTION_BRIDGE, List.of(SoulsItems.TITANITE_CHUNK, SoulsItems.TITANITE_SLAB)),
				Map.entry(BuiltInLootTables.BASTION_HOGLIN_STABLE, List.of(SoulsItems.TITANITE_CHUNK, SoulsItems.TITANITE_SLAB)),
				Map.entry(BuiltInLootTables.ANCIENT_CITY, List.of(SoulsItems.TITANITE_SLAB, SoulsItems.TWINKLING_TITANITE)));
		Map<ResourceKey<LootTable>, List<Drop>> mobs = Map.of(
				EntityType.BAT.getDefaultLootTable(), List.of(new Drop(SoulsItems.TITANITE_SHARD, 0.20f)),
				EntityType.PHANTOM.getDefaultLootTable(), List.of(new Drop(SoulsItems.LARGE_TITANITE_SHARD, 0.15f)),
				EntityType.VEX.getDefaultLootTable(), List.of(new Drop(SoulsItems.TITANITE_CHUNK, 0.02f)),
				EntityType.RAVAGER.getDefaultLootTable(), List.of(new Drop(SoulsItems.TITANITE_SLAB, 0.02f),
						new Drop(SoulsItems.TWINKLING_TITANITE, 0.05f)));
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			List<Item> items = chests.get(key);
			if (items != null) {
				LootPool.Builder pool = LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(CHEST_CHANCE));
				for (Item item : items) {
					pool.add(LootItem.lootTableItem(item).apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 3))));
				}
				builder.withPool(pool);
			}
			for (Drop drop : mobs.getOrDefault(key, List.of())) {
				builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(drop.item()))
						.when(LootItemKilledByPlayerCondition.killedByPlayer())
						.when(LootItemRandomChanceCondition.randomChance(drop.chance())));
			}
		});
	}

	private record Drop(Item item, float chance) {
	}

	/**
	 * The weaponsmith's and the armorer's titanite offer for the level they just reached (VillagerMixin, after vanilla
	 * picks its own offers, so it is always there): shard at novice, large shard at apprentice, chunk at journeyman,
	 * slab at expert; null for other villagers and levels.
	 */
	public static MerchantOffer smithOffer(net.minecraft.world.entity.npc.VillagerData data) {
		if (data.getProfession() != VillagerProfession.WEAPONSMITH && data.getProfession() != VillagerProfession.ARMORER) {
			return null;
		}
		return switch (data.getLevel()) {
			case 1 -> offer(SoulsItems.TITANITE_SHARD, 2, 1);
			case 2 -> offer(SoulsItems.LARGE_TITANITE_SHARD, 5, 5);
			case 3 -> offer(SoulsItems.TITANITE_CHUNK, 12, 10);
			case 4 -> offer(SoulsItems.TITANITE_SLAB, 26, 15);
			default -> null;
		};
	}

	private static MerchantOffer offer(Item item, int emeralds, int xp) {
		return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), new ItemStack(item), USES, xp, PRICE_MULTIPLIER);
	}

	/** The wandering trader's twinkling titanite (WanderingTraderMixin): 30 emeralds, one per trader. */
	public static MerchantOffer twinklingOffer() {
		return new MerchantOffer(new ItemCost(Items.EMERALD, 30), new ItemStack(SoulsItems.TWINKLING_TITANITE), 1, 1,
				PRICE_MULTIPLIER);
	}
}
