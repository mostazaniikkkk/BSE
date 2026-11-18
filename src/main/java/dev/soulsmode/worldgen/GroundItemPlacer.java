package dev.soulsmode.worldgen;

import com.mojang.serialization.Codec;
import dev.soulsmode.block.SoulsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The ground pickups (GroundItemBlock) underground and inside structures (the user's request, 2026-11-10): with the same
 * frequency as on the surface, 1 in {@link #CHANCE} chunks (placed features {@code ground_item} /
 * {@code cave_ground_item}) and 1 in {@link #CHANCE} chunks a structure covers (StructureStartMixin).
 */
public final class GroundItemPlacer {
	/** The surface's rarity_filter (placed_feature/ground_item.json, the user's 1 in 32 chunks). */
	public static final int CHANCE = 32;
	/** Tries per chunk to find a free floor (a design value: enough to find a cave or a room when there is one). */
	private static final int TRIES = 256;
	/** A cave floor is at least this far under the surface, so it is not the surface pickup again (design value). */
	private static final int BELOW_SURFACE = 8;

	private GroundItemPlacer() {
	}

	/** A free spot: air with a sturdy block under it, where the pickup survives. */
	private static boolean free(WorldGenLevel level, BlockPos pos, BlockState item) {
		return level.getBlockState(pos).isAir() && item.canSurvive(level, pos);
	}

	/** Cave pickups: a free floor under the surface in this chunk. */
	public static final class Cave extends Feature<NoneFeatureConfiguration> {
		public Cave(Codec<NoneFeatureConfiguration> codec) {
			super(codec);
		}

		@Override
		public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
			WorldGenLevel level = context.level();
			RandomSource random = context.random();
			BlockPos origin = context.origin();
			BlockState item = SoulsBlocks.GROUND_ITEM.defaultBlockState();
			int minY = level.getMinBuildHeight() + 1;
			for (int i = 0; i < TRIES; i++) {
				int x = (origin.getX() & ~15) + random.nextInt(16);
				int z = (origin.getZ() & ~15) + random.nextInt(16);
				int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - BELOW_SURFACE;
				if (top <= minY) {
					continue;
				}
				BlockPos pos = new BlockPos(x, minY + random.nextInt(top - minY), z);
				if (free(level, pos, item)) {
					level.setBlock(pos, item, 2);
					return true;
				}
			}
			return false;
		}
	}

	/** After a structure is placed in a chunk: 1 in {@link #CHANCE}, a pickup on a free floor inside its pieces. */
	public static void structure(WorldGenLevel level, RandomSource random, BoundingBox chunkBox,
			java.util.List<net.minecraft.world.level.levelgen.structure.StructurePiece> pieces) {
		if (pieces.isEmpty() || random.nextInt(CHANCE) != 0) {
			return;
		}
		BlockState item = SoulsBlocks.GROUND_ITEM.defaultBlockState();
		for (int i = 0; i < TRIES; i++) {
			var piece = pieces.get(random.nextInt(pieces.size()));
			BoundingBox box = piece.getBoundingBox();
			int x0 = Math.max(box.minX(), chunkBox.minX());
			int x1 = Math.min(box.maxX(), chunkBox.maxX());
			int z0 = Math.max(box.minZ(), chunkBox.minZ());
			int z1 = Math.min(box.maxZ(), chunkBox.maxZ());
			if (x0 > x1 || z0 > z1) {
				continue;
			}
			BlockPos pos = new BlockPos(x0 + random.nextInt(x1 - x0 + 1), box.minY() + random.nextInt(box.getYSpan()),
					z0 + random.nextInt(z1 - z0 + 1));
			if (free(level, pos, item)) {
				level.setBlock(pos, item, 2);
				return;
			}
		}
	}
}
