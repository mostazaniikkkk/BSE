package dev.soulsmode.client.mixin;

import dev.soulsmode.client.camera.LockOn;
import dev.soulsmode.client.camera.SoulsCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the Souls camera the mouse orbits the camera instead of turning the player. */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void soulsmode$orbitCamera(double yRot, double xRot, CallbackInfo ci) {
		if ((Object) this == Minecraft.getInstance().player && dev.soulsmode.client.camera.BowAim.active()) {
			dev.soulsmode.client.camera.BowAim.turn(yRot, xRot);
			ci.cancel();
		} else if ((Object) this == Minecraft.getInstance().player && LockOn.isLocked()) {
			// While locked the camera follows the target; the mouse does not move it (+0xd0 = 0). It switches targets.
			LockOn.mouse(yRot, xRot);
			ci.cancel();
		} else if ((Object) this == Minecraft.getInstance().player && SoulsCamera.isActive()) {
			SoulsCamera.turn(yRot, xRot);
			ci.cancel();
		}
	}
}
