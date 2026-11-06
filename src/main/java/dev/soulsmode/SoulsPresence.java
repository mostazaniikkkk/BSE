package dev.soulsmode;

import dev.soulsmode.network.PresencePayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Multiplayer: what other clients need to draw a player like the Souls Mode player sees itself — whether it is in Souls
 * Mode (its locomotion animations, its back items) and whether it carries arrows (its quiver; other players'
 * inventories are not sent to clients). Sent to the player and everyone tracking it when it changes, and to a new
 * tracker.
 */
public final class SoulsPresence {
	private static final int CHECK_TICKS = 10;
	private static final Map<UUID, Integer> LAST = new HashMap<>();

	private SoulsPresence() {
	}

	private static PresencePayload of(ServerPlayer player) {
		return new PresencePayload(player.getId(), SoulsGameMode.isActive(player),
				player.getInventory().contains(ItemTags.ARROWS));
	}

	private static int key(PresencePayload p) {
		return (p.soulsMode() ? 1 : 0) | (p.arrows() ? 2 : 0);
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			PresencePayload payload = of(player);
			Integer last = LAST.put(player.getUUID(), key(payload));
			if (last == null || last != key(payload)) {
				ServerPlayNetworking.send(player, payload);
				for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
					if (viewer != player) {
						ServerPlayNetworking.send(viewer, payload);
					}
				}
			}
		}
	}

	public static void onStartTracking(ServerPlayer player, ServerPlayer viewer) {
		ServerPlayNetworking.send(viewer, of(player));
	}

	public static void forget(ServerPlayer player) {
		LAST.remove(player.getUUID());
	}
}
