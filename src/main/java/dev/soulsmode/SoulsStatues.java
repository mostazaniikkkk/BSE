package dev.soulsmode;

import dev.soulsmode.item.SoulsItems;
import dev.soulsmode.network.StatuePayloads;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Petrified statues and the Fragrant Branch of Yore (docs/research/petrification.md section 5).
 * <p>
 * DS2: the statues are characters held in a petrified start-up state; examining one without a branch shows MapEvent
 * 1017, with one the dialog MapEvent 1002 "Use %s?"; yes starts the player's event action 15 (a70_18_0010 PrayStand,
 * 4.667 s, facing the statue), and 1.3 s in (CompareStateTime) the map ESD spends one branch and sets the flag that
 * puts the character's start-up state to 0: it comes out of the stone.
 * <p>
 * In the mod (the user's design, 2026-11-18): villagers, zombie villagers, pillagers and wandering traders in stone,
 * generated loose on the Overworld's surface; freed, the mob is alive and never despawns. The branch is found in
 * chests (pillager outposts, dungeons, mineshafts, ruined portals, desert and jungle temples).
 * Not ported: the "Examine" key guide (KeyGuideParam 1600, fields unconfirmed), the stone material (EneStone, not
 * traced: the mod draws the mob's texture in grey stone) and the release clips with their SFX 103000 and sounds (DS2
 * characters' clips; Minecraft's mobs have none: they come out at once, with stone particles and sound).
 */
public final class SoulsStatues {
	/** The ESD's CompareStateTime: the branch is spent 1.3 s into the prayer. */
	private static final int BRANCH_AT_TICKS = 26;
	/** a70_18_0010_PrayStand .nsa length. */
	private static final float PRAY_SECONDS = 4.6666665f;
	private static final String PRAY = "70180010";
	/** How far the player may stand from the statue to examine it (Minecraft's entity reach). */
	private static final double REACH = 4.5;
	/** Design choice: chance of a branch per chest. */
	public static final float LOOT_CHANCE = 0.2f;

	public static final net.minecraft.resources.ResourceLocation FEATURE = SoulsMode.id("petrified_statue");
	public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE,
			SoulsMode.id("petrified_statue"));
	private static final List<EntityType<? extends Mob>> TYPES = List.of(EntityType.VILLAGER,
			EntityType.ZOMBIE_VILLAGER, EntityType.PILLAGER, EntityType.WANDERING_TRADER);

	/** A player praying at a statue: game tick it started and the statue. */
	private record Praying(long start, int statue) {
	}

	private static final Map<UUID, Praying> PRAYING = new HashMap<>();

	private SoulsStatues() {
	}

	public static boolean isPetrified(Entity entity) {
		return entity != null && Boolean.TRUE.equals(entity.getAttached(SoulsAttachments.PETRIFIED));
	}

	/** Turns a mob into a statue: no AI, invulnerable, silent, never despawns. */
	public static void petrify(Mob mob) {
		mob.setAttached(SoulsAttachments.PETRIFIED, true);
		mob.setNoAi(true);
		mob.setInvulnerable(true);
		mob.setSilent(true);
		mob.setPersistenceRequired();
	}

	/** Frees a statue: alive again, and it never despawns (the user's rule). */
	public static void release(Mob mob) {
		mob.removeAttached(SoulsAttachments.PETRIFIED);
		mob.setNoAi(false);
		mob.setInvulnerable(false);
		mob.setSilent(false);
		mob.setPersistenceRequired();
		if (mob instanceof WanderingTrader trader) {
			// 0 = the trader never leaves (WanderingTrader.maybeDespawn only counts down from above 0).
			trader.setDespawnDelay(0);
		}
		if (mob.level() instanceof ServerLevel level) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
					mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 60, mob.getBbWidth() / 2,
					mob.getBbHeight() / 3, mob.getBbWidth() / 2, 0.1);
			level.playSound(null, mob.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.NEUTRAL, 1, 0.8f);
		}
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(StatuePayloads.Ask.TYPE, StatuePayloads.Ask.CODEC);
		PayloadTypeRegistry.playC2S().register(StatuePayloads.Use.TYPE, StatuePayloads.Use.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(StatuePayloads.Use.TYPE,
				(payload, context) -> use(context.player(), payload.entityId()));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!isPetrified(entity)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
				examine(sp, entity);
			}
			return InteractionResult.SUCCESS;
		});
		Registry.register(BuiltInRegistries.FEATURE, FEATURE, new StatueFeature());
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.SURFACE_STRUCTURES,
				PLACED);
		Set<ResourceKey<LootTable>> chests = Set.of(BuiltInLootTables.PILLAGER_OUTPOST, BuiltInLootTables.SIMPLE_DUNGEON,
				BuiltInLootTables.ABANDONED_MINESHAFT, BuiltInLootTables.RUINED_PORTAL, BuiltInLootTables.DESERT_PYRAMID,
				BuiltInLootTables.JUNGLE_TEMPLE);
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (source.isBuiltin() && chests.contains(key)) {
				builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(SoulsItems.FRAGRANT_BRANCH))
						.when(LootItemRandomChanceCondition.randomChance(LOOT_CHANCE)));
			}
		});
	}

	/** EventConditionChr_IsSearch: without a branch MapEvent 1017, with one the dialog MapEvent 1002. */
	private static void examine(ServerPlayer player, Entity statue) {
		if (PRAYING.containsKey(player.getUUID()) || SoulsAttack.isVictim(player)) {
			return;
		}
		if (branch(player).isEmpty()) {
			player.displayClientMessage(Component.translatable("message.soulsmode.statue.blocks"), true);
			return;
		}
		ServerPlayNetworking.send(player, new StatuePayloads.Ask(statue.getId()));
	}

	private static ItemStack branch(Player player) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(SoulsItems.FRAGRANT_BRANCH)) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	/** The answer "yes": event action 15 (PrayStand) facing the statue. */
	private static void use(ServerPlayer player, int entityId) {
		Entity statue = player.level().getEntity(entityId);
		if (!(statue instanceof Mob) || !isPetrified(statue) || !player.isAlive()
				|| player.distanceTo(statue) > REACH || branch(player).isEmpty()
				|| PRAYING.containsKey(player.getUUID()) || SoulsAttack.isVictim(player)) {
			return;
		}
		// ChrEventLevelUpActionCtrl vt+0x20: the player turns towards the target.
		player.lookAt(EntityAnchorArgument.Anchor.FEET, statue.position());
		if (SoulsAttack.animate(player, PRAY, PRAY_SECONDS) < 0) {
			return;
		}
		PRAYING.put(player.getUUID(), new Praying(player.serverLevel().getGameTime(), entityId));
	}

	public static void tick(MinecraftServer server) {
		PRAYING.entrySet().removeIf(e -> {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null || !player.isAlive() || !SoulsAttack.isVictim(player)) {
				// The prayer was cut (death, disconnect): no branch spent.
				return true;
			}
			long t = player.serverLevel().getGameTime() - e.getValue().start();
			if (t < BRANCH_AT_TICKS) {
				return false;
			}
			Entity statue = player.level().getEntity(e.getValue().statue());
			ItemStack branch = branch(player);
			if (statue instanceof Mob mob && isPetrified(mob) && !branch.isEmpty()) {
				branch.consume(1, player);
				release(mob);
			}
			return true;
		});
	}

	/**
	 * One statue on the surface: a random one of the four mobs, facing a random way, on a sturdy block with air for its
	 * height (not in water).
	 */
	private static final class StatueFeature extends Feature<NoneFeatureConfiguration> {
		StatueFeature() {
			super(NoneFeatureConfiguration.CODEC);
		}

		@Override
		public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
			WorldGenLevel level = ctx.level();
			BlockPos pos = ctx.origin();
			RandomSource random = ctx.random();
			if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
					|| !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
					|| !level.getFluidState(pos.below()).isEmpty()) {
				return false;
			}
			EntityType<? extends Mob> type = TYPES.get(random.nextInt(TYPES.size()));
			Mob mob = type.create(level.getLevel());
			if (mob == null) {
				return false;
			}
			mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360, 0);
			mob.setYHeadRot(mob.getYRot());
			mob.setYBodyRot(mob.getYRot());
			if (mob instanceof ZombieVillager zombie) {
				// Not finalizeSpawn: a zombie's can add a chicken jockey or equipment. Only the biome's look.
				zombie.setVillagerData(zombie.getVillagerData().setType(VillagerType.byBiome(level.getBiome(pos))));
			} else {
				mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
			}
			petrify(mob);
			level.addFreshEntityWithPassengers(mob);
			return true;
		}
	}
}
