package dev.soulsmode.ascetic;

import com.mojang.serialization.Lifecycle;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.mixin.ServerLevelEntitiesAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.Unit;
import net.minecraft.Util;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Puts a square of chunks back the way the world generated them (the Bonfire Ascetic's reset, the user's design).
 * <p>
 * How (the approach of WorldEdit's {@code //regen} on Fabric): a temporary {@link ServerLevel} is built over an empty
 * save in a temporary folder with the same dimension type, chunk generator and seed as the real one, and a chunk ticket
 * asks it for the area. Vanilla's own pipeline generates the chunks there, neighbours included (structures, features
 * spilling over chunk borders, lighting, initial mobs, post-processing), on the worldgen threads; the server thread
 * only drives the temporary level's main-thread tasks, within a time budget per tick. Then, a few chunks per tick, every
 * block that differs is copied into the real level with {@code setBlock} (light, heightmaps, POIs and the clients'
 * updates follow from it), block entities are replaced by the generated ones (chests come back with their loot table),
 * and at the end the non-player entities of the area are removed and the generated ones (animals, villagers...) copied
 * in (the real area is held loaded meanwhile so its stored entities are loaded before being replaced). Positions in
 * {@code keep} are left untouched, and so are the chunks a protection mod refuses through
 * {@link dev.soulsmode.api.AsceticEvents#ALLOW_CHUNK_RESET} (blocks, block entities and entities).
 * <p>
 * Before the copy, each chunk's ore features ({@code UNDERGROUND_ORES} placed features of its biomes whose ore
 * configuration targets a {@code c:ores} block) are placed {@code oreBonus} extra times on average in the generated
 * level (the user's design: richer zones by cycle), with their own placement rules (count, height, biome).
 * <p>
 * Vanilla's own limit: features that overlap between neighbouring chunks (stone blobs, ores, kelp, gravel disks...)
 * depend on the order the worldgen threads decorate the chunks, so two generations of the same area differ in a few
 * thousand such blocks (measured on the QA server: about 3,500 of 17 x 17 chunks).
 */
public final class ChunkRegenerator {
	/** Design choice: the server thread spends at most this much per tick on a regeneration. */
	private static final long BUDGET_NANOS = 20_000_000L;
	/** Design choice: give up if generating the area takes longer than this (ticks). */
	private static final int GENERATION_TIMEOUT_TICKS = 20 * 120;
	/** Copy flags: send to clients, no shape updates to neighbours (the shapes are copied as generated), no drops. */
	private static final int COPY_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
	private static final TicketType<Unit> TICKET = TicketType.create(SoulsMode.MOD_ID + ":regen", (a, b) -> 0);

	private enum Phase { GENERATE, ORES, COPY, DONE }

	private final ServerLevel level;
	private final ChunkPos center;
	private final int radius;
	private final List<BlockPos> keep;
	private final Runnable onDone;
	/** Expected extra placements per ore feature and chunk. */
	private final double oreBonus;
	/** Chunks a protection mod keeps out (ChunkPos longs). */
	private final java.util.Set<Long> protectedChunks = new java.util.HashSet<>();
	private int nextOreChunk;
	private int extraOres;
	private final long started = System.nanoTime();
	private Phase phase = Phase.GENERATE;
	private Path folder;
	private LevelStorageSource.LevelStorageAccess storage;
	private ServerLevel fresh;
	private int ticks;
	private int totalTicks;
	private int nextChunk;
	private long mainThreadNanos;
	private long worstTickNanos;
	private long openNanos;
	private boolean realTickets;
	private int entityWaitTicks;
	private long generatedAt;
	private int changedBlocks;
	/** For the log: how many blocks changed from which block to which (top ones are printed). */
	private final java.util.Map<String, Integer> transitions = new java.util.HashMap<>();
	private int blockEntities;
	private int removedEntities;
	private int addedEntities;
	private String error;

	public ChunkRegenerator(ServerLevel level, ChunkPos center, int radius, List<BlockPos> keep, double oreBonus,
			Runnable onDone) {
		this.level = level;
		this.center = center;
		this.radius = radius;
		this.keep = List.copyOf(keep);
		this.oreBonus = Math.max(0, oreBonus);
		this.onDone = onDone;
		forEachChunk(pos -> {
			if (!dev.soulsmode.api.AsceticEvents.ALLOW_CHUNK_RESET.invoker().allow(level, pos)) {
				protectedChunks.add(pos.toLong());
			}
		});
	}

	public boolean isDone() {
		return phase == Phase.DONE;
	}

	public ServerLevel level() {
		return level;
	}

	/** Runs one server tick's share of the work. */
	public void tick() {
		if (phase == Phase.DONE) {
			return;
		}
		long start = System.nanoTime();
		totalTicks++;
		try {
			if (fresh == null) {
				open();
				openNanos = System.nanoTime() - start;
			} else if (phase == Phase.GENERATE) {
				generate(start);
			} else if (phase == Phase.ORES) {
				addOres(start);
			} else if (phase == Phase.COPY) {
				copy(start);
			}
		} catch (Exception e) {
			error = e.toString();
			SoulsMode.LOGGER.error("Bonfire Ascetic: regeneration failed", e);
			finish();
		}
		long spent = System.nanoTime() - start;
		mainThreadNanos += spent;
		worstTickNanos = Math.max(worstTickNanos, spent);
	}

	/** Builds the temporary level and asks it for the area. */
	private void open() throws Exception {
		MinecraftServer server = level.getServer();
		folder = Files.createTempDirectory("soulsmode_regen");
		storage = LevelStorageSource.createDefault(folder).createAccess("regen");
		WorldData data = server.getWorldData();
		LevelSettings settings = new LevelSettings("regen", data.getGameType(), data.isHardcore(), data.getDifficulty(),
				data.isAllowCommands(), data.getGameRules().copy(), data.getDataConfiguration());
		PrimaryLevelData.SpecialWorldProperty special = data.isFlatWorld() ? PrimaryLevelData.SpecialWorldProperty.FLAT
				: data.isDebugWorld() ? PrimaryLevelData.SpecialWorldProperty.DEBUG
				: PrimaryLevelData.SpecialWorldProperty.NONE;
		PrimaryLevelData levelData = new PrimaryLevelData(settings, data.worldGenOptions(), special, Lifecycle.stable());
		long seed = data.worldGenOptions().seed();
		fresh = new ServerLevel(server, Util.backgroundExecutor(), storage, levelData, level.dimension(),
				new LevelStem(level.dimensionTypeRegistration(), level.getChunkSource().getGenerator()),
				new ChunkProgressListener() {
					@Override
					public void updateSpawnPos(ChunkPos pos) {
					}

					@Override
					public void onStatusChange(ChunkPos pos, @Nullable ChunkStatus status) {
					}

					@Override
					public void start() {
					}

					@Override
					public void stop() {
					}
				}, level.isDebug(), BiomeManager.obfuscateSeed(seed), List.of(), false, level.getRandomSequences());
		// Ticket level 33 - (radius + 1) at the centre: the whole area is block-ticking in the temporary level, so its
		// chunks are fully generated and post-processed (ChunkMap.prepareTickingChunk), with a full ring around them.
		fresh.getChunkSource().addRegionTicket(TICKET, center, radius + 1, Unit.INSTANCE);
		// The real area is held loaded (FULL, not ticking) meanwhile, so its entities are loaded from disk before they
		// are replaced; otherwise unloaded ones would come back next to the copies.
		forEachChunk(pos -> level.getChunkSource().addRegionTicket(TICKET, pos, 0, Unit.INSTANCE));
		realTickets = true;
	}

	private void forEachChunk(java.util.function.Consumer<ChunkPos> action) {
		for (int x = center.x - radius; x <= center.x + radius; x++) {
			for (int z = center.z - radius; z <= center.z + radius; z++) {
				action.accept(new ChunkPos(x, z));
			}
		}
	}

	private boolean entitiesLoaded() {
		for (int x = center.x - radius; x <= center.x + radius; x++) {
			for (int z = center.z - radius; z <= center.z + radius; z++) {
				if (!level.areEntitiesLoaded(ChunkPos.asLong(x, z))) {
					return false;
				}
			}
		}
		return true;
	}

	private boolean areaReady() {
		for (int x = center.x - radius; x <= center.x + radius; x++) {
			for (int z = center.z - radius; z <= center.z + radius; z++) {
				if (!fresh.getChunkSource().isPositionTicking(ChunkPos.asLong(x, z))) {
					return false;
				}
			}
		}
		return true;
	}

	private void generate(long start) {
		if (++ticks > GENERATION_TIMEOUT_TICKS) {
			throw new IllegalStateException("generation timed out");
		}
		while (System.nanoTime() - start < BUDGET_NANOS && fresh.getChunkSource().pollTask()) {
			// Drives the temporary level's main-thread work (ticket updates, promotion to full chunks, lighting).
		}
		if (areaReady()) {
			generatedAt = System.nanoTime();
			phase = oreBonus > 0 ? Phase.ORES : Phase.COPY;
		}
	}

	private int side() {
		return radius * 2 + 1;
	}

	private void copy(long start) {
		int total = side() * side();
		while (nextChunk < total && System.nanoTime() - start < BUDGET_NANOS) {
			int i = nextChunk++;
			copyChunk(center.x - radius + i % side(), center.z - radius + i / side());
		}
		if (nextChunk >= total) {
			if (!entitiesLoaded() && ++entityWaitTicks < GENERATION_TIMEOUT_TICKS) {
				return;
			}
			if (entityWaitTicks >= GENERATION_TIMEOUT_TICKS) {
				SoulsMode.LOGGER.warn("Bonfire Ascetic: some entities of the area did not load in time");
			}
			copyEntities();
			finish();
		}
	}

	/** A few chunks per tick: the extra ore placements, in the generated level (the ring around it is generated too). */
	private void addOres(long start) {
		int total = side() * side();
		var generator = fresh.getChunkSource().getGenerator();
		int step = net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
		long seed = level.getSeed();
		while (nextOreChunk < total && System.nanoTime() - start < BUDGET_NANOS) {
			int i = nextOreChunk++;
			ChunkPos pos = new ChunkPos(center.x - radius + i % side(), center.z - radius + i / side());
			if (protectedChunks.contains(pos.toLong())) {
				continue;
			}
			LevelChunk chunk = fresh.getChunk(pos.x, pos.z);
			java.util.Set<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> biomes = new java.util.HashSet<>();
			for (LevelChunkSection section : chunk.getSections()) {
				section.getBiomes().getAll(biomes::add);
			}
			java.util.Set<net.minecraft.world.level.levelgen.placement.PlacedFeature> ores = new java.util.LinkedHashSet<>();
			for (var biome : biomes) {
				var steps = generator.getBiomeGenerationSettings(biome).features();
				if (step < steps.size()) {
					for (var feature : steps.get(step)) {
						if (isOre(feature.value())) {
							ores.add(feature.value());
						}
					}
				}
			}
			net.minecraft.world.level.levelgen.WorldgenRandom random = new net.minecraft.world.level.levelgen.WorldgenRandom(
					new net.minecraft.world.level.levelgen.XoroshiroRandomSource(seed ^ pos.toLong() * 0x9E3779B97F4A7C15L));
			BlockPos origin = new BlockPos(pos.getMinBlockX(), fresh.getMinBuildHeight(), pos.getMinBlockZ());
			for (var feature : ores) {
				int passes = (int) oreBonus + (random.nextDouble() < oreBonus - (int) oreBonus ? 1 : 0);
				for (int p = 0; p < passes; p++) {
					if (feature.placeWithBiomeCheck(fresh, generator, random, origin)) {
						extraOres++;
					}
				}
			}
		}
		if (nextOreChunk >= total) {
			phase = Phase.COPY;
		}
	}

	/** An ore feature: its ore configuration places a c:ores block. */
	private static boolean isOre(net.minecraft.world.level.levelgen.placement.PlacedFeature feature) {
		return feature.feature().value().config()
				instanceof net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration ore
				&& ore.targetStates.stream().anyMatch(t -> t.state.is(
						net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags.ORES));
	}

	private void copyChunk(int chunkX, int chunkZ) {
		if (protectedChunks.contains(ChunkPos.asLong(chunkX, chunkZ))) {
			return;
		}
		LevelChunk from = fresh.getChunkSource().getChunkNow(chunkX, chunkZ);
		LevelChunk to = level.getChunk(chunkX, chunkZ);
		if (from == null) {
			throw new IllegalStateException("chunk " + chunkX + "," + chunkZ + " missing from the generated level");
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int s = 0; s < to.getSectionsCount(); s++) {
			LevelChunkSection a = to.getSection(s);
			LevelChunkSection b = from.getSection(s);
			if (a.hasOnlyAir() && b.hasOnlyAir()) {
				continue;
			}
			int baseY = SectionPos.sectionToBlockCoord(to.getSectionYFromSectionIndex(s));
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState want = b.getBlockState(x, y, z);
						BlockState have = a.getBlockState(x, y, z);
						if (want == have && !have.hasBlockEntity()) {
							continue;
						}
						pos.set(chunkX * 16 + x, baseY + y, chunkZ * 16 + z);
						if (!keep.contains(pos)) {
							copyBlock(pos.immutable(), have, want, from);
						}
					}
				}
			}
		}
		for (ServerPlayer player : level.players()) {
			if (player.chunkPosition().x == chunkX && player.chunkPosition().z == chunkZ) {
				unstick(player);
			}
		}
	}

	private void copyBlock(BlockPos pos, BlockState have, BlockState want, LevelChunk from) {
		if (have.hasBlockEntity()) {
			// What /setblock does: empty the old container first so replacing it drops nothing.
			Clearable.tryClear(level.getBlockEntity(pos));
		}
		if (want != have) {
			level.setBlock(pos, want, COPY_FLAGS);
			changedBlocks++;
			transitions.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(have.getBlock()).getPath() + ">"
					+ net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(want.getBlock()).getPath(), 1, Integer::sum);
		}
		BlockEntity generated = want.hasBlockEntity() ? from.getBlockEntity(pos) : null;
		if (generated != null) {
			CompoundTag tag = generated.saveWithFullMetadata(level.registryAccess());
			BlockEntity live = level.getBlockEntity(pos);
			if (live != null && live.getType() == generated.getType()) {
				live.loadWithComponents(tag, level.registryAccess());
				live.setChanged();
			} else {
				BlockEntity created = BlockEntity.loadStatic(pos, want, tag, level.registryAccess());
				if (created != null) {
					level.setBlockEntity(created);
				}
			}
			level.sendBlockUpdated(pos, want, want, Block.UPDATE_CLIENTS);
			blockEntities++;
		}
	}

	/** A player whose body is now inside blocks goes up to the first free spot (or the surface). */
	private void unstick(ServerPlayer player) {
		if (level.noCollision(player, player.getBoundingBox())) {
			return;
		}
		double x = player.getX();
		double z = player.getZ();
		for (int y = player.getBlockY() + 1; y < level.getMaxBuildHeight(); y++) {
			if (level.noCollision(player, player.getBoundingBox().move(0, y - player.getY(), 0))
					&& !level.getBlockState(BlockPos.containing(x, y - 1, z)).getCollisionShape(level,
							BlockPos.containing(x, y - 1, z)).isEmpty()) {
				player.teleportTo(x, y, z);
				return;
			}
		}
		player.teleportTo(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z)), z);
	}

	private AABB area() {
		return new AABB(SectionPos.sectionToBlockCoord(center.x - radius), level.getMinBuildHeight(),
				SectionPos.sectionToBlockCoord(center.z - radius), SectionPos.sectionToBlockCoord(center.x + radius + 1),
				level.getMaxBuildHeight(), SectionPos.sectionToBlockCoord(center.z + radius + 1));
	}

	private boolean inArea(Entity entity) {
		ChunkPos pos = entity.chunkPosition();
		return Math.abs(pos.x - center.x) <= radius && Math.abs(pos.z - center.z) <= radius
				&& !protectedChunks.contains(pos.toLong());
	}

	/** Non-player entities of the area go (except vehicles carrying a player); the generated ones come in. */
	private void copyEntities() {
		List<Entity> gone = new ArrayList<>();
		for (Entity entity : level.getEntities((Entity) null, area(), e -> !(e instanceof Player))) {
			if (inArea(entity) && !entity.hasPassenger(p -> p instanceof Player)
					&& !(entity.getVehicle() instanceof Player)) {
				gone.add(entity);
			}
		}
		gone.forEach(Entity::discard);
		removedEntities = gone.size();
		List<Entity> generated = new ArrayList<>();
		fresh.getAllEntities().forEach(entity -> {
			if (entity.getVehicle() == null && inArea(entity)) {
				generated.add(entity);
			}
		});
		for (Entity entity : generated) {
			CompoundTag tag = new CompoundTag();
			if (entity.save(tag)) {
				Entity copy = EntityType.loadEntityRecursive(tag, level, e -> e);
				if (copy != null && level.tryAddFreshEntityWithPassengers(copy)) {
					addedEntities++;
				}
			}
		}
	}

	private void finish() {
		phase = Phase.DONE;
		long closing = System.nanoTime();
		close();
		long now = System.nanoTime();
		SoulsMode.LOGGER.info("Bonfire Ascetic: regenerated {}x{} chunks around {} in {} ({}): generation {} ms (opening {} ms),"
						+ " copy {} ms, close {} ms, server thread {} ms in {} ticks (worst tick {} ms); {} blocks changed, {} block entities, {} entities"
						+ " removed, {} added, {} extra ore veins, {} protected chunks{}",
				side(), side(), center, level.dimension().location(), error == null ? "ok" : "failed",
				generatedAt == 0 ? -1 : (generatedAt - started) / 1_000_000,
				openNanos / 1_000_000,
				generatedAt == 0 ? -1 : (closing - generatedAt) / 1_000_000, (now - closing) / 1_000_000,
				mainThreadNanos / 1_000_000, totalTicks, worstTickNanos / 1_000_000, changedBlocks, blockEntities, removedEntities, addedEntities,
				extraOres, protectedChunks.size(), error == null ? "" : ": " + error);
		SoulsMode.LOGGER.info("Bonfire Ascetic: most changed: {}", transitions.entrySet().stream()
				.sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed()).limit(12)
				.map(e -> e.getKey() + " " + e.getValue()).toList());
		onDone.run();
	}

	/**
	 * Lets the temporary level go without saving its chunks (ServerLevel.close would write every generated chunk):
	 * its region storages are closed, its entities dropped, and the folder deleted.
	 */
	private void close() {
		if (realTickets) {
			forEachChunk(pos -> level.getChunkSource().removeRegionTicket(TICKET, pos, 0, Unit.INSTANCE));
			realTickets = false;
		}
		try {
			if (fresh != null) {
				fresh.getChunkSource().removeRegionTicket(TICKET, center, radius + 1, Unit.INSTANCE);
				List<Entity> entities = new ArrayList<>();
				fresh.getAllEntities().forEach(entities::add);
				entities.forEach(Entity::discard);
				fresh.getChunkSource().chunkMap.close();
				((ServerLevelEntitiesAccessor) fresh).soulsmode$entityManager().close();
			}
		} catch (Exception e) {
			SoulsMode.LOGGER.warn("Bonfire Ascetic: closing the temporary level", e);
		}
		try {
			if (storage != null) {
				storage.close();
			}
		} catch (Exception e) {
			SoulsMode.LOGGER.warn("Bonfire Ascetic: closing the temporary save", e);
		}
		Path dir = folder;
		if (dir != null) {
			Util.ioPool().execute(() -> {
				try (var paths = Files.walk(dir)) {
					paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
				} catch (Exception e) {
					SoulsMode.LOGGER.warn("Bonfire Ascetic: deleting {}", dir, e);
				}
			});
		}
		fresh = null;
	}
}
