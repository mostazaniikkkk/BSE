package dev.soulsmode.client.anim;

import dev.soulsmode.ds2.Attack;
import dev.soulsmode.ds2.SwingSpeed;
import dev.soulsmode.network.AttackPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Plays each player's attack at DS2's Attack_SwingSpeed inside its AttackSpeedControl 151 window (SwingSpeed): the
 * server sends the rate (AttackPayloads.Swing) after the Start; every client tick sets the layer's speed to the rate
 * while the clip is inside the window and back to 1 outside it.
 */
public final class SwingClient {
	private static final class Swing {
		final Attack attack;
		final boolean mirrored;
		float rate = 1;
		int ticks;

		Swing(Attack attack, boolean mirrored) {
			this.attack = attack;
			this.mirrored = mirrored;
		}
	}

	private static final Map<Integer, Swing> SWINGS = new HashMap<>();

	private SwingClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(AttackPayloads.Swing.TYPE, (payload, context) -> {
			Swing swing = SWINGS.get(payload.entityId());
			if (swing != null) {
				swing.rate = payload.rate();
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(SwingClient::tick);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
				(handler, client) -> SWINGS.clear());
	}

	/** A Start played {@code attack} for this player: its swing begins at rate 1 until the server's Swing arrives. */
	public static void started(int entityId, Attack attack, boolean mirrored) {
		SWINGS.put(entityId, new Swing(attack, mirrored));
	}

	/** The player's attack stopped or was replaced by something without a swing. */
	public static void stopped(int entityId) {
		SWINGS.remove(entityId);
	}

	/** The rate of a player's current swing, 1 without one (QA). */
	public static float rate(int entityId) {
		Swing swing = SWINGS.get(entityId);
		return swing == null ? 1 : swing.rate;
	}

	/** The local player's clip time after {@code seconds} of its current attack. */
	public static float clipTime(int entityId, Attack attack, float seconds) {
		Swing swing = SWINGS.get(entityId);
		return swing == null || swing.attack != attack ? seconds : SwingSpeed.clipTime(attack, swing.rate, seconds);
	}

	private static void tick(Minecraft client) {
		if (client.level == null) {
			SWINGS.clear();
			return;
		}
		Iterator<Map.Entry<Integer, Swing>> it = SWINGS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Swing> e = it.next();
			Swing swing = e.getValue();
			swing.ticks++;
			float clip = SwingSpeed.clipTime(swing.attack, swing.rate, swing.ticks / 20f);
			if (clip >= swing.attack.duration()) {
				it.remove();
				continue;
			}
			if (swing.rate == 1 || !(client.level.getEntity(e.getKey()) instanceof AbstractClientPlayer player)) {
				continue;
			}
			// The speed for the coming tick: the clip time it has to advance in 1/20 s, so the window's edges fall
			// between ticks without drifting from the server's clip time.
			float next = SwingSpeed.clipTime(swing.attack, swing.rate, (swing.ticks + 1) / 20f);
			SoulsAnimations.speed(player, swing.mirrored, (next - clip) * 20f);
		}
	}
}
