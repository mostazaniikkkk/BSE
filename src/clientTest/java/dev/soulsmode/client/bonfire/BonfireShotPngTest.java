package dev.soulsmode.client.bonfire;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bonfire picture's round trip (BonfireShotClient): encode a full-size picture to PNG and read it back the way the
 * client stores it. Reading it with NativeImage.read(byte[]) overflowed LWJGL's stack (crash 2026-11-03).
 */
class BonfireShotPngTest {
	@Test
	void pictureSurvivesEncodeAndRead() throws Exception {
		byte[] png;
		try (NativeImage img = new NativeImage(BonfireShotClient.WIDTH, BonfireShotClient.HEIGHT, false)) {
			// Noise so the PNG does not compress below LWJGL's 64 KB stack (the case that crashed).
			java.util.Random r = new java.util.Random(1);
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					img.setPixelRGBA(x, y, 0xff000000 | r.nextInt(0xffffff));
				}
			}
			png = img.asByteArray();
		}
		assertTrue(png.length > 64 * 1024, "the test picture must exceed the stack size: " + png.length);
		// The old path crashed with this picture.
		byte[] crashing = png;
		org.junit.jupiter.api.Assertions.assertThrows(OutOfMemoryError.class, () -> NativeImage.read(crashing).close());
		try (NativeImage back = NativeImage.read(new ByteArrayInputStream(png))) {
			assertEquals(BonfireShotClient.WIDTH, back.getWidth());
			assertEquals(BonfireShotClient.HEIGHT, back.getHeight());
		}
	}
}
