package dev.soulsmode.mixin;

import dev.soulsmode.SoulsPerception;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * DS2 perception (SoulsPerception): a perceiving mob only finds a Souls Mode player, to attack or to look at, once it
 * has perceived it. For an attack the perception stands in for vanilla's range, line of sight and visibility checks;
 * the rest (selector, canAttack, alliances) stays.
 */
@Mixin(TargetingConditions.class)
public abstract class TargetingConditionsMixin {
	@Shadow
	@Final
	private boolean isCombat;
	@Shadow
	private Predicate<LivingEntity> selector;

	@Inject(method = "test", at = @At("HEAD"), cancellable = true)
	private void soulsmode$perception(LivingEntity attacker, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		if (attacker == null || attacker == target || !SoulsPerception.gates(attacker, target)) {
			return;
		}
		if (!SoulsPerception.acquired((Mob) attacker, (ServerPlayer) target)) {
			cir.setReturnValue(false);
			return;
		}
		if (!isCombat) {
			return;
		}
		cir.setReturnValue(target.canBeSeenByAnyone() && (selector == null || selector.test(target))
				&& attacker.canAttack(target) && attacker.canAttackType(target.getType()) && !attacker.isAlliedTo(target));
	}
}
