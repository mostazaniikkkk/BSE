package dev.soulsmode;

import dev.soulsmode.ds2.WeaponEffect;
import net.minecraft.server.level.ServerPlayer;

/**
 * A weapon's own SpEffect on its hand's hits (dev.soulsmode.ds2.WeaponEffect; docs/research/weapons.md "Efecto del
 * arma"): the product of its attack-rate curves, multiplying S. The counters: the souls held (ExperienceSouls, the
 * mod's souls), the hollowing counter (SoulsHollow) and sin, which the mod does not have (always 0).
 */
public final class SoulsWeaponEffects {
	private SoulsWeaponEffects() {
	}

	/** The factor on S of a hit made with {@code weaponKey} (1 without an own SpEffect). */
	public static float statFactor(ServerPlayer player, String weaponKey) {
		WeaponEffect effect = WeaponEffect.of(weaponKey);
		if (effect == null || effect.rates().isEmpty()) {
			return 1;
		}
		float f = 1;
		for (WeaponEffect.Rate r : effect.rates()) {
			float value = switch (r.source()) {
				case WeaponEffect.SOULS -> ExperienceSouls.get(player);
				case WeaponEffect.HOLLOWING -> SoulsHollow.count(player);
				default -> 0;
			};
			f *= r.at(value);
		}
		return f;
	}
}
