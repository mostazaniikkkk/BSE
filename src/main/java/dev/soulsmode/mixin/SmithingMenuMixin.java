package dev.soulsmode.mixin;

import dev.soulsmode.SoulsUpgrade;
import dev.soulsmode.item.ReinforceRecipe;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Titanite reinforcement (ReinforceRecipe, SoulsUpgrade): the result can only be taken with the experience levels the
 * next level costs (N levels for +N; creative mode is free), and taking it uses that level's whole material count
 * (vanilla takes one of each input).
 */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin extends ItemCombinerMenu {
	@Shadow
	@Nullable
	private RecipeHolder<SmithingRecipe> selectedRecipe;

	private SmithingMenuMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) {
		super(type, id, inventory, access);
	}

	@Inject(method = "mayPickup", at = @At("RETURN"), cancellable = true)
	private void soulsmode$experience(Player player, boolean hasStack, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() || selectedRecipe == null || !(selectedRecipe.value() instanceof ReinforceRecipe)) {
			return;
		}
		SoulsUpgrade.Step step = SoulsUpgrade.next(inputSlots.getItem(1));
		if (step == null || !player.getAbilities().instabuild && player.experienceLevel < step.experience()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void soulsmode$pay(Player player, ItemStack result, CallbackInfo ci) {
		if (selectedRecipe == null || !(selectedRecipe.value() instanceof ReinforceRecipe)) {
			return;
		}
		SoulsUpgrade.Step step = SoulsUpgrade.next(inputSlots.getItem(1));
		if (step == null) {
			return;
		}
		if (!player.getAbilities().instabuild) {
			player.giveExperienceLevels(-step.experience());
		}
		// Vanilla's onTake removes one; the rest of the level's count here.
		ItemStack addition = inputSlots.getItem(2);
		addition.shrink(step.count() - 1);
		inputSlots.setItem(2, addition);
	}
}
