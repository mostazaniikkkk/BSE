package dev.soulsmode.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A covenant's altar (docs/research/covenants.md "Diseño en BSE"): it stands where DS2's joining NPC stands, in that
 * covenant's structure (tools/re/covenant_structures.py), and does what the NPC does. Its shape is DS2's o00_9050, the
 * stone slab on every joining NPC's spot (1.10 x 1.78 x 0.32 m), cut to one block. Unbreakable like the NPC (design).
 */
public class CovenantAltarBlock extends Block {
	/** o00_9050 at 1 m = 1 block: 0.32 m deep, centred. */
	private static final VoxelShape SHAPE = Block.box(0, 0, 5.5, 16, 16, 10.5);
	private final int covenant;

	public CovenantAltarBlock(int covenant, Properties properties) {
		super(properties);
		this.covenant = covenant;
	}

	/** DS2's covenant id (VowParam row, common.fmg 40000000 + id). */
	public int covenant() {
		return covenant;
	}

	/** Right click: the altar's talk and menu (SoulsCovenantAltar). */
	@Override
	protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level,
			BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
		if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			dev.soulsmode.SoulsCovenantAltar.open(serverPlayer, covenant);
		}
		return net.minecraft.world.InteractionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
}
