package dev.soulsmode.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.level.block.Blocks;

/**
 * The Last Giant's phase 2 weapon (SoulsGiants): the user's choice (2026-11-04) is a log in its hand instead of its
 * own torn-off arm. While a clip has DS2's held_arm (R_Wep_L_Forearm grip -> elbow, last_giant.md s.3) the log follows
 * it from the grip; otherwise it hangs from the right hand. Its size is a design choice.
 */
public class GiantLogLayer extends RenderLayer<Giant, HumanoidModel<Giant>> {
	/** Design choice: a 4 x 14 x 4 px log in the model's units (x6 on the giant). */
	private static final float WIDTH = 4 / 16f;
	private static final float LENGTH = 14 / 16f;

	public GiantLogLayer(RenderLayerParent<Giant, HumanoidModel<Giant>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, Giant giant, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (ZombieAnimClient.mode(giant) != 1) {
			return;
		}
		var arm = getParentModel().rightArm;
		pose.pushPose();
		float[] held = ZombieAnimClient.heldArm(giant, partialTick);
		if (held != null) {
			// From the right shoulder pivot by the grip offset, then the held arm's rotation (ModelPart's Z Y X order).
			pose.translate((arm.x + held[3]) / 16f, (arm.y + held[4]) / 16f, (arm.z + held[5]) / 16f);
			pose.mulPose(Axis.ZP.rotationDegrees(held[2]));
			pose.mulPose(Axis.YP.rotationDegrees(held[1]));
			pose.mulPose(Axis.XP.rotationDegrees(held[0]));
		} else {
			// In the right hand: the arm's own transform, down to its end (10 px).
			arm.translateAndRotate(pose);
			pose.translate(-1 / 16f, 10 / 16f, 0);
		}
		pose.scale(WIDTH, LENGTH, WIDTH);
		pose.translate(-0.5, 0, -0.5);
		Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.OAK_LOG.defaultBlockState(), pose, buffers,
				light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}
}
