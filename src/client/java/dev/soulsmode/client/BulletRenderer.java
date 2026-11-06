package dev.soulsmode.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.soulsmode.entity.SoulsBullet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * DS2 bullets: spell bullets are drawn by their particles (server side); a bow's arrow is drawn as Minecraft's arrow
 * item pointing along its flight.
 */
public class BulletRenderer extends EntityRenderer<SoulsBullet> {
	private static final ItemStack ARROW = new ItemStack(Items.ARROW);

	public BulletRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(SoulsBullet bullet, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int light) {
		if (!bullet.isArrow()) {
			return;
		}
		Vec3 d = new Vec3(bullet.getX() - bullet.xo, bullet.getY() - bullet.yo, bullet.getZ() - bullet.zo);
		if (d.lengthSqr() < 1e-6) {
			return;
		}
		float flightYaw = (float) Math.toDegrees(Mth.atan2(d.x, d.z));
		float flightPitch = (float) Math.toDegrees(Mth.atan2(d.y, d.horizontalDistance()));
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(flightYaw - 90));
		pose.mulPose(Axis.ZP.rotationDegrees(flightPitch - 45));
		net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(ARROW, ItemDisplayContext.FIXED, light,
				OverlayTexture.NO_OVERLAY, pose, buffers, bullet.level(), bullet.getId());
		pose.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(SoulsBullet bullet) {
		return null;
	}
}
