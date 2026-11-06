package dev.soulsmode.ds2;

import java.util.Arrays;
import java.util.List;

/**
 * The player's attribute block: 11 consecutive shorts (see docs/research/stats.md).
 * Slot names come from the level-up screen (FUN_1400c7a80 / FUN_1400ccff0), which pairs each
 * row's iconHelp text with a slot index. Slots 9 and 10 are not shown there and stay unnamed.
 */
public final class Attributes {
	public static final int SLOT_COUNT = 11;
	public static final int VIGOR = 0;
	public static final int ENDURANCE = 1;
	public static final int VITALITY = 2;
	public static final int ATTUNEMENT = 3;
	public static final int STRENGTH = 4;
	public static final int DEXTERITY = 5;
	public static final int INTELLIGENCE = 6;
	public static final int FAITH = 7;
	public static final int ADAPTABILITY = 8;
	/** Names of the level-up screen slots, indexed by slot. */
	public static final List<String> NAMES = List.of("vigor", "endurance", "vitality", "attunement",
			"strength", "dexterity", "intelligence", "faith", "adaptability");
	public static final int MIN_VALUE = 1;
	public static final int MAX_VALUE = 99;
	/** FUN_14038d570: soul level = sum of all slots - slot 9 - slot 10 - 0x35, at least 1. */
	private static final int SOUL_LEVEL_OFFSET = 0x35;

	private final short[] slots;

	public Attributes(short... slots) {
		if (slots.length != SLOT_COUNT) {
			throw new IllegalArgumentException("expected " + SLOT_COUNT + " slots, got " + slots.length);
		}
		this.slots = slots.clone();
	}

	public int get(int slot) {
		return slots[slot];
	}

	public short[] toArray() {
		return slots.clone();
	}

	public Attributes with(int slot, int value) {
		short[] copy = slots.clone();
		copy[slot] = (short) value;
		return new Attributes(copy);
	}

	/** FUN_14038d570 skips the whole stat recompute if any slot is outside 1..99. */
	public boolean isValid() {
		for (short value : slots) {
			if (value < MIN_VALUE || value > MAX_VALUE) {
				return false;
			}
		}
		return true;
	}

	/** Port of the soul level computation at the end of FUN_14038d570. */
	public int soulLevel() {
		int sum = 0;
		for (short value : slots) {
			sum += value;
		}
		// The game truncates the intermediate result to a short before subtracting.
		int level = (short) ((short) sum - slots[10] - slots[9]) - SOUL_LEVEL_OFFSET;
		return Math.max(1, level);
	}

	@Override
	public String toString() {
		return Arrays.toString(slots);
	}
}
