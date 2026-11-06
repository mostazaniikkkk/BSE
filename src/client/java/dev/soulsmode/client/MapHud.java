package dev.soulsmode.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The user's mechanic (2026-11-03, not from DS2): in Souls Mode, in third person, a filled map held in either hand is
 * drawn as a minimap in the top right corner of the HUD (below the status effect icons when there are any). The size
 * and margins are design values.
 */
public final class MapHud {
	/** Minimap side in GUI pixels, with its parchment border. */
	private static final int SIZE = 96;
	private static final int BORDER = 4;
	private static final int MARGIN = 4;
	/** Vanilla's status effect icons: one row of 25 px each for beneficial and harmful effects. */
	private static final int EFFECT_ROW = 26;
	private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace(
			"textures/map/map_background.png");

	private MapHud() {
	}

	public static void init() {
		HudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
	}

	private static void render(GuiGraphics graphics) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui || !StaminaHud.soulsMode()
				|| mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		ItemStack map = mc.player.getMainHandItem().is(Items.FILLED_MAP) ? mc.player.getMainHandItem()
				: mc.player.getOffhandItem().is(Items.FILLED_MAP) ? mc.player.getOffhandItem() : ItemStack.EMPTY;
		MapId id = map.isEmpty() ? null : map.get(net.minecraft.core.component.DataComponents.MAP_ID);
		MapItemSavedData data = id == null ? null : MapItem.getSavedData(id, mc.level);
		if (data == null) {
			return;
		}
		int top = MARGIN + effectRows(mc) * EFFECT_ROW;
		int x = graphics.guiWidth() - MARGIN - SIZE;
		graphics.blit(BACKGROUND, x, top, 0, 0, SIZE, SIZE, SIZE, SIZE);
		PoseStack pose = graphics.pose();
		pose.pushPose();
		pose.translate(x + BORDER, top + BORDER, 1);
		float scale = (SIZE - 2 * BORDER) / 128f;
		// The map renderer stacks its markers towards -z (the viewer, in the hand's orientation); in the GUI the viewer
		// is at +z, so z is mirrored to keep the markers in front of the map.
		pose.scale(scale, scale, -1);
		mc.gameRenderer.getMapRenderer().render(pose, graphics.bufferSource(), id, data, false,
				LightTexture.FULL_BRIGHT);
		graphics.flush();
		pose.popPose();
	}

	/** How many rows of status effect icons vanilla draws in the top right (beneficial, harmful). */
	private static int effectRows(Minecraft mc) {
		boolean good = false;
		boolean bad = false;
		for (MobEffectInstance effect : mc.player.getActiveEffects()) {
			if (!effect.showIcon()) {
				continue;
			}
			if (effect.getEffect().value().isBeneficial()) {
				good = true;
			} else {
				bad = true;
			}
		}
		return (good ? 1 : 0) + (bad ? 1 : 0);
	}
}
