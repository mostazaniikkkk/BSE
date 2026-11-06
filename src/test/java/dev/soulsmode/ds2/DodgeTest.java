package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Morpheme windows land on whole 30 fps frames of each animation (see docs/research/animations.md). */
class DodgeTest {
	@Test
	void rerollLocksAreWholeFrames() {
		assertEquals(24, Dodge.ROLL.rerollLock * 30f, 0.01);
		assertEquals(8, Dodge.BACKSTEP.rerollLock * 30f, 0.01);
		assertEquals(46, Dodge.ROLL.duration * 30f, 0.01);
		assertEquals(35, Dodge.BACKSTEP.duration * 30f, 0.01);
	}

	/** The TAE 110500 window, which is not the i-frames (agility.md s.2.1; those are AgilityTest). */
	@Test
	void noPoiseWindows() {
		assertFalse(Dodge.ROLL.inNoPoiseWindow(0.05f));
		assertTrue(Dodge.ROLL.inNoPoiseWindow(0.1f));
		assertTrue(Dodge.ROLL.inNoPoiseWindow(0.6f));
		assertFalse(Dodge.ROLL.inNoPoiseWindow(0.7f));
		assertTrue(Dodge.BACKSTEP.inNoPoiseWindow(0.2f));
		assertFalse(Dodge.BACKSTEP.inNoPoiseWindow(0.34f));
	}
}
