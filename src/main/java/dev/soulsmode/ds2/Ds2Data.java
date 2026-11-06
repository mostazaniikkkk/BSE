package dev.soulsmode.ds2;

/** Lazily loaded game data tables shared by the whole mod. */
public final class Ds2Data {
	private static LevelUpSoulsTable levelUpSouls;
	private static PhysicalStatsTable physicalStats;
	private static StartingClasses startingClasses;

	private Ds2Data() {
	}

	public static synchronized LevelUpSoulsTable levelUpSouls() {
		if (levelUpSouls == null) {
			levelUpSouls = LevelUpSoulsTable.load();
		}
		return levelUpSouls;
	}

	public static synchronized PhysicalStatsTable physicalStats() {
		if (physicalStats == null) {
			physicalStats = PhysicalStatsTable.load();
		}
		return physicalStats;
	}

	public static synchronized StartingClasses startingClasses() {
		if (startingClasses == null) {
			startingClasses = StartingClasses.load();
		}
		return startingClasses;
	}
}
