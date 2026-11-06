package dev.soulsmode.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Remembers whose stain this is; the souls themselves live in the owner's attachment. */
public class BloodstainBlockEntity extends BlockEntity {
	private UUID owner;

	public BloodstainBlockEntity(BlockPos pos, BlockState state) {
		super(SoulsBlocks.BLOODSTAIN_ENTITY, pos, state);
	}

	public UUID owner() {
		return owner;
	}

	public void setOwner(UUID owner) {
		this.owner = owner;
		setChanged();
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (owner != null) {
			tag.putUUID("owner", owner);
		}
	}
}
