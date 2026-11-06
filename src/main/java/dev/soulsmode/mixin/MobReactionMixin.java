package dev.soulsmode.mixin;

import dev.soulsmode.SoulsHitReaction;
import dev.soulsmode.SoulsPerception;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mob in a damage reaction (SoulsHitReaction) runs no goals: it neither moves nor attacks. Every mob's AI step
 * first runs its DS2 perception (SoulsPerception), which also adds the investigation goal at construction.
 */
@Mixin(Mob.class)
public class MobReactionMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void soulsmode$perceptionGoals(EntityType<? extends Mob> type, Level level, CallbackInfo ci) {
		SoulsPerception.addGoals((Mob) (Object) this);
	}

	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void soulsmode$hitReaction(CallbackInfo ci) {
		Mob mob = (Mob) (Object) this;
		SoulsPerception.tickMob(mob);
		if (SoulsHitReaction.frozen(mob)) {
			mob.getNavigation().stop();
			mob.setZza(0);
			mob.setXxa(0);
			mob.setYya(0);
			mob.setJumping(false);
			ci.cancel();
		}
	}
}
