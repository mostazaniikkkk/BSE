package dev.soulsmode;

import dev.soulsmode.api.SoulsWeapons;
import dev.soulsmode.ds2.Ds2Damage;
import dev.soulsmode.item.SoulsComponents;
import dev.soulsmode.item.SoulsItems;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Titanite reinforcement (CHANGELOG "Implementación de la titanita"; docs/research/titanite.md). The user's design
 * (2026-11-09) on top of DS2's data:
 * <ul>
 * <li>The level is stored on the stack ({@link SoulsComponents#REINFORCEMENT}) and raised at the smithing table, one
 * level at a time, without a template; mejorar a +N cuesta N niveles de experiencia.</li>
 * <li>Weapons: maximum by material tier, +5 wood / stone / gold / the stick, +10 iron / diamond and items without a
 * material, +15 netherite (gold and the stick moved to +5 by the user, 2026-11-09). The tier's maximum is the DS2 weapon's maximum (+10, or +5 for the twinkling weapons): a level
 * n of a tier T is DS2 level n x max / T, which may fall between two DS2 levels.</li>
 * <li>Each material multiplies the attack rating: wood 0.67, stone 0.83, iron 1, gold 0.67, diamond 1.17, netherite
 * 1.33, the stick 0.5 (proposed by Claude from vanilla's sword damage, approved by the user); 1 without a material.</li>
 * <li>Material per level: DS2's path (ReinforceCostParam) rescaled to the tier: level n uses the row of DS2 level
 * ceil(n x max / T), so the tier's last level always takes the slab (or twinkling x5).</li>
 * <li>Armour: DS2's own maximum (+10, or +5 for the King's and Drangleic sets) and cost rows.</li>
 * </ul>
 */
public final class SoulsUpgrade {
	/** ItemParam ids of the materials the mod has (titanite.md section 1). */
	private static final int SHARD = 60970000;
	private static final int LARGE_SHARD = 60975000;
	private static final int CHUNK = 60980000;
	private static final int SLAB = 60990000;
	private static final int TWINKLING = 61000000;

	/** The next reinforcement of a stack: the material and how many, and the experience levels it costs. */
	public record Step(int level, Item material, int count, int experience) {
	}

	/** Infusion enchantments by DS2 infusion index (titanite.md 4.1); 0 = none. */
	public static final String[] INFUSIONS = {null, "fire_infusion", "magic_infusion", "lightning_infusion",
			"dark_infusion", "poison_infusion", "bleed_infusion", "raw_infusion", "enchanted_infusion", "mundane_infusion"};

	private SoulsUpgrade() {
	}

	/** The stored reinforcement level (0 when never reinforced). */
	public static int level(ItemStack stack) {
		Integer level = stack.get(SoulsComponents.REINFORCEMENT);
		return level == null ? 0 : level;
	}

	/** The DS2 weapon a stack attacks as, or null without a moveset (fists). */
	public static String weaponKey(ItemStack stack) {
		var moveset = SoulsWeapons.movesetFor(stack);
		if (moveset.equals(SoulsWeapons.DEFAULT)) {
			return null;
		}
		return SoulsAttack.damageKey(moveset);
	}

	/** The material tier's maximum level of a weapon stack: 5, 10 or 15 (the user's tiers). */
	public static int tierMax(ItemStack stack) {
		if (stack.is(Items.STICK)) {
			return 5;
		}
		if (stack.getItem() instanceof TieredItem tiered) {
			Tier tier = tiered.getTier();
			if (tier == Tiers.WOOD || tier == Tiers.STONE || tier == Tiers.GOLD) {
				return 5;
			}
			if (tier == Tiers.NETHERITE) {
				return 15;
			}
			return 10;
		}
		return 10;
	}

	/** The material's attack multiplier (1 for items without a material). */
	public static float materialFactor(ItemStack stack) {
		if (stack.is(Items.STICK)) {
			return 0.5f;
		}
		if (stack.getItem() instanceof TieredItem tiered) {
			Tier tier = tiered.getTier();
			return tier == Tiers.WOOD || tier == Tiers.GOLD ? 0.67f
					: tier == Tiers.STONE ? 0.83f
					: tier == Tiers.DIAMOND ? 1.17f
					: tier == Tiers.NETHERITE ? 1.33f : 1;
		}
		return 1;
	}

	/** The DS2 level a weapon stack attacks at: its level rescaled from its tier to the DS2 weapon's maximum. */
	public static float ds2Level(ItemStack stack) {
		int level = level(stack);
		String key = level <= 0 ? null : weaponKey(stack);
		Ds2Damage.WeaponData w = key == null ? null : Ds2Damage.weapon(key);
		if (w == null || w.maxLevel() <= 0) {
			return 0;
		}
		return Math.min(w.maxLevel(), level * w.maxLevel() / (float) tierMax(stack));
	}

	/** The maximum level of a stack, 0 when it cannot be reinforced. */
	public static int maxLevel(ItemStack stack) {
		if (stack.getItem() instanceof ArmorItem armor) {
			Ds2Damage.ArmorUpgrade up = armorUpgrade(armor);
			return up == null || !hasMaterials(up.cost()) ? 0 : up.maxLevel();
		}
		String key = weaponKey(stack);
		Ds2Damage.WeaponUpgrade up = key == null ? null : Ds2Damage.upgrade(key);
		return up == null || up.cost().length == 0 || !hasMaterials(up.cost()) ? 0 : tierMax(stack);
	}

	/** The next reinforcement of a stack, or null at the maximum or when it cannot be reinforced. */
	public static Step next(ItemStack stack) {
		int max = maxLevel(stack);
		int n = level(stack) + 1;
		if (max <= 0 || n > max) {
			return null;
		}
		int[][] cost;
		int row;
		if (stack.getItem() instanceof ArmorItem armor) {
			cost = armorUpgrade(armor).cost();
			row = n - 1;
		} else {
			cost = Ds2Damage.upgrade(weaponKey(stack)).cost();
			int ds2Max = cost.length;
			row = Math.max(1, Math.min(ds2Max, (int) Math.ceil(n * ds2Max / (double) max))) - 1;
		}
		Item material = material(cost[row][1]);
		return material == null ? null : new Step(n, material, Math.max(1, cost[row][2]), n);
	}

	/** Durability per level (the user's rule, 2026-11-09, not DS2): +10 % of the base per titanite level. */
	public static final float DURABILITY_PER_LEVEL = 0.10f;

	/** The factor on a stack's maximum durability: 1 + 10 % per level (ItemStackDurabilityMixin). */
	public static float durabilityFactor(ItemStack stack) {
		Integer level = stack.get(SoulsComponents.REINFORCEMENT);
		return level == null || level <= 0 ? 1 : 1 + DURABILITY_PER_LEVEL * level;
	}

	/** A copy of the stack one level up. */
	public static ItemStack reinforced(ItemStack stack, int level) {
		ItemStack out = stack.copyWithCount(1);
		out.set(SoulsComponents.REINFORCEMENT, level);
		return out;
	}

	/** The DS2 infusion index of a stack's infusion enchantment (0 = none). */
	public static int infusion(ItemStack stack) {
		for (var entry : stack.getEnchantments().entrySet()) {
			int i = infusionIndex(entry.getKey());
			if (i > 0) {
				return i;
			}
		}
		return 0;
	}

	/** The infusion index of an enchantment (0 when it is not one of the mod's infusions). */
	public static int infusionIndex(Holder<Enchantment> enchantment) {
		var key = enchantment.unwrapKey().orElse(null);
		if (key == null || !SoulsMode.MOD_ID.equals(key.location().getNamespace())) {
			return 0;
		}
		for (int i = 1; i < INFUSIONS.length; i++) {
			if (INFUSIONS[i].equals(key.location().getPath())) {
				return i;
			}
		}
		return 0;
	}

	/** An armour item's DS2 set piece reinforcement (the mod's material -> set mapping), or null. */
	public static Ds2Damage.ArmorUpgrade armorUpgrade(ArmorItem armor) {
		String set = armorSet(armor.getMaterial());
		int slot = armorSlot(armor.getEquipmentSlot());
		return set == null || slot < 0 ? null : Ds2Damage.armorUpgrade(set, slot);
	}

	/** The mod's DS2 armour set of a material (docs/research/equipload.md), or null. */
	public static String armorSet(Holder<ArmorMaterial> m) {
		return m.equals(ArmorMaterials.LEATHER) ? "leather"
				: m.equals(ArmorMaterials.CHAIN) ? "chainmail"
				: m.equals(ArmorMaterials.IRON) ? "iron"
				: m.equals(ArmorMaterials.GOLD) ? "gold"
				: m.equals(ArmorMaterials.DIAMOND) || m.equals(ArmorMaterials.NETHERITE) ? "diamond"
				: m.equals(ArmorMaterials.TURTLE) ? "turtle" : null;
	}

	/** DS2 piece index of an armour slot: head 0, chest 1, arms (boots) 2, legs 3. */
	public static int armorSlot(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> 0;
			case CHEST -> 1;
			case FEET -> 2;
			case LEGS -> 3;
			default -> -1;
		};
	}

	private static boolean hasMaterials(int[][] cost) {
		for (int[] row : cost) {
			if (material(row[1]) == null) {
				return false;
			}
		}
		return cost.length > 0;
	}

	/** The mod item of a DS2 material id, or null (Petrified Dragon Bone, Fire Seed... are not in the mod). */
	public static Item material(int id) {
		return switch (id) {
			case SHARD -> SoulsItems.TITANITE_SHARD;
			case LARGE_SHARD -> SoulsItems.LARGE_TITANITE_SHARD;
			case CHUNK -> SoulsItems.TITANITE_CHUNK;
			case SLAB -> SoulsItems.TITANITE_SLAB;
			case TWINKLING -> SoulsItems.TWINKLING_TITANITE;
			default -> null;
		};
	}
}
