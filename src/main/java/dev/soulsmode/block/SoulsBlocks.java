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

	/**
	 * The titanite slab placed as a block (the user's design, 2026-11-09): a slab like Minecraft's, from the titanite
	 * slab item (SoulsItems.TITANITE_SLAB). Looks like the titanite block. Mined with a pickaxe.
	 */
	public static final net.minecraft.world.level.block.SlabBlock TITANITE_SLAB = Registry.register(BuiltInRegistries.BLOCK,
			SoulsMode.id("titanite_slab"), new net.minecraft.world.level.block.SlabBlock(titaniteProperties()));

	/** The decorative titanite block: two slabs crafted together; looks like netherite, lighter and cracked. */
	public static final net.minecraft.world.level.block.Block TITANITE_BLOCK = Registry.register(BuiltInRegistries.BLOCK,
			SoulsMode.id("titanite_block"), new net.minecraft.world.level.block.Block(titaniteProperties()));

	/**
	 * The covenant altars (CovenantAltarBlock), by DS2 covenant id. BSE keeps only the Way of Blue (the user, 2026-11-10:
	 * a small totem; the other covenants and their full DS2 sites go to DarkTwo).
	 */
	public static final CovenantAltarBlock WAY_OF_BLUE_ALTAR = altar("way_of_blue_altar", 4);

	private static CovenantAltarBlock altar(String name, int covenant) {
		return Registry.register(BuiltInRegistries.BLOCK, SoulsMode.id(name), new CovenantAltarBlock(covenant,
				BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0f, 3600000.0f).noLootTable()
						.noOcclusion().sound(SoundType.STONE)));
	}

	/** Iron-block-like properties: any pickaxe mines it (mineable/pickaxe, no tier tag). */
	private static BlockBehaviour.Properties titaniteProperties() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.requiresCorrectToolForDrops()
				.strength(5.0f, 6.0f)
				.sound(SoundType.METAL);
	}

	private SoulsBlocks() {
	}

	public static void init() {
		// Forces class loading so the registrations run during mod init.
	}
}
