package dev.soulsmode.client.mixin;

import dev.soulsmode.client.world.SoulsWorldClient;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The game mode button cycles Survival, Hardcore, Souls-like, Creative. */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
public abstract class CreateWorldGameTabMixin {
	@ModifyArg(method = "<init>", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/CycleButton$Builder;withValues([Ljava/lang/Object;)Lnet/minecraft/client/gui/components/CycleButton$Builder;",
			ordinal = 0))
	private Object[] soulsmode$soulsAfterHardcore(Object[] values) {
		return new Object[] {WorldCreationUiState.SelectedGameMode.SURVIVAL, WorldCreationUiState.SelectedGameMode.HARDCORE,
				SoulsWorldClient.souls(), WorldCreationUiState.SelectedGameMode.CREATIVE};
	}
}
