package dev.soulsmode.client.mixin;

import dev.soulsmode.client.StaminaHud;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No recipe book in Souls Mode at the crafting table either (like InventoryScreenMixin); its button goes in InventoryStats. */
@Mixin(CraftingScreen.class)
public abstract class CraftingScreenMixin {
	@Shadow
	@Final
	private RecipeBookComponent recipeBookComponent;

	@Inject(method = "init", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;updateScreenPosition(II)I"))
	private void soulsmode$noRecipeBook(CallbackInfo ci) {
		if (StaminaHud.soulsMode() && recipeBookComponent.isVisible()) {
			((RecipeBookComponentAccessor) recipeBookComponent).soulsmode$setVisible(false);
		}
	}
}
