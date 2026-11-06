package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** docs/research/last_giant.md: the data the penalty giant (SoulsGiants) reads. */
class C3096AttacksTest {
	@Test
	void phasesAndActions() {
		C3096Attacks.Phase one = C3096Attacks.phase(1);
		assertEquals(0, one.mode());
		assertEquals(3f, one.stopDistance(), 1e-4f);
		assertEquals(18, one.rows().get(0).action());
		C3096Attacks.Phase two = C3096Attacks.phase(2);
		assertEquals(1, two.mode());
		assertEquals(19, two.rows().get(0).action());
		for (C3096Attacks.Phase p : new C3096Attacks.Phase[] {one, two}) {
			for (C3096Attacks.Row row : p.rows()) {
				assertNotNull(C3096Attacks.action(p.mode(), row.action()), "action " + row.action());
			}
			assertNotNull(C3096Attacks.action(p.mode(), p.afterAction()));
		}
		C3096Attacks.Action rip = C3096Attacks.action(0, C3096Attacks.phaseChangeAction());
		assertEquals(65001050, rip.steps().get(0).tae());
		assertEquals(50, C3096Attacks.phaseChangePercent());
	}

	@Test
	void damageRowsAndLoops() {
		C3096Attacks.DamageRow foot = C3096Attacks.damageRow(130960210);
		assertEquals(622f, foot.values()[0], 1e-3f);
		assertEquals(9999f, foot.poise().poiseDamage(), 1e-3f);
		for (int i = 0; i < 2; i++) {
			for (C3096Attacks.Row row : C3096Attacks.phase(i + 1).rows()) {
				for (C3096Attacks.Step s : C3096Attacks.action(i, row.action()).steps()) {
					for (C3096Attacks.Hit h : s.hits()) {
						assertTrue(h.rate() == 0 || C3096Attacks.damageRow(h.enemyDamageParam()) != null,
								"row " + h.enemyDamageParam());
					}
				}
			}
		}
		assertEquals(2.1667f, C3096Attacks.loop(37001).speed(), 1e-3f);
		assertEquals(6f, C3096Attacks.loop(47001).speed(), 1e-3f);
		assertNotNull(C3096Attacks.loop(7002));
	}
}
