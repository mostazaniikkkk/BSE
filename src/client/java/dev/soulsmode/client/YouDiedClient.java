package dev.soulsmode.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.soulsmode.SoulsMode;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * DS2's death banner (docs/research/you_died.md, tools/re/you_died.py): in Souls Mode, Minecraft's death screen waits
 * while DS2's sequence runs. FUN_14018ed90 waits 3.0 s after the death notice, then the message scene (EventResult 1,
 * FE type 1 -> banner type 3) shows l01_14_message.flo scene 3 at 30 fps, hiding the rest of the HUD (FUN_1405070d0),
 * and starts the sting m000000010 (FUN_1401905c0 slot 2); the scene closes at frame 102. Then Minecraft's death screen.
 * <p>
 * Frames are the FLO's 1280 x 720 space; how DS2 maps it to the screen is not traced, so it is scaled to the screen's
 * height and centred (you_died.md "Sin confirmar"). The language picks DS2's English or NeutralSpanish texture.
 */
public final class YouDiedClient {
	private static final String RESOURCE = "/data/soulsmode/ds2/you_died.json";
	private static final ResourceLocation BAND = SoulsMode.id("textures/gui/you_died_band.png");
	private static final ResourceLocation TEXT_EN = SoulsMode.id("textures/gui/you_died_en.png");
	private static final ResourceLocation TEXT_ES = SoulsMode.id("textures/gui/you_died_es.png");
	/** Pixel sizes of the exported crops (you_died.py rounds the uv rectangles). */
	private static final int TEXT_W = 1024;
	private static final int TEXT_H = 130;
	private static final int BAND_W = 83;
	private static final int BAND_H = 207;

	private static float fps;
	private static int closeFrame;
	private static float delay;
	private static float spaceW;
	private static float spaceH;
	private static float textW;
	private static float textH;
	private static float bandW;
	private static float bandH;
	private static String sound;
	private static float[][] text;
	private static float[][] band;

	/** Minecraft's death screen held back while the banner runs. */
	private static Screen pending;
	private static long started;
	private static boolean soundStarted;
	private static boolean releasing;

	private YouDiedClient() {
	}

	public static void init() {
		try (InputStream in = YouDiedClient.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				return;
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			fps = json.get("fps").getAsFloat();
			closeFrame = json.get("closeFrame").getAsInt();
			delay = json.get("delay").getAsFloat();
			spaceW = json.getAsJsonArray("space").get(0).getAsFloat();
			spaceH = json.getAsJsonArray("space").get(1).getAsFloat();
			textW = json.getAsJsonArray("textSize").get(0).getAsFloat();
			textH = json.getAsJsonArray("textSize").get(1).getAsFloat();
			bandW = json.getAsJsonArray("bandSize").get(0).getAsFloat();
			bandH = json.getAsJsonArray("bandSize").get(1).getAsFloat();
			sound = json.get("sound").getAsString();
			JsonArray frames = json.getAsJsonArray("frames");
			text = new float[frames.size()][];
			band = new float[frames.size()][];
			for (int i = 0; i < frames.size(); i++) {
				text[i] = floats(frames.get(i).getAsJsonObject().getAsJsonArray("text"));
				band[i] = floats(frames.get(i).getAsJsonObject().getAsJsonArray("band"));
			}
		} catch (java.io.IOException e) {
			SoulsMode.LOGGER.warn("[BSE] no death banner data: {}", e.toString());
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(YouDiedClient::tick);
	}

	private static float[] floats(JsonArray a) {
		float[] out = new float[a.size()];
		int i = 0;
		for (JsonElement e : a) {
			out[i++] = e.getAsFloat();
		}
		return out;
	}

	/** Minecraft.setScreen: the death screen waits for the banner in Souls Mode. Returns true to hold it back. */
	public static boolean holdDeathScreen(Screen screen) {
		if (releasing || text == null || !(screen instanceof net.minecraft.client.gui.screens.DeathScreen)
				|| !StaminaHud.soulsMode()) {
			return false;
		}
		if (pending == null) {
			started = Util.getMillis();
			soundStarted = false;
		}
		pending = screen;
		return true;
	}

	private static float elapsed() {
		return (Util.getMillis() - started) / 1000f;
	}

	/** The banner's frame now (1-based), or 0 while waiting / without a banner. */
	private static int frame() {
		if (pending == null) {
			return 0;
		}
		float t = elapsed() - delay;
		return t < 0 ? 0 : (int) Math.floor(t * fps) + 1;
	}

	/** The banner is on screen: the rest of the HUD is hidden (FUN_1405070d0, type 3). */
	public static boolean showing() {
		return frame() > 0;
	}

	private static void tick(Minecraft client) {
		if (pending == null) {
			return;
		}
		if (client.player == null || client.player.isAlive()) {
			// Respawned or left: nothing to show.
			pending = null;
			return;
		}
		int f = frame();
		if (f > 0 && !soundStarted) {
			soundStarted = true;
			var event = BuiltInRegistries.SOUND_EVENT.get(SoulsMode.id("menu." + sound));
			if (event != null) {
				client.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0f, dev.soulsmode.SoulsSounds.MIX));
			}
		}
		if (f > closeFrame) {
			Screen screen = pending;
			pending = null;
			releasing = true;
			try {
				client.setScreen(screen);
			} finally {
				releasing = false;
			}
		}
	}

	/** Draws the banner's frame (from Gui.render, instead of the HUD). */
	public static void render(GuiGraphics graphics) {
		int f = frame();
		if (f <= 0 || f > closeFrame || f > text.length) {
			return;
		}
		float s = graphics.guiHeight() / spaceH;
		float ox = (graphics.guiWidth() - spaceW * s) / 2;
		boolean es = Minecraft.getInstance().getLanguageManager().getSelected().startsWith("es");
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		sprite(graphics, BAND, band[f - 1], bandW, bandH, BAND_W, BAND_H, s, ox);
		sprite(graphics, es ? TEXT_ES : TEXT_EN, text[f - 1], textW, textH, TEXT_W, TEXT_H, s, ox);
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.disableBlend();
	}

	private static void sprite(GuiGraphics graphics, ResourceLocation texture, float[] m, float w, float h, int pngW,
			int pngH, float s, float ox) {
		if (m[9] <= 0) {
			return;
		}
		var pose = graphics.pose();
		pose.pushPose();
		pose.translate(ox, 0, 0);
		pose.scale(s, s, 1);
		// [a, b, c, d, tx, ty]: x' = a x + c y + tx, y' = b x + d y + ty (column-major).
		pose.mulPose(new Matrix4f(m[0], m[1], 0, 0, m[2], m[3], 0, 0, 0, 0, 1, 0, m[4], m[5], 0, 1));
		pose.scale(w / pngW, h / pngH, 1);
		RenderSystem.setShaderColor(m[6] / 255f, m[7] / 255f, m[8] / 255f, m[9] / 255f);
		graphics.blit(texture, 0, 0, 0, 0, pngW, pngH, pngW, pngH);
		pose.popPose();
	}
}
