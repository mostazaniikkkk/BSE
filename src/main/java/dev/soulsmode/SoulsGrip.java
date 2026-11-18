package dev.soulsmode;

import dev.soulsmode.api.SoulsWeapons;
import dev.soulsmode.ds2.Moveset;
import dev.soulsmode.ds2.Weapons;
import dev.soulsmode.network.AttackPayloads;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * DS2 two-handing (docs/research/weapons.md, "Agarre a dos manos: cambio y postura"):
 * <ul>
 * <li>The request (FUN_140395330) puts the other weapon away with the animation of its WeaponStorage
 * (WeaponTypeParam +0x24): a00_08_0010 / 0020 / 0030, mirrored when it is the right weapon (SM_ChangeWeapon_R). Going back
 * to one hand plays the same animation. With storage 0 the grip changes at once.</li>
 * <li>The grip changes when the UpperBody event (9810 / 9811) starts, 0.2333 s in (FUN_14036b930 / FUN_14036bb70).</li>
 * <li>The animation is an upper-body layer without CancelControl_Move, _Avoid or _Attack: walking, dodging and
 * attacking stay allowed; CancelControl_Itemuse blocks item use (the torch) for its first 0.333 s (0.367 s at the
 * hip).</li>
 * <li>Changing the weapon in either hand (FUN_14036b9d0) and lighting the torch (FUN_14034f300) go back to one hand at
 * once.</li>
 * </ul>
 * Not traced, so these are choices: which button gives each request (the G key toggles, sneak + G two-hands the left
 * weapon), and what blocks the request; here no change starts during an attack or dodge, and one that is cut by an
 * attack or dodge before 0.2333 s does not happen (the change state machine sits in BT_Move). The animation plays at
 * WeaponChangeSpeed (FUN_14035dfe0: Agility column +0x74, 1.20 .. 1.45, agility.md s.3), so every time above is on
 * its clock.
 */
public final class SoulsGrip {
	/** Start of the UpperBody track of a00_08_00x0 (frame 7 at 30 fps). */
	private static final float SWITCH_AT = 7 / 30f;
	/** Length of a00_08_0010 / 0020 / 0030. */
	private static final float DURATION = 0.5f;
	/** End of CancelControl_Itemuse: 0.333 s, 0.367 s for a00_08_0030 (storage 30). */
	private static final float ITEM_USE_LOCK = 1 / 3f;
	private static final float ITEM_USE_LOCK_HIP = 11 / 30f;
	private static final int HIP = 30;

	private static final class Change {
		final Moveset.Grip to;
		final int storage;
		final long start;
		/** WeaponChangeSpeed: the clip's seconds per real second. */
		final float speed;
		boolean applied;

		Change(Moveset.Grip to, int storage, long start, float speed) {
			this.to = to;
			this.storage = storage;
			this.start = start;
			this.speed = speed;
		}
	}

	/** Two-handed players and the items they held when it started. */
	private record Held(Moveset.Grip grip, Item main, Item off) {
	}

	private static final Map<UUID, Held> GRIP = new HashMap<>();
	private static final Map<UUID, Change> CHANGES = new HashMap<>();

	private SoulsGrip() {
	}

	public static Moveset.Grip grip(ServerPlayer player) {
		Held held = GRIP.get(player.getUUID());
		return held == null ? Moveset.Grip.ONE_HANDED : held.grip();
	}

	/** The grip key: two-hands the right weapon (the left one with {@code leftWeapon}), or goes back to one hand. */
	public static void onKey(ServerPlayer player, boolean leftWeapon) {
		if (!SoulsAttack.ENABLED || !SoulsGameMode.isActive(player) || !player.isAlive()
				|| CHANGES.containsKey(player.getUUID()) || SoulsAttack.isActing(player) || SoulsRoll.isDodging(player)) {
			return;
		}
		Moveset.Grip current = grip(player);
		// Power stance (powerstance.md): "two-hand the left weapon" with a qualifying pair goes straight to state 5 / 6,
		// and any request leaves it straight back (FUN_14034f530 / FUN_14034f470): no change animation.
		if (current.powerStance()) {
			set(player, Moveset.Grip.ONE_HANDED);
			return;
		}
		if (current == Moveset.Grip.ONE_HANDED && leftWeapon) {
			Moveset.Grip power = powerStance(player);
			if (power != null) {
				set(player, power);
				return;
			}
		}
		Moveset.Grip to = current != Moveset.Grip.ONE_HANDED ? Moveset.Grip.ONE_HANDED
				: leftWeapon ? Moveset.Grip.TWO_HANDED_LEFT : Moveset.Grip.TWO_HANDED_RIGHT;
		// The weapon put away (or taken back): the other hand's.
		boolean storedLeft = (to == Moveset.Grip.ONE_HANDED ? current : to) == Moveset.Grip.TWO_HANDED_RIGHT;
		ResourceLocation stored = SoulsAttack.movesetFor(player, storedLeft);
		int storage = stored == null ? 0 : SoulsWeapons.grip(stored).map(Weapons.GripData::storage).orElse(0);
		if (storage == 0) {
			set(player, to);
			return;
		}
		var stats = SoulsPlayer.data(player).map(SoulsData::toAttributes).orElse(null);
		float speed = stats == null ? 1f : dev.soulsmode.ds2.Agility.weaponChangeSpeed(stats);
		CHANGES.put(player.getUUID(), new Change(to, storage, player.serverLevel().getGameTime(), speed));
		broadcast(player, new AttackPayloads.GripChange(player.getId(), storage, !storedLeft, speed));
	}

