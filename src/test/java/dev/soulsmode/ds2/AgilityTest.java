package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** docs/research/agility.md: index, menu value, i-frame fraction with its penalty and the dodge windows. */
class AgilityTest {
	private static Attributes stats(int attunement, int adaptability) {
		short[] slots = new short[Attributes.SLOT_COUNT];
		java.util.Arrays.fill(slots, (short) 1);
		slots[Attributes.ATTUNEMENT] = (short) attunement;
		slots[Attributes.ADAPTABILITY] = (short) adaptability;
		return new Attributes(slots);
	}

	@Test
	void indexAndMenuValue() {
		assertEquals((10 + 3 * 7) / 4, Agility.index(stats(10, 7)));
		assertEquals(85, Agility.shown(stats(5, 5)));
		assertEquals(110, Agility.shown(stats(30, 30)));
		assertEquals(120, Agility.shown(stats(99, 99)));
	}

	@Test
	void rollWindowGrowsWithAgility() {
		float low = Agility.iframeFraction(true, stats(1, 1), 0, 0);
		assertEquals(0.25f, low, 1e-4f);
		// [0, 0.1 + 0.4667 a): 0.2167 s at index 1, 0.5667 s at 99.
		assertTrue(Dodge.ROLL.isInvincible(0f, low));
		assertTrue(Dodge.ROLL.isInvincible(0.21f, low));
		assertFalse(Dodge.ROLL.isInvincible(0.22f, low));
		float high = Agility.iframeFraction(true, stats(99, 99), 0, 0);
		assertEquals(1f, high, 1e-4f);
		assertTrue(Dodge.ROLL.isInvincible(0.56f, high));
		assertFalse(Dodge.ROLL.isInvincible(0.57f, high));
		assertFalse(Dodge.ROLL_FAILED.isInvincible(0.2f, high));
	}

	@Test
	void backstepWindow() {
		float a = Agility.iframeFraction(false, stats(1, 1), 0, 0);
		assertEquals(0.2f, a, 1e-4f);
		assertFalse(Dodge.BACKSTEP.isInvincible(0.13f, a));
		assertTrue(Dodge.BACKSTEP.isInvincible(0.14f, a));
		assertFalse(Dodge.BACKSTEP.isInvincible(0.21f, a));
		assertTrue(Dodge.BACKSTEP_TIRED.isInvincible(0.34f, 1f));
		assertFalse(Dodge.BACKSTEP_TIRED.isInvincible(0.67f, 1f));
	}

	@Test
	void penalty() {
		assertEquals(0f, Agility.staminaLack(100, 50), 0);
		assertEquals(0.5f, Agility.staminaLack(25, 50), 1e-6f);
		assertEquals(1f, Agility.staminaLack(0, 50), 0);
		float full = Agility.iframeFraction(true, stats(99, 99), 0, 0);
		// g = 1: pen = 0.5; f = 1: pen = 0.5 (30 -> 50); both: clamped to 1.
		assertEquals(full * 0.5f, Agility.iframeFraction(true, stats(99, 99), 0, 1), 1e-5f);
		assertEquals(full * 0.5f, Agility.iframeFraction(true, stats(99, 99), 1, 0), 1e-5f);
		assertEquals(full * 0.7f, Agility.iframeFraction(true, stats(99, 99), 0.001f, 0), 1e-3f);
		assertEquals(0f, Agility.iframeFraction(true, stats(99, 99), 1, 1), 1e-6f);
	}

	@Test
	void weaponChangeSpeed() {
		assertEquals(1.2f, Agility.weaponChangeSpeed(stats(1, 1)), 1e-4f);
		assertEquals(1.45f, Agility.weaponChangeSpeed(stats(99, 99)), 1e-4f);
	}
}
