package dev.soulsmode.ascetic;

import java.util.List;

/**
 * "Llama de la adversidad" (Bonfire Ascetic): every number of the mechanic, in one place. The mechanic is the user's
 * own design (2026-11-03), inspired by DS2's Bonfire Ascetic (ItemParam 60527000; DS2 raises the bonfire intensity,
 * FUN_14017ffa0, docs/research/bonfire.md) but not a port of it: none of these values comes from DS2.
 * docs/research/llama_adversidad.md lists which ones the user fixed and which are the default proposal.
 * <p>
 * Pure Java (no Minecraft classes) so the unit tests can check it.
 */
public final class AsceticRules {
	/** Master switch: off, the item cannot be burned and zones have no effect. */
	public static final boolean ENABLED = true;

	/** User: the area reset around the bonfire and the zone it makes, in chunks around the bonfire's chunk (17 x 17). */
	public static final int RADIUS_CHUNKS = 8;
	/** User: each burn adds one cycle to the bonfire's zone, up to 5. */
	public static final int MAX_CYCLE = 5;

	/** Default proposal (user, 2026-11-03): hostile mobs get +20 % max health per cycle. */
	public static final double HEALTH_PER_CYCLE = 0.20;
	/** Default proposal: hostile mobs deal +20 % damage per cycle. */
	public static final double DAMAGE_PER_CYCLE = 0.20;
	/** Default proposal: +10 % movement speed per cycle ("better AI"). */
	public static final double SPEED_PER_CYCLE = 0.10;
	/** Default proposal: +25 % follow range per cycle ("better AI"). */
	public static final double FOLLOW_RANGE_PER_CYCLE = 0.25;
	/** Default proposal: one extra roll of the mob's own loot table per cycle. */
	public static final int EXTRA_LOOT_ROLLS_PER_CYCLE = 1;

	/** User: about one vindicator every 2 minutes per zone, by day, while a player is inside. */
	public static final int VINDICATOR_INTERVAL_TICKS = 2 * 60 * 20;
	/** Design choice: how far from the player the vindicator appears (blocks, horizontal). */
	public static final int VINDICATOR_MIN_DISTANCE = 16;
	public static final int VINDICATOR_MAX_DISTANCE = 32;
	/** User: from the 4th burn (more than 3) a vindicator attempt may also bring a ravager. */
	public static final int RAVAGER_MIN_CYCLE = 4;
	/** User: chance per vindicator attempt of a ravager in addition. */
	public static final double RAVAGER_CHANCE = 0.02;

	/** Default proposal (the user fixed only "cycle 5 = any light"): spawners accept light up to 3 per cycle. */
	public static final int SPAWNER_LIGHT_PER_CYCLE = 3;
	/** User (2026-11-03): from the second burn, evokers also come by day, on their own timer like the vindicators'. */
	public static final int EVOKER_MIN_CYCLE = 2;
	/** Default proposal: one evoker every 2 minutes per zone (the vindicators' interval). */
	public static final int EVOKER_INTERVAL_TICKS = VINDICATOR_INTERVAL_TICKS;
	/**
	 * User (2026-11-03): the reset adds ore veins by cycle. Default proposal: each of the chunk's ore features is placed
	 * this many extra times per cycle on average (+25 % of the vanilla ores per cycle).
	 */
	public static final double ORE_BONUS_PER_CYCLE = 0.25;
	/** User: from the first burn, phantoms ignore the insomnia requirement in the zone. */
	public static final int PHANTOM_MIN_CYCLE = 1;

	/** Default proposal: a hostile mob killed by a player drops a soul item with 5 % per cycle. */
	public static final double SOUL_CHANCE_PER_CYCLE = 0.05;
	/**
	 * Default proposal: soul item weights by cycle (row c - 1); columns: lost undead, large lost undead, nameless
	 * soldier, large nameless soldier.
	 */
	private static final int[][] SOUL_WEIGHTS = {
			{100, 0, 0, 0},
			{60, 40, 0, 0},
			{30, 40, 30, 0},
			{10, 30, 40, 20},
			{0, 20, 40, 40},
	};

