package dev.soulsmode.ds2;

/**
 * Derived player stats, ported from FUN_14038d790 / FUN_14038e1e0. Only fields whose meaning is backed
 * by a consumer in the binary are ported; see docs/research/stats.md.
 *
 * @param maxHp           derived+0x00 (u16); FUN_14037e8a0 uses it as the max of the int resource at chr+0x168
 * @param attunementSlots derived+0x02 (u8)
 * @param maxStamina      derived+0x04 (f32); FUN_14037e8a0 uses it as the max of the float resource at chr+0x1ac
 * @param maxEquipLoad    derived+0x08 (f32); FUN_14037e430 divides the equipped weight by it
 * @param spellUsesLevel  derived+0x03 (u8, 1..10); FUN_1401ab750 picks the spell's uses column with it
 */
public record DerivedStats(int maxHp, int attunementSlots, float maxStamina, float maxEquipLoad, int spellUsesLevel) {
	public static DerivedStats compute(Attributes effective, PhysicalStatsTable table) {
		return new DerivedStats(
				maxHp(effective, table),
				table.row(table.relatedValue(0, effective)).attunementSlots(),
				table.row(table.relatedValue(3, effective)).stamina(),
				table.row(table.relatedValue(4, effective)).equipLoad(),
				table.row(table.relatedValue(1, effective)).spellUsesLevel());
	}

	/** FUN_14038e1e0: Vigor row's hp, plus every other counted slot's hpBonus (slots 9 and 10 are skipped). */
	static int maxHp(Attributes attributes, PhysicalStatsTable table) {
		int hp = table.row(attributes.get(Attributes.VIGOR)).hp();
		for (int slot = 1; slot <= 8; slot++) {
			hp += table.row(attributes.get(slot)).hpBonus();
		}
		return hp & 0xFFFF;
	}
}
