package dev.soulsmode.item;

import dev.soulsmode.SoulsMode;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class SoulsItems {
	/** DS2 Human Effigy (docs/research/hollowing.md): restores human form. */
	public static final Item HUMAN_EFFIGY = register("human_effigy", new HumanEffigyItem(new Item.Properties().stacksTo(64)));
	/**
	 * DS2 Bonfire Ascetic (ItemParam 60527000; icon IC_0060527000): burned at a bonfire it resets the area and raises
	 * the zone's cycle (dev.soulsmode.ascetic.SoulsAscetic, the user's design, docs/research/llama_adversidad.md).
	 */
	public static final Item BONFIRE_ASCETIC = register("bonfire_ascetic",
			new BonfireAsceticItem(new Item.Properties().stacksTo(64)));

	/**
	 * DS2 Lloyd's Talisman (ItemParam 60531000; icon IC_0060531000): thrown, its zone blocks healing (SoulsThrown, the
	 * user's design). Stack of 1 (the user's choice).
	 */
	public static final Item LLOYDS_TALISMAN = register("lloyds_talisman",
			new LloydsTalismanItem(new Item.Properties().stacksTo(1)));

	/**
	 * DS2 Alluring Skull (ItemParam 60530000; thrown_items.md): thrown, lures the undead after the thrower (SoulsThrown).
	 * Stack of 99 (+0x4a). Recipe: the user's design (2026-11-10), 4 bones in a cross around any soul -> 4.
	 */
	public static final Item ALLURING_SKULL = register("alluring_skull", new AlluringSkullItem(new Item.Properties().stacksTo(99)));

	/**
	 * DS2 Homeward Bone (ItemParam 60350000; bonfire.md "Hueso de regreso"): back to the last bonfire rested at
	 * (SoulsHomeward). Stack of 99 (+0x4a). Recipe: the user's design (2026-11-10), a bone and any soul.
	 */
	public static final Item HOMEWARD_BONE = register("homeward_bone", new HomewardBoneItem(new Item.Properties().stacksTo(99)));

	/**
	 * DS2 Cracked Red Eye Orb (ItemParam 62060000, max 99, consumed) and Black Separation Crystal (62160000, max 1, kept):
	 * the invasion items (SoulsMultiplayItems, SoulsPvp). The orb is loot of structure chests (10 %), the crystal is
	 * crafted (the user's design, 2026-11-10: an amethyst shard and any soul).
	 */
	public static final Item CRACKED_RED_EYE_ORB = register("cracked_red_eye_orb", new MultiplayItem(new Item.Properties().stacksTo(99)));
	public static final Item BLACK_SEPARATION_CRYSTAL = register("black_separation_crystal",
			new MultiplayItem(new Item.Properties().stacksTo(1)));

	/** DS2 Firebomb (ItemParam 60570000): thrown, explodes without breaking blocks (SoulsThrown, the user's design). */
	public static final Item FIREBOMB = register("firebomb", new FirebombItem(new Item.Properties().stacksTo(16)));

	/**
	 * Pine resins, crafting chain designed by the user (2026-10-31): a glass bottle on a stripped spruce log gives a
	 * resin bottle, smelting it gives solid resin (the bottle stays in the furnace's input), and solid resin + an
	 * ingredient + a bundle make each DS2 resin (the bundle, experimental in 1.21.1, is enabled by BundleItemMixin).
 * What the resins do comes from docs/research/resins.md.
	 */
	/**
	 * Incandescent minerals (the user's design, 2026-11-16, first tried in DarkTwo; dev.soulsmode.SoulsIncandescent):
	 * what a Souls Mode player takes out of a furnace instead of the ingot / scrap.
	 */
	public static final Item INCANDESCENT_IRON_INGOT = register("incandescent_iron_ingot", new Item(new Item.Properties()));
	public static final Item INCANDESCENT_GOLD_INGOT = register("incandescent_gold_ingot", new Item(new Item.Properties()));
	public static final Item INCANDESCENT_COPPER_INGOT = register("incandescent_copper_ingot",
			new Item(new Item.Properties()));
	public static final Item INCANDESCENT_NETHERITE_SCRAP = register("incandescent_netherite_scrap",
			new Item(new Item.Properties().fireResistant()));
	/**
	 * Fragrant Branch of Yore (ItemParam 60537000, up to 99 held): spent on a petrified statue to free it
	 * (dev.soulsmode.SoulsStatues, docs/research/petrification.md section 5). It has no use of its own (+0x04 = 0).
	 */
	public static final Item FRAGRANT_BRANCH = register("fragrant_branch", new Item(new Item.Properties().stacksTo(99)));
	public static final Item RESIN_BOTTLE = register("resin_bottle", new Item(new Item.Properties().stacksTo(16)));
	public static final Item SOLID_RESIN = register("solid_resin", new Item(new Item.Properties()));
	public static final Item CHARCOAL_PINE_RESIN = register("charcoal_pine_resin", new ResinItem(new Item.Properties()));
	public static final Item ROTTEN_PINE_RESIN = register("rotten_pine_resin", new ResinItem(new Item.Properties()));
	public static final Item GOLD_PINE_RESIN = register("gold_pine_resin", new ResinItem(new Item.Properties()));
	public static final Item DARK_PINE_RESIN = register("dark_pine_resin", new ResinItem(new Item.Properties()));

	/**
	 * Base items for datapack content (dev.soulsmode.content.SoulsContent): what they are comes from their
	 * {@link SoulsComponents} component, their look from {@code custom_model_data} overrides in a resource pack.
	 */
	public static final Item WEAPON = register("weapon", new Item(new Item.Properties().stacksTo(1)));
	public static final Item CONSUMABLE = register("consumable", new Item(new Item.Properties()));
	/** A DS2 spell (one owned copy, like a DS2 spell entry): attuned at a bonfire, cast with a catalyst. */
	public static final Item SPELL = register("spell", new Item(new Item.Properties().stacksTo(1)));

	/**
	 * DS2 soul items (docs/research/soul_items.md): souls from SpEffectActiveItem.emevd (930 with N), up to 99 held
	 * (ItemParam +0x4a). Found on the ground (dev.soulsmode.block.GroundItemBlock).
	 */
	public static final Item SOUL_OF_A_LOST_UNDEAD = register("soul_of_a_lost_undead",
			new SoulItem(200, new Item.Properties().stacksTo(99)));
	public static final Item LARGE_SOUL_OF_A_LOST_UNDEAD = register("large_soul_of_a_lost_undead",
			new SoulItem(400, new Item.Properties().stacksTo(99)));
	public static final Item SOUL_OF_A_NAMELESS_SOLDIER = register("soul_of_a_nameless_soldier",
			new SoulItem(800, new Item.Properties().stacksTo(99)));
	public static final Item LARGE_SOUL_OF_A_NAMELESS_SOLDIER = register("large_soul_of_a_nameless_soldier",
			new SoulItem(1000, new Item.Properties().stacksTo(99)));

	/**
	 * DS2 Soul of a Giant (ItemParam 50920000; soul_items.md "Alma de un gigante"): 10 000 souls
	 * (SpEffectPassiveItem.emevd 100130[01]). Nothing drops it since 0.5.0 (the Last Giant drops its own soul).
	 */
	public static final Item SOUL_OF_A_GIANT = register("soul_of_a_giant",
			new SoulItem(10000, new Item.Properties().stacksTo(99)));
	/**
	 * DS2 Soul of the Last Giant (ItemParam 64010000, icon +0x00 = 64000000, max +0x4a = 99; soul_items.md "Alma del
	 * último gigante"): 6000 souls (SpEffectActiveItem.emevd 64010000 (1 930 0 6000)). The Last Giant drops it, as
	 * ItemLotParam2_Other 309600 does in DS2 (the user's request, 2026-11-10).
	 */
	public static final Item SOUL_OF_THE_LAST_GIANT = register("soul_of_the_last_giant",
			new SoulItem(6000, new Item.Properties().stacksTo(99)));

	/**
	 * DS2 catalysts (docs/research/catalysts.md): WeaponParam 3800000 Sorcerer's Staff, 4010000 Cleric's Sacred Chime,
	 * 5400000 Pyromancy Flame (ItemParam rows of the same id: icon +0x00 = the id, stack +0x4a = 1). They attack as the
	 * weapons.json entries of the same name (assigned in {@link #init()}); casting needs datapack spells. Requested by
	 * the user for 0.3.1 (2026-11-09); recipes are the user's design (data/soulsmode/recipe/<name>.json), no loot.
	 */
	public static final Item SORCERERS_STAFF = register("sorcerers_staff", new Item(new Item.Properties().stacksTo(1)));
	public static final Item CLERICS_CHIME = register("clerics_chime", new Item(new Item.Properties().stacksTo(1)));
	public static final Item PYROMANCY_FLAME = register("pyromancy_flame", new Item(new Item.Properties().stacksTo(1)));

	private SoulsItems() {
	}

	/**
	 * Titanite (docs/research/titanite.md section 1, ItemParam 60970000 / 60975000 / 60980000 / 60990000 / 61000000):
	 * reinforcement materials (dev.soulsmode.SoulsUpgrade). The slab also places the titanite slab block.
	 */
	public static final Item TITANITE_SHARD = register("titanite_shard", new Item(new Item.Properties()));
	public static final Item LARGE_TITANITE_SHARD = register("large_titanite_shard", new Item(new Item.Properties()));
	public static final Item TITANITE_CHUNK = register("titanite_chunk", new Item(new Item.Properties()));
	public static final Item TITANITE_SLAB = register("titanite_slab",
			new net.minecraft.world.item.BlockItem(dev.soulsmode.block.SoulsBlocks.TITANITE_SLAB, new Item.Properties()));
	/** With the enchantment glint (the user's design, 2026-11-09). */
	public static final Item TWINKLING_TITANITE = register("twinkling_titanite", new Item(new Item.Properties()
			.component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
	public static final Item TITANITE_BLOCK = register("titanite_block",
			new net.minecraft.world.item.BlockItem(dev.soulsmode.block.SoulsBlocks.TITANITE_BLOCK, new Item.Properties()));

	private static Item register(String name, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, SoulsMode.id(name), item);
	}

	public static void init() {
		dev.soulsmode.api.SoulsWeapons.assign(SORCERERS_STAFF, dev.soulsmode.api.SoulsWeapons.id("sorcerers_staff"));
		dev.soulsmode.api.SoulsWeapons.assign(CLERICS_CHIME, dev.soulsmode.api.SoulsWeapons.id("clerics_chime"));
		dev.soulsmode.api.SoulsWeapons.assign(PYROMANCY_FLAME, dev.soulsmode.api.SoulsWeapons.id("pyromancy_flame"));
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(entries -> {
			entries.accept(HUMAN_EFFIGY);
			entries.accept(BONFIRE_ASCETIC);
			entries.accept(HOMEWARD_BONE);
			entries.accept(FRAGRANT_BRANCH);
			entries.accept(SOUL_OF_A_LOST_UNDEAD);
			entries.accept(LARGE_SOUL_OF_A_LOST_UNDEAD);
			entries.accept(SOUL_OF_A_NAMELESS_SOLDIER);
			entries.accept(LARGE_SOUL_OF_A_NAMELESS_SOLDIER);
			entries.accept(SOUL_OF_A_GIANT);
			entries.accept(SOUL_OF_THE_LAST_GIANT);
		});
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
			entries.accept(TITANITE_SHARD);
			entries.accept(LARGE_TITANITE_SHARD);
			entries.accept(TITANITE_CHUNK);
			entries.accept(TITANITE_SLAB);
			entries.accept(TWINKLING_TITANITE);
			entries.accept(RESIN_BOTTLE);
			entries.accept(SOLID_RESIN);
			entries.accept(INCANDESCENT_IRON_INGOT);
			entries.accept(INCANDESCENT_GOLD_INGOT);
			entries.accept(INCANDESCENT_COPPER_INGOT);
			entries.accept(INCANDESCENT_NETHERITE_SCRAP);
		});
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.BUILDING_BLOCKS).register(entries -> entries.accept(TITANITE_BLOCK));
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> {
			entries.accept(LLOYDS_TALISMAN);
			entries.accept(FIREBOMB);
			entries.accept(ALLURING_SKULL);
			entries.accept(CRACKED_RED_EYE_ORB);
			entries.accept(BLACK_SEPARATION_CRYSTAL);
			entries.accept(SORCERERS_STAFF);
			entries.accept(CLERICS_CHIME);
			entries.accept(PYROMANCY_FLAME);
			entries.accept(CHARCOAL_PINE_RESIN);
			entries.accept(ROTTEN_PINE_RESIN);
			entries.accept(GOLD_PINE_RESIN);
			entries.accept(DARK_PINE_RESIN);
		});
	}
}
