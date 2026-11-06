package dev.soulsmode.ascetic;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.soulsmode.ActiveBonfires;
import dev.soulsmode.SoulsBonfire;
import dev.soulsmode.SoulsHollow;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.item.SoulsItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Llama de la adversidad" (Bonfire Ascetic), the user's design (2026-11-03), inspired by DS2's item of the same name
 * (docs/research/llama_adversidad.md). Burned from the bonfire menu at an activated bonfire it:
 * <ul>
 * <li>puts every chunk within {@link AsceticRules#RADIUS_CHUNKS} of the bonfire's chunk back the way the world
 * generated it, except the campfire itself (ChunkRegenerator);</li>
 * <li>adds a cycle to the bonfire's zone (AsceticZones). In a zone, hostile mobs are stronger, faster, see farther and
 * drop more; vindicators come by day; ravagers may come from cycle 4; spawners care less about light; phantoms ignore
 * insomnia; hostile mobs killed by a player may drop soul items. All numbers: AsceticRules.</li>
 * </ul>
 * Found like the Human Effigy (same chance and price, SoulsHollow) plus dungeons and trial chamber vaults.
 */
public final class SoulsAscetic {
	private static final ResourceLocation HEALTH = SoulsMode.id("ascetic_health");
	private static final ResourceLocation SPEED = SoulsMode.id("ascetic_speed");
	private static final ResourceLocation FOLLOW = SoulsMode.id("ascetic_follow_range");
	/** Design choice: how often mobs are re-checked against the zones (ticks). */
	private static final int MOB_UPDATE_TICKS = 20;
	private static final Item[] SOUL_ITEMS = {SoulsItems.SOUL_OF_A_LOST_UNDEAD, SoulsItems.LARGE_SOUL_OF_A_LOST_UNDEAD,
			SoulsItems.SOUL_OF_A_NAMELESS_SOLDIER, SoulsItems.LARGE_SOUL_OF_A_NAMELESS_SOLDIER};

	/** Wandering trader offer: the Human Effigy's (SoulsHollow: 2 emeralds, 4 uses). */
	private static final int TRADER_PRICE = 2;
	private static final int TRADER_USES = 4;

	/** One regeneration at a time on the whole server. */
	private static ChunkRegenerator job;
	/** Ticks left to each zone's next vindicator (key: dimension|chunkX|chunkZ). */
	private static final Map<String, Integer> VINDICATOR_TIMERS = new HashMap<>();
	/** Ticks left to each zone's next evoker (same key), from cycle 2. */
	private static final Map<String, Integer> EVOKER_TIMERS = new HashMap<>();
	/** Light limit of the spawner ticking now (BaseSpawnerMixin), -1 when none. Server thread only. */
	private static int spawnerLightLimit = -1;

	private SoulsAscetic() {
	}

	public static void init() {
		// The effigy's places (SoulsHollow), plus dungeons and the trial chambers' vault rewards (normal and ominous).
		Set<?> chests = Set.of(BuiltInLootTables.DESERT_PYRAMID, BuiltInLootTables.JUNGLE_TEMPLE,
				BuiltInLootTables.NETHER_BRIDGE, BuiltInLootTables.SIMPLE_DUNGEON, BuiltInLootTables.TRIAL_CHAMBERS_REWARD,
				BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS);
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (AsceticRules.ENABLED && source.isBuiltin() && chests.contains(key)) {
				builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(SoulsItems.BONFIRE_ASCETIC))
						.when(LootItemRandomChanceCondition.randomChance(SoulsHollow.LOOT_CHANCE)));
			}
		});
	}

	/** Added to every new wandering trader's trades (WanderingTraderMixin), like the Human Effigy. */
	public static MerchantOffer traderOffer() {
		return new MerchantOffer(new ItemCost(Items.EMERALD, TRADER_PRICE), new ItemStack(SoulsItems.BONFIRE_ASCETIC),
				TRADER_USES, 1, 0.05f);
	}

	public static AsceticZones zones(MinecraftServer server) {
		return AsceticZones.get(server);
	}

	public static int cycleAt(Entity entity) {
		MinecraftServer server = entity.getServer();
		return server == null || !AsceticRules.ENABLED ? 0 : zones(server).cycleAt(entity.level(), entity.blockPosition());
	}

	// ---- Burning ----

	/** The bonfire menu's button: the player rests at an activated bonfire and has the item. */
	public static void onBurnRequest(ServerPlayer player) {
		BlockPos bonfire = SoulsBonfire.menuBonfire(player);
		if (!AsceticRules.ENABLED || bonfire == null) {
			return;
		}
		ItemStack stack = find(player);
		if (stack.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.soulsmode.ascetic.none"), true);
			return;
		}
		if (job != null) {
			player.displayClientMessage(Component.translatable("message.soulsmode.ascetic.busy"), true);
			return;
		}
		stack.shrink(1);
		burn(player.serverLevel(), bonfire);
		SoulsBonfire.sendMenu(player);
	}

	private static ItemStack find(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(SoulsItems.BONFIRE_ASCETIC)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Burns one at this bonfire: the zone gains a cycle and the area starts regenerating. False if busy. */
	public static boolean burn(ServerLevel level, BlockPos bonfire) {
		if (job != null) {
			return false;
		}
		int cycle = zones(level.getServer()).burn(level, bonfire);
		level.playSound(null, bonfire, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1, 0.6f);
		Component done = Component.translatable("message.soulsmode.ascetic.burned", cycle);
		job = new ChunkRegenerator(level, new net.minecraft.world.level.ChunkPos(bonfire), AsceticRules.RADIUS_CHUNKS,
				List.of(bonfire.immutable()), AsceticRules.oreBonus(cycle), () -> {
					for (ServerPlayer player : level.players()) {
						if (zones(level.getServer()).cycleAt(level, player.blockPosition()) > 0) {
							player.displayClientMessage(done, true);
						}
					}
				});
		return true;
	}

	public static boolean isRegenerating() {
		return job != null;
	}

	// ---- Mobs in zones ----

	/** Max health, speed and follow range of a hostile mob follow the cycle where it is. */
	public static void updateMob(Mob mob) {
		if (!(mob instanceof Monster)) {
			return;
		}
		int cycle = cycleAt(mob);
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		float before = mob.getMaxHealth();
		set(health, HEALTH, AsceticRules.healthMultiplier(cycle) - 1);
		float after = mob.getMaxHealth();
		if (after != before && before > 0) {
			mob.setHealth(mob.getHealth() * after / before);
		}
		set(mob.getAttribute(Attributes.MOVEMENT_SPEED), SPEED, AsceticRules.speedMultiplier(cycle) - 1);
		set(mob.getAttribute(Attributes.FOLLOW_RANGE), FOLLOW, AsceticRules.followRangeMultiplier(cycle) - 1);
	}

	private static void set(AttributeInstance attribute, ResourceLocation id, double amount) {
		if (attribute == null) {
			return;
		}
		AttributeModifier current = attribute.getModifier(id);
		if (amount <= 0) {
			if (current != null) {
				attribute.removeModifier(id);
			}
		} else if (current == null || current.amount() != amount) {
			attribute.addOrReplacePermanentModifier(new AttributeModifier(id, amount,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	/** Damage dealt by a hostile mob (melee, its arrows, its explosion...) grows with the cycle where the mob is. */
	public static float scaleDamage(float amount, DamageSource source) {
		if (source.getEntity() instanceof Monster monster) {
			int cycle = cycleAt(monster);
			if (cycle > 0) {
				return (float) (amount * AsceticRules.damageMultiplier(cycle));
			}
		}
		return amount;
	}

	/**
	 * After a mob's loot table dropped (LivingEntityMixin): a hostile mob in a zone rolls its table once more per
	 * cycle, and if a player killed it, may drop a soul item.
	 */
	public static void afterLoot(LivingEntity entity, LootTable table, net.minecraft.world.level.storage.loot.LootParams params,
			Player killer) {
		if (!(entity instanceof Monster)) {
			return;
		}
		int cycle = cycleAt(entity);
		if (cycle == 0) {
			return;
		}
		for (int i = 0; i < AsceticRules.extraLootRolls(cycle); i++) {
			table.getRandomItems(params, entity::spawnAtLocation);
		}
		RandomSource random = entity.getRandom();
		if (killer != null && random.nextDouble() < AsceticRules.soulChance(cycle)) {
			int tier = AsceticRules.soulTier(cycle, random.nextDouble());
			if (tier >= 0) {
				entity.spawnAtLocation(new ItemStack(SOUL_ITEMS[tier]));
			}
		}
	}

	/** BaseSpawnerMixin: a spawner starts / ends its tick at this position. */
	public static void spawnerTick(ServerLevel level, BlockPos pos) {
		spawnerLightLimit = AsceticRules.ENABLED
				? AsceticRules.spawnerLightLimit(zones(level.getServer()).cycleAt(level, pos)) : -1;
	}

	public static void spawnerTickEnd() {
		spawnerLightLimit = -1;
	}

	/** MonsterMixin: while a spawner in a zone ticks, a spot this dark passes the light checks. */
	public static boolean spawnerLightAllows(net.minecraft.world.level.LevelReader level, BlockPos pos) {
		return spawnerLightLimit >= 0 && level.getMaxLocalRawBrightness(pos) <= spawnerLightLimit;
	}

	/** PhantomSpawnerMixin: in a zone of cycle >= 1 the player counts as sleepless. */
	public static boolean ignoresInsomnia(ServerPlayer player) {
		return AsceticRules.ENABLED && AsceticRules.phantomsIgnoreInsomnia(cycleAt(player));
	}

	// ---- Ticking ----

	public static void tick(MinecraftServer server) {
		if (job != null) {
			job.tick();
			if (job.isDone()) {
				job = null;
			}
		}
		if (!AsceticRules.ENABLED || server.getTickCount() % MOB_UPDATE_TICKS != 0) {
			return;
		}
		List<AsceticRules.Zone> zones = zones(server).zones();
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof Mob mob && entity instanceof Monster && entity.isAlive()) {
					updateMob(mob);
				}
			}
			if (!zones.isEmpty()) {
				tickVindicators(level, zones);
			}
		}
	}

	/**
	 * User: about one vindicator every 2 minutes per zone, by day, while a player is inside; from cycle 2 an evoker too
	 * (its own timer).
	 */
	private static void tickVindicators(ServerLevel level, List<AsceticRules.Zone> zones) {
		String dimension = level.dimension().location().toString();
		for (AsceticRules.Zone zone : zones) {
			if (!zone.dimension().equals(dimension)) {
				continue;
			}
			List<ServerPlayer> inside = level.players().stream().filter(p -> !p.isSpectator() && p.isAlive()
					&& zone.contains(dimension, p.chunkPosition().x, p.chunkPosition().z)).toList();
			if (inside.isEmpty() || !level.isDay() || level.getDifficulty() == Difficulty.PEACEFUL) {
				continue;
			}
			String key = dimension + "|" + zone.chunkX() + "|" + zone.chunkZ();
			if (AsceticRules.evokersPossible(zone.cycle())) {
				int evokerLeft = EVOKER_TIMERS.getOrDefault(key, AsceticRules.EVOKER_INTERVAL_TICKS) - MOB_UPDATE_TICKS;
				if (evokerLeft > 0) {
					EVOKER_TIMERS.put(key, evokerLeft);
				} else {
					EVOKER_TIMERS.put(key, AsceticRules.EVOKER_INTERVAL_TICKS);
					spawnNear(level, zone, inside.get(level.random.nextInt(inside.size())), EntityType.EVOKER);
				}
			}
			int left = VINDICATOR_TIMERS.getOrDefault(key, AsceticRules.VINDICATOR_INTERVAL_TICKS) - MOB_UPDATE_TICKS;
			if (left > 0) {
				VINDICATOR_TIMERS.put(key, left);
				continue;
			}
			VINDICATOR_TIMERS.put(key, AsceticRules.VINDICATOR_INTERVAL_TICKS);
			ServerPlayer player = inside.get(level.random.nextInt(inside.size()));
			int cycle = AsceticRules.cycleAt(zones, dimension, player.chunkPosition().x, player.chunkPosition().z);
			spawnNear(level, zone, player, EntityType.VINDICATOR);
			if (AsceticRules.ravagersPossible(cycle) && level.random.nextDouble() < AsceticRules.RAVAGER_CHANCE) {
				spawnNear(level, zone, player, EntityType.RAVAGER);
			}
		}
	}

	/** Spawns one mob on solid ground 16-32 blocks from the player, inside the zone, out of view if possible. */
	public static Mob spawnNear(ServerLevel level, AsceticRules.Zone zone, ServerPlayer player, EntityType<? extends Mob> type) {
		BlockPos seen = null;
		BlockPos hidden = null;
		RandomSource random = level.random;
		String dimension = level.dimension().location().toString();
		for (int attempt = 0; attempt < 24 && hidden == null; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = AsceticRules.VINDICATOR_MIN_DISTANCE
					+ random.nextDouble() * (AsceticRules.VINDICATOR_MAX_DISTANCE - AsceticRules.VINDICATOR_MIN_DISTANCE);
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
			if (!zone.contains(dimension, x >> 4, z >> 4) || !level.hasChunkAt(new BlockPos(x, 0, z))) {
				continue;
			}
			BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			BlockPos below = pos.below();
			if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || !level.getFluidState(pos).isEmpty()
					|| !level.noCollision(type.getSpawnAABB(x + 0.5, pos.getY(), z + 0.5))) {
				continue;
			}
			if (visible(level, player, pos)) {
				seen = seen == null ? pos : seen;
			} else {
				hidden = pos;
			}
		}
		BlockPos pos = hidden != null ? hidden : seen;
		if (pos == null) {
			return null;
		}
		Mob mob = type.create(level);
		if (mob == null) {
			return null;
		}
		mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360, 0);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
		level.addFreshEntityWithPassengers(mob);
		updateMob(mob);
		return mob;
	}

	/** In front of the player (within 60 degrees of the view) with nothing in between. */
	private static boolean visible(ServerLevel level, ServerPlayer player, BlockPos pos) {
		Vec3 eye = player.getEyePosition();
		Vec3 target = Vec3.atBottomCenterOf(pos).add(0, 1.5, 0);
		Vec3 to = target.subtract(eye).normalize();
		if (to.dot(player.getViewVector(1)) < 0.5) {
			return false;
		}
		return level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
				.getType() == HitResult.Type.MISS;
	}

	// ---- Debug / QA commands ----

	/** /souls ascetic burn <pos> | info <x> <z> | vindicator (op): QA over RCON. */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("souls").then(Commands.literal("ascetic")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("burn").then(Commands.argument("pos", BlockPosArgument.blockPos())
						.executes(ctx -> commandBurn(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
				.then(Commands.literal("info").then(Commands.argument("x", IntegerArgumentType.integer())
						.then(Commands.argument("z", IntegerArgumentType.integer())
								.executes(ctx -> commandInfo(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "x"),
										IntegerArgumentType.getInteger(ctx, "z"))))))));
	}

	/** Burns at the campfire at pos (activating it first, as the debug path skips the player's level cost). */
	private static int commandBurn(CommandSourceStack source, BlockPos pos) {
		ServerLevel level = source.getLevel();
		if (!(level.getBlockState(pos).getBlock() instanceof CampfireBlock)) {
			source.sendFailure(Component.literal("No campfire at " + pos.toShortString()));
			return 0;
		}
		ActiveBonfires.get(source.getServer()).activate(level.dimension().location(), pos.immutable());
		if (!burn(level, pos)) {
			source.sendFailure(Component.literal("A regeneration is already running"));
			return 0;
		}
		int cycle = zones(source.getServer()).cycleAt(level, pos);
		source.sendSuccess(() -> Component.literal("Burned at " + pos.toShortString() + ": cycle " + cycle
				+ ", regenerating " + (AsceticRules.RADIUS_CHUNKS * 2 + 1) + "x" + (AsceticRules.RADIUS_CHUNKS * 2 + 1)
				+ " chunks"), true);
		return cycle;
	}

	private static int commandInfo(CommandSourceStack source, int x, int z) {
		ServerLevel level = source.getLevel();
		int cycle = zones(source.getServer()).cycleAt(level, new BlockPos(x, 0, z));
		source.sendSuccess(() -> Component.literal("Cycle at " + x + " " + z + " (" + level.dimension().location() + "): "
				+ cycle + (isRegenerating() ? " (regenerating)" : "")
				+ (cycle > 0 ? String.format(" | health x%.2f, damage x%.2f, speed x%.2f, follow x%.2f, +%d loot rolls,"
								+ " spawner light <= %d, souls %.0f%%", AsceticRules.healthMultiplier(cycle),
						AsceticRules.damageMultiplier(cycle), AsceticRules.speedMultiplier(cycle),
						AsceticRules.followRangeMultiplier(cycle), AsceticRules.extraLootRolls(cycle),
						AsceticRules.spawnerLightLimit(cycle), AsceticRules.soulChance(cycle) * 100) : "")), false);
		return cycle;
	}
}
