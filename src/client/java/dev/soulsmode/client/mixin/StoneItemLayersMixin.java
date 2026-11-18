package dev.soulsmode.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.soulsmode.client.StoneTextures;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A statue shows no held or worn items in colour: they are left out (StoneTextures). */
@Mixin({ItemInHandLayer.class, CrossedArmsItemLayer.class, CustomHeadLayer.class})
public abstract class StoneItemLayersMixin {
	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
			at = @At("HEAD"), cancellable = true)
	private void soulsmode$stoneNoItems(PoseStack pose, MultiBufferSource buffer, int light, LivingEntity entity,
			float a, float b, float c, float d, float e, float f, CallbackInfo ci) {
		if (StoneTextures.stone(entity)) {
			ci.cancel();
		}
	}
}
