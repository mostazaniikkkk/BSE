package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartingClassesTest {
	private static final StartingClasses CLASSES = StartingClasses.load();

	@Test
	void soulLevelFormulaMatchesEveryClassRow() {
		// PlayerStatusParam stores each class SL at +0x04; it must equal the FUN_14038d570 formula
		// applied to the slots read through FUN_1401fbc00's offsets.
		assertEquals(8, CLASSES.all().size());
		for (StartingClasses.StartingClass c : CLASSES.all()) {
			assertEquals(c.paramSoulLevel(), c.attributes().soulLevel(), c.name());
			assertTrue(c.attributes().isValid(), c.name());
		}
	}

	@Test
	void lookupByIdAndIndex() {
		assertEquals("Knight", CLASSES.byId("knight").orElseThrow().name());
		assertEquals("Deprived", CLASSES.byIndex(10).orElseThrow().name());
	}
}
