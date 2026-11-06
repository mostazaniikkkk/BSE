package dev.soulsmode.ds2;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackTest {
	private static final Map<String, Moveset> WEAPONS = Weapons.load();

	@Test
	void weaponsJsonLoadersAcceptEveryWeapon() {
		// Bows / crossbows keep {pos, fwd} dummies; catalysts plain arrays (crash 2026-11-01 in SoulsMagic's init).
		Map<String, Map<Integer, float[]>> dummies = Weapons.dummyPolys();
		assertFalse(dummies.get("short_bow").isEmpty());
		assertEquals(0.9f / 1.25f, HitReaction.moveLock(1.25f), 1e-6f);
		Attack bounce = HitBack.bounce(false, false, 0.9f, 0.75f);
		assertEquals("soulsmode:ds2_hitback_1h_r_75", bounce.animation());
		assertEquals(1.1667f / 0.9f, bounce.duration(), 1e-3f);
	}

	@Test
	void guardValuesFollowTheSlot() {
		// Shield in the left hand: WAMP +0x08 1.3, WTP +0x51 20; (10 + 3 x 10) / 4 = PSPL row 10 = 1.01.
		var shield = Weapons.guardSlot("shield", true, false, Moveset.Type.LIGHT);
		assertEquals(1.3f * 1.01f, Weapons.guardSpeed(shield, 10, 10, 0), 1e-4f);
		// A Str / Dex lack of 0.5: x (1 - 0.01 (10 + 20 x 0.5)) and one category up (0.5 s -> 0.8 s).
		assertEquals(1.3f * 1.01f * 0.8f, Weapons.guardSpeed(shield, 10, 10, 0.5f), 1e-4f);
		assertEquals(0.5f, Weapons.guardExitBlend(Weapons.guardCancelCategory(shield, false)), 1e-6f);
		assertEquals(0.8f, Weapons.guardExitBlend(Weapons.guardCancelCategory(shield, true)), 1e-6f);
		// Two-handed longsword, the other hand's light button: slot 50, 1.7.
		var sword = Weapons.guardSlot("longsword", false, true, Moveset.Type.OTHER_LIGHT);
		assertEquals(1.7f, Weapons.guardSpeed(sword, 1, 1, 0), 1e-4f);
		assertEquals(null, Weapons.guardSlot("longsword", false, false, Moveset.Type.LIGHT));
	}

	@Test
	void builtInWeaponsHaveSaneWindows() {
		assertTrue(WEAPONS.containsKey("shield"));
		assertTrue(WEAPONS.keySet().containsAll(List.of("longsword", "battle_axe", "pickaxe", "mace", "spear", "fists")));
		for (Map.Entry<String, Moveset> e : WEAPONS.entrySet()) {
			for (boolean left : new boolean[] {false, true}) {
				for (Moveset.Type type : List.of(Moveset.Type.LIGHT, Moveset.Type.LIGHT_COMBO, Moveset.Type.HEAVY,
						Moveset.Type.HEAVY_COMBO)) {
					Attack a = e.getValue().attack(left, type);
					String where = e.getKey() + " " + a.taeId();
					if (a.guard() || a.isCast()) {
						continue;
					}
					assertTrue(a.moveLock() < a.duration(), where);
					assertTrue(a.comboEnd() <= a.duration(), where);
				}
			}
		}
	}

	@Test
	void longswordSlotsComeFromTheParams() {
		// WeaponActionCategoryParam 1220, slots 0..3.
		Moveset longsword = WEAPONS.get("longsword");
		assertEquals(29010012, longsword.attack(false, Moveset.Type.LIGHT).taeId());
		assertEquals(29010021, longsword.attack(false, Moveset.Type.LIGHT_COMBO).taeId());
		assertEquals(29010038, longsword.attack(false, Moveset.Type.HEAVY).taeId());
		assertEquals(29010022, longsword.attack(false, Moveset.Type.HEAVY_COMBO).taeId());
		assertEquals("soulsmode:ds2_29010012", longsword.attack(false, Moveset.Type.LIGHT).animation());
	}

	@Test
	void fistsLeftHeavyIsTheShieldParry() {
		// Category 3400 slot 42 (left hand, heavy) is SmallShieldAttack1HandParry.
		assertEquals(44010290, WEAPONS.get("fists").attack(true, Moveset.Type.HEAVY).taeId());
	}

	@Test
	void overlappingHitEventsMerge() {
		// a25_01_0030 (Spear heavy combo) has three overlapping 2200 events: one window.
		Attack spear = WEAPONS.get("spear").attack(false, Moveset.Type.HEAVY_COMBO);
		assertEquals(25010030, spear.taeId());
		assertEquals(1, spear.hits().size());
	}

	@Test
	void shieldGuardsAndParriesInEitherHandAndBashesTwoHanded() {
		// Drangleic Shield, category 11230: slots 0/40 guard (type 13), 2/42 parry, 50/51 two-handed bashes.
		Moveset shield = WEAPONS.get("shield");
		assertTrue(shield.attack(false, Moveset.Type.LIGHT).guard());
		assertTrue(shield.attack(true, Moveset.Type.LIGHT).guard());
		assertFalse(shield.attack(true, Moveset.Type.HEAVY).guard());
		// WeaponReinforceParam 11230 +0x50 = 100: a full physical block.
		assertEquals(0f, shield.guardFactor(0), 1e-6);
		assertEquals(0.5f, WEAPONS.get("longsword").guardFactor(0), 1e-6);
		Attack bash = shield.attack(true, true, Moveset.Type.OTHER_LIGHT);
		assertFalse(bash.guard());
		assertFalse(bash.hits().isEmpty());
	}

	@Test
	void backstabComesFromSlot63() {
		// Longsword category 1220 slot 63: StraightSwordAttack1HandBackStab, damage at its two TAE 2201 events.
		Attack backstab = WEAPONS.get("longsword").backstab();
		assertEquals(29010300, backstab.taeId());
		assertEquals(2, backstab.hits().size());
		assertTrue(backstab.isInvulnerable(3.0f));
		// CancelControl_Move ends at 2.9667 s, before the 4.0 s animation does.
		assertTrue(backstab.blocksMovement(2.9f));
		assertFalse(backstab.blocksMovement(3.0f));
		// The Drangleic Shield's slot 63 has motion type 0: no backstab.
		assertEquals(null, WEAPONS.get("shield").backstab());
		Weapons.CriticalRange mob = Weapons.backstabRange(false);
		assertEquals(2.0f, mob.distance(), 1e-6);
	}

	@Test
	void torchComesFromWeapon6000000AndPlayerCommonParam() {
		// WeaponActionCategoryParam 6000: left light = FistGuard (type 13), left heavy = AxeAttack1HandLight1 / 2.
		Moveset torch = WEAPONS.get("torch");
		assertTrue(torch.attack(true, Moveset.Type.LIGHT).guard());
		assertEquals(35010010, torch.attack(true, Moveset.Type.HEAVY).taeId());
		assertEquals(35010020, torch.attack(true, Moveset.Type.HEAVY_COMBO).taeId());
		Weapons.Torch data = Weapons.torch();
		assertEquals(300f, data.firstAdd(), 1e-6);
		assertEquals(359999f, data.limit(), 1e-6);
		// a00_18_0220: TAE 210099 (torch in hand) before 210100 (lit), both inside the attack lock.
		assertTrue(data.equipAt() < data.lightAt());
		assertTrue(data.lightAt() < data.make().lightLock());
		assertFalse(data.make().blocksMovement(0f));
	}

	@Test
	void parryWindowFollowsWeaponParam74() {
		// a45_01_0290: TAE 1220 0.1667-0.4 s; Drangleic Shield WeaponParam +0x74 = 0.68 -> active from ~0.242 s.
		Moveset shield = WEAPONS.get("shield");
		Attack parry = shield.attack(true, Moveset.Type.HEAVY);
		assertEquals(45010290, parry.taeId());
		assertFalse(parry.isParrying(0.23f, shield.parryRate()));
		assertTrue(parry.isParrying(0.25f, shield.parryRate()));
		assertFalse(parry.isParrying(0.41f, shield.parryRate()));
		assertFalse(WEAPONS.get("longsword").attack(false, Moveset.Type.LIGHT).isParrying(0.3f, 1f));
	}

	@Test
	void damageRatesComeFromPlayerDamageParam1c() {
		// Longsword: WeaponAttackMotionParam +0x2c -> PlayerDamageParam 10031800 (1.0), 21013000 backstab (1.4).
		Moveset longsword = WEAPONS.get("longsword");
		assertEquals(1.0f, longsword.attack(false, Moveset.Type.LIGHT).damageRate(0), 1e-6);
		assertEquals(1.2f, longsword.attack(false, Moveset.Type.HEAVY).damageRate(0), 1e-6);
		Attack backstab = longsword.backstab();
		assertEquals(1.4f, backstab.damageRate(0), 1e-6);
		assertEquals(1.4f, backstab.damageRate(1), 1e-6);
		// The Drangleic Shield parry's hit event deals PlayerDamageParam +0x1c = 0.
		assertEquals(0f, WEAPONS.get("shield").attack(true, Moveset.Type.HEAVY).damageRate(0), 1e-6);
		// Spear heavy combo: slot 0 (1.152) overlaps events of the empty slots 1 and 2.
		assertEquals(1.152f, WEAPONS.get("spear").attack(false, Moveset.Type.HEAVY_COMBO).damageRate(0), 1e-6);
	}

	@Test
	void staminaCostsComeFromWeaponStaminaCostParam() {
		// Longsword WeaponParam +0x34 = 210 x WeaponStaminaCostParam row 10: slot 0 0.95, slot 2 1.4.
		Moveset longsword = WEAPONS.get("longsword");
		Attack light = longsword.attack(false, Moveset.Type.LIGHT);
		assertEquals(199.5f, light.staminaDue(-1, light.duration()), 1e-3);
		assertEquals(0f, light.staminaDue(-1, light.hits().get(0)[0] - 0.01f), 1e-6);
		assertEquals(294f, longsword.attack(false, Moveset.Type.HEAVY).staminaDue(-1, 10), 1e-3);
		// Guards (motion type 13) cost nothing; regen is held by TAE 101100 (0 to 1.5 s on the light attack).
		assertEquals(0f, WEAPONS.get("shield").attack(true, Moveset.Type.LIGHT).staminaDue(-1, 10), 1e-6);
		assertTrue(light.blocksRegen(1.0f));
		assertFalse(light.blocksRegen(1.6f));
	}

	@Test
	void comboTypesFollowFun140396680() {
		assertEquals(Moveset.Type.LIGHT, Moveset.next(null, false, false, false, false));
		assertEquals(Moveset.Type.LIGHT_COMBO, Moveset.next(Moveset.Type.LIGHT, false, true, false, false));
		assertEquals(Moveset.Type.LIGHT, Moveset.next(Moveset.Type.LIGHT_COMBO, false, true, false, false));
		assertEquals(Moveset.Type.LIGHT, Moveset.next(Moveset.Type.LIGHT, false, false, false, false));
		assertEquals(Moveset.Type.LIGHT, Moveset.next(Moveset.Type.LIGHT, true, true, false, false));
		assertEquals(Moveset.Type.HEAVY_COMBO, Moveset.next(Moveset.Type.HEAVY, false, true, true, false));
		assertEquals(Moveset.Type.HEAVY, Moveset.next(Moveset.Type.HEAVY_COMBO, false, true, true, false));
		assertEquals(Moveset.Type.HEAVY, Moveset.next(Moveset.Type.LIGHT, false, true, true, false));
	}

	@Test
	void dodgeLocks() {
		Attack first = WEAPONS.get("longsword").attack(false, Moveset.Type.LIGHT);
		assertFalse(first.blocksDodge(0.2f));
		assertTrue(first.blocksDodge(0.5f));
		assertTrue(Dodge.ROLL.blocksAttack(false, 0.45f));
		assertFalse(Dodge.ROLL.blocksAttack(false, 0.55f));
		assertFalse(Dodge.BACKSTEP.blocksAttack(true, 0.34f));
	}

	@Test
	void hitBackDataComesFromPdpAndWeaponBones() {
		// wall_bounce.md: Longsword light 29010012 bounces on walls, not on the floor; dummy 100, r 0.3, length 0.75.
		Attack light = WEAPONS.get("longsword").attack(false, Moveset.Type.LIGHT);
		HitBack.Slot slot = HitBack.slot(light);
		assertTrue(slot.map() && slot.noFloor() && !slot.noWall());
		assertEquals(100, slot.dummy());
		assertEquals(0.75f, slot.length(), 1e-6);
		assertTrue(HitBack.bone(29010012, 0.5f) != null);
		assertTrue(HitBack.dummy("longsword", 100) != null);
		assertEquals(280, HitBack.event(29010012));
		assertEquals(1.1667f, HitBack.bounce(false, false).duration(), 1e-4);
	}

	@Test
	void powerStancePairsAndHitsComeFromTheParams() {
		// powerstance.md: two longswords and two clubs qualify, fists and shields never; 1.5x the larger Str / Dex.
		assertTrue(PowerStance.pairAllowed("longsword", "longsword"));
		assertTrue(PowerStance.pairAllowed("club", "club"));
		assertFalse(PowerStance.pairAllowed("fists", "fists"));
		assertFalse(PowerStance.pairAllowed("longsword", "shield"));
		assertEquals(15, PowerStance.requirements("longsword", "longsword")[0]);
		assertEquals(13, PowerStance.requirements("longsword", "longsword")[1]);
		assertEquals(18, PowerStance.requirements("club", "club")[0]);
		// Two-club heavy (33050030): three events, slots 0 / 5 / 0, each charging the cost.
		Attack heavy = PowerStance.data("club").heavy();
		assertEquals(3, PowerStance.events(heavy).size());
		assertEquals(3 * heavy.staminaCost(), PowerStance.staminaDue(heavy, -1, 5), 1e-3);
		assertEquals(2, PowerStance.events(PowerStance.data("longsword").light()).size());
	}

	@Test
	void bowDataComesFromTheParams() {
		// bow.md: Prepare 0.8333 s (stamina at 0.3667), CF arrow at 0.1333 s, 381 stamina, BulletParam 60760020.
		Bow.Shot bow = Bow.BOW.shot(Bow.TWO_HAND);
		assertEquals(0.8333f, bow.prepare().duration(), 1e-4);
		assertEquals(0.3667f, bow.shootAt(), 1e-4);
		assertEquals(0.1333f, bow.bulletAt(), 1e-4);
		assertEquals(381.027f, bow.staminaCost(), 1e-2);
		assertTrue(Bow.BOW.bullets().get(bow.bullet()) != null);
		assertEquals(25f, (float) Math.toDegrees(Bow.BOW.aimFov()), 0.1f);
		assertEquals(1.3f, bow.origin(true)[1], 0.1f);
		assertTrue(Bow.BOW.shot(Bow.ONE_HAND_RIGHT) == null);
		// crossbow.md: one-handed shots, 0.4 s Prepare, 1.8333 s CF, 389.5 / 486.67 stamina, a bullet per grip.
		Bow.Shot right = Bow.CROSSBOW.shot(Bow.ONE_HAND_RIGHT);
		assertEquals(0.4f, right.prepare().duration(), 1e-4);
		assertEquals(1.8333f, right.cf().duration(), 1e-4);
		assertEquals(389.5f, right.staminaCost(), 1e-2);
		assertEquals("60910000", right.bullet());
		assertEquals("60910020", Bow.CROSSBOW.shot(Bow.TWO_HAND).bullet());
		assertEquals(486.67f, Bow.CROSSBOW.shot(Bow.TWO_HAND).staminaCost(), 1e-2);
	}
}
