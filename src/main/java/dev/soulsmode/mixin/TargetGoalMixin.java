package dev.soulsmode.mixin;

import dev.soulsmode.SoulsPerception;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DS2 perception (SoulsPerception): a perceiving mob keeps a Souls Mode player as its target while it remembers it
 * (DS2's memory instead of vanilla's follow range and 3 s unseen limit).
 */
@Mixin(TargetGoal.class)
public abstract class TargetGoalMixin {
	@Shadow
	@Final
	protected Mob mob;
	@Shadow
	protected LivingEntity targetMob;

	@Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
	private void soulsmode$perceptionMemory(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity target = mob.getTarget() != null ? mob.getTarget() : targetMob;
		if (target == null || !SoulsPerception.gates(mob, target)) {
			return;
		}
		PlayerTeam team = mob.getTeam();
		if (!SoulsPerception.acquired(mob, (ServerPlayer) target) || !mob.canAttack(target)
				|| team != null && target.getTeam() == team) {
			cir.setReturnValue(false);
			return;
		}
		mob.setTarget(target);
		cir.setReturnValue(true);
	}
}
