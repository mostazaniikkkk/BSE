package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Infusions (docs/research/titanite.md section 4, bleed.md section 8). */
class InfusionTest {
	private static Attributes stats(int value) {
		short[] s = new short[Attributes.SLOT_COUNT];
		Arrays.fill(s, (short) value);
		return new Attributes(s);
	}

	@Test
	void mundaneLongswordAtLowestStat10() {
		// bleed.md section 8: Longsword Mundane, lowest stat 10 -> AR 166 at +0, 222.5 at +10.
		assertEquals(166, Ds2Damage.attackRating("longsword", 0, 9, stats(10), false, false, -1, 0, null, null)[0], 1);
		assertEquals(222.5, Ds2Damage.attackRating("longsword", 10, 9, stats(10), false, false, -1, 0, null, null)[0], 1);
	}

	@Test
	void fireMovesHalfTheSplit() {
		float[] a = Ds2Damage.attackRating("longsword", 0, 1, stats(20), false, false, -1, 0, null, null);
		// Fire infusion: 50 / 50 physical / fire, base x 1.4.
		assertEquals(112 * 1.4f * 0.5f, Ds2Damage.attackParts("longsword", 0, 1, stats(20), false, false, -1, 0, null,
				null)[0][3], 0.01);
		assertEquals(true, a[3] > 0 && a[0] > 0);
	}

	@Test
	void bleedLongswordAgainstTheGenericEnemy() {
		// bleed.md section 6: Bleed Longsword +0, Dex 20 / Fth 20 -> value 662, 26.43 points; x 0.7 -> 18.50 per hit.
		short[] s = new short[Attributes.SLOT_COUNT];
		Arrays.fill(s, (short) 20);
		float value = Ds2Damage.bleedAttack("longsword", 0, 6, new Attributes(s), false, false, 0);
		assertEquals(662, value, 1);
		assertEquals(18.50, value * 100 / 2505 * 0.7, 0.05);
	}
}
