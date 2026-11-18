package dev.soulsmode.client.mixin;

import dev.soulsmode.client.StaminaHud;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lifts the hearts and armor rows in Souls Mode so the stamina bar fits under them. */
@Mixin(Gui.class)
public abstract class GuiMixin {
	/**
	 * Aiming a bow or crossbow (BowAim): DS2's bow sight (l01_18_bow_sights.flo; pixel art, tools/gui_sprites.py)
	 * replaces Minecraft's crosshair, centred.
	 */
	@org.spongepowered.asm.mixin.injection.Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void soulsmode$bowSights(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.DeltaTracker delta,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (dev.soulsmode.client.camera.BowAim.active()) {
			int size = 49;
			com.mojang.blaze3d.systems.RenderSystem.enableBlend();
			graphics.blit(dev.soulsmode.SoulsMode.id("textures/gui/bow_sights.png"), (graphics.guiWidth() - size) / 2,
					(graphics.guiHeight() - size) / 2, 0, 0, size, size, size, size);
			com.mojang.blaze3d.systems.RenderSystem.disableBlend();
			ci.cancel();
		}
	}

	/** DS2's death banner hides the rest of the HUD while it shows (FUN_1405070d0, type 3; YouDiedClient). */
	@org.spongepowered.asm.mixin.injection.Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void soulsmode$youDied(net.minecraft.client.gui.GuiGraphics graphics,
			net.minecraft.client.DeltaTracker delta, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (dev.soulsmode.client.YouDiedClient.showing()) {
			dev.soulsmode.client.YouDiedClient.render(graphics);
			ci.cancel();
		}
	}

	@ModifyVariable(method = "renderHearts", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int soulsmode$liftHearts(int y) {
		return StaminaHud.visible() ? y - StaminaHud.HEART_LIFT : y;
	}

	@ModifyVariable(method = "renderArmor", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static int soulsmode$liftArmor(int y) {
		return StaminaHud.visible() ? y - StaminaHud.HEART_LIFT : y;
	}
}
