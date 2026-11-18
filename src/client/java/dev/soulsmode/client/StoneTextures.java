package dev.soulsmode.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.soulsmode.SoulsAttachments;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.SoulsStatues;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The look of a petrified statue or a petrified player's body (dev.soulsmode.SoulsStatues, SoulsStatus). DS2 blends a
 * stone material (EneStone_d / _n / _s) whose mixing is not traced (docs/research/petrification.md); the mod draws the
 * entity's own texture in grey (its luminance) over Minecraft's stone block, so the model keeps its shapes. A texture
 * that cannot be read from the resources (a downloaded player skin) gets the stone block tiled instead.
 */
public final class StoneTextures {
	private static final ResourceLocation STONE = ResourceLocation.withDefaultNamespace("textures/block/stone.png");
	private static final Map<ResourceLocation, ResourceLocation> CACHE = new HashMap<>();
	private static NativeImage stone;

	private StoneTextures() {
	}

	/** Whether the entity is drawn in stone. */
	public static boolean stone(Entity entity) {
		return SoulsStatues.isPetrified(entity) || entity instanceof Player
				&& Boolean.TRUE.equals(entity.getAttached(SoulsAttachments.PETRIFIED_DEATH));
	}

	/** Whether {@code texture} is already one of the stone textures. */
	public static boolean isStone(ResourceLocation texture) {
		return texture.getNamespace().equals(SoulsMode.MOD_ID) && texture.getPath().startsWith("stone/");
	}

	/** The stone version of {@code texture}, made once and kept. */
	public static ResourceLocation of(ResourceLocation texture) {
		if (isStone(texture)) {
			return texture;
		}
		return CACHE.computeIfAbsent(texture, StoneTextures::make);
	}

	private static NativeImage stone() {
		if (stone == null) {
			stone = read(STONE);
		}
		return stone;
	}

	private static NativeImage read(ResourceLocation id) {
		var res = Minecraft.getInstance().getResourceManager().getResource(id);
		if (res.isEmpty()) {
			return null;
		}
		try (InputStream in = res.get().open()) {
			return NativeImage.read(in);
		} catch (Exception e) {
			return null;
		}
	}

	private static ResourceLocation make(ResourceLocation texture) {
		NativeImage src = read(texture);
		NativeImage block = stone();
		int w = src == null ? 64 : src.getWidth();
		int h = src == null ? 64 : src.getHeight();
		NativeImage out = new NativeImage(w, h, true);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int s = block == null ? 0xFF808080 : block.getPixelRGBA(x % block.getWidth(), y % block.getHeight());
				int sr = s & 0xFF;
				int sg = s >> 8 & 0xFF;
				int sb = s >> 16 & 0xFF;
				if (src == null) {
					out.setPixelRGBA(x, y, 0xFF000000 | sb << 16 | sg << 8 | sr);
					continue;
				}
				// NativeImage pixels are ABGR.
				int p = src.getPixelRGBA(x, y);
				int a = p >>> 24;
				float lum = (0.299f * (p & 0xFF) + 0.587f * (p >> 8 & 0xFF) + 0.114f * (p >> 16 & 0xFF)) / 255f;
				float f = 0.8f + 0.5f * lum;
				int r = Math.min(255, Math.round(sr * f));
				int g = Math.min(255, Math.round(sg * f));
				int b = Math.min(255, Math.round(sb * f));
				out.setPixelRGBA(x, y, a << 24 | b << 16 | g << 8 | r);
			}
		}
		if (src != null) {
			src.close();
		}
		ResourceLocation id = SoulsMode.id("stone/" + texture.getNamespace() + "/" + texture.getPath());
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(out));
		return id;
	}
}
