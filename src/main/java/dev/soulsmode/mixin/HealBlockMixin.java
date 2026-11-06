package dev.soulsmode.mixin;

import dev.soulsmode.SoulsThrown;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lloyd's Talisman (the user's design): no healing of any kind inside its zone (SoulsThrown). */
@Mixin(LivingEntity.class)
public abstract class HealBlockMixin {
	@Inject(method = "heal", at = @At("HEAD"), cancellable = true)
	private void soulsmode$noHealing(float amount, CallbackInfo ci) {
		if (SoulsThrown.healBlocked((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