	private AsceticRules() {
	}

	/** A zone: the bonfire's dimension and chunk, and how many times the item was burned there (1..MAX_CYCLE). */
	public record Zone(String dimension, int chunkX, int chunkZ, int cycle) {
		public boolean contains(String otherDimension, int otherChunkX, int otherChunkZ) {
			return dimension.equals(otherDimension) && Math.abs(otherChunkX - chunkX) <= RADIUS_CHUNKS
					&& Math.abs(otherChunkZ - chunkZ) <= RADIUS_CHUNKS;
		}
	}

	/** User: overlapping zones take the highest cycle. 0 outside every zone. */
	public static int cycleAt(List<Zone> zones, String dimension, int chunkX, int chunkZ) {
		int cycle = 0;
		for (Zone zone : zones) {
			if (zone.cycle() > cycle && zone.contains(dimension, chunkX, chunkZ)) {
				cycle = zone.cycle();
			}
		}
		return cycle;
	}

	/** The cycle after one more burn, capped. */
	public static int nextCycle(int cycle) {
		return Math.min(MAX_CYCLE, Math.max(0, cycle) + 1);
	}

	/** Extra placements per ore feature and chunk when a zone reaches this cycle. */
	public static double oreBonus(int cycle) {
		return ORE_BONUS_PER_CYCLE * clamp(cycle);
	}

	public static boolean evokersPossible(int cycle) {
		return cycle >= EVOKER_MIN_CYCLE;
	}

	private static int clamp(int cycle) {
		return Math.max(0, Math.min(MAX_CYCLE, cycle));
	}

	/** Multiplier of max health at this cycle (1 outside zones). */
	public static double healthMultiplier(int cycle) {
		return 1 + HEALTH_PER_CYCLE * clamp(cycle);
	}

	public static double damageMultiplier(int cycle) {
		return 1 + DAMAGE_PER_CYCLE * clamp(cycle);
	}

	public static double speedMultiplier(int cycle) {
		return 1 + SPEED_PER_CYCLE * clamp(cycle);
	}

	public static double followRangeMultiplier(int cycle) {
		return 1 + FOLLOW_RANGE_PER_CYCLE * clamp(cycle);
	}

	public static int extraLootRolls(int cycle) {
		return EXTRA_LOOT_ROLLS_PER_CYCLE * clamp(cycle);
	}

	/** Highest light at which a spawner in a zone of this cycle spawns regardless of vanilla's light rules; -1 = none. */
	public static int spawnerLightLimit(int cycle) {
		return clamp(cycle) == 0 ? -1 : Math.min(15, SPAWNER_LIGHT_PER_CYCLE * clamp(cycle));
	}

	public static boolean phantomsIgnoreInsomnia(int cycle) {
		return clamp(cycle) >= PHANTOM_MIN_CYCLE;
	}

	public static boolean ravagersPossible(int cycle) {
		return clamp(cycle) >= RAVAGER_MIN_CYCLE;
	}

	public static double soulChance(int cycle) {
		return SOUL_CHANCE_PER_CYCLE * clamp(cycle);
	}

	/** The soul weights of this cycle (a copy); all zero outside zones. */
	public static int[] soulWeights(int cycle) {
		return clamp(cycle) == 0 ? new int[4] : SOUL_WEIGHTS[clamp(cycle) - 1].clone();
	}

	/** Soul tier (0..3, see SOUL_WEIGHTS) for a roll in [0, 1); -1 outside zones. */
	public static int soulTier(int cycle, double roll) {
		if (clamp(cycle) == 0) {
			return -1;
		}
		int[] weights = SOUL_WEIGHTS[clamp(cycle) - 1];
		int total = 0;
		for (int w : weights) {
			total += w;
		}
		double pick = roll * total;
		for (int tier = 0; tier < weights.length; tier++) {
			pick -= weights[tier];
			if (pick < 0) {
				return tier;
			}
		}
		for (int tier = weights.length - 1; tier >= 0; tier--) {
			if (weights[tier] > 0) {
				return tier;
			}
		}
		return 0;
	}
}
