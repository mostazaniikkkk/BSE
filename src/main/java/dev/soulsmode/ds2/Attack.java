package dev.soulsmode.ds2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One DS2 attack animation (see docs/research/attack.md, weapons.md). Every time is in seconds from the start of the
 * animation:
 * <ul>
 * <li>duration: .nsa +0x28;</li>
 * <li>hit windows: TAE event 2200, which registers an attack slot on chr+0x230 while active and charges its stamina
 * (FUN_1403269e0). Overlapping events are merged: a target is hit once per merged window;</li>
 * <li>locks: c0001.nmb CancelControl_* tracks; while one is active that action is refused (confirmed for Avoid,
 * FUN_14038e850). Attack1L / Attack1H lock the right hand's light / heavy attack, Attack2L / Attack2H the left
 * hand's;</li>
 * <li>combo window: AttackComboControl track (userData 130 raises chr.b8+0x4d4, which FUN_1401f9d50 requires for a
 * combo follow-up).</li>
 * </ul>
 *
 * @param animation Player Animation Library id (namespace:path) of the animation to play
 * @param hits      merged hit windows, sorted, each {start, end, damage rate}; empty for actions that do not hit. The
 *                  rate is PlayerDamageParam +0x1c of the event's attack slot (NaN: the slot is empty, see
 *                  {@link #damageRate})
 * @param guard     WeaponAttackMotionParam type 13: a guard stance, held while the button is held (no hits, no locks)
 * @param leftAuthored the animation was made for the left hand (source name "..._L"); played with the other hand it
 *                     is mirrored (both hands use the same "_L" guard in WeaponActionCategoryParam)
 * @param parry     TAE 1220 events, each {start, end}: the parry flag window (see {@link #isParrying})
 * @param staminaCost  FUN_14038fb10: WeaponParam +0x34 (+0x38 for motion types 8..12) x WeaponStaminaCostParam of the
 *                     action slot; 0 for guards
 * @param staminaCharges times at which {@code staminaCost} is charged: the start of every hit event (2200, or 2201
 *                       for critical attacks) whose attack slot is filled (FUN_1403269e0 / FUN_14015c2d0)
 * @param regenBlock   TAE 101100 windows {start, end}: no stamina regen inside them (FUN_1403287b0)
 */
public record Attack(String animation, int taeId, float duration, List<float[]> hits, float avoidLockStart,
		float avoidLockEnd, float moveLock, float lightLock, float heavyLock, float leftLightLock, float leftHeavyLock,
		float comboStart, float comboEnd, boolean guard, boolean leftAuthored, List<float[]> invulnerable,
		List<float[]> parry, float staminaCost, List<Float> staminaCharges, List<float[]> regenBlock) {

	/** Animation id of {@link #cast()}: not an animation, the attuned spell decides what plays. */
	public static final String CAST = "soulsmode:cast";

	/**
	 * A catalyst's cast slot (WeaponAttackMotionParam type 12, docs/research/catalysts.md): the attack button casts the
	 * active spell instead (dev.soulsmode.SoulsMagic). {@code staminaCost} is the slot's base (FUN_14038fb10:
	 * WeaponParam +0x38 x WeaponStaminaCostParam), which the spell's block +0x10 multiplies (casting.md section 6).
	 */
	public static Attack cast(float staminaCost) {
		return new Attack(CAST, 0, 0, List.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, List.of(), List.of(),
				staminaCost, List.of(), List.of());
	}

	public boolean isCast() {
		return CAST.equals(animation);
	}

	/** A held guard stance of the given length. */
	public static Attack guard(String animation, int taeId, float duration, boolean leftAuthored) {
		return new Attack(animation, taeId, duration, List.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0, true, leftAuthored, List.of(),
				List.of(), 0, List.of(), List.of());
	}

	/**
	 * A critical attack (backstab...): no CancelControl_Attack1L..2H, so CancelControl_AttackAll ends every attack lock;
	 * {@code avoidEnd} / {@code moveLock} are its CancelControl_Avoid / Move. Hits are its TAE 2201 events and
	 * {@code invulnerable} its TAE 110100 windows.
	 */
	public static Attack critical(String animation, int taeId, float duration, List<float[]> hits,
			List<float[]> invulnerable, boolean leftAuthored, float staminaCost, List<Float> staminaCharges,
			List<float[]> regenBlock, float avoidEnd, float moveLock, float attackLock) {
		return new Attack(animation, taeId, duration, hits, 0, avoidEnd, moveLock, attackLock, attackLock, attackLock,
				attackLock, 0, 0, false, leftAuthored, List.copyOf(invulnerable), List.of(), staminaCost, staminaCharges,
				regenBlock);
	}

	/**
	 * FUN_14016cd70: inside a TAE 1220 event the parry flag (chr.b8+0x550) is up once the elapsed fraction of the event
	 * is at least {@code 1 - parryRate} (WeaponParam +0x74 of the weapon in that hand).
	 */
	public boolean isParrying(float t, float parryRate) {
		for (float[] w : parry) {
			if (t >= w[0] + (w[1] - w[0]) * (1 - parryRate) && t < w[1]) {
				return true;
			}
		}
		return false;
	}

	public boolean isInvulnerable(float t) {
		for (float[] w : invulnerable) {
			if (t >= w[0] && t < w[1]) {
				return true;
			}
		}
		return false;
	}

	/** Whether to play the animation mirrored when the weapon is in the given hand. */
	public boolean mirroredFor(boolean weaponLeft) {
		return weaponLeft != leftAuthored;
	}

	public Attack {
		hits = merge(hits);
	}

	/** Index of the hit window active at {@code t}, or -1. */
	public int hitWindow(float t) {
		for (int i = 0; i < hits.size(); i++) {
			if (t >= hits.get(i)[0] && t < hits.get(i)[1]) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Damage multiplier of hit window {@code i} (PlayerDamageParam +0x1c). A window whose TAE events only name empty
	 * attack slots keeps 1: FUN_140326e80 skips a slot whose damage id is 0, but whether the slots are cleared between
	 * attacks (or keep the previous attack's hit) is not confirmed.
	 */
	/** PlayerDamageParam +0x18 of hit window {@code i}: the damage floor per type (FUN_140138d50). */
	public int damageFloor(int i) {
		return hits.get(i).length > 4 ? (int) hits.get(i)[4] : 0;
	}

	/** Length of a hit's reaction motion (Weapons.withMotion). */
	public static final int MOTION_FIELDS = 7;

	/**
	 * What the victim's damage animation reads of hit window {@code i} (weapons.json {@code reactionMotions},
	 * hit_reaction.md s.9): reaction, thrust, DmgDownOrUp mode, DmgDistance base, near, far, falloff; null when unknown.
	 */
	public float[] reactionMotion(int i) {
		float[] h = hits.get(i);
		return h.length >= 6 + MOTION_FIELDS && !Float.isNaN(h[6]) ? java.util.Arrays.copyOfRange(h, 6, 6 + MOTION_FIELDS)
				: null;
	}

	/** The PDP slot (0..2) of hit window {@code i} (poise.json playerWeapons hits), -1 when unknown. */
	public int hitSlot(int i) {
		float[] h = hits.get(i);
		return h.length > 6 + MOTION_FIELDS && !Float.isNaN(h[6 + MOTION_FIELDS]) ? (int) h[6 + MOTION_FIELDS] : -1;
	}

	/** PlayerDamageParam +0x2c of hit window {@code i}: the victim's reaction speed (HitReaction), NaN when unknown. */
	public float reactionSpeed(int i) {
		return hits.get(i).length > 5 ? hits.get(i)[5] : Float.NaN;
	}

	public float damageRate(int i) {
		float rate = hits.get(i)[2];
		return Float.isNaN(rate) ? 1 : rate;
	}

	/**
	 * Base stamina damage on guard of hit window {@code i} (PlayerDamageParam u16 +0x24, A+0x68 in FUN_140139210), or NaN
	 * when its slots are empty.
	 */
	public float staminaDamage(int i) {
		return hits.get(i).length > 3 ? hits.get(i)[3] : Float.NaN;
	}

	/** Stamina to charge for the animation time going from {@code from} (exclusive) to {@code to} (inclusive). */
	public float staminaDue(float from, float to) {
		float due = 0;
		for (float t : staminaCharges) {
			if (t > from && t <= to) {
				due += staminaCost;
			}
		}
		return due;
	}

	/** Whether a TAE 101100 window holds stamina regen at time {@code t}. */
	public boolean blocksRegen(float t) {
		for (float[] w : regenBlock) {
			if (t >= w[0] && t < w[1]) {
				return true;
			}
		}
		return false;
	}

	public boolean blocksDodge(float t) {
		return t >= avoidLockStart && t < avoidLockEnd;
	}

	public boolean blocksMovement(float t) {
		return t < moveLock;
	}

	/** CancelControl_Attack1L / 1H (2L / 2H for the left hand): whether that attack can follow {@code t} seconds in. */
	public boolean allows(boolean heavy, boolean leftHand, float t) {
		float lock = leftHand ? (heavy ? leftHeavyLock : leftLightLock) : (heavy ? heavyLock : lightLock);
		return t >= lock;
	}

	public boolean inCombo(float t) {
		return t >= comboStart && t < comboEnd;
	}

	private static List<float[]> merge(List<float[]> windows) {
		List<float[]> sorted = new ArrayList<>(windows);
		sorted.sort(Comparator.comparingDouble(w -> w[0]));
		List<float[]> out = new ArrayList<>();
		for (float[] w : sorted) {
			float[] last = out.isEmpty() ? null : out.get(out.size() - 1);
			// Overlapping events are one window; back-to-back events (end == start) stay separate hits, and so do
			// overlapping events with two different damage rates (Gyrm Axe two-handed rolling, TAE 34030221: each event
			// hits with its own PlayerDamageParam rate; while both are open the earlier one is active).
			boolean differentRates = last != null && !Float.isNaN(last[2]) && !Float.isNaN(w[2]) && w[2] != last[2];
			if (last != null && w[0] < last[1] && !differentRates) {
				last[1] = Math.max(last[1], w[1]);
				// The merged window takes the rate of its events that have one.
				if (Float.isNaN(last[2])) {
					last[2] = w[2];
				}
				if (Float.isNaN(last[3]) && w.length > 3) {
					last[3] = w[3];
				}
				if (w.length > 4) {
					last[4] = Math.max(last[4], w[4]);
				}
				if (Float.isNaN(last[5]) && w.length > 5) {
					last[5] = w[5];
				}
				if (Float.isNaN(last[6]) && w.length >= 6 + MOTION_FIELDS) {
					System.arraycopy(w, 6, last, 6, MOTION_FIELDS);
				}
				if (Float.isNaN(last[6 + MOTION_FIELDS]) && w.length > 6 + MOTION_FIELDS) {
					last[6 + MOTION_FIELDS] = w[6 + MOTION_FIELDS];
				}
			} else {
				float[] m = new float[6 + MOTION_FIELDS + 1];
				java.util.Arrays.fill(m, Float.NaN);
				m[4] = 0;
				System.arraycopy(w, 0, m, 0, Math.min(w.length, m.length));
				out.add(m);
			}
		}
		return List.copyOf(out);
	}
}
