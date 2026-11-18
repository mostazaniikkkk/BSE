package dev.soulsmode.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.soulsmode.client.StoneTextures;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Texture layers of a statue (a villager's biome and profession) in stone too (StoneTextures). */
@Mixin(RenderLayer.class)
public abstract class StoneLayerMixin {
	@ModifyVariable(method = "renderColoredCutoutModel", at = @At("HEAD"), argsOnly = true)
	private static <T extends LivingEntity> ResourceLocation soulsmode$stoneLayer(ResourceLocation texture,
			EntityModel<T> model, ResourceLocation same, PoseStack pose, MultiBufferSource buffer, int light, T entity,
			int color) {
		return StoneTextures.stone(entity) ? StoneTextures.of(texture) : texture;
	}
}
