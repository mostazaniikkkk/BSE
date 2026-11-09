package dev.soulsmode.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.soulsmode.client.bonfire.BonfireClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.CampfireRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An active bonfire (the user's design, 2026-11-09) shows the stone sword it was kindled with, planted point down in
 * the fire. Its pose is a design choice: as in the user's chosen screenshot (2026-11-09).
 */
@Mixin(CampfireRenderer.class)
public abstract class CampfireRendererMixin {
	private static final ItemStack SWORD = new ItemStack(Items.STONE_SWORD);

	@Inject(method = "render(Lnet/minecraft/world/level/block/entity/CampfireBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
			at = @At("TAIL"))
	private void soulsmode$plantedSword(CampfireBlockEntity campfire, float partialTick, PoseStack pose,
			MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
		if (campfire.getLevel() == null || !BonfireClient.isActive(campfire.getLevel(), campfire.getBlockPos())) {
			return;
		}
		// The user's chosen look (2026-11-09, the first screenshot): the item turned 135 deg about the view axis.
		pose.pushPose();
		pose.translate(0.5, 0.75, 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(20));
		pose.mulPose(Axis.XP.rotationDegrees(8));
		pose.mulPose(Axis.ZP.rotationDegrees(135));
		pose.scale(0.9f, 0.9f, 0.9f);
		Minecraft.getInstance().getItemRenderer().renderStatic(SWORD, ItemDisplayContext.FIXED, light,
				OverlayTexture.NO_OVERLAY, pose, buffers, campfire.getLevel(), 0);
		pose.popPose();
	}
}
