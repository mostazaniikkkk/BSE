package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** docs/research/poise.md: the maximum's parts, the enemy and weapon hit rows and the TAE windows. */
class PoiseTest {
	private static Attributes stats(int str, int dex, int endurance, int adaptability) {
		short[] slots = new short[Attributes.SLOT_COUNT];
		java.util.Arrays.fill(slots, (short) 6);
		slots[Attributes.STRENGTH] = (short) str;
		slots[Attributes.DEXTERITY] = (short) dex;
		slots[Attributes.ENDURANCE] = (short) endurance;
		slots[Attributes.ADAPTABILITY] = (short) adaptability;
		return new Attributes(slots);
	}

	private static float set(String material, Attributes stats) {
		float sum = 0;
		for (String piece : new String[] {"helmet", "chestplate", "leggings", "boots"}) {
			sum += Poise.piecePoise(Poise.piece(material, piece), stats);
		}
		return sum;
	}

	@Test
	void maximum() {
		Attributes s = stats(10, 10, 6, 6);
		// s.2.2: Knight 46 + min(Agu, Adp) = 6 -> 1.8.
		assertEquals(46f, set("iron", s), 1e-3f);
		assertEquals(1.8f, Poise.statPoise(s), 1e-4f);
		assertEquals(0f, Poise.base(), 0);
		// King's set with Str 10 / Dex 10: f = 0.452, pen = 0.526 -> 17.8 instead of 37.5.
		assertEquals(17.8f, set("gold", s), 0.05f);
		assertEquals(37.5f, set("gold", stats(14, 12, 6, 6)), 1e-3f);
		assertEquals(60f, set("netherite", s), 1e-3f);
		assertEquals(0.56f, Poise.regenPerSecond(), 1e-4f);
	}

	@Test
	void enemyHits() {
		Poise.Hit soldier = Poise.enemyHit("c1020", 110200110);
		assertNotNull(soldier);
		assertEquals(20f, soldier.poiseDamage(), 1e-4f);
		assertEquals(0, soldier.rule());
		assertEquals(2, soldier.remapped());
		assertEquals(1.15f, soldier.speed(), 1e-4f);
		Poise.Hit ogre = Poise.enemyHit("c3000", 130000110);
		assertEquals(2, ogre.rule());
		assertEquals(4, ogre.remapped());
		assertNull(Poise.enemyHit("c1020", 1));
	}

	@Test
	void weaponHitsBySlot() {
		Poise.Hit r1 = Poise.weaponHit(29010012, 0);
		assertNotNull(r1);
		assertEquals(10f, r1.poiseDamage(), 1e-4f);
		assertNull(Poise.weaponHit(29010012, 1));
	}

	@Test
	void windows() {
		// The roll's 110500 (0.1 .. 0.667): no reaction unless the rule is 2.
		assertEquals(true, Poise.superArmor(120020, 0.3f));
		assertEquals(false, Poise.superArmor(120020, 0.7f));
		assertEquals(1.3333f, Poise.staggerClip(160010).duration(), 1e-4f);
		assertEquals(0.9f, Poise.staggerClip(160050).end("CancelControl_Move"), 1e-4f);
		assertEquals(0f, Poise.staggerClip(160010).end("CancelControl_Move"), 0);
	}
}
