package dev.soulsmode;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * What an infusion enchantment does on a hit beyond the attack rating (SoulsUpgrade.infusion; the attack rating side is
 * Ds2Damage.attackParts). Poison: the user's choice (2026-11-09), the same as the Rotten Pine Resin: Minecraft's
 * Poison I for 5 s (SoulsResin), since the victim's DS2 poison build-up is not ported.
 */
public final class SoulsInfusions {
	/** The Rotten Pine Resin's poison (SoulsResin.POISON_TICKS): 5 s. */
	private static final int POISON_TICKS = 5 * 20;

	private SoulsInfusions() {
	}

	public static void onHit(ServerPlayer player, ItemStack weapon, LivingEntity target) {
		int infusion = SoulsUpgrade.infusion(weapon);
		String key = infusion == 0 ? null : SoulsUpgrade.weaponKey(weapon);
		if (key == null || !dev.soulsmode.ds2.Ds2Damage.infusionAllowed(key, infusion)) {
			return;
		}
		if (infusion == 5) {
			target.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0), player);
		}
	}
}
