package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoulsTest {
	private static final LevelUpSoulsTable TABLE = LevelUpSoulsTable.load();

	@Test
	void gainRaisesHeldAndMemory() {
		Souls s = new Souls(100, 1000);
		s.gain(50);
		assertEquals(150, s.held());
		assertEquals(1050, s.memory());
	}

	@Test
	void gainSaturates() {
		Souls s = new Souls(Souls.MAX - 10, Souls.MAX - 10);
		s.gain(10);
		assertEquals(Souls.MAX, s.held());
		s.gain(1_000_000);
		assertEquals(Souls.MAX, s.held());
		assertEquals(Souls.MAX, s.memory());
	}

	@Test
	void spendOnlyTouchesHeldAndFloorsAtZero() {
		Souls s = new Souls(300, 5000);
		s.spend(200);
		assertEquals(100, s.held());
		assertEquals(5000, s.memory());
		s.spend(500);
		assertEquals(0, s.held());
	}

	@Test
	void bloodstainRecoveryDoesNotCountTowardsMemory() {
		Souls s = new Souls(10, 1000);
		s.recover(500);
		assertEquals(510, s.held());
		assertEquals(1000, s.memory());
	}

	@Test
	void levelUpNeedsCostOfCurrentLevel() {
		assertTrue(new Souls(500, 0).canLevelUp(1, TABLE));
		assertFalse(new Souls(499, 0).canLevelUp(1, TABLE));
		assertFalse(new Souls(Souls.MAX, 0).canLevelUp(1000, TABLE));
	}
}
