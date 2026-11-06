package dev.soulsmode.client.mixin;

import dev.soulsmode.client.world.SoulsWorldClient;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers that the world being created is Souls-like until its server starts. */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
	@Inject(method = "onCreate", at = @At("HEAD"))
	private void soulsmode$soulsLike(CallbackInfo ci) {
		SoulsWorldClient.setPending(((CreateWorldScreen) (Object) this).getUiState().getGameMode() == SoulsWorldClient.souls());
	}
}
