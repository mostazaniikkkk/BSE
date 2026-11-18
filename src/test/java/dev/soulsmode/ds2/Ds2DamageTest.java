package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Ds2DamageTest {
	private static Attributes stats(int str, int dex) {
		short[] s = new short[Attributes.SLOT_COUNT];
		java.util.Arrays.fill(s, (short) 5);
		s[Attributes.STRENGTH] = (short) str;
		s[Attributes.DEXTERITY] = (short) dex;
		return new Attributes(s);
	}

	@Test
	void petrificationAndCurseResistance() {
		// petrification.md 1.2 / 5: Adp 10 / Vgr 10 -> PSPL +0x50 row 10 = 16 %; Adp 10 / Att 10 -> +0x54 row 10 = 16 %.
		short[] s = new short[Attributes.SLOT_COUNT];
		java.util.Arrays.fill(s, (short) 10);
		Attributes a = new Attributes(s);
		assertEquals(0.16f, Ds2Damage.statusStatAbsorption(false, a), 1e-4f);
		assertEquals(0.16f, Ds2Damage.statusStatAbsorption(true, a), 1e-4f);
		// Adp 20 / Vgr 20 -> row 20 = 24 %; Adp 1 / Vgr 1 -> row 1 = 10 %.
		java.util.Arrays.fill(s, (short) 20);
		assertEquals(0.24f, Ds2Damage.statusStatAbsorption(false, new Attributes(s)), 1e-4f);
		java.util.Arrays.fill(s, (short) 1);
		assertEquals(0.10f, Ds2Damage.statusStatAbsorption(false, new Attributes(s)), 1e-4f);
		// The gold set's +0 head (its ARP +0x28 in weapons.json): 1.7 % petrification.
		assertEquals(0.017f, Ds2Damage.pieceStatus(false, "gold", 0, 0), 1e-4f);
	}

	@Test
	void longswordWorkedExample() {
		// docs/research/damage.md §6: Longsword +0, Str 16 / Dex 11 -> 112 + 70 x 0.45 + 59 x 0.25 = 158.25.
		float[] ar = Ds2Damage.attackRating("longsword", 0, stats(16, 11), false, false, -1);
		assertEquals(158.25f, ar[0], 0.01f);
		assertEquals(0f, ar[3], 1e-6f);
		// Zero defence enemy: 6 x 158.25 / 6 = 158.25 (rate 1.0, floor 60).
		assertEquals(158.25f, Ds2Damage.damageToEnemy("longsword", ar, 1.0f, 60), 0.01f);
	}

	@Test
	void magicWeaponWorkedExample() {
		// speffects.md §11: Longsword +0 right hand, Str 16 / Dex 11 / Int 10, Magic Weapon (attack +30 magic, split
		// +0.15 magic): magic = (53 x 0.35 + 30 + 112) x 0.15 = 24.08; physical unchanged.
		short[] s = new short[Attributes.SLOT_COUNT];
		java.util.Arrays.fill(s, (short) 5);
		s[Attributes.STRENGTH] = 16;
		s[Attributes.DEXTERITY] = 11;
		s[Attributes.INTELLIGENCE] = 10;
		float[] attack = new float[Ds2Damage.TYPES];
		float[] split = new float[Ds2Damage.TYPES];
		attack[1] = 30;
		split[1] = 0.15f;
		float[] ar = Ds2Damage.attackRating("longsword", 0, new Attributes(s), false, false, -1, 0, attack, split);
		assertEquals(158.25f, ar[0], 0.01f);
		assertEquals(24.08f, ar[1], 0.02f);
	}

	@Test
	void playerVictimWorkedExample() {
		// docs/research/damage.md §6 (b), corrected by speffects.md §11.3 (no + 5.5): Knight set +0, End 6 Str 16 Vit 6
		// Dex 11 -> def 239 + 1.33 x 78 = 342.74, (1582.5 - 342.74) / 12 = 103.31; naked 39 -> 128.63.
		short[] s = new short[Attributes.SLOT_COUNT];
		java.util.Arrays.fill(s, (short) 5);
		s[Attributes.VIGOR] = 8;
		s[Attributes.ENDURANCE] = 6;
		s[Attributes.VITALITY] = 6;
		s[Attributes.STRENGTH] = 16;
		s[Attributes.DEXTERITY] = 11;
		Attributes a = new Attributes(s);
		float[] ar = Ds2Damage.attackRating("longsword", 0, a, false, false, -1);
		Ds2Damage.Piece[] knight = new Ds2Damage.Piece[4];
		Ds2Damage.Piece[] naked = new Ds2Damage.Piece[4];
		for (int i = 0; i < 4; i++) {
			knight[i] = Ds2Damage.piece("iron", i);
			naked[i] = Ds2Damage.piece(null, i);
		}
		assertEquals(103.31f, Ds2Damage.damageToPlayer("longsword", ar, 1f, 60, Ds2Damage.playerDefence(a, knight)), 0.02f);
		assertEquals(128.63f, Ds2Damage.damageToPlayer("longsword", ar, 1f, 60, Ds2Damage.playerDefence(a, naked)), 0.02f);
	}

	@Test
	void unmetRequirementsAndTwoHanding() {
		// Pickaxe needs Str 22: with Str 16 one-handed f = 1 - 16/22, penalty 0.3 + 0.5 f; two-handed the
		// requirement halves (11) and the penalty goes.
		float one = Ds2Damage.attackRating("pickaxe", 0, stats(16, 11), false, false, -1)[0];
		float two = Ds2Damage.attackRating("pickaxe", 0, stats(16, 11), true, false, -1)[0];
		assertEquals(185f, two, 0.01f);
		assertEquals(185f * (1 - (0.3f + 0.5f * (1 - 16 / 22f))), one, 0.05f);
	}

	@Test
	void scalingLettersMatchFeWeaponThreshold() {
		// docs/research/scaling_letters.md: Longsword C/C, Club A then S, Battle Axe C/D then B/D, Mace B/E then A/E.
		assertEquals("C", Ds2Damage.scalingLetters("longsword", 0)[0]);
		assertEquals("C", Ds2Damage.scalingLetters("longsword", 10)[1]);
		assertEquals("A", Ds2Damage.scalingLetters("club", 0)[0]);
		assertEquals("-", Ds2Damage.scalingLetters("club", 0)[1]);
		assertEquals("S", Ds2Damage.scalingLetters("club", 10)[0]);
		assertEquals("B", Ds2Damage.scalingLetters("battle_axe", 10)[0]);
		assertEquals("D", Ds2Damage.scalingLetters("battle_axe", 10)[1]);
		assertEquals("A", Ds2Damage.scalingLetters("mace", 10)[0]);
		assertEquals("E", Ds2Damage.scalingLetters("mace", 0)[1]);
		assertEquals("-", Ds2Damage.scalingLetters("longsword", 0)[2]);
	}
}
