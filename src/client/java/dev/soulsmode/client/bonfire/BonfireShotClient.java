package dev.soulsmode.client.bonfire;

import com.mojang.blaze3d.platform.NativeImage;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.network.BonfirePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The bonfires' pictures (the user's design, after DS2's travel menu): when the server asks, the next rendered world
 * frame (before the HUD is drawn) is captured, cropped to 16:9, scaled down and uploaded; pictures for the travel menu
 * are fetched from the server and kept as textures for the session.
 */
public final class BonfireShotClient {
	public static final int WIDTH = 320;
	public static final int HEIGHT = 180;
	private static BonfirePayloads.ShotRequest pending;
	private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
	private static final Set<String> REQUESTED = new HashSet<>();

	private BonfireShotClient() {
	}

	private static String key(ResourceLocation dimension, BlockPos pos) {
		return dimension + "|" + pos.asLong();
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(BonfirePayloads.ShotRequest.TYPE,
				(payload, context) -> pending = payload);
		ClientPlayNetworking.registerGlobalReceiver(BonfirePayloads.Shot.TYPE,
				(payload, context) -> store(payload.dimension(), payload.pos(), payload.png()));
		WorldRenderEvents.END.register(context -> {
			if (pending != null) {
				BonfirePayloads.ShotRequest request = pending;
				pending = null;
				capture(request);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			TEXTURES.values().forEach(id -> client.getTextureManager().release(id));
			TEXTURES.clear();
			REQUESTED.clear();
			pending = null;
		});
	}

	private static void capture(BonfirePayloads.ShotRequest request) {
		Minecraft mc = Minecraft.getInstance();
		try (NativeImage full = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
			int w = full.getWidth();
			int h = full.getHeight();
			// Centre crop to 16:9.
			int cw = Math.min(w, h * 16 / 9);
			int ch = Math.min(h, w * 9 / 16);
			int x0 = (w - cw) / 2;
			int y0 = (h - ch) / 2;
			try (NativeImage small = new NativeImage(WIDTH, HEIGHT, false)) {
				for (int y = 0; y < HEIGHT; y++) {
					for (int x = 0; x < WIDTH; x++) {
						small.setPixelRGBA(x, y, average(full, x0 + x * cw / WIDTH, y0 + y * ch / HEIGHT,
								Math.max(1, cw / WIDTH), Math.max(1, ch / HEIGHT)));
					}
				}
				byte[] png = small.asByteArray();
				if (png.length <= BonfirePayloads.SHOT_MAX_BYTES) {
					ClientPlayNetworking.send(new BonfirePayloads.Shot(request.dimension(), request.pos(), png));
					store(request.dimension(), request.pos(), png);
				}
			}
		} catch (Exception e) {
			SoulsMode.LOGGER.warn("[BSE] bonfire picture: {}", e.toString());
		}
	}

	/** Box average of a block of pixels (ABGR as NativeImage stores it), opaque. */
	private static int average(NativeImage img, int x, int y, int bw, int bh) {
		long r = 0;
		long g = 0;
		long b = 0;
		int n = 0;
		for (int j = 0; j < bh && y + j < img.getHeight(); j++) {
			for (int i = 0; i < bw && x + i < img.getWidth(); i++) {
				int c = img.getPixelRGBA(x + i, y + j);
				r += c & 0xff;
				g += (c >> 8) & 0xff;
				b += (c >> 16) & 0xff;
				n++;
			}
		}
		n = Math.max(1, n);
		return 0xff000000 | (int) (b / n) << 16 | (int) (g / n) << 8 | (int) (r / n);
	}

	private static void store(ResourceLocation dimension, BlockPos pos, byte[] png) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			try {
				String key = key(dimension, pos);
				ResourceLocation id = SoulsMode.id("bonfire_shot/" + Integer.toHexString(key.hashCode()));
				// NativeImage.read(byte[]) copies into LWJGL's 64 KB stack (OutOfMemoryError "Out of stack space" for a
				// picture this size); the stream version reads into heap memory.
				mc.getTextureManager().register(id, new DynamicTexture(NativeImage.read(new java.io.ByteArrayInputStream(png))));
				TEXTURES.put(key, id);
			} catch (Exception e) {
				SoulsMode.LOGGER.warn("[BSE] bonfire picture: {}", e.toString());
			}
		});
	}

	/** The picture's texture, asking the server once when it is not here yet; null meanwhile or without one. */
	public static ResourceLocation texture(ResourceLocation dimension, BlockPos pos) {
		String key = key(dimension, pos);
		ResourceLocation id = TEXTURES.get(key);
		if (id == null && REQUESTED.add(key)) {
			ClientPlayNetworking.send(new BonfirePayloads.ShotFetch(dimension, pos));
		}
		return id;
	}
}
