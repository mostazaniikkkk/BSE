package dev.soulsmode.client.camera;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Health bar over the locked-on target (asked for by the user). Its look, size and placement are design choices,
 * not decoded from DS2's menus.
 */
public final class LockOnHud {
	private static final int WIDTH = 40;
	private static final int HEIGHT = 3;
	/** Blocks above the target's head. */
	private static final double LIFT = 0.5;
	private static final int BORDER = 0xFF101010;
	private static final int BACKGROUND = 0xFF3A0A0A;
	private static final int FILL = 0xFFB01818;
	private static final int FILL_HIGHLIGHT = 0xFFE04040;

	private LockOnHud() {
	}

	public static void init() {
		HudRenderCallback.EVENT.register((graphics, deltaTracker) ->
				render(graphics, deltaTracker.getGameTimeDeltaPartialTick(true)));
	}

	private static void render(GuiGraphics graphics, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		Entity target = LockOn.target();
		if (mc.options.hideGui || !(target instanceof LivingEntity living) || !living.isAlive()) {
			return;
		}
		Vec3 head = living.getPosition(partialTick).add(0, living.getBbHeight() + LIFT, 0);
		double[] ndc = LockOn.project(mc.gameRenderer.getMainCamera(), head);
		if (ndc == null) {
			return;
		}
		int x = (int) Math.round((ndc[0] + 1) / 2 * graphics.guiWidth()) - WIDTH / 2;
		int y = (int) Math.round((1 - ndc[1]) / 2 * graphics.guiHeight()) - HEIGHT;
		// Kept on screen when the target's head is above the top edge (close, tall targets).
		x = Math.max(2, Math.min(graphics.guiWidth() - WIDTH - 2, x));
		y = Math.max(2, Math.min(graphics.guiHeight() - HEIGHT - 2, y));
		graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, BORDER);
		graphics.fill(x, y, x + WIDTH, y + HEIGHT, BACKGROUND);
		float fraction = Math.max(0, Math.min(1, living.getHealth() / living.getMaxHealth()));
		int filled = Math.round(WIDTH * fraction);
		if (filled > 0) {
			graphics.fill(x, y, x + filled, y + HEIGHT, FILL);
			graphics.fill(x, y, x + filled, y + 1, FILL_HIGHLIGHT);
		}
	}
}
