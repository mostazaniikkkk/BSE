package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The level-up screen flow of FUN_1400c9e80 / FUN_1400ce180 / FUN_1400c9f50 (docs/research/stats.md). */
class LevelUpMenuTest {
	private static final LevelUpSoulsTable TABLE = LevelUpSoulsTable.load();

	/** Every counted slot at 7: 63 - 53 = SL 10. */
	private static Attributes sl10() {
		short[] slots = new short[Attributes.SLOT_COUNT];
		java.util.Arrays.fill(slots, (short) 7);
		return new Attributes(slots);
	}

	@Test
	void raisingPaysTheCostOfTheCurrentLevel() {
		LevelUpMenu menu = new LevelUpMenu(sl10(), 10_000, TABLE);
		assertTrue(menu.raise(Attributes.VIGOR));
		assertEquals(11, menu.level());
		assertEquals(8, menu.attributes().get(Attributes.VIGOR));
		assertEquals(10_000 - TABLE.cost(10), menu.souls());
		assertEquals(TABLE.cost(11), menu.cost());
		assertTrue(menu.canConfirm());
	}

	@Test
	void loweringRefundsAndCannotGoBelowTheStart() {
		LevelUpMenu menu = new LevelUpMenu(sl10(), 10_000, TABLE);
		assertFalse(menu.lower(Attributes.STRENGTH));
		menu.raise(Attributes.STRENGTH);
		menu.raise(Attributes.STRENGTH);
		assertTrue(menu.lower(Attributes.STRENGTH));
		assertEquals(11, menu.level());
		assertEquals(10_000 - TABLE.cost(10), menu.souls());
		assertTrue(menu.lower(Attributes.STRENGTH));
		assertFalse(menu.lower(Attributes.STRENGTH));
		assertEquals(10_000, menu.souls());
		assertFalse(menu.canConfirm());
	}

	@Test
	void notEnoughSoulsBuysNothing() {
		LevelUpMenu menu = new LevelUpMenu(sl10(), TABLE.cost(10) - 1, TABLE);
		assertFalse(menu.canRaise(Attributes.VIGOR));
		assertFalse(menu.raise(Attributes.VIGOR));
		assertEquals(10, menu.level());
		assertEquals(TABLE.cost(10) - 1, menu.souls());
	}

	@Test
	void slotAt99RefundsTheBoughtLevel() {
		LevelUpMenu menu = new LevelUpMenu(sl10().with(Attributes.FAITH, 99), 10_000_000, TABLE);
		int level = menu.level();
		assertFalse(menu.canRaise(Attributes.FAITH));
		assertFalse(menu.raise(Attributes.FAITH));
		assertEquals(level, menu.level());
		assertEquals(10_000_000, menu.souls());
	}

	@org.junit.jupiter.api.Test
	void experienceLevelsCostTheTargetSoulLevel() {
		// The user's exception: from 12 to 13 costs 13 experience levels, from 13 to 14 costs 14.
		org.junit.jupiter.api.Assertions.assertEquals(13, LevelUpSoulsTable.EXPERIENCE_LEVELS.cost(12));
		org.junit.jupiter.api.Assertions.assertEquals(14, LevelUpSoulsTable.EXPERIENCE_LEVELS.cost(13));
		org.junit.jupiter.api.Assertions.assertEquals(1, LevelUpSoulsTable.EXPERIENCE_LEVELS.cost(0));
	}
}
