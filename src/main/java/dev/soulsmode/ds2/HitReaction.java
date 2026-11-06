package dev.soulsmode.ds2;

import com.google.gson.JsonObject;

/**
 * The c0001 damage reaction (docs/research/hit_reaction.md): a normal hit gives reaction 2 / 3 (light / heavy; both
 * DmgKnockBack blends, the same timing), played at the speed of the hit's PlayerDamageParam +0x2c (DmgDuration). At
 * speed 1 the victim can attack again at 0.9 s (CancelControl_AttackAll) and move at 0.9 s (CancelControl_Move); every
 * window is divided by the speed. weapons.json: each slot's {@code reactionSpeeds} (per PDP, in the hit arrays), the
 * top-level {@code hitReaction}.
 */
public final class HitReaction {
	private static float moveLock = Float.NaN;
	private static float attackLock;

	private HitReaction() {
	}

	private static synchronized void load() {
		if (!Float.isNaN(moveLock)) {
			return;
		}
		JsonObject r = Weapons.raw().getAsJsonObject("hitReaction");
		moveLock = r.get("moveLock").getAsFloat();
		attackLock = r.get("attackLock").getAsFloat();
	}

	/** Seconds the victim cannot move, for a hit of reaction speed {@code speed}. */
	public static float moveLock(float speed) {
		load();
		return moveLock / speed;
	}

	/** Seconds the victim cannot attack, for a hit of reaction speed {@code speed}. */
	public static float attackLock(float speed) {
		load();
		return attackLock / speed;
	}
}
