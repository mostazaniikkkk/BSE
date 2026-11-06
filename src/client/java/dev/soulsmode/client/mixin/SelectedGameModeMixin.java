package dev.soulsmode.client.mixin;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Adds the "Souls-like" game mode to the world creation screen (the user's request). It creates a survival world
 * marked as Souls-like (SoulsWorld): its display name is the lang key selectWorld.gameMode.souls.
 */
@Mixin(WorldCreationUiState.SelectedGameMode.class)
public abstract class SelectedGameModeMixin {
	@Shadow
	@Final
	@Mutable
	private static WorldCreationUiState.SelectedGameMode[] $VALUES;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void soulsmode$addSouls(CallbackInfo ci) {
		WorldCreationUiState.SelectedGameMode souls = SelectedGameModeAccessor.soulsmode$create("SOULS", $VALUES.length,
				"souls", GameType.SURVIVAL);
		WorldCreationUiState.SelectedGameMode[] values = Arrays.copyOf($VALUES, $VALUES.length + 1);
		values[values.length - 1] = souls;
		$VALUES = values;
	}
}
