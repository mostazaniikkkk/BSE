package dev.soulsmode.mixin;

import dev.soulsmode.item.SoulsItems;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Smelting a resin bottle (the user's design): only one bottle at a time cooks, and the empty bottle stays in the
 * input slot when the solid resin comes out.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {
	private static final int INPUT = 0;

	@Inject(method = "canBurn", at = @At("HEAD"), cancellable = true)
	private static void soulsmode$oneResinBottle(RegistryAccess registries, RecipeHolder<?> recipe,
			NonNullList<ItemStack> items, int maxStackSize, CallbackInfoReturnable<Boolean> cir) {
		ItemStack input = items.get(INPUT);
		if (input.is(SoulsItems.RESIN_BOTTLE) && input.getCount() > 1) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "burn", at = @At("RETURN"))
	private static void soulsmode$bottleStays(RegistryAccess registries, RecipeHolder<?> recipe,
			NonNullList<ItemStack> items, int maxStackSize, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && recipe != null && recipe.value().getResultItem(registries).is(SoulsItems.SOLID_RESIN)
				&& items.get(INPUT).isEmpty()) {
			items.set(INPUT, new ItemStack(Items.GLASS_BOTTLE));
		}
	}
}
