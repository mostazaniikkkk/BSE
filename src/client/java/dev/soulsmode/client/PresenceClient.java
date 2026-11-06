package dev.soulsmode.client;

import dev.soulsmode.network.PresencePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/** Other players' Souls Mode flag and quiver (SoulsPresence); the local player uses its own state. */
public final class PresenceClient {
	private static final Map<Integer, PresencePayload> PRESENCE = new HashMap<>();

	private PresenceClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(PresencePayload.TYPE,
				(payload, context) -> PRESENCE.put(payload.entityId(), payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PRESENCE.clear());
	}

	public static boolean soulsMode(Entity entity) {
		if (entity == Minecraft.getInstance().player) {
			return StaminaHud.soulsMode();
		}
		PresencePayload p = PRESENCE.get(entity.getId());
		return p != null && p.soulsMode();
	}

	public static boolean arrows(Entity entity) {
		PresencePayload p = PRESENCE.get(entity.getId());
		return p != null && p.arrows();
	}
}
