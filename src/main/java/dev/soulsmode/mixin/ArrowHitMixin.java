package dev.soulsmode.mixin;

import dev.soulsmode.SoulsBow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A DS2 bow / crossbow shot (SoulsBow) is Minecraft's arrow; hitting an entity deals DS2's damage instead. */
@Mixin(AbstractArrow.class)
public abstract class ArrowHitMixin {
	@Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
	private void soulsmode$ds2Hit(EntityHitResult result, CallbackInfo ci) {
		AbstractArrow self = (AbstractArrow) (Object) this;
		if (!self.level().isClientSide() && SoulsBow.onArrowHit(self, result.getEntity())) {
			self.discard();
			ci.cancel();
		}
	}
}
