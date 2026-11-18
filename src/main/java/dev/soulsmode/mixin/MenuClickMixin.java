package dev.soulsmode.mixin;

import dev.soulsmode.SoulsIncandescent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Incandescent minerals (SoulsIncandescent): a Souls Mode player's click on a furnace result slot takes the
 * incandescent version, whatever the click type.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class MenuClickMixin {
	@Shadow
	@Final
	public net.minecraft.core.NonNullList<Slot> slots;

	private Slot soulsmode$hotSlot;

	@Inject(method = "clicked", at = @At("HEAD"))
	private void soulsmode$heat(int index, int button, ClickType type, Player player, CallbackInfo ci) {
		soulsmode$hotSlot = null;
		if (player instanceof ServerPlayer sp && index >= 0 && index < slots.size()) {
			soulsmode$hotSlot = SoulsIncandescent.beforeClick(sp, slots.get(index));
		}
	}

	@Inject(method = "clicked", at = @At("RETURN"))
	private void soulsmode$restore(int index, int button, ClickType type, Player player, CallbackInfo ci) {
		SoulsIncandescent.afterClick(soulsmode$hotSlot);
		soulsmode$hotSlot = null;
	}
}
