package dev.soulsmode;

import dev.soulsmode.ds2.Weapons;
import dev.soulsmode.item.SoulsItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hollowing (docs/research/hollowing.md):
 * <ul>
 * <li>every death adds 1 to the counter H, clamped to 0..32 (FUN_14037dcc0 -> FUN_140202c30, CriminalAndDeceasedParam
 * row 0 byte 5); the player is hollow while H > 0 (FUN_140203db0);</li>
 * <li>while hollow, max HP is ceil(f x max HP) with f = max(100 - 5H, 50) / 100 (FUN_140202820 / FUN_140202ca0; floor =
 * byte 8). Max stamina is not affected;</li>
 * <li>the Human Effigy (ItemParam 60151000, SpEffect 60151000 -> FUN_140203d50) sets H = 0: human again, full max HP.
 * Its use is a00_18_0190 ItemUseAbsorb; the effect applies at TAE 220000.</li>
 * </ul>
 * Not ported: the +5 of death type 3 and the flags that skip the increment (unidentified), the 5% floor of status
 * +0x1d6 == 2 (unidentified), state 2 at H >= 5, the hollow look. When the effigy leaves the inventory is not known:
 * here it is used up when its effect applies.
 * <p>
 * The Minecraft side is the user's design (2026-10-30), only while in Souls Mode:
 * <ul>
 * <li>villagers do not trade with a hollow player and flee from them; the wandering trader still trades;</li>
 * <li>iron golems attack hollow players;</li>
 * <li>being hollow cancels Hero of the Village;</li>
 * <li>the wandering trader always sells the Human Effigy for 2 emeralds; master clerics may sell it; it may be found in
 * desert pyramids, jungle temples and nether fortresses;</li>
 * <li>restoring human form from hollow clears every loaded villager's gossip about the player (reputation 0).</li>
 * </ul>
 */
public final class SoulsHollow {
	/** Distance at which villagers flee a hollow player: vanilla's for a zombie (VillagerHostilesSensor), a choice. */
	public static final double VILLAGER_FLEE_DISTANCE = 8.0;
	/** Design choice: chance that a master cleric's trades include the Human Effigy. */
	public static final float CLERIC_CHANCE = 0.25f;
	/** Design choice: chance per chest of a Human Effigy in temples and nether fortresses. */
	public static final float LOOT_CHANCE = 0.2f;
	/** Wandering trader offer: 2 emeralds (the user's price); uses are a design choice. */
	private static final int TRADER_PRICE = 2;
	private static final int TRADER_USES = 4;

	private SoulsHollow() {
	}

	/** CriminalAndDeceasedParam row 0: +1 per death (byte 5), cap 32 (FUN_14038bac0), 5 % per death, floor 50 % (byte 8). */
	private static final int PER_DEATH = 1;
	private static final int MAX_COUNT = 32;
	private static final int PERCENT_PER_DEATH = 5;
	private static final int FLOOR_PERCENT = 50;

	public static final ResourceLocation EFFIGY_USE = SoulsMode.id("human_effigy_use");
	public static final Weapons.Effigy EFFIGY = Weapons.effigy();

	/** A player using an effigy: game tick the animation started and the hand it is used from. */
	private record Using(long start, InteractionHand hand) {
	}

	private static final Map<UUID, Using> USING = new HashMap<>();

	public static int count(ServerPlayer player) {
		return player.getAttachedOrElse(SoulsAttachments.HOLLOWING, 0);
	}

	/** Hollow and in Souls Mode: outside the mode the state has no effect and is not shown. */
	public static boolean isHollow(Player player) {
		return player instanceof ServerPlayer p && SoulsGameMode.isActive(p) && count(p) > 0;
	}

	/** FUN_140202820: max HP factor, 1 while human. */
	public static double maxHpFactor(ServerPlayer player) {
		if (!isHollow(player)) {
			return 1;
		}
		int percent = Math.max(100 - count(player) * PERCENT_PER_DEATH, FLOOR_PERCENT);
		return Math.max(0.01, Math.min(1, percent / 100.0));
	}

	/** Every death in Souls Mode adds to the counter (also while human). */
	public static void onDeath(ServerPlayer player) {
		if (SoulsGameMode.isActive(player)) {
			// A petrification death is death type 3: u8@6 = 5 instead of u8@5 = 1 (FUN_140202c30, petrification.md 2.4).
			player.setAttached(SoulsAttachments.HOLLOWING,
					Math.min(MAX_COUNT, count(player) + SoulsStatus.deathHollowing(player, PER_DEATH)));
		}
	}

