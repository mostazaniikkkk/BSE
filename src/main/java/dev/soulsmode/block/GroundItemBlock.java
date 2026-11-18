package dev.soulsmode.block;

import dev.soulsmode.SoulsMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An item lying on the ground, DS2's glowing pickup (the user's feature, 2026-11-01): a small white block that terrain
 * generation scatters (worldgen placed feature {@code soulsmode:ground_item}, 1 in 32 chunks; set by the user 2026-11-03;
 * since 2026-11-10 also underground and inside structures at the same rate, GroundItemPlacer); right click picks it up
 * and gives one roll of the loot table {@code soulsmode:gameplay/ground_item} (the user's odds: low soul 50 %,
 * intermediate soul 30 %, a resin 15 %, Human Effigy 5 %). One pickup; it does not come back.
 */
public class GroundItemBlock extends Block {
	public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE,
			SoulsMode.id("gameplay/ground_item"));
	private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 4, 10);

	public GroundItemBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
			LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		return direction == Direction.DOWN && !canSurvive(state, level, pos)
				? Blocks.AIR.defaultBlockState()
				: super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		LootTable table = server.getServer().reloadableRegistries().getLootTable(LOOT);
		LootParams params = new LootParams.Builder(server)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
				.withParameter(LootContextParams.THIS_ENTITY, serverPlayer)
				.create(LootContextParamSets.GIFT);
		for (ItemStack stack : table.getRandomItems(params)) {
			if (!serverPlayer.getInventory().add(stack)) {
				serverPlayer.drop(stack, false);
			}
		}
		server.removeBlock(pos, false);
		server.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.0f);
		return InteractionResult.CONSUME;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		// A faint sparkle so it can be spotted (stands in for DS2's glow SFX).
		if (random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
					(random.nextDouble() - 0.5) * 0.01, 0.02, (random.nextDouble() - 0.5) * 0.01);
		}
	}
}
