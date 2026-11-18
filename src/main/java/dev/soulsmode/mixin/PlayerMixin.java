package dev.soulsmode.mixin;

import dev.soulsmode.SoulsAttack;
import dev.soulsmode.SoulsGameMode;
import dev.soulsmode.SoulsPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
	/** Experience is souls in Souls Mode, so every gain also counts towards soul memory. */
	@Inject(method = "giveExperiencePoints", at = @At("HEAD"))
	private void soulsmode$trackSoulMemory(int amount, CallbackInfo ci) {
		if (amount > 0 && (Object) this instanceof ServerPlayer player && SoulsGameMode.isActive(player)) {
			SoulsPlayer.onSoulsGained(player, amount);
		}
	}

	/** DS2 guard: a held guard reduces the hit by the guarding weapon's guard rate. */
	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
	private float soulsmode$guard(float amount, DamageSource source) {
		if ((Object) this instanceof ServerPlayer player && SoulsGameMode.isActive(player)) {
			// b8+0x3bc index 26 (A+0x78): the victim factor every hit is multiplied by (speffects.md s.11.7).
			// Blue Tearstone Ring as an armour enchantment (SoulsRings): the same A+0x78 factor at <= 30 % HP.
			return SoulsAttack.guarded(player, source, amount) * dev.soulsmode.SoulsSpEffects.damageTakenRate(player)
					* dev.soulsmode.SoulsRings.damageTakenFactor(player);
		}
		return amount;
	}

	/** In Souls Mode held souls go into the bloodstain on death instead of dropping as orbs. */
	@Inject(method = "getBaseExperienceReward", at = @At("HEAD"), cancellable = true)
	private void soulsmode$noOrbsOnDeath(CallbackInfoReturnable<Integer> cir) {
		if ((Object) this instanceof ServerPlayer player && SoulsGameMode.isActive(player)) {
			cir.setReturnValue(0);
		}
	}
}