	/** Using the item: plays the absorb animation when nothing locks it (same locks as the torch). */
	public static boolean use(ServerPlayer player, InteractionHand hand) {
		if (!SoulsGameMode.isActive(player) || USING.containsKey(player.getUUID())
				|| !SoulsAttack.startItemUse(player, EFFIGY_USE, EFFIGY.use())) {
			return false;
		}
		USING.put(player.getUUID(), new Using(player.serverLevel().getGameTime(), hand));
		return true;
	}

	private static void tickUse(MinecraftServer server) {
		USING.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || !SoulsGameMode.isActive(player)) {
				return true;
			}
			float t = (player.serverLevel().getGameTime() - entry.getValue().start()) / 20f;
			if (!SoulsAttack.isDoing(player, EFFIGY.use())) {
				// Cut short (a dodge...) before TAE 220000: no effect, the effigy is kept.
				return true;
			}
			if (t < EFFIGY.effectAt()) {
				return false;
			}
			ItemStack stack = player.getItemInHand(entry.getValue().hand());
			if (stack.is(SoulsItems.HUMAN_EFFIGY)) {
				stack.consume(1, player);
				restore(player);
			}
			return true;
		});
	}

	/** FUN_140203d50: H = 0, human, max HP recomputed. From hollow it also resets villager reputation (user's design). */
	public static void restore(ServerPlayer player) {
		boolean wasHollow = isHollow(player);
		player.setAttached(SoulsAttachments.HOLLOWING, 0);
		SoulsStats.apply(player);
		if (wasHollow) {
			resetReputation(player);
		}
	}

	public static void forget(ServerPlayer player) {
		USING.remove(player.getUUID());
	}

	public static void init() {
		TradeOfferHelper.registerVillagerOffers(VillagerProfession.CLERIC, 5, factories -> factories.add(
				(trader, random) -> random.nextFloat() < CLERIC_CHANCE
						? new MerchantOffer(new ItemCost(Items.EMERALD, TRADER_PRICE), new ItemStack(SoulsItems.HUMAN_EFFIGY),
								TRADER_USES, 30, 0.05f)
						: null));
		Set<?> chests = Set.of(BuiltInLootTables.DESERT_PYRAMID, BuiltInLootTables.JUNGLE_TEMPLE,
				BuiltInLootTables.NETHER_BRIDGE);
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (source.isBuiltin() && chests.contains(key)) {
				builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(SoulsItems.HUMAN_EFFIGY))
						.when(LootItemRandomChanceCondition.randomChance(LOOT_CHANCE)));
			}
		});
	}

	/** The wandering trader's fixed offer (added to every new trader's trades). */
	public static MerchantOffer traderOffer() {
		return new MerchantOffer(new ItemCost(Items.EMERALD, TRADER_PRICE), new ItemStack(SoulsItems.HUMAN_EFFIGY),
				TRADER_USES, 1, 0.05f);
	}

	public static void tick(MinecraftServer server) {
		tickUse(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			// The user's visual cue: a hollow player gives off the bloodstain's soul particles (BloodstainBlock
			// animateTick: SOUL, rising at 0.02, one tick in three), around the body.
			if (isHollow(player) && player.getRandom().nextInt(3) == 0) {
				var random = player.getRandom();
				player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
						player.getX() + (random.nextDouble() - 0.5) * player.getBbWidth(),
						player.getY() + random.nextDouble() * player.getBbHeight(),
						player.getZ() + (random.nextDouble() - 0.5) * player.getBbWidth(), 0, 0, 1, 0, 0.02);
			}
			if (isHollow(player) && player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
				player.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
			}
		}
	}

	/** Reputation back to 0 with every loaded villager (gossip lives in each villager; unloaded ones keep theirs). */
	public static void resetReputation(ServerPlayer player) {
		for (ServerLevel level : player.server.getAllLevels()) {
			for (Villager villager : level.getEntities(EntityTypeTest.forClass(Villager.class), v -> true)) {
				for (GossipType type : GossipType.values()) {
					villager.getGossips().remove(player.getUUID(), type);
				}
			}
		}
	}
}
