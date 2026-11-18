package dev.soulsmode.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.soulsmode.SoulsAttachments;
import dev.soulsmode.SoulsMode;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DS2's status gauges (docs/research/petrification.md section 3, tools/re/status_hud.py: l01_06_tolerance.flo): up to
 * 3 slots from the top, in DS2's order (petrification, curse; poison, toxic and bleed are not player meters in the
 * mod). A meter shows above 2 % as the build-up bar (purple / teal); the curse's 2 s entry as the active bar (yellow /
 * orange) with its time left. The fill grows from the left with the fraction (the FLO's mask). The FLO's 1280 x 720
 * space is scaled to the screen's height and centred, as YouDiedClient (how DS2 maps it is not traced).
 */
public final class StatusHud {
	private static final String RESOURCE = "/data/soulsmode/ds2/status_hud.json";
	private static final String[] FRAME = {"plate_left", "plate_mid", "plate_right", "plate_notch", "gage_cap_left",
			"gage_mid", "gage_cap_right", "icon_frame"};
	private static float[] slots;
	private static float maskWidth;
	private static float maskFull;
	private static float maskEmpty;
	private static float barOrigin;
	private static float threshold;
	private static final Map<String, float[]> RECTS = new HashMap<>();
	private static final Map<String, int[]> SIZES = new HashMap<>();

	private StatusHud() {
	}

	private record Entry(String icon, boolean active, float fraction) {
	}

	public static void init() {
		try (InputStream in = StatusHud.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				SoulsMode.LOGGER.warn("[BSE] {} missing (tools/ds2_build.py --only menu): no status gauges", RESOURCE);
				return;
			}
			JsonObject j = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			var s = j.getAsJsonArray("slots");
			slots = new float[s.size()];
			for (int i = 0; i < slots.length; i++) {
				slots[i] = s.get(i).getAsFloat();
			}
			JsonObject mask = j.getAsJsonObject("mask");
			maskWidth = mask.get("width").getAsFloat();
			maskFull = mask.get("full").getAsFloat();
			maskEmpty = mask.get("empty").getAsFloat();
			barOrigin = mask.get("barOrigin").getAsFloat();
			threshold = j.get("threshold").getAsFloat();
			for (var e : j.getAsJsonObject("pieces").entrySet()) {
				var r = e.getValue().getAsJsonObject().getAsJsonArray("rect");
				var z = e.getValue().getAsJsonObject().getAsJsonArray("size");
				RECTS.put(e.getKey(), new float[] {r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat(),
						r.get(3).getAsFloat()});
				SIZES.put(e.getKey(), new int[] {z.get(0).getAsInt(), z.get(1).getAsInt()});
			}
		} catch (Exception e) {
			SoulsMode.LOGGER.warn("[BSE] cannot read {}: {}", RESOURCE, e.toString());
			return;
		}
		HudRenderCallback.EVENT.register((graphics, delta) -> render(graphics));
	}

	private static List<Entry> entries() {
		Minecraft mc = Minecraft.getInstance();
		List<Float> m = mc.player == null ? null : mc.player.getAttached(SoulsAttachments.STATUS_METERS);
		List<Entry> out = new ArrayList<>();
		if (m == null || m.size() < 3) {
			return out;
		}
		if (m.get(0) / 100f > threshold) {
			out.add(new Entry("icon_petrify", false, m.get(0) / 100f));
		}
		// The 呪 entry active wins over the meter (FUN_14006d020: type 1 first).
		if (m.get(2) > 1.19e-7f) {
			out.add(new Entry("icon_curse", true, m.get(2)));
		} else if (m.get(1) / 100f > threshold) {
			out.add(new Entry("icon_curse", false, m.get(1) / 100f));
		}
		return out;
	}

	private static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || slots == null) {
			return;
		}
		List<Entry> entries = entries();
		if (entries.isEmpty()) {
			return;
		}
		float scale = g.guiHeight() / 720f;
		float ox = (g.guiWidth() - 1280 * scale) / 2;
		RenderSystem.enableBlend();
		g.pose().pushPose();
		g.pose().translate(ox, 0, 0);
		g.pose().scale(scale, scale, 1);
		for (int i = 0; i < Math.min(entries.size(), slots.length); i++) {
			Entry e = entries.get(i);
			float dy = slots[i];
			for (String piece : FRAME) {
				draw(g, piece, dy, 1);
			}
			draw(g, e.icon(), dy, 1);
			draw(g, e.active() ? "bar_active_bg" : "bar_gauge_bg", dy, 1);
			// The mask at fraction f: local x from 'empty' to 'full'; the fill shows where the mask covers the bar.
			String fill = e.active() ? "bar_active_fill" : "bar_gauge_fill";
			float[] r = RECTS.get(fill);
			float maskX = maskEmpty + (maskFull - maskEmpty) * e.fraction();
			float from = Math.max(-barOrigin, maskX);
			float to = Math.min(r[2] - r[0] - barOrigin, maskX + maskWidth);
			if (to > from) {
				drawPart(g, fill, dy, (from + barOrigin) / (r[2] - r[0]), (to + barOrigin) / (r[2] - r[0]));
			}
		}
		g.pose().popPose();
		RenderSystem.disableBlend();
	}

	private static void draw(GuiGraphics g, String piece, float dy, float right) {
		drawPart(g, piece, dy, 0, right);
	}

	/** The piece's horizontal span {@code from..to} (fractions of its width). */
	private static void drawPart(GuiGraphics g, String piece, float dy, float from, float to) {
		float[] r = RECTS.get(piece);
		int[] size = SIZES.get(piece);
		if (r == null) {
			return;
		}
		ResourceLocation tex = SoulsMode.id("textures/gui/status/" + piece + ".png");
		g.pose().pushPose();
		float w = r[2] - r[0];
		g.pose().translate(r[0] + w * from, r[1] + dy, 0);
		g.pose().scale(w / size[0], (r[3] - r[1]) / size[1], 1);
		int u0 = Math.round(size[0] * from);
		int u1 = Math.round(size[0] * to);
		g.blit(tex, 0, 0, u1 - u0, size[1], u0, 0, u1 - u0, size[1], size[0], size[1]);
		g.pose().popPose();
	}
}
