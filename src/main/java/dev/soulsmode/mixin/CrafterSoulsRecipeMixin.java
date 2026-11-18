package dev.soulsmode.mixin;

import dev.soulsmode.SoulsModeRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** The crafter has no player: it never makes a Souls Mode recipe (SoulsModeRecipe). */
@Mixin(CrafterBlock.class)
public abstract class CrafterSoulsRecipeMixin {
	@Inject(method = "getPotentialResults", at = @At("RETURN"), cancellable = true)
	private static void soulsmode$noSoulsRecipes(Level level, CraftingInput input,
			CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> cir) {
		if (cir.getReturnValue().filter(h -> h.value() instanceof SoulsModeRecipe).isPresent()) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
