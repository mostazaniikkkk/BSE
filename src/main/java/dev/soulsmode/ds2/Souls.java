package dev.soulsmode.ds2;

/**
 * Soul counters of the player status object (chr+0x490), see docs/research/souls.md.
 * Ported from FUN_14038ab40 (gain), FUN_14038be30 (spend) and FUN_14038b1a0 (level-up commit).
 */
public final class Souls {
	public static final int MAX = 999_999_999;

	/** obj+0xec: souls held. */
	private int held;
	/** obj+0xf4: total gained, never reduced by spending. Shown with level/souls/required souls. */
	private int memory;

	public Souls(int held, int memory) {
		this.held = held;
		this.memory = memory;
	}

	public int held() {
		return held;
	}

	public int memory() {
		return memory;
	}

	/** FUN_14038ab40: every counter grows by the amount, saturating at 999,999,999. */
	public void gain(int amount) {
		held = saturatingAdd(held, amount);
		memory = saturatingAdd(memory, amount);
	}

	/** FUN_14026b610 (bloodstain pickup): only the held souls go up; soul memory does not. */
	public void recover(int amount) {
		held = saturatingAdd(held, amount);
	}

	/** FUN_14038be30: only the held souls go down, floored at 0. */
	public void spend(int amount) {
		held = Integer.compareUnsigned(amount, held) < 0 ? held - amount : 0;
	}

	/**
	 * One level-up step as in FUN_1401fb970: allowed when the cost of the current level is affordable
	 * and the level is below 1000.
	 */
	public boolean canLevelUp(int soulLevel, LevelUpSoulsTable table) {
		return soulLevel < 1000 && table.cost(soulLevel) <= held;
	}

	private static int saturatingAdd(int current, int amount) {
		// The game compares unsigned: amount < MAX - current.
		if (Integer.compareUnsigned(amount, MAX - current) < 0) {
			return current + amount;
		}
		return MAX;
	}
}
