package dev.soulsmode.client;

import dev.soulsmode.network.StaminaPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Stamina bar in the hearts' row; in Souls Mode the hearts are lifted to make room (see GuiMixin).
 * Hunger stays where vanilla puts it.
 */
public final class StaminaHud {
	/** Pixels the hearts and armor move up while the bar is shown. */
	public static final int HEART_LIFT = 6;
	/**
	 * Like DS2, the bar grows with max stamina: 40 px at the lowest max (820, Endurance 1) up to the hearts'
	 * full 81 px at the highest (2000, Endurance 99). The pixel range is a layout choice.
	 */
	private static final int MIN_WIDTH = 40;
	private static final int MAX_WIDTH = 81;
	private static final float LOWEST_MAX = 820f;
	private static final float HIGHEST_MAX = 2000f;
	private static final int HEIGHT = 5;
	private static final int BORDER = 0xFF1A1A1A;
	private static final int BACKGROUND = 0xFF3A3A3A;
	private static final int FILL = 0xFF3FA34D;
	private static final int FILL_HIGHLIGHT = 0xFF6FD07B;

	private static boolean soulsMode;
	private static float current;
	private static float max;
	private static boolean sprintBlocked;

	private StaminaHud() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(StaminaPayload.TYPE, (payload, context) -> {
			soulsMode = payload.soulsMode();
			current = payload.current();
			max = payload.max();
			sprintBlocked = payload.sprintBlocked();
		});
		HudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			soulsMode = false;
			current = 0;
			max = 0;
		});
	}

	/** True while the local player is in Souls Mode (as told by the server); gates every client-side mechanic. */
	public static boolean soulsMode() {
		Minecraft mc = Minecraft.getInstance();
		return soulsMode && mc.player != null && mc.gameMode != null && mc.gameMode.canHurtPlayer();
	}

	/** The server refuses sprinting (DS2 dash rules: no stamina, or the cooldown after running out). */
	public static boolean sprintBlocked() {
		return soulsMode() && sprintBlocked;
	}

	/** The bar itself needs a class: max stamina comes from the attributes. */
	public static boolean visible() {
		return soulsMode() && max > 0;
	}

	private static void render(GuiGraphics graphics) {
		Minecraft mc = Minecraft.getInstance();
		if (!visible() || mc.options.hideGui) {
			return;
		}
		// Vanilla draws the hearts at (width / 2 - 91, height - 39); the bar takes the lower part of that row.
		int x = graphics.guiWidth() / 2 - 91;
		int y = graphics.guiHeight() - 39 + 4;
		float t = Math.max(0, Math.min(1, (max - LOWEST_MAX) / (HIGHEST_MAX - LOWEST_MAX)));
		int width = Math.round(MIN_WIDTH + (MAX_WIDTH - MIN_WIDTH) * t);
		graphics.fill(x - 1, y - 1, x + width + 1, y + HEIGHT + 1, BORDER);
		graphics.fill(x, y, x + width, y + HEIGHT, BACKGROUND);
		int filled = Math.round(width * Math.max(0, Math.min(1, current / max)));
		if (filled > 0) {
			graphics.fill(x, y, x + filled, y + HEIGHT, FILL);
			graphics.fill(x, y, x + filled, y + 1, FILL_HIGHLIGHT);
		}
	}
}
