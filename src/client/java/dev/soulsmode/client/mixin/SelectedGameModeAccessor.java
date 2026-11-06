package dev.soulsmode.client.mixin;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WorldCreationUiState.SelectedGameMode.class)
public interface SelectedGameModeAccessor {
	@Invoker("<init>")
	static WorldCreationUiState.SelectedGameMode soulsmode$create(String name, int ordinal, String id, GameType type) {
		throw new AssertionError();
	}
}
