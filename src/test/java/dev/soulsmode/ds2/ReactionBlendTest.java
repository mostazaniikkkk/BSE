package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReactionBlendTest {
	@Test
	void quadrantsBlendBetweenNeighbours() {
		// 0 deg: half FL (160010), half FR (160011); 45 deg: all FR.
		var front = ReactionBlend.quadrants(1, 0);
		assertEquals(160010, front.get(0).tae());
		assertEquals(0.5f, front.get(0).weight(), 1e-6f);
		assertEquals(160011, front.get(1).tae());
		var right = ReactionBlend.quadrants(1, 45);
		assertEquals(160011, right.get(1).tae());
		assertEquals(1f, right.get(1).weight(), 1e-6f);
	}

	@Test
	void rootMotionComesFromTheClips() {
		// Attacker front-right, a quarter Heavy: Light stays, Heavy FR (a00_16_0051) ends 3.5535 m left and back.
		float[] end = ReactionBlend.delta(ReactionBlend.c0001Lower(1, 45, 0.25f), 0, ReactionBlend.C0001_LENGTH);
		assertEquals(0.25f * 3.5535f, end[0], 1e-3f);
		assertEquals(0.25f * 3.5535f, end[1], 1e-3f);
		// Straight from the front: FL and FR half each, slerped (FUN_1405ea3f0): straight back, full 5.03 m length.
		float[] front = ReactionBlend.delta(ReactionBlend.c0001Lower(1, 0, 1), 0, ReactionBlend.C0001_LENGTH);
		assertEquals(0, front[0], 1e-3f);
		assertEquals(3.5535f * (float) Math.sqrt(2), front[1], 1e-3f);
		// Hollow Soldier hit from behind, all Heavy: a00_16_0056 ends 4.24 m forward.
		float[] back = ReactionBlend.delta(ReactionBlend.c1020Lower(false, true, 1), 0, ReactionBlend.C1020_LENGTH);
		assertEquals(0, back[0], 1e-4f);
		assertEquals(-4.24f, back[1], 1e-3f);
	}
}
