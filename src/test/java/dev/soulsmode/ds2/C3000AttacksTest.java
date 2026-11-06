package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class C3000AttacksTest {
	@Test
	void tableFollowsTheEsdOrder() {
		// ai300000 S2147483609: behind within 4.5 m -> combo 52, whatever r0.
		assertEquals(52, C3000Attacks.table(3, 120, 3));
		// Near (< 2.5 m): 0 < r0 < 5 -> 14, < 10 -> 15, < 15 -> 51, else 50 (r0 = 0 falls to the end).
		assertEquals(14, C3000Attacks.table(2, 0, 1));
		assertEquals(15, C3000Attacks.table(2, 0, 9));
		assertEquals(51, C3000Attacks.table(2, 0, 14));
		assertEquals(50, C3000Attacks.table(2, 0, 0));
		assertEquals(50, C3000Attacks.table(2, 0, 20));
		// Far: no 14 / 15.
		assertEquals(51, C3000Attacks.table(3, 0, 3));
		assertEquals(50, C3000Attacks.table(4.5, 120, 15));
	}

	@Test
	void indexHasTheOgreActions() {
		C3000Attacks.Action single = C3000Attacks.action(14);
		assertFalse(single.combo());
		assertEquals(2, single.reach(), 1e-6f);
		assertEquals(30, single.angle(), 1e-6f);
		assertEquals("c3000_65141010", single.steps().get(0).animation());
		assertEquals(1.1f, single.steps().get(0).hits().get(0).start(), 1e-4f);

		C3000Attacks.Action combo = C3000Attacks.action(51);
		assertTrue(combo.combo());
		assertEquals(3, combo.steps().size());
		assertEquals(65101130, combo.steps().get(2).tae());
		// Step 0 chains at its HitDuration end (1.0 s); it lunges 2.8 m forward (trajectory Y back < 0).
		assertEquals(1.0f, combo.steps().get(0).hitDurationEnd(), 1e-4f);
		C3000Attacks.Trajectory tr = combo.steps().get(0).trajectory();
		assertEquals(-2.8f, tr.at(combo.steps().get(0).duration())[1], 1e-3f);
		// 65101130's slot 3 windows use EnemyDamageParam 130000050 (rate 0): no damage.
		assertEquals(0, combo.steps().get(2).hits().get(1).rate(), 1e-6f);

		C3000Attacks.Action rear = C3000Attacks.action(52);
		assertTrue(Float.isNaN(rear.reach()));
		assertEquals(0, rear.continueDistance(), 1e-6f);

		assertEquals(3.9f, C3000Attacks.locomotion(47001).speed(), 1e-4f);
		assertEquals(0.5f, C3000Attacks.damageDuration(), 1e-4f);
	}

	@Test
	void reactionRootMotionUsesTheOgreClips() {
		// Hit from the front, all Heavy: a00_16_0055 ends 5.0 m back; Light does not move.
		float[] heavy = ReactionBlend.delta(ReactionBlend.c3000Lower(false, false, 1), 0, ReactionBlend.C3000_LENGTH);
		assertEquals(5.0f, heavy[1], 1e-3f);
		float[] light = ReactionBlend.delta(ReactionBlend.c3000Lower(false, false, 0), 0, ReactionBlend.C3000_LENGTH);
		assertEquals(0, light[1], 1e-6f);
	}
}
