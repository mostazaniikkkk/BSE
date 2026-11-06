package dev.soulsmode.client.mixin;

import dev.soulsmode.client.InventoryStats;
import dev.soulsmode.client.StaminaHud;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * In Souls Mode the stats panel (InventoryStats) sits right of the inventory, where vanilla draws the active effects:
 * the effects move right of the panel (vanilla then picks the compact list, or none, by the room left).
 */
@Mixin(EffectRenderingInventoryScreen.class)
public abstract class EffectRenderingInventoryScreenMixin {
	@ModifyVariable(method = "renderEffects(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("STORE"), ordinal = 2)
	private int soulsmode$besideStats(int x) {
		return (Object) this instanceof InventoryScreen && StaminaHud.soulsMode()
				? x + InventoryStats.WIDTH + InventoryStats.GAP : x;
	}
}
