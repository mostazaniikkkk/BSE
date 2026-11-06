package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DS2 perception (docs/research/detection.md). */
class PerceptionTest {
	@Test
	void soundRadii() {
		assertEquals(4, Perception.JOG.radius(), 1e-6);
		assertEquals(8, Perception.DASH.radius(), 1e-6);
		assertEquals(12, Perception.HIT_BACK.radius(), 1e-6);
		assertEquals(0, Perception.level(1));
		assertEquals(20, Perception.radius(15, 20), 1e-6);
	}

	@Test
	void hearingNeedsPerceivedLevelOne() {
		Perception.Logic h = Perception.HOLLOW;
		// L 2, r 4: (16 - d^2) / 16 x 2 >= 1 up to d^2 = 8.
		assertTrue(Perception.hears(h, 2, 20, 7.9));
		assertFalse(Perception.hears(h, 2, 20, 8.1));
		// L 4, r 8: up to d^2 = 48.
		assertTrue(Perception.hears(h, 4, 20, 47.9));
		assertFalse(Perception.hears(h, 4, 20, 48.1));
		// L 8, factor 15, r 12: up to d^2 = 126.
		assertTrue(Perception.hears(h, 8, 15, 125.9));
		assertFalse(Perception.hears(h, 8, 15, 126.1));
		// Outside the radius nothing; level 0 is never heard.
		assertEquals(-1, Perception.perceived(2, 20, 16, 1), 1e-6);
		assertFalse(Perception.hears(h, 0, 20, 0));
	}

	@Test
	void sightRangeWithLight() {
		Perception.Logic h = Perception.HOLLOW;
		assertEquals(20, Perception.sightRange(h, true, false), 1e-6);
		assertEquals(13, Perception.sightRange(h, false, false), 1e-6);
		assertEquals(27, Perception.sightRange(h, false, true), 1e-6);
		assertEquals(20, Perception.sightRange(h, true, true), 1e-6);
	}

	@Test
	void cone() {
		Perception.Logic h = Perception.HOLLOW;
		// Yaw 0 faces +z in Minecraft.
		assertTrue(Perception.inCone(h, 0, 0, 5, 0));
		assertFalse(Perception.inCone(h, 0, 0, -5, 0));
		assertTrue(Perception.inCone(h, -5 * Math.sin(Math.toRadians(59)), 0, 5 * Math.cos(Math.toRadians(59)), 0));
		assertFalse(Perception.inCone(h, -5 * Math.sin(Math.toRadians(61)), 0, 5 * Math.cos(Math.toRadians(61)), 0));
		assertTrue(Perception.inCone(h, 0, 4.9, 5, 0));
		assertFalse(Perception.inCone(h, 0, 5.1, 5, 0));
		assertTrue(Perception.inCone(h, 0, -4.9, 5, 0));
		// Facing west (yaw 90 faces -x).
		assertTrue(Perception.inCone(h, -5, 0, 0, 90));
	}

	@Test
	void seesAlwaysUpClose() {
		assertTrue(Perception.sees(Perception.HOLLOW, 0.9, 20, false, false));
		assertFalse(Perception.sees(Perception.HOLLOW, 1.5, 20, false, true));
		assertTrue(Perception.sees(Perception.OGRE, 1.5, 20, false, false));
		assertTrue(Perception.sees(Perception.HOLLOW, 19, 20, true, true));
		assertFalse(Perception.sees(Perception.HOLLOW, 21, 20, true, true));
	}

	@Test
	void checkTimers() {
		assertEquals(0.15f, Perception.checkInterval(10, false, 0.5f), 1e-6);
		assertEquals(0.05f, Perception.checkInterval(10, true, 0), 1e-6);
		assertEquals(0.15f, Perception.checkInterval(10, true, 1), 1e-6);
		assertEquals(1.0f, Perception.checkInterval(35, false, 0), 1e-6);
		// f = 0.5: between 0.275 and 1.0.
		assertEquals(0.275f, Perception.checkInterval(35, true, 0), 1e-6);
		assertEquals(1.0f, Perception.checkInterval(35, true, 1), 1e-6);
		assertEquals(1.5f, Perception.checkInterval(60, false, 0), 1e-6);
		assertEquals(0.5f, Perception.checkInterval(60, true, 0), 1e-6);
	}

	@Test
	void gaitEvents() {
		Perception.Gait jog = Perception.Gait.JOG;
		assertFalse(jog.fires(0.30, 0.35));
		assertTrue(jog.fires(0.35, 0.40));
		assertTrue(jog.fires(0.70, 0.75));
		// The next cycle: 0.7667 + 0.367.
		assertTrue(jog.fires(1.10, 1.15));
		assertFalse(jog.fires(0.75, 1.10));
		assertTrue(Perception.Gait.DASH.fires(0.25, 0.30));
		assertTrue(Perception.Gait.SIDE_JOG_FORWARD.fires(0.35, 0.40));
		assertFalse(Perception.Gait.SIDE_JOG_FORWARD.fires(0.70, 0.75));
	}

	@Test
	void dodgeSounds() {
		assertEquals(0.233f, Perception.dodgeTime(Dodge.ROLL), 1e-6);
		assertEquals(0.333f, Perception.dodgeTime(Dodge.BACKSTEP), 1e-6);
		assertEquals(4, Perception.dodgeSound(Dodge.ROLL).level());
		assertEquals(2, Perception.dodgeSound(Dodge.BACKSTEP_TIRED).level());
	}

	@Test
	void jumpAndRollLanding() {
		Perception.Air air = new Perception.Air();
		double t = 0;
		assertEquals(Perception.Air.Event.NONE, air.update(true, 64, 5, t));
		assertEquals(Perception.Air.Event.TAKEOFF, air.update(false, 64.4, 5, t += 0.05));
		assertEquals(Perception.Air.Event.NONE, air.update(false, 64.8, 5, t += 0.05));
		// Grounded before 0.4333 s waits for it.
		assertEquals(Perception.Air.Event.NONE, air.update(true, 64, 5, t += 0.05));
		t = 0.5;
		assertEquals(Perception.Air.Event.ROLL_LANDING, air.update(true, 64, 5, t));
		// A slow jump lands without rolling.
		assertEquals(Perception.Air.Event.TAKEOFF, air.update(false, 64.4, 1, t += 0.05));
		assertEquals(Perception.Air.Event.NONE, air.update(true, 64, 1, t += 0.5));
		// A walk-off fall rolls only from 5 m at 4.5 m/s.
		assertEquals(Perception.Air.Event.NONE, air.update(false, 63.9, 5, t += 0.05));
		assertEquals(Perception.Air.Event.NONE, air.update(false, 63, 5, t += 0.05));
		assertEquals(Perception.Air.Event.ROLL_LANDING, air.update(true, 58, 5, t += 0.05));
		assertEquals(Perception.Air.Event.NONE, air.update(false, 57.9, 5, t += 0.05));
		assertEquals(Perception.Air.Event.NONE, air.update(false, 57, 5, t += 0.05));
		assertEquals(Perception.Air.Event.NONE, air.update(true, 55, 5, t += 0.05));
	}
}
