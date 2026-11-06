package dev.soulsmode.client.mixin;

import dev.soulsmode.client.AttackClient;
import dev.soulsmode.client.StaminaHud;
import dev.soulsmode.client.camera.SoulsCamera;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Movement keys become camera-relative, the body turns towards the movement direction, and attacks lock movement. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "aiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/Input;tick(ZF)V", shift = At.Shift.AFTER))
	private void soulsmode$cameraRelativeMovement(CallbackInfo ci) {
		SoulsCamera.afterInput((LocalPlayer) (Object) this);
		AttackClient.afterInput((LocalPlayer) (Object) this);
	}

	/** DS2 dash rules (FUN_140385620): no sprint with stamina <= 0 or during the cooldown after running out. */
	@Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
	private void soulsmode$staminaSprint(CallbackInfoReturnable<Boolean> cir) {
		if (StaminaHud.sprintBlocked()) {
			cir.setReturnValue(false);
		}
	}
}
