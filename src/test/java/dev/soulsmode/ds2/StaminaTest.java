package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Values from ChrParam/ChrCommonParam row 100 and ChrEquipLoadParam (see docs/research/stamina.md). */
class StaminaTest {
	private static final Stamina.Params PARAMS = Stamina.Params.load();
	private static final Stamina.LoadCurves CURVES = Stamina.LoadCurves.load();

	private static Stamina full() {
		return new Stamina(PARAMS, CURVES, 820);
	}

	@Test
	void paramsMatchRegulation() {
		assertEquals(519f, PARAMS.regenPerSecond());
		assertEquals(-25f, PARAMS.exhaustedCeiling());
		assertEquals(-150f, PARAMS.exhaustedFloor());
		assertEquals(0.1f, PARAMS.regenDelay());
		assertEquals(360f, PARAMS.jumpCost(), 1e-3);
	}

	@Test
	void dodgeCostsFromFun140376770() {
		// ChrCommonParam+0x24 300 x +0x2c 1.0 (roll) / +0x28 0.7 (backstep); x1.25 chained or inside an attack.
		assertEquals(300f, PARAMS.dodgeCost(true, false, false), 1e-3);
		assertEquals(210f, PARAMS.dodgeCost(false, false, false), 1e-3);
		assertEquals(375f, PARAMS.dodgeCost(true, true, false), 1e-3);
		assertEquals(262.5f, PARAMS.dodgeCost(false, false, true), 1e-3);
		assertEquals(200f, PARAMS.drainBase() * PARAMS.sprintRate(), 1e-3);
		assertEquals(3.25f, PARAMS.sprintCooldown(), 1e-4);
	}

	@Test
	void actionRegenBlockHoldsRegen() {
		Stamina s = full();
		s.consume(400);
		s.setActionBlocksRegen(true);
		for (int i = 0; i < 10; i++) {
			s.tick(0.05f);
		}
		assertEquals(420f, s.current(), 1e-3);
		s.setActionBlocksRegen(false);
		s.tick(0.05f);
		assertTrue(s.current() > 420f);
	}

	@Test
	void overspendingStopsAtZeroBecauseMinIsZero() {
		Stamina s = full();
		s.consume(2000);
		assertEquals(0f, s.current());
		assertTrue(s.isExhausted());
	}

	@Test
	void regenWaitsForTheDelayThenRuns() {
		Stamina s = full();
		s.consume(400);
		s.tick(0.05f);
		assertEquals(420f, s.current(), 1e-3);
		s.tick(0.06f); // delay expires in this tick, so regen already applies
		assertEquals(420f + 519f * 0.06f, s.current(), 1e-3);
	}

	@Test
	void regenCapsAtMax() {
		Stamina s = full();
		s.consume(10);
		s.tick(0.2f);
		s.tick(1f);
		assertEquals(820f, s.current());
		assertFalse(s.isExhausted());
	}

	@Test
	void equipLoadSlowsRegen() {
		Stamina s = full();
		assertEquals(1f, s.regenMultiplier(), 1e-6);
		s.setEquipLoadRatio(0.5f); // 50%: between 30% (7.5) and 70% (35) -> 21.25
		assertEquals(1f - 0.2125f, s.regenMultiplier(), 1e-5);
		s.setEquipLoadRatio(1.0f); // 100% -> 50
		assertEquals(0.5f, s.regenMultiplier(), 1e-6);
		s.setEquipLoadRatio(2.0f); // beyond the last threshold -> 60
		assertEquals(0.4f, s.regenMultiplier(), 1e-6);
	}

	@Test
	void drainsBlockRegenAndScaleWithLoad() {
		Stamina s = full();
		s.beginDrain();
		s.drain(2.0f, CURVES.drainA(), 1f); // 100 * 2.0 * (1 - 0.665 / 100)
		assertEquals(820f - 200f * (1f - 0.00665f), s.current(), 1e-3);
		float afterDrain = s.current();
		s.tick(1f);
		assertEquals(afterDrain, s.current(), 1e-3);
		s.endDrain();
		s.tick(0.1f);
		assertEquals(afterDrain + 51.9f, s.current(), 1e-3);
	}
}
