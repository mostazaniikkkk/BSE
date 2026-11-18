package dev.soulsmode.mixin;

import dev.soulsmode.SoulsGameMode;
import dev.soulsmode.SoulsModeRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Souls Mode recipes (SoulsModeRecipe) give no result to a player outside Souls Mode (table and inventory grid). */
@Mixin(CraftingMenu.class)
public abstract class SoulsModeRecipeMixin {
	@Redirect(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/inventory/ResultContainer;setRecipeUsed(Lnet/minecraft/world/level/Level;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/crafting/RecipeHolder;)Z"))
	private static boolean soulsmode$soulsOnly(ResultContainer result, Level level, ServerPlayer player,
			RecipeHolder<?> recipe) {
		if (recipe.value() instanceof SoulsModeRecipe && !SoulsGameMode.isActive(player)) {
			return false;
		}
		return result.setRecipeUsed(level, player, recipe);
	}
}
