package dev.soulsmode.mixin;

import dev.soulsmode.SoulsUpgrade;
import dev.soulsmode.item.SoulsComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The user's rule (2026-11-09): two reinforced items combined at the anvil give the higher of their two levels, not a
 * sum or an average.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {
	private AnvilMenuMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) {
		super(type, id, inventory, access);
	}

	@Inject(method = "createResult", at = @At("TAIL"))
	private void soulsmode$highestLevel(CallbackInfo ci) {
		ItemStack result = resultSlots.getItem(0);
		if (result.isEmpty()) {
			return;
		}
		int level = Math.max(SoulsUpgrade.level(inputSlots.getItem(0)), SoulsUpgrade.level(inputSlots.getItem(1)));
		if (level > 0) {
			result.set(SoulsComponents.REINFORCEMENT, level);
		} else {
			result.remove(SoulsComponents.REINFORCEMENT);
		}
	}
}
