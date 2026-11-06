package dev.soulsmode.mixin;

import dev.soulsmode.SoulsPerception;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.DefendVillageTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The golem defends the village against a Souls Mode player only once it perceives it (SoulsPerception, Ogre). */
@Mixin(DefendVillageTargetGoal.class)
public abstract class DefendVillageTargetGoalMixin {
	@Shadow
	@Final
	private IronGolem golem;
	@Shadow
	private LivingEntity potentialTarget;

	@Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
	private void soulsmode$perception(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && potentialTarget != null && SoulsPerception.gates(golem, potentialTarget)
				&& !SoulsPerception.acquired(golem, (ServerPlayer) potentialTarget)) {
			cir.setReturnValue(false);
		}
	}
}
