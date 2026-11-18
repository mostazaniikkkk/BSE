package dev.soulsmode;

import dev.soulsmode.ds2.DerivedStats;
import dev.soulsmode.ds2.Ds2Data;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Applies DS2 derived stats to the vanilla player. Only max HP has a vanilla counterpart so far;
 * stamina and equip load wait for their own systems.
 */
public final class SoulsStats {
	/**
	 * Design choice, not DS2 data: DS2 HP per vanilla health point (half a heart). With 40, a Deprived
	 * (776 HP) has ~19.4, close to vanilla's 20, and all-99 attributes (2505 HP) reach ~62.6.
	 */
	public static final double DS2_HP_PER_HEALTH = 40.0;

	/**
	 * DS2 HP per health point of a Minecraft mob hit by a player (the user's rule, 2026-11-17): a zombie's 20 health
	 * stands for the Hollow Soldier's 320 HP (EnemyParam 102000 +0x28), so 16 (SoulsAttack.mobDamage).
	 */
	public static final double MOB_HP_PER_HEALTH = 16.0;
	private static final ResourceLocation MAX_HEALTH_MODIFIER = SoulsMode.id("ds2_max_hp");

	private SoulsStats() {
	}

	/** Re-applies (or removes) the modifiers for the player's current mode and attributes. */
	public static void apply(ServerPlayer player) {
		SoulsStamina.apply(player);
		SoulsMagic.refresh(player);
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null) {
			return;
		}
		var data = SoulsGameMode.isActive(player) ? SoulsPlayer.data(player) : java.util.Optional.<SoulsData>empty();
		if (data.isEmpty()) {
			maxHealth.removeModifier(MAX_HEALTH_MODIFIER);
			clampHealth(player);
			return;
		}
		DerivedStats derived = DerivedStats.compute(data.get().toAttributes(), Ds2Data.physicalStats());
		// Hollowing (FUN_140202ca0): max(1, ceil(f x max HP)).
		// The Blue Seal as an armour enchantment (SoulsRings): chr+0x170 = derived x b8+0x3d8 (1.03).
		double target = Math.max(1, Math.ceil(SoulsHollow.maxHpFactor(player) * derived.maxHp()
				* SoulsRings.maxHpFactor(player) - 1e-6)) / DS2_HP_PER_HEALTH;
		// Permanent so it is saved before health on logout; otherwise health would be clamped to 20 on login.
		maxHealth.addOrReplacePermanentModifier(new AttributeModifier(MAX_HEALTH_MODIFIER,
				target - maxHealth.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
		clampHealth(player);
	}

	private static void clampHealth(ServerPlayer player) {
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}
}
