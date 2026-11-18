package dev.soulsmode;

import net.minecraft.world.entity.player.Player;

/**
 * Fighting in water (the user's rules, not DS2): in deep water (swimming, or the head under water) the attack and use
 * keys are Minecraft's own (the 2026-11-04 placeholder); in shallow water (in water, head out) the Souls moveset stays and
 * weapon attacks run at {@link #ATTACK_RATE} as a whole (2026-11-16: "ligeramente más lentos", factor 0.9 chosen by the
 * user).
 */
public final class SoulsWater {
	/** Whole-clip speed of a weapon attack started in shallow water (the user's factor). */
	public static final float ATTACK_RATE = 0.9f;

	private SoulsWater() {
	}

	public static boolean deep(Player player) {
		return player.isSwimming() || player.isUnderWater();
	}

	public static boolean shallow(Player player) {
		return player.isInWater() && !deep(player);
	}
}
