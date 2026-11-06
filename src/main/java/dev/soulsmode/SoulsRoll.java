package dev.soulsmode;

import dev.soulsmode.ds2.Dodge;
import dev.soulsmode.network.RollPayloads;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the dodge: picks roll or backstep like DS2, tells clients to play it, and applies the
 * invincibility window. Movement is done by the dodging player's own client (vanilla movement is
 * client-authoritative).
 */
public final class SoulsRoll {
	/** Game-feel feature switch: on so it can be playtested; turn off if it doesn't feel right. */
	public static final boolean ENABLED = true;

	/**
	 * {@code speed}: playback speed; every window is checked on the animation clock (elapsed x speed).
	 * {@code iframes}: chr.b8+0xa4, the share of the clip's TAE 110300 that is invulnerable (Agility).
	 */
	private record Active(Dodge kind, long startTick, float speed, float iframes) {
	}

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();

	private SoulsRoll() {
	}

	public static void onRequest(ServerPlayer player, RollPayloads.Request request) {
		if (!ENABLED || !SoulsGameMode.isActive(player) || isRerollLocked(player) || !player.onGround()
				|| SoulsAttack.isVictim(player)
				|| SoulsAttack.blocksDodge(player) || SoulsPoise.blocksDodge(player)) {
			return;
		}
		// FUN_140367ea0: no dodge request is sent with stamina <= 0.
		if (!SoulsStamina.canAct(player)) {
			return;
		}
		// FUN_140376770 k: chained while the roll (SM_Dodge) still plays, or cancelling an attack (AttackDuration).
		Active previous = ACTIVE.get(player.getUUID());
		boolean chained = previous != null && previous.kind().isRoll()
				&& seconds(player, previous) < previous.kind().duration;
		boolean inAttack = SoulsAttack.inAttackDuration(player);
		// Outside its CancelControl_Avoid window a dodge cuts the attack short.
		SoulsAttack.cancel(player);
		float length = (float) Math.sqrt(request.dx() * request.dx() + request.dz() * request.dz());
		Dodge kind;
		float dx;
		float dz;
		if (length > 1e-3f) {
			// FUN_14038e900: a movement direction makes it a roll that way.
			kind = Dodge.of(true, SoulsEquipLoad.ratio(player));
			dx = request.dx() / length;
			dz = request.dz() / length;
		} else {
			// FUN_14038e9a0: no direction makes it a backstep, away from where the player faces.
			kind = Dodge.of(false, SoulsEquipLoad.ratio(player));
			double yaw = Math.toRadians(player.getYRot());
			dx = (float) Math.sin(yaw);
			dz = (float) -Math.cos(yaw);
		}
		float speed = kind.speed(SoulsEquipLoad.ratio(player), SoulsEquipLoad.ROWS);
		float cost = SoulsStamina.params().dodgeCost(kind.isRoll(), chained, inAttack);
		var stamina = SoulsStamina.get(player);
		var stats = SoulsPlayer.data(player).map(SoulsData::toAttributes).orElse(null);
		// FUN_14038e440 / FUN_14038e5d0, before the cost is charged. The equipment's stat lack (f) is 0: which param
		// holds each piece's requirements is not confirmed (agility.md), and Minecraft's armour has none.
		float iframes = stats == null ? 0 : dev.soulsmode.ds2.Agility.iframeFraction(kind.isRoll(), stats, 0,
				dev.soulsmode.ds2.Agility.staminaLack(stamina == null ? cost : stamina.current(), cost));
		ACTIVE.put(player.getUUID(), new Active(kind, player.serverLevel().getGameTime(), speed, iframes));
		// The clip's TAE event 300 makes the dodge's sound (SoulsPerception).
		SoulsPerception.onDodge(player, kind, speed);
		// FUN_140328d10 charges the cost computed at the request as the Morpheme state is entered.
		SoulsStamina.spend(player, cost);
		// The direction's length is the distance in blocks (equip load, docs/research/equipload.md).
		float distance = kind.distance(SoulsEquipLoad.ratio(player), SoulsEquipLoad.ROWS);
		RollPayloads.Start start = new RollPayloads.Start(player.getId(), kind.ordinal(), dx * distance, dz * distance,
				speed);
		ServerPlayNetworking.send(player, start);
		for (ServerPlayer other : PlayerLookup.tracking(player)) {
			if (other != player) {
				ServerPlayNetworking.send(other, start);
			}
		}
	}

	/** The dodge still playing (SM_Dodge / SM_Step), or null. */
	public static Dodge dodging(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		return active != null && seconds(player, active) < active.kind().duration ? active.kind() : null;
	}

	/** Whether a dodge animation is still playing. */
	public static boolean isDodging(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		return active != null && seconds(player, active) < active.kind().duration;
	}

	/** DS2 CancelControl_Avoid window of the current dodge: no new dodge until it ends. */
	public static boolean isRerollLocked(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		return active != null && seconds(player, active) < active.kind().rerollLock;
	}

	/** CancelControl_Attack1L / 1H (AttackAll for the backstep) of the current dodge. */
	public static boolean blocksAttack(ServerPlayer player, boolean heavy) {
		Active active = ACTIVE.get(player.getUUID());
		return active != null && active.kind().blocksAttack(heavy, seconds(player, active));
	}

	/** DS2 i-frames: the current dodge clip's TAE 110100 and the Agility share of its 110300 (agility.md s.2). */
	public static boolean isInvincible(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		return active != null && active.kind().isInvincible(seconds(player, active), active.iframes());
	}

	/** TAE 101100 of the current dodge animation (roll a00_12_0020, backstep a00_11_0011). */
	public static boolean blocksRegen(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		if (active == null) {
			return false;
		}
		float t = seconds(player, active);
		for (float[] w : REGEN_BLOCK.get(active.kind().ordinal())) {
			if (t >= w[0] && t < w[1]) {
				return true;
			}
		}
		return false;
	}

	private static final java.util.List<java.util.List<float[]>> REGEN_BLOCK = dev.soulsmode.ds2.Weapons.dodgeRegenBlock();

	/** The dodge clip playing (TAE id) and its animation time, for the poise checks; null without one. */
	public static SoulsAttack.Clip currentClip(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		if (active == null) {
			return null;
		}
		float t = seconds(player, active);
		return t < active.kind().duration ? new SoulsAttack.Clip(active.kind().taeId(), t, null) : null;
	}

	public static void forget(ServerPlayer player) {
		ACTIVE.remove(player.getUUID());
	}

	/** Animation time of the dodge (real time x its playback speed). */
	private static float seconds(ServerPlayer player, Active active) {
		return (player.serverLevel().getGameTime() - active.startTick()) / 20f * active.speed();
	}
}
