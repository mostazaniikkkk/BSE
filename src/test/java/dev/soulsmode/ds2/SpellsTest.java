package dev.soulsmode.ds2;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Reference values from docs/research/spells.md (SpellParam 31010000 Soul Arrow, attunement table). */
class SpellsTest {
	private static final Spells.Spell SOUL_ARROW = Spells.parse(JsonParser.parseString("""
			{"school": 0, "requirements": [10, 0], "slots": 1, "uses": [30, 31, 32, 33, 34, 35, 36, 37, 38, 40],
			 "prepare": "38110010", "cast": "38110020", "staminaFactor": 0.8, "castTime": 0.25}""").getAsJsonObject());

	@Test
	void usesFollowTheAttunementLevel() {
		PhysicalStatsTable table = PhysicalStatsTable.load();
		assertEquals(30, Spells.maxUses(SOUL_ARROW, table.row(10).spellUsesLevel()));
		assertEquals(40, Spells.maxUses(SOUL_ARROW, table.row(99).spellUsesLevel()));
		assertEquals(1, table.row(10).attunementSlots());
		assertEquals(6, table.row(30).attunementSlots());
		assertEquals(3, table.row(30).spellUsesLevel());
	}

	@Test
	void castWaitFollowsTheStats() {
		// casting.md section 6: Soul Arrow, Sorcerer's Staff (WTP +0x10c 0.65), Att / Int / Fth 10 / 10 / 5 -> 0.2347 s.
		Attributes stats = new Attributes((short) 1, (short) 1, (short) 1, (short) 10, (short) 1, (short) 1, (short) 10,
				(short) 5, (short) 1, (short) 1, (short) 1);
		assertEquals(0.2347f, Spells.castWait(SOUL_ARROW, 0.65f, stats, PhysicalStatsTable.load(), 0, 0), 1e-3);
	}

	@Test
	void restoreRoundsUp() {
		assertEquals(30, Spells.restored(30, 100));
		// Amber Herb, 35 %: ceil(10.5) = 11.
		assertEquals(11, Spells.restored(30, 35));
	}

	@Test
	void entriesPastTheCapacityAreDropped() {
		assertEquals(2, Spells.keptWithin(List.of(1, 2, 1), 3));
		// The sum keeps the removed entry: a later 1-slot spell does not come back.
		assertEquals(1, Spells.keptWithin(List.of(1, 3, 1), 3));
		assertEquals(3, Spells.keptWithin(List.of(1, 1, 1), 3));
	}

	@Test
	void castAnimationsAreExported() {
		Spells.CastAnimation cast = Spells.builtInCastAnimation("38110020");
		assertNotNull(cast);
		assertEquals(2.1667f, cast.duration(), 1e-4);
		assertEquals(0.5667f, cast.bullets().get(0)[0], 1e-4);
		assertEquals(1.2f, cast.lightLock(), 1e-4);
		assertNull(Spells.builtInCastAnimation("12345"));
	}

	@Test
	void unsupportedTargetsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> Spells.parse(JsonParser.parseString("""
				{"school": 0, "requirements": [0, 0], "slots": 1, "uses": [1,1,1,1,1,1,1,1,1,1], "target": 3,
				 "prepare": "38110010", "cast": "38110720", "staminaFactor": 1.0, "castTime": 0.25}""").getAsJsonObject()));
	}
}
