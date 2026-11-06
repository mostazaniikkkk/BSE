package dev.soulsmode.mixin;

import dev.soulsmode.SoulsGameMode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	/** Any vanilla game mode change (e.g. /gamemode creative) drops the player out of Souls Mode. */
	@Inject(method = "setGameMode", at = @At("HEAD"))
	private void soulsmode$leaveSoulsMode(GameType gameMode, CallbackInfoReturnable<Boolean> cir) {
		SoulsGameMode.leave((ServerPlayer) (Object) this);
	}
}
