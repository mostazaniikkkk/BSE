package dev.soulsmode.ascetic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsceticRulesTest {
	private static final String OW = "minecraft:overworld";

	@Test
	void zoneIsSeventeenBySeventeenChunks() {
		var zones = List.of(new AsceticRules.Zone(OW, 10, -4, 2));
		assertEquals(2, AsceticRules.cycleAt(zones, OW, 10, -4));
		assertEquals(2, AsceticRules.cycleAt(zones, OW, 18, 4));
		assertEquals(2, AsceticRules.cycleAt(zones, OW, 2, -12));
		assertEquals(0, AsceticRules.cycleAt(zones, OW, 19, -4));
		assertEquals(0, AsceticRules.cycleAt(zones, OW, 10, -13));
		assertEquals(0, AsceticRules.cycleAt(zones, "minecraft:the_nether", 10, -4));
	}

	@Test
	void overlappingZonesTakeTheHighestCycle() {
		var zones = List.of(new AsceticRules.Zone(OW, 0, 0, 1), new AsceticRules.Zone(OW, 10, 0, 4),
				new AsceticRules.Zone(OW, 5, 0, 2));
		assertEquals(4, AsceticRules.cycleAt(zones, OW, 5, 0));
		assertEquals(1, AsceticRules.cycleAt(zones, OW, -4, 0));
		assertEquals(4, AsceticRules.cycleAt(zones, OW, 18, 8));
	}

	@Test
	void cycleIsCappedAtFive() {
		int cycle = 0;
		for (int burn = 1; burn <= 8; burn++) {
			cycle = AsceticRules.nextCycle(cycle);
			assertEquals(Math.min(burn, 5), cycle);
		}
	}

	@Test
	void scalingNumbers() {
		assertEquals(1.0, AsceticRules.healthMultiplier(0), 1e-9);
		assertEquals(1.2, AsceticRules.healthMultiplier(1), 1e-9);
		assertEquals(2.0, AsceticRules.healthMultiplier(5), 1e-9);
		assertEquals(2.0, AsceticRules.healthMultiplier(9), 1e-9);
		assertEquals(1.6, AsceticRules.damageMultiplier(3), 1e-9);
		assertEquals(1.5, AsceticRules.speedMultiplier(5), 1e-9);
		assertEquals(2.25, AsceticRules.followRangeMultiplier(5), 1e-9);
		assertEquals(3, AsceticRules.extraLootRolls(3));
		assertEquals(0, AsceticRules.extraLootRolls(0));
	}

	@Test
	void spawnerLight() {
		assertEquals(-1, AsceticRules.spawnerLightLimit(0));
		assertEquals(3, AsceticRules.spawnerLightLimit(1));
		assertEquals(12, AsceticRules.spawnerLightLimit(4));
		assertEquals(15, AsceticRules.spawnerLightLimit(5));
	}

	@Test
	void phantomsAndRavagers() {
		assertFalse(AsceticRules.phantomsIgnoreInsomnia(0));
		assertTrue(AsceticRules.phantomsIgnoreInsomnia(1));
		assertFalse(AsceticRules.ravagersPossible(3));
		assertTrue(AsceticRules.ravagersPossible(4));
	}

	@Test
	void soulDrops() {
		assertEquals(0.0, AsceticRules.soulChance(0), 1e-9);
		assertEquals(0.25, AsceticRules.soulChance(5), 1e-9);
		assertEquals(-1, AsceticRules.soulTier(0, 0.5));
		for (double roll = 0; roll < 1; roll += 0.01) {
			assertEquals(0, AsceticRules.soulTier(1, roll));
		}
		assertArrayEquals(new int[] {0, 20, 40, 40}, AsceticRules.soulWeights(5));
		assertEquals(1, AsceticRules.soulTier(5, 0.0));
		assertEquals(2, AsceticRules.soulTier(5, 0.2));
		assertEquals(3, AsceticRules.soulTier(5, 0.99));
		assertEquals(0, AsceticRules.soulTier(2, 0.59));
		assertEquals(1, AsceticRules.soulTier(2, 0.6));
		for (int c = 1; c <= 5; c++) {
			int[] counts = new int[4];
			for (int i = 0; i < 1000; i++) {
				counts[AsceticRules.soulTier(c, (i + 0.5) / 1000)]++;
			}
			int[] w = AsceticRules.soulWeights(c);
			for (int t = 0; t < 4; t++) {
				assertEquals(w[t] * 10, counts[t], 1, "cycle " + c + " tier " + t);
			}
		}
	}
}
