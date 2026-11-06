package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Expected values come from FUN_14038d570 / FUN_14038d140 and the regulation's PlayerLevelUpSoulsParam. */
class StatsTest {
	private static final LevelUpSoulsTable TABLE = LevelUpSoulsTable.load();

	@Test
	void soulLevelIgnoresSlots9And10() {
		// 9 counted slots at 6 = 54, minus 0x35 (53) = 1; slots 9 and 10 don't count.
		Attributes a = new Attributes((short) 6, (short) 6, (short) 6, (short) 6, (short) 6, (short) 6,
				(short) 6, (short) 6, (short) 6, (short) 99, (short) 99);
		assertEquals(1, a.soulLevel());
	}

	@Test
	void soulLevelHasFloorOfOne() {
		Attributes a = new Attributes((short) 1, (short) 1, (short) 1, (short) 1, (short) 1, (short) 1,
				(short) 1, (short) 1, (short) 1, (short) 1, (short) 1);
		assertEquals(1, a.soulLevel());
	}

	@Test
	void soulLevelAtAllMax() {
		short[] max = new short[Attributes.SLOT_COUNT];
		java.util.Arrays.fill(max, (short) 99);
		assertEquals(99 * 9 - 0x35, new Attributes(max).soulLevel());
	}

	@Test
	void validRangeIsOneToNinetyNine() {
		short[] slots = new short[Attributes.SLOT_COUNT];
		java.util.Arrays.fill(slots, (short) 1);
		assertTrue(new Attributes(slots).isValid());
		slots[3] = 100;
		assertFalse(new Attributes(slots).isValid());
		slots[3] = 0;
		assertFalse(new Attributes(slots).isValid());
	}

	@Test
	void costUsesExactRowsFromParam() {
		assertEquals(0, TABLE.cost(0));
		assertEquals(500, TABLE.cost(1));
		assertEquals(810, TABLE.cost(10));
		assertEquals(997, TABLE.cost(14));
		assertEquals(1328037, TABLE.cost(850));
	}

	@Test
	void costPastLastDenseRowUsesRow850() {
		// Rows stop at 850 (plus a lone row 999 that index lookup never reaches); slope is 0.
		assertEquals(1328037, TABLE.cost(851));
		assertEquals(1328037, TABLE.cost(900));
		assertEquals(1328037, TABLE.cost(999));
	}
}
