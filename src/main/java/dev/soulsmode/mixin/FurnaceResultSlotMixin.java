package dev.soulsmode.mixin;

import dev.soulsmode.SoulsIncandescent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Incandescent minerals give no experience out of the furnace (it drops when they are quenched): the furnace forgets
 * the recipes of the finished minerals instead of popping their experience; the rest pops as usual.
 */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {
	@Shadow
	@Final
	private Player player;
	@Shadow
	private int removeCount;

	@Inject(method = "checkTakeAchievements", at = @At("HEAD"), cancellable = true)
	private void soulsmode$noExperience(ItemStack stack, CallbackInfo ci) {
		if (!SoulsIncandescent.isHot(stack) || !(player instanceof ServerPlayer sp)
				|| !(((Slot) (Object) this).container instanceof AbstractFurnaceBlockEntity furnace)) {
			return;
		}
		var used = ((FurnaceRecipesAccessor) furnace).soulsmode$recipesUsed();
		var recipes = sp.serverLevel().getRecipeManager();
		used.keySet().removeIf(id -> recipes.byKey(id)
				.map(h -> SoulsIncandescent.finishedMineral(h.value().getResultItem(sp.registryAccess()))).orElse(false));
		furnace.setChanged();
		removeCount = 0;
		ci.cancel();
	}
}
