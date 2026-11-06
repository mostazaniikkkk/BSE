package dev.soulsmode;

import net.minecraft.world.entity.player.Player;

/**
 * Souls held = vanilla experience points. Derived from level + progress (what the XP bar shows),
 * because vanilla's totalExperience drifts when enchanting or using anvils.
 */
public final class ExperienceSouls {
	private ExperienceSouls() {
	}

	/** Same curve as {@link Player#getXpNeededForNextLevel()}. */
	static int xpNeededForNextLevel(int level) {
		if (level >= 30) {
			return 112 + (level - 30) * 9;
		}
		if (level >= 15) {
			return 37 + (level - 15) * 5;
		}
		return 7 + level * 2;
	}

	public static int get(Player player) {
		long total = 0;
		for (int level = 0; level < player.experienceLevel; level++) {
			total += xpNeededForNextLevel(level);
		}
		total += Math.round(player.experienceProgress * xpNeededForNextLevel(player.experienceLevel));
		return (int) Math.min(total, Integer.MAX_VALUE);
	}

	public static void set(Player player, int souls) {
		int remaining = Math.max(0, souls);
		int level = 0;
		while (remaining >= xpNeededForNextLevel(level)) {
			remaining -= xpNeededForNextLevel(level);
			level++;
		}
		player.experienceLevel = level;
		player.experienceProgress = (float) remaining / xpNeededForNextLevel(level);
		player.totalExperience = Math.max(0, souls);
	}
}
