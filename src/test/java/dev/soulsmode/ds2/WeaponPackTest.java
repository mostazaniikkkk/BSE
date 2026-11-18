package dev.soulsmode.ds2;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.soulsmode.content.SoulsContent;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Datapack weapons read every per-weapon field (docs/datapacks.md "Armas"); counter windows and own SpEffects. */
class WeaponPackTest {
	private static Attributes stats(int value) {
		short[] s = new short[Attributes.SLOT_COUNT];
		Arrays.fill(s, (short) value);
		return new Attributes(s);
	}

	private static void apply(Map<ResourceLocation, JsonObject> weapons) {
		Map<SoulsContent.Kind, Map<ResourceLocation, JsonObject>> sources = new EnumMap<>(SoulsContent.Kind.class);
		sources.put(SoulsContent.Kind.WEAPON, weapons);
		SoulsContent.apply(sources);
	}

	@AfterEach
	void clear() {
		SoulsContent.clear();
	}

	@Test
	void extendedWeaponKeepsTheParentsReinforcementPowerStanceAndCounter() {
		JsonObject sword = JsonParser.parseString("{\"extends\": \"soulsmode:longsword\"}").getAsJsonObject();
		apply(Map.of(ResourceLocation.parse("test:sword"), sword));
		Ds2Damage.WeaponUpgrade up = Ds2Damage.upgrade("test:sword");
		assertNotNull(up);
		assertEquals(Ds2Damage.upgrade("longsword").cost().length, up.cost().length);
		assertNotNull(PowerStance.data("test:sword"));
		assertEquals(1.1f, Weapons.counterRate("test:sword"), 1e-4);
	}

	@Test
	void materialMayBeAnItemId() {
		JsonObject sword = JsonParser.parseString("{\"extends\": \"soulsmode:longsword\"}").getAsJsonObject();
		JsonObject attack = Weapons.raw().getAsJsonObject("weapons").getAsJsonObject("longsword")
				.getAsJsonObject("attack").deepCopy();
		JsonArray rows = new JsonArray();
		for (int i = 0; i < 10; i++) {
			rows.add(JsonParser.parseString("[" + (100 * i) + ", \"darktwo:petrified_dragon_bone\", 1]"));
		}
		attack.add("reinforceCost", rows);
		sword.add("attack", attack);
		apply(Map.of(ResourceLocation.parse("test:bone_sword"), sword));
		Ds2Damage.WeaponUpgrade up = Ds2Damage.upgrade("test:bone_sword");
		assertEquals("darktwo:petrified_dragon_bone", up.materialItem(0));
		assertEquals(-1, up.cost()[0][1]);
		assertEquals(900, up.cost()[9][0]);
	}

	@Test
	void sovereignAndForlornCurves() {
		// DarkTwo espadones.md section 12: 1000[14] (0, 10000, 1000000, 0.85, 1.3), 1000[15] (1, 0, 10, 0.1, 1.0).
		JsonObject sword = JsonParser.parseString("{\"extends\": \"soulsmode:longsword\", \"spEffect\": {\"instructions\": ["
				+ "{\"type\": \"atkRateBySoul\", \"low\": 10000, \"high\": 1000000, \"rateLow\": 0.85, \"rateHigh\": 1.3},"
				+ "{\"type\": \"atkRateByHollowCount\", \"counter\": \"hollow\", \"low\": 0, \"high\": 10, \"rateLow\": 0.1,"
				+ " \"rateHigh\": 1.0}, {\"type\": \"changeWeaponParam\", \"weapon\": \"soulsmode:shortsword\"}]}}")
				.getAsJsonObject();
		apply(Map.of(ResourceLocation.parse("test:sovereign"), sword));
		WeaponEffect e = WeaponEffect.of("test:sovereign");
		WeaponEffect.Rate souls = e.rates().get(0);
		assertEquals(0.85f, souls.at(0), 1e-5);
		assertEquals(0.85f, souls.at(10000), 1e-5);
		assertEquals(0.85f + 0.45f * 0.5f, souls.at(505000), 1e-4);
		assertEquals(1.3f, souls.at(2_000_000), 1e-5);
		WeaponEffect.Rate hollow = e.rates().get(1);
		assertEquals(0.1f, hollow.at(0), 1e-5);
		assertEquals(0.55f, hollow.at(5), 1e-5);
		assertEquals(1.0f, hollow.at(32), 1e-5);
		assertEquals("soulsmode:shortsword", e.leftWeapon());
		assertNull(WeaponEffect.of("longsword"));
	}

	@Test
	void statFactorScalesOnlyTheStatPart() {
		float[][] plain = Ds2Damage.attackParts("longsword", 0, 0, stats(30), false, false, -1, 0, null, null);
		float[][] half = Ds2Damage.attackParts("longsword", 0, 0, stats(30), false, false, -1, 0, null, null, 0.5f);
		assertEquals(plain[0][0], half[0][0], 1e-4);
		assertEquals(plain[1][0] * 0.5f, half[1][0], 1e-4);
	}

	@Test
	void longswordLightHasItsCounterWindow() {
		Moveset m = Weapons.load().get("longsword");
		Attack light = m.attack(false, Moveset.Type.LIGHT);
		// TAE 111600 of 29010012: 0.3333 - 0.8333 s; PDP +0x20 of its slot 0 = 1.0.
		assertTrue(AttackData.counterWindow(light, 0.5f));
		assertFalse(AttackData.counterWindow(light, 0.1f));
		assertEquals(1.0f, AttackData.counterRate(light, 0), 1e-4);
	}

	@Test
	void datapackAttackRegistersItsBulletsAndPoise() {
		JsonObject sword = Weapons.raw().getAsJsonObject("weapons").getAsJsonObject("longsword").deepCopy();
		JsonObject slot = sword.getAsJsonObject("right").getAsJsonObject("light");
		slot.add("bullets", JsonParser.parseString("[\"wave\", null, null]"));
		slot.add("poise", JsonParser.parseString("[{\"poiseDamage\": 30, \"poiseRule\": 0, \"reaction\": 2, \"level\": 3}]"));
		sword.add("bullets", new JsonObject());
		apply(Map.of(ResourceLocation.parse("test:wave"), sword));
		Attack light = SoulsContent.weapon(ResourceLocation.parse("test:wave")).moveset().attack(false, Moveset.Type.LIGHT);
		AttackData.Data d = AttackData.of(light);
		assertEquals("wave", d.bullets()[0]);
		assertFalse(d.shots().isEmpty());
		assertEquals(30, Poise.weaponHit("test:wave", light, 0).poiseDamage(), 1e-4);
	}
}
