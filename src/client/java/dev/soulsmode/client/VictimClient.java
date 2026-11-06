package dev.soulsmode.client;

import dev.soulsmode.client.anim.SoulsAnimations;
import dev.soulsmode.network.VictimPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Players held as a critical's or a parry's victim (SoulsAttack, VictimPayload): plays their DS2 victim animations in
 * sequence, and keeps the local player from moving until moveAt and from attacking or dodging until actAt.
 */
public final class VictimClient {
	private static final class Victim {
		final VictimPayload payload;
		int ticks;
		int stage;

		Victim(VictimPayload payload) {
			this.payload = payload;
		}
	}

	private static final Map<Integer, Victim> VICTIMS = new HashMap<>();

	private VictimClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(VictimPayload.TYPE, (payload, context) -> {
			if (payload.entityId() == context.player().getId()) {
				AttackClient.cancel();
			}
			VICTIMS.put(payload.entityId(), new Victim(payload));
			play(context.client(), payload.entityId(), payload.first());
		});
		ClientTickEvents.END_CLIENT_TICK.register(VictimClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> VICTIMS.clear());
	}

	private static void play(Minecraft client, int entityId, String animation) {
		if (client.level != null && !animation.isEmpty()
				&& client.level.getEntity(entityId) instanceof AbstractClientPlayer player) {
			SoulsAnimations.play(player, ResourceLocation.parse(animation));
		}
	}

	private static void tick(Minecraft client) {
		if (client.isPaused()) {
			return;
		}
		Iterator<Map.Entry<Integer, Victim>> it = VICTIMS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Victim> e = it.next();
			Victim v = e.getValue();
			float t = ++v.ticks / 20f;
			VictimPayload p = v.payload;
			if (v.stage == 0 && t >= p.secondAt()) {
				v.stage = 1;
				play(client, e.getKey(), p.second());
			}
			if (v.stage == 1 && !p.third().isEmpty() && t >= p.thirdAt()) {
				v.stage = 2;
				play(client, e.getKey(), p.third());
			}
			if (t >= Math.max(p.moveAt(), p.actAt())) {
				it.remove();
			}
		}
	}

	private static Victim local() {
		var player = Minecraft.getInstance().player;
		return player == null ? null : VICTIMS.get(player.getId());
	}

	/** The local player is held and cannot move yet. */
	public static boolean cannotMove() {
		Victim v = local();
		return v != null && v.ticks / 20f < v.payload.moveAt();
	}

	/** The local player is held and cannot attack, dodge or use items yet. */
	public static boolean cannotAct() {
		Victim v = local();
		return v != null && v.ticks / 20f < v.payload.actAt();
	}
}
