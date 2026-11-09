package dev.soulsmode;

import dev.soulsmode.ds2.DerivedStats;
import dev.soulsmode.ds2.Ds2Data;
import dev.soulsmode.ds2.Stamina;
import dev.soulsmode.network.StaminaPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runs each Souls Mode player's DS2 stamina on the server and mirrors it to the client.
 * Not persisted: like DS2, it simply starts full.
 */
public final class SoulsStamina {
	/** The bar and its regen. */
	public static final boolean ENABLED = true;
	/** Actions spend stamina and are refused when it is exhausted (enabled by the user on 2026-10-29). */
	public static final boolean COSTS_ENABLED = true;
	private static final float TICK_SECONDS = 0.05f;
	private static final Map<UUID, Stamina> STAMINA = new HashMap<>();
	private static final Map<UUID, float[]> LAST_SENT = new HashMap<>();
	private static final Map<UUID, Sprint> SPRINT = new HashMap<>();

	/**
	 * Dash state (FUN_140385560 / FUN_140385a80): whether drain A runs, whether stamina ran out mid-sprint, and the
	 * cooldown armed once it is back above 0.
	 */
	private static final class Sprint {
		boolean draining;
		boolean ranOut;
		float cooldown;
	}
	private static Stamina.Params params;
	private static Stamina.LoadCurves curves;

	private SoulsStamina() {
	}

	/** The player's stamina, or null when not in Souls Mode with a class. */
	/** Players locked on (sent by the client, where the lock-on runs). */
	private static final java.util.Set<java.util.UUID> LOCKED = new java.util.HashSet<>();

	public static void setLocked(ServerPlayer player, boolean locked) {
		if (locked) {
			LOCKED.add(player.getUUID());
		} else {
			LOCKED.remove(player.getUUID());
		}
	}

	/** Whether the player is locked on (the client's lock-on state). */
	public static boolean isLocked(ServerPlayer player) {
		return LOCKED.contains(player.getUUID());
	}

	public static Stamina get(ServerPlayer player) {
		return STAMINA.get(player.getUUID());
	}

	/**
	 * Whether the player may start an action gated by FUN_1403337c0 (stamina exhausted = current <= 0). Always
	 * true while costs are off or the player has no stamina (no class).
	 */
	public static boolean canAct(ServerPlayer player) {
		Stamina stamina = get(player);
		return !COSTS_ENABLED || stamina == null || !stamina.isExhausted();
	}

	/** Spends {@code cost} through FUN_1403335d0, if costs are on. */
	public static void spend(ServerPlayer player, float cost) {
		Stamina stamina = get(player);
		if (COSTS_ENABLED && stamina != null) {
			stamina.consume(cost);
		}
	}

	/** Creates, resizes or removes the stamina to match the player's mode and attributes. */
	public static void apply(ServerPlayer player) {
		boolean soulsMode = SoulsGameMode.isActive(player);
		var data = ENABLED && soulsMode ? SoulsPlayer.data(player) : java.util.Optional.<SoulsData>empty();
		if (data.isEmpty()) {
			// Souls Mode without a class still has the camera, animations and attacks; only the bar waits for a class.
			STAMINA.remove(player.getUUID());
			send(player, soulsMode, 0, 0);
			return;
		}
		float max = DerivedStats.compute(data.get().toAttributes(), Ds2Data.physicalStats()).maxStamina();
		if (SoulsBleed.isBleeding(player)) {
			// SpEffect 900210 (bleed.md section 4): max stamina x 0.75 for the 10 s bleed state.
			max *= SoulsBleed.BLEEDING_STAMINA;
		}
		Stamina stamina = STAMINA.get(player.getUUID());
		if (stamina == null) {
			STAMINA.put(player.getUUID(), new Stamina(params(), curves(), max));
		} else {
			stamina.setMax(max);
		}
	}

	/** Respawn: a fresh, full bar. */
	public static void reset(ServerPlayer player) {
		STAMINA.remove(player.getUUID());
		apply(player);
	}

