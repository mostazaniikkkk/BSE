package dev.soulsmode.ds2;

/**
 * The level-up screen's working copy (object at menu+0x1cb0, built by FUN_1401fbac0), see docs/research/stats.md.
 * A "+" on a row buys one level (FUN_1401fb970) and assigns it to the row's slot (FUN_1401fba30); a "-" takes it
 * back from the slot (FUN_1401fb890) and refunds the level (FUN_1401fb800). Confirming (FUN_1400c9f50) needs every
 * bought level assigned.
 */
public final class LevelUpMenu {
	/** FUN_1401fb970 / FUN_1401fbd70: no level can be bought at 1000 or above. */
	private static final int MAX_LEVEL = 1000;
	/** FUN_1401fb740: a slot at 99 cannot take more points. */
	private static final int MAX_SLOT_VALUE = 99;
	/** FUN_1401fb740 / FUN_1401fba30 refuse slot 10. */
	private static final int LOCKED_SLOT = 10;

	private final LevelUpSoulsTable table;
	private final Attributes original;
	private final int startLevel;
	private final int startSouls;
	private final short[] slots;
	/** +0xcc: points added to each slot. */
	private final short[] added = new short[Attributes.SLOT_COUNT];
	/** +0xe6: bought levels not yet assigned to a slot. */
	private int unassigned;
	/** +0xe8: levels bought. */
	private int bought;
	/** +0xf0: current level. */
	private int level;
	/** +0xf8: souls left. */
	private int souls;
	/** +0xfc: cost of the next level. */
	private int cost;

	public LevelUpMenu(Attributes attributes, int souls, LevelUpSoulsTable table) {
		this.table = table;
		this.original = attributes;
		this.slots = attributes.toArray();
		this.startLevel = attributes.soulLevel();
		this.startSouls = souls;
		this.level = startLevel;
		this.souls = souls;
		this.cost = table.cost(level);
	}

	/** FUN_1400c9e80: "+" on a slot. Returns whether the slot went up. */
	public boolean raise(int slot) {
		if (unassigned == 0 && canBuy()) {
			unassigned++;
			bought++;
			souls -= cost;
			level++;
			cost = table.cost(level);
		}
		if (canAssign(slot)) {
			slots[slot]++;
			added[slot]++;
			unassigned--;
			return true;
		}
		// The bought level could not be placed (slot at 99): give it back.
		refund();
		return false;
	}

	/** FUN_1400ce180: "-" on a slot. Returns whether the slot went down. */
	public boolean lower(int slot) {
		if (!canLower(slot)) {
			return false;
		}
		slots[slot]--;
		added[slot]--;
		unassigned++;
		refund();
		return true;
	}

	/** Whether "+" would raise the slot (buy check of FUN_1401fb7b0, then FUN_1401fb740). */
	public boolean canRaise(int slot) {
		return (unassigned > 0 || canBuy()) && slot != LOCKED_SLOT && slots[slot] < MAX_SLOT_VALUE;
	}

	/** FUN_1401fb6e0: only points added in this menu can be taken back, and never below 1. */
	public boolean canLower(int slot) {
		return added[slot] > 0 && slots[slot] > 1;
	}

	/** FUN_1401fb730 / FUN_1401fbd70: something was bought and every bought level is assigned. */
	public boolean canConfirm() {
		return bought > 0 && unassigned == 0 && level == startLevel + bought && level <= MAX_LEVEL;
	}

	private boolean canBuy() {
		return cost <= souls && level < MAX_LEVEL;
	}

	private boolean canAssign(int slot) {
		return slots[slot] < MAX_SLOT_VALUE && unassigned > 0 && slot != LOCKED_SLOT;
	}

	/** FUN_1401fb800. */
	private void refund() {
		if (unassigned > 0 && bought > 0) {
			unassigned--;
			bought--;
			level--;
			cost = table.cost(level);
			souls += cost;
		}
	}

	public Attributes attributes() {
		return new Attributes(slots);
	}

	public Attributes original() {
		return original;
	}

	public int added(int slot) {
		return added[slot];
	}

	public int level() {
		return level;
	}

	public int souls() {
		return souls;
	}

	/** Souls the confirmed levels take (FUN_1401fb950 total, subtracted by FUN_14038b1a0). */
	public int spent() {
		return startSouls - souls;
	}

	public int cost() {
		return cost;
	}
}
