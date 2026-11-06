package dev.soulsmode.client.mixin;

import dev.soulsmode.client.camera.LockOn;
import dev.soulsmode.client.camera.SoulsCamera;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Places the camera on a DS2 arm: pivot above the player, camera yaw/pitch, wall collision via getMaxZoom. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void move(float zoom, float dy, float dx);

	@Shadow
	protected abstract float getMaxZoom(float maxZoom);

	@Inject(method = "setup", at = @At("HEAD"))
	private void soulsmode$lockOn(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
			float partialTick, CallbackInfo ci) {
		LockOn.frame(partialTick);
	}

	@Inject(method = "setup", at = @At("TAIL"))
	private void soulsmode$ds2Camera(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
			float partialTick, CallbackInfo ci) {
		if (!detached || !SoulsCamera.isActive()) {
			return;
		}
		if (dev.soulsmode.client.camera.BowAim.active()) {
			// CameraSubjectiveParam 10: eye 1.5 m above the feet and 0.4 m forward along the aim.
			float yaw = dev.soulsmode.client.camera.BowAim.yaw();
			double rad = Math.toRadians(yaw);
			var weapon = dev.soulsmode.client.camera.BowAim.weapon();
			double forward = weapon.aimEyeForward();
			setRotation(yaw, dev.soulsmode.client.camera.BowAim.pitch());
			setPosition(Mth.lerp(partialTick, entity.xo, entity.getX()) - Math.sin(rad) * forward,
					Mth.lerp(partialTick, entity.yo, entity.getY()) + weapon.aimEyeUp(),
					Mth.lerp(partialTick, entity.zo, entity.getZ()) + Math.cos(rad) * forward);
			return;
		}
		double x = Mth.lerp(partialTick, entity.xo, entity.getX());
		double y = Mth.lerp(partialTick, entity.yo, entity.getY()) + SoulsCamera.PIVOT_HEIGHT;
		double z = Mth.lerp(partialTick, entity.zo, entity.getZ());
		setRotation(SoulsCamera.yaw(), SoulsCamera.pitch());
		setPosition(x, y, z);
		// Same collision as vanilla third person (shortens the arm when blocks are in the way).
		move(-getMaxZoom((float) SoulsCamera.DISTANCE), 0, 0);
	}
}