	public static void forget(ServerPlayer player) {
		LOCKED.remove(player.getUUID());
		STAMINA.remove(player.getUUID());
		LAST_SENT.remove(player.getUUID());
		SPRINT.remove(player.getUUID());
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Stamina stamina = STAMINA.get(player.getUUID());
			if (stamina == null) {
				continue;
			}
			boolean sprintBlocked = false;
			if (COSTS_ENABLED) {
				stamina.setActionBlocksRegen(SoulsAttack.blocksRegen(player) || SoulsRoll.blocksRegen(player)
						|| SoulsPoise.blocksRegen(player));
				// Above 120 % load there is no dash (FUN_14037e430 raises b8+0x588).
				sprintBlocked = tickSprint(player, stamina) | SoulsEquipLoad.overloaded(player);
			}
			stamina.tick(TICK_SECONDS);
			send(player, true, stamina.current(), stamina.max(), sprintBlocked);
		}
	}

	/**
	 * Drain A (FUN_140311110): 100 x ChrCommonParam+0x14 per second while dashing, scaled by the equip-load row 0x1e,
	 * and regen is held meanwhile. FUN_140385620 refuses the dash with stamina <= 0 or during the cooldown; running
	 * out mid-dash drops to a jog (FUN_140385a80) and arms PlayerCommonParam+0x10 once stamina is back above 0.
	 * The lock-on rate (+0x18) is not used: the server does not know the client's lock-on yet. Returns whether
	 * sprinting is refused.
	 */
	private static boolean tickSprint(ServerPlayer player, Stamina stamina) {
		Sprint sprint = SPRINT.computeIfAbsent(player.getUUID(), id -> new Sprint());
		if (sprint.cooldown > 0) {
			sprint.cooldown = Math.max(0, sprint.cooldown - TICK_SECONDS);
		}
		if (sprint.ranOut && !stamina.isExhausted()) {
			sprint.ranOut = false;
			sprint.cooldown = stamina.params().sprintCooldown();
		}
		boolean blocked = stamina.isExhausted() || sprint.cooldown > 0 || sprint.ranOut;
		boolean dashing = player.isSprinting() && !blocked;
		if (dashing != sprint.draining) {
			if (dashing) {
				stamina.beginDrain();
			} else {
				stamina.endDrain();
			}
			sprint.draining = dashing;
		}
		if (dashing) {
			// Drain A: 200/s, or 232.75/s while locked on (vfunc +0x20 != 0 -> +0x18).
			float rate = LOCKED.contains(player.getUUID()) ? stamina.params().lockedSprintRate()
					: stamina.params().sprintRate();
			stamina.drain(rate, stamina.curves().drainA(), TICK_SECONDS);
			if (stamina.isExhausted()) {
				sprint.ranOut = true;
				stamina.endDrain();
				sprint.draining = false;
				blocked = true;
			}
		}
		if (blocked && player.isSprinting()) {
			player.setSprinting(false);
		}
		return blocked;
	}

	private static void send(ServerPlayer player, boolean soulsMode, float current, float max) {
		send(player, soulsMode, current, max, false);
	}

	private static void send(ServerPlayer player, boolean soulsMode, float current, float max, boolean sprintBlocked) {
		float mode = (soulsMode ? 1 : 0) + (sprintBlocked ? 2 : 0);
		float[] last = LAST_SENT.get(player.getUUID());
		if (last != null && last[0] == current && last[1] == max && last[2] == mode) {
			return;
		}
		LAST_SENT.put(player.getUUID(), new float[] {current, max, mode});
		if (ServerPlayNetworking.canSend(player, StaminaPayload.TYPE)) {
			ServerPlayNetworking.send(player, new StaminaPayload(soulsMode, current, max, sprintBlocked));
		}
	}

	public static synchronized Stamina.LoadCurves curves() {
		if (curves == null) {
			curves = Stamina.LoadCurves.load();
		}
		return curves;
	}

	/**
	 * Regen delay after every stamina expense. DS2's is 0.1 s (DAT_14157f380); 1 s at the user's request (2026-10-30), an
	 * authorized departure from DS2 for game feel.
	 */
	public static final float REGEN_DELAY = 1.0f;

	public static synchronized Stamina.Params params() {
		if (params == null) {
			Stamina.Params p = Stamina.Params.load();
			params = new Stamina.Params(p.regenPerSecond(), p.exhaustedCeiling(), p.exhaustedFloor(), p.drainBase(),
					p.exhaustTimer(), p.exhaustTimer2(), REGEN_DELAY, p.actionCostBase(), p.jumpFactor(), p.dodgeFactor(),
					p.stepFactor(), p.chainedMultiplier(), p.attackMultiplier(), p.sprintRate(), p.lockedSprintRate(),
					p.sprintCooldown());
		}
		return params;
	}
}
