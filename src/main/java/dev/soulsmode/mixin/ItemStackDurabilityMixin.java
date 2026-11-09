package dev.soulsmode.mixin;

import dev.soulsmode.SoulsUpgrade;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The user's rule (2026-11-09, not DS2: for balance in Minecraft): every titanite level adds 10 % of the item's
 * durability (SoulsUpgrade.durabilityFactor). Computed on read, so the smithing table's netherite upgrade, the anvil
 * and the stick's own durability keep working from the item's base value.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackDurabilityMixin {
	@Inject(method = "getMaxDamage", at = @At("RETURN"), cancellable = true)
	private void soulsmode$reinforced(CallbackInfoReturnable<Integer> cir) {
		int base = cir.getReturnValueI();
		if (base > 0) {
			float factor = SoulsUpgrade.durabilityFactor((ItemStack) (Object) this);
			if (factor != 1) {
				cir.setReturnValue(Math.round(base * factor));
			}
		}
	}
}
