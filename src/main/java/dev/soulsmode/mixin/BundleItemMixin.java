package dev.soulsmode.mixin;

import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The bundle is behind the experimental "bundle" feature in 1.21.1. The user asked for it with all its features: it
 * needs no feature flag, so it can be crafted (data/soulsmode/recipe/bundle.json), used and shown in creative.
 */
@Mixin(Item.class)
public abstract class BundleItemMixin {
	@Inject(method = "requiredFeatures", at = @At("HEAD"), cancellable = true)
	private void soulsmode$bundleEnabled(CallbackInfoReturnable<FeatureFlagSet> cir) {
		if ((Object) this instanceof BundleItem) {
			cir.setReturnValue(FeatureFlagSet.of());
		}
	}
}
