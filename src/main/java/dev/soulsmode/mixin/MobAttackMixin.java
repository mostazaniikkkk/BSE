package dev.soulsmode.mixin;

import dev.soulsmode.SoulsIllagers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Illagers hit Souls Mode players with the player's DS2 attacks (SoulsIllagers): the hit waits for the window. */
@Mixin(Mob.class)
public class MobAttackMixin {
	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void soulsmode$illagerAttack(Entity target, CallbackInfoReturnable<Boolean> cir) {
		Mob mob = (Mob) (Object) this;
		if (!mob.level().isClientSide() && !SoulsIllagers.allowHit(mob) && SoulsIllagers.applies(mob, target)) {
			SoulsIllagers.request(mob, (LivingEntity) target);
			cir.setReturnValue(false);
		}
	}
}
