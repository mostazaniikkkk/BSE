package dev.soulsmode.api;

import dev.soulsmode.SoulsMode;
import dev.soulsmode.ds2.Moveset;
import dev.soulsmode.content.SoulsContent;
import dev.soulsmode.ds2.Weapons;
import dev.soulsmode.item.SoulsComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Public API: which DS2 weapon an item attacks as in Souls Mode.
 * <p>
 * Like DS2, the moveset belongs to each weapon (WeaponParam -> WeaponActionCategoryParam, docs/research/weapons.md).
 * Built in, as {@code soulsmode:<name>} ({@link #BUILT_IN}): swords → {@code longsword} (DS2 Longsword), axes →
 * {@code battle_axe}, pickaxes → {@code pickaxe}, hoes → {@code silverblack_sickle}, mace → {@code mace}, trident → {@code spear}; anything else →
 * {@code fists}. Mods can point their items at any registered weapon, or register their own:
 * <pre>{@code
 * SoulsWeapons.assign(MyItems.KATANA, ResourceLocation.fromNamespaceAndPath("mymod", "katana"));
 * SoulsWeapons.registerMoveset(ResourceLocation.fromNamespaceAndPath("mymod", "katana"), new Moveset(right, left));
 * }</pre>
 * Each {@link dev.soulsmode.ds2.Attack} names its Player Animation Library animation (a JSON file in
 * {@code assets/<ns>/player_animations/}); left-hand attacks play it mirrored. Call these from a common (both sides)
 * initializer: client and server must agree.
 */
public final class SoulsWeapons {
	/** Moveset used by anything without an assignment. */
	public static final ResourceLocation DEFAULT = id("fists");

	private static final Map<ResourceLocation, Moveset> MOVESETS = new LinkedHashMap<>();
	private static final Map<ResourceLocation, Weapons.GripData> GRIPS = new LinkedHashMap<>();
	private static final Map<Item, ResourceLocation> ITEMS = new LinkedHashMap<>();
	private static final Map<TagKey<Item>, ResourceLocation> TAGS = new LinkedHashMap<>();
	private static final List<Function<ItemStack, ResourceLocation>> RESOLVERS = new ArrayList<>();
	/** Ids of the DS2 weapon classes shipped with the mod. */
	public static final Set<ResourceLocation> BUILT_IN;

	static {
		Weapons.load().forEach((name, moveset) -> MOVESETS.put(id(name), moveset));
		Weapons.grips().forEach((name, grip) -> GRIPS.put(id(name), grip));
		BUILT_IN = Collections.unmodifiableSet(new java.util.LinkedHashSet<>(MOVESETS.keySet()));
		// Minecraft item -> DS2 weapon, chosen by the user. One DS2 weapon per material (the user's table, 2026-10-31):
		// each brings its own requirements, damage, scaling and moves; other swords and axes use the tag's.
		assign(Items.WOODEN_SWORD, id("broken_straight_sword"));
		assign(Items.STONE_SWORD, id("puzzling_stone_sword"));
		assign(Items.IRON_SWORD, id("longsword"));
		assign(Items.GOLDEN_SWORD, id("shortsword"));
		assign(Items.DIAMOND_SWORD, id("drakekeepers_sword"));
		assign(Items.NETHERITE_SWORD, id("red_rust_sword"));
		assign(Items.WOODEN_AXE, id("hand_axe"));
		assign(Items.STONE_AXE, id("infantry_axe"));
		assign(Items.IRON_AXE, id("battle_axe"));
		assign(Items.GOLDEN_AXE, id("bandit_axe"));
		assign(Items.DIAMOND_AXE, id("dragonslayers_crescent_axe"));
		assign(Items.NETHERITE_AXE, id("gyrm_axe"));
		assign(ItemTags.SWORDS, id("longsword"));
		assign(ItemTags.AXES, id("battle_axe"));
		assign(ItemTags.PICKAXES, id("pickaxe"));
		// Hoes: DS2's Silverblack Sickle (the user's choice, 2026-11-09; docs/research/scythes.md). They still till
		// (SoulsTools).
		assign(ItemTags.HOES, id("silverblack_sickle"));
		assign(Items.MACE, id("mace"));
		assign(Items.STICK, id("club"));
		assign(Items.TRIDENT, id("spear"));
		// The bow is DS2's Short Bow (docs/research/bow.md, SoulsBow).
		assign(Items.BOW, id("short_bow"));
		// The crossbow is DS2's Light Crossbow (docs/research/crossbow.md).
		assign(Items.CROSSBOW, id("light_crossbow"));
		assign(Items.SHIELD, id("shield"));
	}

	private SoulsWeapons() {
	}

	public static ResourceLocation id(String builtInClass) {
		return SoulsMode.id(builtInClass);
	}

	/** Registers (or replaces) a moveset. */
	public static synchronized void registerMoveset(ResourceLocation id, Moveset moveset) {
		MOVESETS.put(id, moveset);
	}

	/** A moveset by id: datapack weapons (dev.soulsmode.content.SoulsContent) first, then registered ones. */
	public static synchronized Optional<Moveset> moveset(ResourceLocation id) {
		Weapons.Def pack = SoulsContent.weapon(id);
		return Optional.ofNullable(pack != null ? pack.moveset() : MOVESETS.get(id));
	}

	/** Registers (or replaces) a moveset's two-handing data; without it, grip changes have no animation or stance. */
	public static synchronized void registerGrip(ResourceLocation id, Weapons.GripData grip) {
		GRIPS.put(id, grip);
	}

	public static synchronized Optional<Weapons.GripData> grip(ResourceLocation id) {
		Weapons.Def pack = SoulsContent.weapon(id);
		return Optional.ofNullable(pack != null ? pack.grip() : GRIPS.get(id));
	}

	public static synchronized Set<ResourceLocation> movesets() {
		Set<ResourceLocation> out = new java.util.LinkedHashSet<>(MOVESETS.keySet());
		out.addAll(SoulsContent.weaponIds());
		return Set.copyOf(out);
	}

	/** An item attacks with this moveset (beats tag assignments). */
	public static synchronized void assign(Item item, ResourceLocation moveset) {
		ITEMS.put(item, moveset);
	}

	/** Every item in the tag attacks with this moveset; earlier tag assignments win when an item is in several. */
	public static synchronized void assign(TagKey<Item> tag, ResourceLocation moveset) {
		TAGS.putIfAbsent(tag, moveset);
	}

	/**
	 * Custom rule, checked before item and tag assignments (latest registered first). Return null to pass, e.g. for
	 * weapons whose moveset depends on components.
	 */
	public static synchronized void registerResolver(Function<ItemStack, ResourceLocation> resolver) {
		RESOLVERS.add(0, resolver);
	}

	/** The moveset the stack attacks with; unknown ids fall back to {@link #DEFAULT}. */
	public static synchronized ResourceLocation movesetFor(ItemStack stack) {
		ResourceLocation id = resolve(stack);
		return id != null && (MOVESETS.containsKey(id) || SoulsContent.weapon(id) != null) ? id : DEFAULT;
	}

	/**
	 * Order: the stack's {@code soulsmode:weapon} component, a datapack binding of its item or tag, then the resolvers,
	 * item and tag assignments made in code.
	 */
	private static ResourceLocation resolve(ItemStack stack) {
		ResourceLocation component = stack.get(SoulsComponents.WEAPON);
		if (component != null) {
			return component;
		}
		ResourceLocation bound = SoulsContent.boundWeapon(stack);
		if (bound != null) {
			return bound;
		}
		for (Function<ItemStack, ResourceLocation> resolver : RESOLVERS) {
			ResourceLocation id = resolver.apply(stack);
			if (id != null) {
				return id;
			}
		}
		ResourceLocation id = ITEMS.get(stack.getItem());
		if (id != null) {
			return id;
		}
		for (Map.Entry<TagKey<Item>, ResourceLocation> entry : TAGS.entrySet()) {
			if (stack.is(entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}


	/** For debugging: the registry name of an item's moveset. */
	public static String describe(Item item) {
		return BuiltInRegistries.ITEM.getKey(item) + " -> " + movesetFor(new ItemStack(item));
	}
}
