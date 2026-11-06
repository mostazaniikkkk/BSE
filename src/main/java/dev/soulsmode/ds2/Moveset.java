package dev.soulsmode.ds2;

/**
 * The attacks of a DS2 weapon as WeaponActionCategoryParam lays them out (docs/research/weapons.md):
 * slot = base(grip) + offset(action type). One-handed right base 0, one-handed left base 40, two-handed base 10;
 * action types 1..4 (light, light combo, heavy, heavy combo) use offsets 0..3. While a weapon is two-handed, the other
 * hand's buttons give action types 0x0b / 0x0c, slots 50 / 51 of the two-handed weapon ({@link #twoHandedOther}).
 */
public record Moveset(Hand right, Hand left, Hand twoHanded, OtherHand twoHandedOther, float[] guardRates,
		Attack backstab, float backstabAngleBonus, float parryRate, Kick kick) {
	public Moveset(Hand right, Hand left, Hand twoHanded, OtherHand twoHandedOther, float[] guardRates, Attack backstab,
			float backstabAngleBonus, float parryRate) {
		this(right, left, twoHanded, twoHandedOther, guardRates, backstab, backstabAngleBonus, parryRate,
				new Kick(null, null));
	}

	/** Action types returned by FUN_140396680 for the attack buttons. */
	public enum Type {
		LIGHT, LIGHT_COMBO, HEAVY, HEAVY_COMBO, OTHER_LIGHT, OTHER_HEAVY, BACKSTAB,
		/** Riposte (action 0x16, slot 61), on a parried victim. */
		RIPOSTE,
		/** Guard-break stab (action 0x17, slot 62), on a guard-broken player. */
		GUARD_BREAK_STAB,
		/** Movement attacks (docs/research/weapons.md, "Ataques en movimiento"): types 5, 6, 10, 9 and 0x10 / 0x11. */
		DASH, ROLLING, BACKSTEP, JUMP, KICK,
		/** Plunging attack (type 8): the airborne part, then the landing. */
		FALL, FALL_LANDING,
		/** Not a weapon attack: an item-use animation (lighting the torch) run through the same locks. */
		ITEM_USE,
		/** A catalyst's cast slot (motion type 12): the spell's Prepare (and the wait after it), or the failed cast. */
		CAST_PREPARE,
		/** The spell's cast animation, started by the second request when the wait runs out (FUN_1403972d0). */
		CAST,
		/** Power stance (docs/research/powerstance.md): the left buttons' types 0x0d / 0x0e / 0x0f, slots 52 / 53 / 54. */
		POWER_LIGHT, POWER_LIGHT_COMBO, POWER_HEAVY,
		/** Attacking without lowering the guard (weapon_arts.md s.2): types 0x12 / 0x13, slots 57 / 58. */
		GUARD_ATTACK, GUARD_ATTACK_COMBO;

		public boolean heavy() {
			return this == HEAVY || this == HEAVY_COMBO || this == OTHER_HEAVY || this == JUMP || this == POWER_HEAVY;
		}

		public boolean powerStance() {
			return this == POWER_LIGHT || this == POWER_LIGHT_COMBO || this == POWER_HEAVY;
		}
	}

	/** Which weapon, if any, is held with both hands (DS2's grip states). */
	public enum Grip {
		ONE_HANDED, TWO_HANDED_RIGHT, TWO_HANDED_LEFT,
		/**
		 * Power stance (handCtrl+0x10 states 5 / 6, docs/research/powerstance.md): both weapons in hand; the left
		 * buttons' moves come from the left weapon in state 5 (POWER_STANCE_LEFT), the right one in state 6.
		 */
		POWER_STANCE_LEFT, POWER_STANCE_RIGHT;

		/** The hand whose weapon is two-handed. */
		public boolean leftWeapon() {
			return this == TWO_HANDED_LEFT;
		}

		/** One weapon held with both hands (states 2 / 3). */
		public boolean twoHanded() {
			return this == TWO_HANDED_RIGHT || this == TWO_HANDED_LEFT;
		}

		public boolean powerStance() {
			return this == POWER_STANCE_LEFT || this == POWER_STANCE_RIGHT;
		}
	}

	/**
	 * One grip's attack slots (any may be the same animation, as in the game data): offsets 0..3, and the movement
	 * attacks at offsets 4 (dash), 5 (rolling), 6 (backstep) and 9 (jump), null when the slot is empty or has no
	 * animation in c0001.
	 */
	public record Hand(Attack light, Attack lightCombo, Attack heavy, Attack heavyCombo, Attack dash, Attack rolling,
			Attack backstep, Attack jump, Fall fall) {
		public Hand(Attack light, Attack lightCombo, Attack heavy, Attack heavyCombo) {
			this(light, lightCombo, heavy, heavyCombo, null, null, null, null, null);
		}

		public Attack get(Type type) {
			return switch (type) {
				case LIGHT -> light;
				case LIGHT_COMBO -> lightCombo;
				case HEAVY -> heavy;
				case HEAVY_COMBO -> heavyCombo;
				case DASH -> dash;
				case ROLLING -> rolling;
				case BACKSTEP -> backstep;
				case JUMP -> jump;
				case FALL -> fall == null ? null : fall.air();
				case FALL_LANDING -> fall == null ? null : fall.landing();
				default -> throw new IllegalArgumentException(type.name());
			};
		}
	}

	/**
	 * Slot +8, the plunging attack (docs/research/weapons.md, "Tipo 8"): {@code air} runs from the request until the
	 * player lands (its animation is the airborne loop, played after {@code startAnimation}); {@code landing} is the
	 * landing part of aGG_01_0260.
	 */
	public record Fall(Attack air, Attack landing, String startAnimation) {
	}

	/** Slots 55 / 56: the kick, one-handed or while a weapon is two-handed (FUN_1403962f0); null when empty. */
	public record Kick(Attack oneHanded, Attack twoHanded) {
	}

	/** Slots 50 / 51: the other hand's buttons while this weapon is two-handed. */
	public record OtherHand(Attack light, Attack heavy) {
	}

	/**
	 * Damage multiplier for a hit taken while guarding with this weapon (FUN_140137aa0 fills the guard rates for a
	 * hit of category 2, FUN_140138d50 applies 1 - clamp((1 - penetration) * rate, 0, 1)); {@code type} indexes
	 * guardRates. Minecraft attackers have no guard penetration.
	 */
	public float guardFactor(int type) {
		return 1f - Math.max(0f, Math.min(1f, guardRates[type]));
	}

	public Hand hand(boolean leftHand) {
		return leftHand ? left : right;
	}

	/** One-handed attack. */
	public Attack attack(boolean leftHand, Type type) {
		return hand(leftHand).get(type);
	}

	/**
	 * The attack for a request with this weapon. {@code twoHanded}: the weapon is held with both hands;
	 * {@code otherButtons}: the request came from the other hand's buttons (type is then OTHER_LIGHT / OTHER_HEAVY).
	 */
	public Attack attack(boolean leftHand, boolean twoHanded, Type type) {
		return switch (type) {
			case OTHER_LIGHT -> twoHandedOther.light();
			case OTHER_HEAVY -> twoHandedOther.heavy();
			case BACKSTAB -> backstab;
			case KICK -> twoHanded ? kick.twoHanded() : kick.oneHanded();
			default -> twoHanded ? this.twoHanded.get(type) : attack(leftHand, type);
		};
	}

	/**
	 * Action type of a new request (FUN_140396680 with FUN_1401f9d50): the other hand's buttons while a weapon is
	 * two-handed give OTHER_LIGHT / OTHER_HEAVY; otherwise, inside the current attack's combo window and with the same
	 * hand, light after LIGHT gives LIGHT_COMBO, heavy after HEAVY gives HEAVY_COMBO and after HEAVY_COMBO gives HEAVY
	 * again; anything else starts with LIGHT / HEAVY. {@code current} is null when not attacking.
	 */
	public static Type next(Type current, boolean currentLeftHand, boolean inCombo, boolean heavy, boolean leftHand) {
		boolean chained = current != null && inCombo && currentLeftHand == leftHand;
		if (!heavy) {
			return chained && current == Type.LIGHT ? Type.LIGHT_COMBO : Type.LIGHT;
		}
		if (chained && current == Type.HEAVY) {
			return Type.HEAVY_COMBO;
		}
		return Type.HEAVY;
	}

	/** The request's type when the pressed hand's buttons are the other hand of a two-handed weapon. */
	public static Type other(boolean heavy) {
		return heavy ? Type.OTHER_HEAVY : Type.OTHER_LIGHT;
	}
}
