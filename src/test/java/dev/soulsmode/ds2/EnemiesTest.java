package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** docs/research/enemies.md s.4.3 / s.11: Hollow Soldier 102000 (def 317, absorption 15/30/30/30, attack 110200010). */
class EnemiesTest {
	private static final Enemies.Def HOLLOW = new Enemies.Def(320, 1240, 0, 1.47f, 317, new float[] {15, 30, 30, 30},
			50, new float[] {80, 80, 80, 80, 80}, 1, 90, 1, List.of(), List.of(), 1, 2, 0.6f, 1.9f, 16, Map.of(), Map.of(),
			"", "", "", null, false, null);
	private static final Enemies.Damage SLASH = new Enemies.Damage(new float[] {118, 0, 0, 0, 0}, 34, 1, 240);

	@Test
	void hollowSoldierAgainstThePlayer() {
		assertEquals(98.3f, Enemies.damageToPlayer(SLASH, 1, null), 0.05f);
		assertEquals(95.1f, Enemies.damageToPlayer(SLASH, 1, new Ds2Damage.Defence(39, new float[4])), 0.05f);
		assertEquals(69.8f, Enemies.damageToPlayer(SLASH, 1, new Ds2Damage.Defence(342.74f, new float[4])), 0.05f);
	}

	@Test
	void longswordAgainstTheHollowSoldier() {
		// 158.25 physical: (6 x 158.25 - 317) / 6 = 105.4.
		assertEquals(105.4f, Enemies.damageToEnemy(new float[] {158.25f, 0, 0, 0, 0, 0}, 1, 60, 1, HOLLOW), 0.05f);
	}
}
