package dev.soulsmode;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * DS2 rings ported as armour enchantments (the user's rule, 2026-11-10: the Way of Blue's rings work as enchantments with
 * the same effects; docs/research/covenants.md). The effect counts while a worn armour piece has the enchantment; like
 * a ring, it does not stack: the highest level among the pieces counts.
 * <ul>
 * <li>{@code soulsmode:blue_seal}: SpEffectRing 40740000 {@code 1000[01] (7, 0, 1.03)}: max HP x 1.03 (b8+0x3d8).</li>
 * <li>{@code soulsmode:bloodbite}: 40110000 / +1 40110001 {@code 100090[04] (0, 6, 15 / 30)}: bleed absorption
 * +0.15 / +0.30 (level 1 / 2).</li>
 * <li>{@code soulsmode:blue_tearstone}: 40280000 {@code 1000[00] (5, 0, 30)} + {@code 1000[01] (28, 0, 0.65)}: with
 * HP at or under 30 % of its maximum, all damage taken x 0.65 (FUN_1401373b0 -> A+0x78).</li>
 * </ul>
 */
public final class SoulsRings {
	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
			EquipmentSlot.FEET};
	/** SpEffectRing 40740000 1000[01] (7, 0, 1.03). */
	public static final float BLUE_SEAL_HP = 1.03f;
	/** 100090[04] (0, 6, 15) per level (40110000 +0: 15, +1: 30). */
	public static final float BLOODBITE_PER_LEVEL = 0.15f;
	/** 1000[00] (5, 0, 30) and 1000[01] (28, 0, 0.65). */
	public static final float TEARSTONE_HP_PERCENT = 30;
	public static final float TEARSTONE_RATE = 0.65f;

	private SoulsRings() {
	}

	/** The highest level of {@code soulsmode:<name>} on the worn armour. */
	public static int level(LivingEntity entity, String name) {
		int best = 0;
		for (EquipmentSlot slot : ARMOUR) {
			ItemStack stack = entity.getItemBySlot(slot);
			for (var e : stack.getEnchantments().entrySet()) {
				var key = e.getKey().unwrapKey().orElse(null);
				if (key != null && SoulsMode.MOD_ID.equals(key.location().getNamespace())
						&& name.equals(key.location().getPath())) {
					best = Math.max(best, e.getIntValue());
				}
			}
		}
		return best;
	}

	public static float maxHpFactor(LivingEntity entity) {
		return level(entity, "blue_seal") > 0 ? BLUE_SEAL_HP : 1;
	}

	public static float bleedAbsorption(LivingEntity entity) {
		return level(entity, "bloodbite") * BLOODBITE_PER_LEVEL;
	}

	/** FUN_1401373b0: (s8) b8+0x30a >= HP / max x 100 -> factor x b8+0x42c. */
	public static float damageTakenFactor(LivingEntity entity) {
		if (level(entity, "blue_tearstone") <= 0 || entity.getMaxHealth() <= 0) {
			return 1;
		}
		return TEARSTONE_HP_PERCENT >= entity.getHealth() / entity.getMaxHealth() * 100 ? TEARSTONE_RATE : 1;
	}
}
