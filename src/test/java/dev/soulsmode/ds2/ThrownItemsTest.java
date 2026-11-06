package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** docs/research/thrown_items.md: the throw clips, release times and locks. */
class ThrownItemsTest {
	@Test
	void lloydsTalisman() {
		Weapons.Effigy lloyd = Weapons.lloydsTalisman();
		assertEquals(0.5333f, lloyd.effectAt(), 1e-4f);
		assertEquals(2.6667f, lloyd.use().duration(), 1e-4f);
		assertTrue(lloyd.use().blocksMovement(1.4f));
		assertFalse(lloyd.use().blocksMovement(1.6f));
	}

	@Test
	void firebomb() {
		Weapons.Effigy bomb = Weapons.firebomb();
		assertEquals(0.8667f, bomb.effectAt(), 1e-4f);
		assertEquals(3.0f, bomb.use().duration(), 1e-4f);
		assertTrue(bomb.use().blocksMovement(1.8f));
		assertEquals(180110, bomb.use().taeId());
	}
}
