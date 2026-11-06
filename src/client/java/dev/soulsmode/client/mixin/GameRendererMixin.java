package dev.soulsmode.client.mixin;

import dev.soulsmode.client.camera.CameraPick;
import dev.soulsmode.client.camera.SoulsCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DS2 vertical FOV (CameraExFollowParam +0x04) and the camera-centered pick while the Souls camera is active. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "pick(F)V", at = @At("TAIL"))
	private void soulsmode$cameraPick(float partialTick, CallbackInfo ci) {
		CameraPick.pick(Minecraft.getInstance());
	}

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void soulsmode$ds2Fov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
		if (useFovSetting && dev.soulsmode.client.camera.BowAim.active()) {
			// CameraSubjectiveParam 10 +fov: 0.4363 rad (25 deg).
			cir.setReturnValue(Math.toDegrees(dev.soulsmode.client.camera.BowAim.weapon().aimFov()));
		} else if (useFovSetting && SoulsCamera.USE_DS2_FOV && SoulsCamera.isActive()) {
			cir.setReturnValue(SoulsCamera.FOV_DEGREES);
		}
	}
}
