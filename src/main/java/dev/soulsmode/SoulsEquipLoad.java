package dev.soulsmode;

import dev.soulsmode.api.SoulsWeapons;
import dev.soulsmode.ds2.DerivedStats;
import dev.soulsmode.ds2.Ds2Data;
import dev.soulsmode.ds2.EquipLoadRows;
import dev.soulsmode.ds2.Weapons;
import dev.soulsmode.network.StatsPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Equip load (docs/research/equipload.md). DS2 sums the weight of the equipped weapons, armor and rings
 * (FUN_14008dda0); by the user's choices (2026-10-30/31) the whole inventory counts, one weight per stack, with these
 * weights:
 * <ul>
 * <li>armor: a DS2 set per material, piece by piece (helmet = head, chestplate = chest, leggings = legs, boots = arms);
 * netherite = the diamond piece + a netherite ingot;</li>
 * <li>material per ingot = its set's weight / 24 (iron Knight 25.0, gold King's 27.1, diamond Drangleic 34.2, leather
 * 11.5); netherite ingot = 4 gold + 4 scraps that weigh as iron; materials without armor use their real density
 * against iron's (7.87);</li>
 * <li>DS2 weapons: WeaponParam +0x20 x their material against iron; netherite = diamond + a netherite ingot;</li>
 * <li>blocks: 9 units of their family's material x the block's volume (slabs, stairs, panes...); other items one unit
 * of their material (sticks are wood; food and mob drops 0.6, the user's value; the rest density 0.3).</li>
 * </ul>
 * The load slows walking, jogging and dashing, forbids the dash above 120 % and picks the roll variant (Dodge).
 */
public final class SoulsEquipLoad {
	public static final EquipLoadRows ROWS = EquipLoadRows.load();
	private static final ResourceLocation SPEED_MODIFIER = SoulsMode.id("equip_load_speed");
	/** Stats sync interval in ticks (design choice). */
	private static final int SYNC_TICKS = 10;

	private static final double IRON_DENSITY = 7.87;
	private static final double IRON = 25.0 / 24;
	private static final double GOLD = 27.1 / 24;
	private static final double DIAMOND = 34.2 / 24;
	private static final double LEATHER = 11.5 / 24;
	private static final double NETHERITE_INGOT = 4 * GOLD + 4 * IRON;

	private static final double WOOD = 0.6;
	private static final double STONE = 2.7;
	private static final double EARTH = 1.6;
	private static final double GLASS = 2.5;
	private static final double LIGHT = 0.3;
	private static final double OBSIDIAN = 2.6;
	private static final double COPPER = 8.96;
	private static final double COAL = 1.35;
	private static final double GEM = 2.7;
	/** Food and mob drops, per stack: small objects (the user's value, 2026-10-31). */
	private static final double SMALL_ORGANIC = 0.6;

	/** Helmet, chestplate, leggings, boots. */
	private static final Map<Holder<ArmorMaterial>, double[]> ARMOR = Map.of(
			ArmorMaterials.LEATHER, new double[] {1.2, 4.7, 2.8, 2.8},
			ArmorMaterials.CHAIN, new double[] {4.3, 9.7, 4.5, 3.4},
			ArmorMaterials.IRON, new double[] {4.2, 10.9, 6.4, 3.5},
			ArmorMaterials.GOLD, new double[] {3.8, 12.0, 7.1, 4.2},
			ArmorMaterials.DIAMOND, new double[] {5.9, 14.5, 7.6, 6.2},
			ArmorMaterials.TURTLE, new double[] {6.0, 6.0, 6.0, 6.0});

	private static final Map<ResourceLocation, Float> WEAPON_WEIGHTS = new HashMap<>();
	private static final Map<Item, Double> CACHE = new HashMap<>();
	private static final Map<Item, Double> ITEMS = new HashMap<>();
	private static final Set<Item> MOB_DROPS = Set.of(Items.BONE, Items.STRING, Items.GUNPOWDER, Items.ROTTEN_FLESH,
			Items.SPIDER_EYE, Items.ENDER_PEARL, Items.SLIME_BALL, Items.BLAZE_ROD, Items.GHAST_TEAR, Items.FEATHER,
			Items.RABBIT_HIDE, Items.PHANTOM_MEMBRANE, Items.MAGMA_CREAM, Items.INK_SAC, Items.GLOW_INK_SAC, Items.BREEZE_ROD,
			Items.SHULKER_SHELL, Items.PRISMARINE_SHARD, Items.PRISMARINE_CRYSTALS, Items.NAUTILUS_SHELL, Items.ARMADILLO_SCUTE);

	static {
		Weapons.weights().forEach((name, weight) -> WEAPON_WEIGHTS.put(SoulsWeapons.id(name), weight));
		ITEMS.put(Items.IRON_INGOT, IRON);
		ITEMS.put(Items.RAW_IRON, IRON);
		ITEMS.put(Items.IRON_NUGGET, IRON / 9);
		ITEMS.put(Items.GOLD_INGOT, GOLD);
		ITEMS.put(Items.RAW_GOLD, GOLD);
		ITEMS.put(Items.GOLD_NUGGET, GOLD / 9);
		ITEMS.put(Items.DIAMOND, DIAMOND);
		ITEMS.put(Items.LEATHER, LEATHER);
		ITEMS.put(Items.NETHERITE_SCRAP, IRON);
		ITEMS.put(Items.NETHERITE_INGOT, NETHERITE_INGOT);
		ITEMS.put(Items.COPPER_INGOT, unit(COPPER));
		ITEMS.put(Items.RAW_COPPER, unit(COPPER));
		ITEMS.put(Items.TURTLE_SCUTE, 6.0 / 5);
		for (Item gem : List.of(Items.EMERALD, Items.LAPIS_LAZULI, Items.QUARTZ, Items.AMETHYST_SHARD, Items.REDSTONE)) {
			ITEMS.put(gem, unit(GEM));
		}
		ITEMS.put(Items.COAL, unit(COAL));
		ITEMS.put(Items.CHARCOAL, unit(COAL));
		ITEMS.put(Items.IRON_BLOCK, 9 * IRON);
		ITEMS.put(Items.RAW_IRON_BLOCK, 9 * IRON);
		ITEMS.put(Items.GOLD_BLOCK, 9 * GOLD);
		ITEMS.put(Items.RAW_GOLD_BLOCK, 9 * GOLD);
		ITEMS.put(Items.DIAMOND_BLOCK, 9 * DIAMOND);
		ITEMS.put(Items.NETHERITE_BLOCK, 9 * NETHERITE_INGOT);
		ITEMS.put(Items.RAW_COPPER_BLOCK, 9 * unit(COPPER));
		ITEMS.put(Items.EMERALD_BLOCK, 9 * unit(GEM));
		ITEMS.put(Items.LAPIS_BLOCK, 9 * unit(GEM));
		ITEMS.put(Items.REDSTONE_BLOCK, 9 * unit(GEM));
		ITEMS.put(Items.COAL_BLOCK, 9 * unit(COAL));
		// Metal objects by their recipe's ingots.
		ITEMS.put(Items.BUCKET, 3 * IRON);
		ITEMS.put(Items.SHEARS, 2 * IRON);
		ITEMS.put(Items.MINECART, 5 * IRON);
		ITEMS.put(Items.ANVIL, 31 * IRON);
		ITEMS.put(Items.CAULDRON, 7 * IRON);
		ITEMS.put(Items.HOPPER, 5 * IRON);
		ITEMS.put(Items.COMPASS, 4 * IRON);
		ITEMS.put(Items.CLOCK, 4 * GOLD);
		ITEMS.put(Items.CHAIN, IRON * (1 + 2 / 9.0));
	}

	private SoulsEquipLoad() {
	}

	/** One item of a material at ingot volume (1/9 block), by density against iron. */
	private static double unit(double density) {
		return IRON * density / IRON_DENSITY;
	}

	/** Weight of a stack: one item's weight, whatever the count (the user's choice). */
	public static float weight(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		// Datapack content first (dev.soulsmode.content.SoulsContent), then a built-in weapon set by component.
		ResourceLocation moveset = SoulsWeapons.movesetFor(stack);
		Float pack = dev.soulsmode.content.SoulsContent.weight(stack, moveset);
		if (pack != null) {
			return pack;
		}
		if (stack.has(dev.soulsmode.item.SoulsComponents.WEAPON) && WEAPON_WEIGHTS.containsKey(moveset)) {
			return WEAPON_WEIGHTS.get(moveset);
		}
		return (float) (double) CACHE.computeIfAbsent(stack.getItem(), SoulsEquipLoad::itemWeight);
	}

	private static double itemWeight(Item item) {
		Double fixed = ITEMS.get(item);
		if (fixed != null) {
			return fixed;
		}
		if (item instanceof ArmorItem armor) {
			return armorWeight(armor);
		}
		ItemStack stack = new ItemStack(item);
		ResourceLocation moveset = SoulsWeapons.movesetFor(stack);
		float ds2 = WEAPON_WEIGHTS.getOrDefault(moveset, 0f);
		if (item == Items.STICK) {
			return unit(WOOD);
		}
		// The mod's own DS2 weapon items (the catalysts) weigh exactly their WeaponParam +0x20, 0 included (Pyromancy
		// Flame).
		if (WEAPON_WEIGHTS.containsKey(moveset)
				&& SoulsMode.MOD_ID.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
			return ds2;
		}
		// Hoes keep their recipe weight (below) while the user decides whether the Silverblack Sickle's DS2 weight
		// applies (asked 2026-11-09).
		if (ds2 > 0 && !(item instanceof net.minecraft.world.item.HoeItem)) {
			return item instanceof TieredItem tiered ? tiered(tiered.getTier(), ds2) : ds2;
		}
		if (item instanceof TieredItem tiered) {
			// Tools without a DS2 weapon (shovel, hoe...): their recipe's material pieces + 2 sticks.
			double stick = unit(WOOD);
			int pieces = item.toString().contains("hoe") ? 2 : 1;
			return tieredPieces(tiered.getTier(), pieces) + 2 * stick;
		}
		if (item instanceof BlockItem blockItem) {
			Block block = blockItem.getBlock();
			return 9 * unit(density(block)) * volume(block.defaultBlockState());
		}
		if (stack.has(DataComponents.FOOD) || MOB_DROPS.contains(item)) {
			return SMALL_ORGANIC;
		}
		return unit(LIGHT);
	}

	private static double armorWeight(ArmorItem armor) {
		int piece = switch (armor.getType()) {
			case HELMET -> 0;
			case CHESTPLATE -> 1;
			case LEGGINGS -> 2;
			case BOOTS -> 3;
			default -> -1;
		};
		Holder<ArmorMaterial> material = armor.getMaterial();
		if (piece < 0) {
			return unit(LIGHT);
		}
		if (material.equals(ArmorMaterials.NETHERITE)) {
			return ARMOR.get(ArmorMaterials.DIAMOND)[piece] + NETHERITE_INGOT;
		}
		double[] set = ARMOR.get(material);
		return set == null ? unit(LIGHT) : set[piece];
	}

	/** A DS2 weapon's weight for its Minecraft material (iron = the DS2 weight). */
	private static double tiered(Tier tier, double ds2) {
		if (tier == Tiers.NETHERITE) {
			return tiered(Tiers.DIAMOND, ds2) + NETHERITE_INGOT;
		}
		return ds2 * materialUnit(tier) / IRON;
	}

	private static double tieredPieces(Tier tier, int pieces) {
		if (tier == Tiers.NETHERITE) {
			return tieredPieces(Tiers.DIAMOND, pieces) + NETHERITE_INGOT;
		}
		return pieces * materialUnit(tier);
	}

	private static double materialUnit(Tier tier) {
		if (tier == Tiers.WOOD) {
			return unit(WOOD);
		}
		if (tier == Tiers.STONE) {
			return unit(STONE);
		}
		if (tier == Tiers.GOLD) {
			return GOLD;
		}
		if (tier == Tiers.DIAMOND) {
			return DIAMOND;
		}
		return IRON;
	}

	/** The block's family (by tags and sound type): wood, light, earth, glass, metal, obsidian or stone. */
	private static double density(Block block) {
		BlockState state = block.defaultBlockState();
		if (block == net.minecraft.world.level.block.Blocks.OBSIDIAN
				|| block == net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN) {
			return OBSIDIAN;
		}
		if (state.is(BlockTags.LEAVES) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.FLOWERS)
				|| state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.REPLACEABLE)) {
			return LIGHT;
		}
		if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) {
			return WOOD;
		}
		SoundType sound = state.getSoundType();
		if (sound == SoundType.WOOD || sound == SoundType.NETHER_WOOD || sound == SoundType.CHERRY_WOOD
				|| sound == SoundType.BAMBOO_WOOD || sound == SoundType.STEM || sound == SoundType.LADDER
				|| sound == SoundType.SCAFFOLDING || sound == SoundType.BAMBOO || sound == SoundType.CHISELED_BOOKSHELF
				|| sound == SoundType.HANGING_SIGN || sound == SoundType.NETHER_WOOD_HANGING_SIGN
				|| sound == SoundType.BAMBOO_WOOD_HANGING_SIGN || sound == SoundType.CHERRY_WOOD_HANGING_SIGN) {
			return WOOD;
		}
		if (sound == SoundType.WOOL || sound == SoundType.SNOW || sound == SoundType.POWDER_SNOW
				|| sound == SoundType.MOSS || sound == SoundType.MOSS_CARPET || sound == SoundType.AZALEA_LEAVES
				|| sound == SoundType.CHERRY_LEAVES || sound == SoundType.CROP || sound == SoundType.VINE
				|| sound == SoundType.CANDLE || sound == SoundType.COBWEB || sound == SoundType.SPONGE
				|| sound == SoundType.WET_SPONGE || sound == SoundType.SLIME_BLOCK || sound == SoundType.HONEY_BLOCK) {
			return LIGHT;
		}
		if (sound == SoundType.GRAVEL || sound == SoundType.SAND || sound == SoundType.GRASS || sound == SoundType.SOUL_SAND
				|| sound == SoundType.SOUL_SOIL || sound == SoundType.ROOTED_DIRT || sound == SoundType.MUD
				|| sound == SoundType.PACKED_MUD || sound == SoundType.MUDDY_MANGROVE_ROOTS || sound == SoundType.NYLIUM
				|| sound == SoundType.SUSPICIOUS_SAND || sound == SoundType.SUSPICIOUS_GRAVEL) {
			return EARTH;
		}
		if (sound == SoundType.GLASS || sound == SoundType.AMETHYST || sound == SoundType.AMETHYST_CLUSTER) {
			return GLASS;
		}
		if (sound == SoundType.COPPER || sound == SoundType.COPPER_BULB || sound == SoundType.COPPER_GRATE) {
			return COPPER;
		}
		if (sound == SoundType.METAL || sound == SoundType.ANVIL || sound == SoundType.LANTERN || sound == SoundType.CHAIN) {
			return IRON_DENSITY;
		}
		return STONE;
	}

	/** Fraction of a full block the block's shape fills (slabs 1/2, stairs 3/4, carpets 1/16...). */
	private static double volume(BlockState state) {
		try {
			double v = 0;
			for (AABB box : state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
				v += box.getXsize() * box.getYsize() * box.getZsize();
			}
			return v > 0 ? Math.min(1, v) : 1.0 / 16;
		} catch (RuntimeException e) {
			return 1;
		}
	}

	/** Everything the player carries: main inventory, armor and off hand, one weight per stack. */
	public static float carried(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		float total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			total += weight(inventory.getItem(i));
		}
		// FUN_14037e430: weight + (s8)b8+0x306 (buffs, 1000[00] index 1).
		return total + SoulsSpEffects.extraWeight(player);
	}

	public static float maxLoad(ServerPlayer player) {
		return SoulsPlayer.data(player)
				.map(d -> DerivedStats.compute(d.toAttributes(), Ds2Data.physicalStats()).maxEquipLoad()).orElse(0f)
				* strengthFactor(player);
	}

	/** The user's rule (2026-11-01): Strength I / II raise the max equip load by 20 % / 40 % while they last. */
	public static float strengthFactor(net.minecraft.world.entity.LivingEntity entity) {
		var strength = entity.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST);
		return strength == null ? 1 : 1 + 0.2f * (strength.getAmplifier() + 1);
	}

	/** Weight / max load (FUN_14037e430 with its multipliers at 1); 0 outside Souls Mode. */
	public static float ratio(ServerPlayer player) {
		if (!SoulsGameMode.isActive(player)) {
			return 0;
		}
		float max = maxLoad(player);
		return max > 0 ? carried(player) / max : 0;
	}

	public static boolean overloaded(ServerPlayer player) {
		return EquipLoadRows.overloaded(ratio(player));
	}

	public static void tick(MinecraftServer server) {
		boolean sync = server.getTickCount() % SYNC_TICKS == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			applySpeed(player);
			if (server.getTickCount() % 10 == 0) {
				loadEffects(player);
			}
			if (sync && SoulsGameMode.isActive(player)) {
				var data = SoulsPlayer.data(player);
				ServerPlayNetworking.send(player, new StatsPayload(
						data.map(SoulsData::attributes).orElse(List.of()), data.map(SoulsData::soulMemory).orElse(0),
						SoulsHollow.count(player), carried(player)));
			}
		}
	}

	/** The user's rule (2026-11-01): load above 70 % / 100 % / 115 % = Slowness and Mining Fatigue I / II / III. */
	private static final float[] LOAD_EFFECT_THRESHOLDS = {0.70f, 1.00f, 1.15f};
	/** Refreshed every 10 ticks while it applies, so it ends soon after the load drops. */
	private static final int LOAD_EFFECT_TICKS = 40;

	private static void loadEffects(ServerPlayer player) {
		float ratio = ratio(player);
		int amplifier = -1;
		for (float threshold : LOAD_EFFECT_THRESHOLDS) {
			if (ratio > threshold) {
				amplifier++;
			}
		}
		for (var effect : List.of(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
				net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN)) {
			var current = player.getEffect(effect);
			// Ours are ambient and short; potions and other sources are left alone.
			boolean ours = current != null && current.isAmbient() && current.getDuration() <= LOAD_EFFECT_TICKS;
			if (amplifier < 0) {
				if (ours) {
					player.removeEffect(effect);
				}
				continue;
			}
			if (ours && current.getAmplifier() != amplifier) {
				player.removeEffect(effect);
			}
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, LOAD_EFFECT_TICKS, amplifier, true,
					false, true));
		}
	}

	/**
	 * FUN_140385fe0: the speed multiplier of the current gait (sneak = walk row 3, normal = jog row 4, sprint = dash
	 * row 5), x 0.3 above 120 %, where the dash is not allowed.
	 */
	private static void applySpeed(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		if (!SoulsGameMode.isActive(player)) {
			speed.removeModifier(SPEED_MODIFIER);
			return;
		}
		float ratio = ratio(player);
		if (EquipLoadRows.overloaded(ratio) && player.isSprinting()) {
			player.setSprinting(false);
		}
		float[] row = player.isShiftKeyDown() ? ROWS.walkSpeed() : player.isSprinting() ? ROWS.dashSpeed() : ROWS.jogSpeed();
		double factor = ROWS.speed(ratio, row);
		AttributeModifier current = speed.getModifier(SPEED_MODIFIER);
		if (factor >= 1) {
			speed.removeModifier(SPEED_MODIFIER);
		} else if (current == null || current.amount() != factor - 1) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_MODIFIER, factor - 1,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
