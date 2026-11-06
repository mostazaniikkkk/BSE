package dev.soulsmode.block;

import dev.soulsmode.SoulsMode;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class SoulsBlocks {
	/** No block item: stains only appear when a Souls Mode player dies. Unbreakable and immovable. */
	public static final BloodstainBlock BLOODSTAIN = Registry.register(BuiltInRegistries.BLOCK, SoulsMode.id("bloodstain"),
			new BloodstainBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_RED)
					.noCollission()
					.noOcclusion()
					.strength(-1.0f, 3600000.0f)
					.noLootTable()
					.sound(SoundType.SLIME_BLOCK)
					.pushReaction(PushReaction.BLOCK)
					.lightLevel(state -> 3)));

	public static final BlockEntityType<BloodstainBlockEntity> BLOODSTAIN_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, SoulsMode.id("bloodstain"),
			BlockEntityType.Builder.of(BloodstainBlockEntity::new, BLOODSTAIN).build(null));

	/** An item on the ground (GroundItemBlock): placed by terrain generation; no block item, no drops when broken. */
	public static final GroundItemBlock GROUND_ITEM = Registry.register(BuiltInRegistries.BLOCK, SoulsMode.id("ground_item"),
			new GroundItemBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.SNOW)
					.noCollission()
					.noOcclusion()
					.instabreak()
					.noLootTable()
					.sound(SoundType.WOOL)
					.pushReaction(PushReaction.DESTROY)));

	private SoulsBlocks() {
	}

	public static void init() {
		// Forces class loading so the registrations run during mod init.
	}
}
