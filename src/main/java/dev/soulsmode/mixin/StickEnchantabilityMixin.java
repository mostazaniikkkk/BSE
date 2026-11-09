package dev.soulsmode.mixin;

import dev.soulsmode.SoulsStick;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The stick enchants at the enchanting table like a wooden tool (SoulsStick.ENCHANTABILITY). Only a Souls Mode stick
 * (single and damageable, SoulsStick) is enchantable at all, so plain sticks are unaffected.
 */
@Mixin(Item.class)
public abstract class StickEnchantabilityMixin {
	@Inject(method = "getEnchantmentValue", at = @At("HEAD"), cancellable = true)
	private void soulsmode$stick(CallbackInfoReturnable<Integer> cir) {
		if ((Object) this == Items.STICK) {
			cir.setReturnValue(SoulsStick.ENCHANTABILITY);
		}
	}
}