	/**
	 * FUN_14034d930 + FUN_140350170: the power stance state for the held pair (POWER_STANCE_RIGHT = state 6, the right
	 * weapon gives the left buttons' moves), or null when the pair or the Strength / Dexterity do not qualify. The lit
	 * torch replaces the left weapon, so it never qualifies.
	 */
	public static Moveset.Grip powerStance(ServerPlayer player) {
		if (SoulsTorch.isHeld(player)) {
			return null;
		}
		String right = SoulsAttack.damageKey(SoulsWeapons.movesetFor(player.getMainHandItem()));
		String left = SoulsAttack.damageKey(SoulsWeapons.movesetFor(player.getOffhandItem(), true));
		var stats = SoulsPlayer.data(player).map(SoulsData::toAttributes).orElse(null);
		if (stats == null || !dev.soulsmode.ds2.PowerStance.pairAllowed(right, left)) {
			return null;
		}
		int[] need = dev.soulsmode.ds2.PowerStance.requirements(right, left);
		if (stats.get(dev.soulsmode.ds2.Attributes.STRENGTH) < need[0]
				|| stats.get(dev.soulsmode.ds2.Attributes.DEXTERITY) < need[1]) {
			return null;
		}
		return dev.soulsmode.ds2.PowerStance.rightGivesMoves(right, left) ? Moveset.Grip.POWER_STANCE_RIGHT
				: Moveset.Grip.POWER_STANCE_LEFT;
	}

	public static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Change>> it = CHANGES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Change> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Change change = entry.getValue();
			if (player == null || !player.isAlive() || !SoulsGameMode.isActive(player)) {
				it.remove();
				continue;
			}
			float t = (player.serverLevel().getGameTime() - change.start) / 20f * change.speed;
			if (!change.applied && (SoulsAttack.isActing(player) || SoulsRoll.isDodging(player))) {
				it.remove();
				continue;
			}
			if (!change.applied && t >= SWITCH_AT) {
				change.applied = true;
				set(player, change.to);
			}
			if (t >= DURATION) {
				it.remove();
			}
		}
		// FUN_14036b9d0: a weapon change in either hand goes back to one hand.
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Held held = GRIP.get(player.getUUID());
			if (held != null && (!SoulsGameMode.isActive(player) || player.getMainHandItem().getItem() != held.main()
					|| player.getOffhandItem().getItem() != held.off())) {
				set(player, Moveset.Grip.ONE_HANDED);
			}
		}
	}

	/** CancelControl_Itemuse of a running grip change. */
	public static boolean blocksItemUse(ServerPlayer player) {
		Change change = CHANGES.get(player.getUUID());
		if (change == null) {
			return false;
		}
		float t = (player.serverLevel().getGameTime() - change.start) / 20f * change.speed;
		return t < (change.storage == HIP ? ITEM_USE_LOCK_HIP : ITEM_USE_LOCK);
	}

	/** Back to one hand at once (FUN_14034f470), e.g. when the torch is lit. */
	public static void reset(ServerPlayer player) {
		CHANGES.remove(player.getUUID());
		if (GRIP.containsKey(player.getUUID())) {
			set(player, Moveset.Grip.ONE_HANDED);
		}
	}

	private static void set(ServerPlayer player, Moveset.Grip grip) {
		if (grip == Moveset.Grip.ONE_HANDED) {
			GRIP.remove(player.getUUID());
		} else {
			GRIP.put(player.getUUID(), new Held(grip, player.getMainHandItem().getItem(), player.getOffhandItem().getItem()));
		}
		broadcast(player, new AttackPayloads.GripState(player.getId(), grip.ordinal()));
		player.displayClientMessage(Component.translatable(
				"message.soulsmode.grip." + grip.name().toLowerCase(Locale.ROOT)), true);
	}

	public static void onStartTracking(ServerPlayer player, ServerPlayer viewer) {
		Moveset.Grip grip = grip(player);
		if (grip != Moveset.Grip.ONE_HANDED) {
			ServerPlayNetworking.send(viewer, new AttackPayloads.GripState(player.getId(), grip.ordinal()));
		}
	}

	public static void forget(ServerPlayer player) {
		GRIP.remove(player.getUUID());
		CHANGES.remove(player.getUUID());
	}

	private static void broadcast(ServerPlayer player, CustomPacketPayload packet) {
		ServerPlayNetworking.send(player, packet);
		for (ServerPlayer other : PlayerLookup.tracking(player)) {
			if (other != player) {
				ServerPlayNetworking.send(other, packet);
			}
		}
	}
}
