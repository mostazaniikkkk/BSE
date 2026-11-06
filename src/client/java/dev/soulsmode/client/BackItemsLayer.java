package dev.soulsmode.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.soulsmode.api.SoulsWeapons;
import dev.soulsmode.client.anim.GripClient;
import dev.soulsmode.ds2.Moveset;
import dev.soulsmode.ds2.Weapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Items carried on the body (the user's request; placement and look are design choices):
 * <ul>
 * <li>the weapon put away while the other one is two-handed, where DS2's change animation puts it (WeaponTypeParam +0x24:
 * 10 a00_08_0010 WeaponChangeToBackTop, 20 0020 ToBackBottom, 30 0030 ToHipFar); DS2's attachment point was not
 * found, so the exact spot is ours;</li>
 * <li>a quiver of arrows hanging at the right hip when the player carries arrows (the user's exception, 2026-11-01:
 * DS2 draws no quiver; other players: the server's flag, SoulsPresence).</li>
 * </ul>
 */
public final class BackItemsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final int BACK_TOP = 10;
	private static final int BACK_BOTTOM = 20;
	private static final int HIP = 30;
	private static final ItemStack ARROW = new ItemStack(Items.ARROW);

	public BackItemsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (!PresenceClient.soulsMode(player)) {
			return;
		}
		Moveset.Grip grip = GripClient.grip(player);
		if (grip.twoHanded()) {
			// Two-handing the right weapon puts the left one away, and the other way round.
			ItemStack stored = grip == Moveset.Grip.TWO_HANDED_RIGHT ? player.getOffhandItem() : player.getMainHandItem();
			if (!stored.isEmpty()) {
				int storage = SoulsWeapons.grip(SoulsWeapons.movesetFor(stored)).map(Weapons.GripData::storage).orElse(BACK_TOP);
				renderStored(pose, buffers, light, player, stored, storage);
			}
		}
		if (carriesArrows(player)) {
			renderQuiver(pose, buffers, light, player);
		}
	}

	private static boolean carriesArrows(AbstractClientPlayer player) {
		return player == Minecraft.getInstance().player ? player.getInventory().contains(ItemTags.ARROWS)
				: PresenceClient.arrows(player);
	}

	private void renderStored(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			ItemStack stack, int storage) {
		pose.pushPose();
		getParentModel().body.translateAndRotate(pose);
		switch (storage) {
			case HIP -> {
				// Hanging at the left hip, pointing down and back.
				pose.translate(0.3, 0.7, 0.05);
				pose.mulPose(Axis.YP.rotationDegrees(90));
				pose.mulPose(Axis.ZP.rotationDegrees(-135));
			}
			case BACK_BOTTOM -> {
				// Across the lower back.
				pose.translate(0, 0.55, 0.2);
				pose.mulPose(Axis.ZP.rotationDegrees(90));
			}
			default -> {
				// Diagonal across the upper back, the grip over the right shoulder.
				pose.translate(0, 0.3, 0.2);
				pose.mulPose(Axis.ZP.rotationDegrees(45));
			}
		}
		pose.scale(0.85f, 0.85f, 0.85f);
		Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
				OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), player.getId());
		pose.popPose();
	}

	/** Three arrows hanging at the right hip, tips down (the user's placement). */
	private void renderQuiver(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player) {
		for (int i = 0; i < 3; i++) {
			pose.pushPose();
			getParentModel().body.translateAndRotate(pose);
			// Model space: -x is the player's right, +y down from the neck (the hip at 0.75), +z the back.
			pose.translate(-0.3, 0.7, -0.03 + i * 0.04);
			pose.mulPose(Axis.YP.rotationDegrees(90));
			pose.mulPose(Axis.ZP.rotationDegrees(135 + (i - 1) * 6));
			pose.scale(0.6f, 0.6f, 0.6f);
			Minecraft.getInstance().getItemRenderer().renderStatic(ARROW, ItemDisplayContext.FIXED, light,
					OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), player.getId() + i);
			pose.popPose();
		}
	}
}
