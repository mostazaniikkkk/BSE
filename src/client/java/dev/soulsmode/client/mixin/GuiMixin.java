package dev.soulsmode.client.mixin;

import dev.soulsmode.client.StaminaHud;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lifts the hearts and armor rows in Souls Mode so the stamina bar fits under them. */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@ModifyVariable(method = "renderHearts", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int soulsmode$liftHearts(int y) {
		return StaminaHud.visible() ? y - StaminaHud.HEART_LIFT : y;
	}

	@ModifyVariable(method = "renderArmor", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static int soulsmode$liftArmor(int y) {
		return StaminaHud.visible() ? y - StaminaHud.HEART_LIFT : y;
	}
}
