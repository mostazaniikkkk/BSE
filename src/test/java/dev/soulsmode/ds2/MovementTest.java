package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MovementTest {
	@Test
	void turnRateFollowsSpeedBands() {
		assertEquals(Math.toRadians(720), Movement.turnRate(0f, false), 1e-3);
		assertEquals(Math.toRadians(720), Movement.turnRate(1.5f, false), 1e-3);
		assertEquals((12.566f + 18.850f) / 2, Movement.turnRate(2.75f, false), 1e-3);
		assertEquals(Math.toRadians(1080), Movement.turnRate(9f, false), 1e-3);
		assertEquals(9.076f, Movement.turnRate(5f, true), 1e-4);
		assertEquals(11.170f, Movement.turnRate(7f, true), 1e-4);
	}
}
