package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Expected values are computed by hand from the regulation rows for 1 and 99 (see docs/research/stats.md). */
class DerivedStatsTest {
	private static final PhysicalStatsTable TABLE = PhysicalStatsTable.load();

	private static Attributes all(int value) {
		short[] slots = new short[Attributes.SLOT_COUNT];
		Arrays.fill(slots, (short) value);
		return new Attributes(slots);
	}

	@Test
	void allOnes() {
		DerivedStats d = DerivedStats.compute(all(1), TABLE);
		assertEquals(530 + 8 * 2, d.maxHp());
		assertEquals(820f, d.maxStamina());
		assertEquals(40f, d.maxEquipLoad());
		assertEquals(0, d.attunementSlots());
	}

	@Test
	void allNinetyNine() {
		DerivedStats d = DerivedStats.compute(all(99), TABLE);
		assertEquals(1945 + 8 * 70, d.maxHp());
		assertEquals(2000f, d.maxStamina());
		assertEquals(120f, d.maxEquipLoad());
		assertEquals(10, d.attunementSlots());
	}

	@Test
	void staminaFollowsEnduranceAndLoadFollowsVitality() {
		short[] slots = new short[Attributes.SLOT_COUNT];
		Arrays.fill(slots, (short) 1);
		slots[Attributes.ENDURANCE] = 99;
		slots[Attributes.VITALITY] = 99;
		slots[Attributes.ATTUNEMENT] = 99;
		DerivedStats d = DerivedStats.compute(new Attributes(slots), TABLE);
		assertEquals(2000f, d.maxStamina());
		assertEquals(120f, d.maxEquipLoad());
		assertEquals(10, d.attunementSlots());
	}

	@Test
	void outOfRangeValuesUseRowOne() {
		assertEquals(TABLE.row(1), TABLE.row(0));
		assertEquals(TABLE.row(1), TABLE.row(100));
	}
}
