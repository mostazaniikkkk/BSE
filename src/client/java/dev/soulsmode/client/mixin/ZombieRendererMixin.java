package dev.soulsmode.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.soulsmode.client.ZombieAnimClient;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The body bone of a DS2 clip on a mob (ZombieAnimClient.applyBody). */
@Mixin(LivingEntityRenderer.class)
public abstract class ZombieRendererMixin {
	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void soulsmode$hollowSoldierBody(LivingEntity entity, PoseStack pose, float bob, float yBodyRot,
			float partialTick, float scale, CallbackInfo ci) {
		if (ZombieAnimClient.playing(entity)) {
			ZombieAnimClient.applyBody(entity, partialTick, pose, scale);
		}
	}
}
