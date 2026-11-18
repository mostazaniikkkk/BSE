package dev.soulsmode.mixin;

import dev.soulsmode.SoulsIncandescent;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Incandescent minerals on the ground: quenched in a water cauldron, cooled after 5 s (SoulsIncandescent). */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void soulsmode$incandescent(CallbackInfo ci) {
		SoulsIncandescent.itemTick((ItemEntity) (Object) this);
	}
}
