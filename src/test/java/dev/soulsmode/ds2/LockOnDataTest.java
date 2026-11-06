package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockOnDataTest {
	private static final LockOnData DATA = LockOnData.load();

	@Test
	void valuesComeFromLockOnParamRow0() {
		// LockOnParam 0 = [22.5, 18, 15, 90, 50, 1.309, 0.7854, 5]; LockTargetParam 0 = (1, 1).
		assertEquals(22.5f, DATA.listRadius(), 1e-4);
		assertEquals(15f, DATA.acquireDistance(), 1e-4);
		assertEquals(Math.toRadians(75), DATA.coneYaw(), 1e-3);
		assertEquals(Math.toRadians(45), DATA.conePitch(), 1e-3);
		assertEquals(5f, DATA.closeRange(), 1e-4);
		// CommonParam +0x360: 7 s for enemies, 2 s for players.
		assertEquals(7f, DATA.mob().lostSightTime(), 1e-4);
		assertEquals(2f, DATA.player().lostSightTime(), 1e-4);
	}

	@Test
	void scoringPrefersTheCentreOfTheScreenThenDistance() {
		// FUN_140382f40: zone bonus outweighs any distance difference.
		assertTrue(LockOnData.score(14, 1) > LockOnData.score(2, 2));
		assertTrue(LockOnData.score(3, 2) > LockOnData.score(4, 2));
		assertEquals(1, DATA.zone(0.4, 0.8));
		assertEquals(2, DATA.zone(0.7, 0.8));
		assertEquals(3, DATA.zone(0.7, 0.95));
		assertEquals(0, DATA.zone(1.2, 0));
	}

	@Test
	void lockedPitchLimitsWidenWithHeightDifference() {
		// +0x80/+0x84 (+-40 deg) near level, +0x78/+0x7c (-40 / +70 deg) from 2 m of height difference.
		assertEquals(Math.toRadians(40), DATA.pitchLimits(0)[1], 1e-3);
		assertEquals(Math.toRadians(70), DATA.pitchLimits(3)[1], 1e-3);
	}
}
