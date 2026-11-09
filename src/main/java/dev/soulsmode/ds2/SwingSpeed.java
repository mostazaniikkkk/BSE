package dev.soulsmode.ds2;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * DS2's attack swing speed (docs/research/lack_of_stats.md section 1): FUN_140393350, at the attack start, sets
 * b8+0x1fc = WAMP +0x0c x (1 - pen), pen from PlayerLackOfStatsParam row 8 with the weapon's Str/Dex lack f (slot
 * +0x8c, Strength requirement halved two-handed) and the stamina shortfall g (FUN_140376510). While the clip is inside
 * its AttackSpeedControl 151 window, FUN_14035d580 writes that value to the Morpheme CP Attack_SwingSpeed, and the
 * attack's 0x7d play-speed nodes run the whole clip at it; outside the windows the speed is 1.
 * <p>
 * Not ported: the 150 window (b8+0x200 x b8+0x1f8, PlayerLackOfStatsParam row 7): b8+0x200 has no confirmed writer, so
 * that window plays at 1 (backlog). Times stay in clip seconds: {@link #clipTime} maps the real time onto them.
 */
public final class SwingSpeed {
	private record Data(float start, float end, float base) {
	}

	private static final Map<Attack, Data> SLOTS = Collections.synchronizedMap(new IdentityHashMap<>());

	private SwingSpeed() {
	}

	/** An attack slot's 151 window (its animation's swingSpeedWindow) and WAMP +0x0c (the slot's motionFactors[1]). */
	static void register(Attack attack, JsonObject slot, JsonObject animation) {
		if (attack == null || !animation.has("swingSpeedWindow") || animation.get("swingSpeedWindow").isJsonNull()
				|| !slot.has("motionFactors")) {
			return;
		}
		JsonArray w = animation.getAsJsonArray("swingSpeedWindow");
		SLOTS.put(attack, new Data(w.get(0).getAsFloat(), w.get(1).getAsFloat(),
				slot.getAsJsonArray("motionFactors").get(1).getAsFloat()));
	}

	/**
	 * Attack_SwingSpeed inside the 151 window: WAMP +0x0c x (1 - pen), pen = clamp(0.01 [r0 + (r1 - r0) f] (f > 0) +
	 * 0.01 [r2 + (r3 - r2) g] (g > 0), 0, 1) with PlayerLackOfStatsParam row 8. 1 for attacks without the window.
	 */
	public static float rate(Attack attack, float strDexLack, float staminaLack) {
		Data d = attack == null ? null : SLOTS.get(attack);
		if (d == null) {
			return 1;
		}
		JsonArray row = Weapons.raw().getAsJsonArray("swingSpeedLackRow");
		float pen = 0;
		if (strDexLack > 0) {
			float r0 = row.get(0).getAsFloat();
			pen += 0.01f * (r0 + (row.get(1).getAsFloat() - r0) * strDexLack);
		}
		if (staminaLack > 0) {
			float r2 = row.get(2).getAsFloat();
			pen += 0.01f * (r2 + (row.get(3).getAsFloat() - r2) * staminaLack);
		}
		pen = Math.max(0, Math.min(1, pen));
		return d.base() * (1 - pen);
	}

	/** FUN_140376510: 0 when the stamina covers the cost (or the cost is 0), (cost - stamina) / cost, 1 at 0 stamina. */
	public static float staminaShortfall(float stamina, float cost) {
		if (cost <= 0 || stamina >= cost) {
			return 0;
		}
		return stamina <= 0 ? 1 : (cost - stamina) / cost;
	}

	/** The clip time reached after {@code seconds} of real time, the clip running at {@code rate} inside the window. */
	public static float clipTime(Attack attack, float rate, float seconds) {
		Data d = attack == null ? null : SLOTS.get(attack);
		if (d == null || rate == 1 || rate <= 0 || seconds <= d.start()) {
			return seconds;
		}
		float inside = (d.end() - d.start()) / rate;
		if (seconds < d.start() + inside) {
			return d.start() + (seconds - d.start()) * rate;
		}
		return seconds - inside + (d.end() - d.start());
	}

	/** Whether a clip time is inside the attack's 151 window (where the animation plays at the swing rate). */
	public static boolean inWindow(Attack attack, float clipTime) {
		Data d = attack == null ? null : SLOTS.get(attack);
		return d != null && clipTime >= d.start() && clipTime < d.end();
	}
}
