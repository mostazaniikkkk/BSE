package dev.soulsmode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.soulsmode.network.AttackPayloads;
import dev.soulsmode.network.RollPayloads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DS2's input buffer (docs/research/attack.md "Búfer de entrada", FUN_140368900 / FUN_140367b60 / FUN_140367ea0): a
 * press refused by the current action's lock is kept, in one slot, when it falls inside that action's recording window
 * (nmb InputControl: any command; InputControl_Attack: attacks only). Every tick the kept press is tried again and runs
 * on the first tick its lock allows it; a newer press replaces it; it is dropped after PlayerCommonParam +0x64 (1.5 s).
 * <p>
 * Covered actions: attacks, guards, item uses and dodges of SoulsAttack / SoulsRoll. Not covered (backlog): the
 * recording window of the damage reactions (SoulsPoise, InputControl 0.5-1.333) and the item / weapon switch / interact
 * commands (codes 7..0xe). DS2 records the dodge on the button's release; the mod's dodge is on the press.
 */
public final class SoulsInputBuffer {
	private static final String RESOURCE = "/data/soulsmode/ds2/weapons.json";

	private record Windows(List<float[]> all, List<float[]> attack) {
	}

	/** The kept press: an attack request or a dodge request, until {@code expires} (game time). */
	private record Entry(Object request, long expires) {
	}

	private static final Map<Integer, Windows> WINDOWS = new HashMap<>();
	private static final long KEEP_TICKS;
	private static final Map<UUID, Entry> KEPT = new HashMap<>();
	/** A kept press being tried again: refused, it must not be recorded over itself. */
	private static boolean replaying;

	static {
		try (InputStream in = SoulsInputBuffer.class.getResourceAsStream(RESOURCE)) {
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			KEEP_TICKS = Math.round(json.get("inputBufferSeconds").getAsFloat() * 20);
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("inputControl").entrySet()) {
				JsonObject w = e.getValue().getAsJsonObject();
				WINDOWS.put(Integer.parseInt(e.getKey()), new Windows(spans(w.getAsJsonArray("all")),
						spans(w.getAsJsonArray("attack"))));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private SoulsInputBuffer() {
	}

	private static List<float[]> spans(com.google.gson.JsonArray array) {
		List<float[]> out = new ArrayList<>();
		for (JsonElement s : array) {
			out.add(new float[] {s.getAsJsonArray().get(0).getAsFloat(), s.getAsJsonArray().get(1).getAsFloat()});
		}
		return out;
	}

	private static boolean inside(List<float[]> spans, float t) {
		for (float[] s : spans) {
			if (t >= s[0] && t < s[1]) {
				return true;
			}
		}
		return false;
	}

	/** The clip the player is in now (an attack / item use, else a dodge). */
	private static SoulsAttack.Clip clip(ServerPlayer player) {
		SoulsAttack.Clip clip = SoulsAttack.currentClip(player);
		return clip != null ? clip : SoulsRoll.currentClip(player);
	}

	/** An attack press refused by a lock: kept if the current clip records attacks now (InputControl / _Attack). */
	public static void attackRefused(ServerPlayer player, AttackPayloads.Request request) {
		SoulsAttack.Clip clip = replaying ? null : clip(player);
		Windows w = clip == null ? null : WINDOWS.get(clip.taeId());
		if (w != null && (inside(w.all(), clip.t()) || inside(w.attack(), clip.t()))) {
			keep(player, request);
		}
	}

	/** A dodge press refused by CancelControl_Avoid: kept if the current clip records every command (InputControl). */
	public static void dodgeRefused(ServerPlayer player, RollPayloads.Request request) {
		SoulsAttack.Clip clip = replaying ? null : clip(player);
		Windows w = clip == null ? null : WINDOWS.get(clip.taeId());
		if (w != null && inside(w.all(), clip.t())) {
			keep(player, request);
		}
	}

	private static void keep(ServerPlayer player, Object request) {
		// Replayed later, it is not the press the client predicted: no latency compensation then.
		if (request instanceof AttackPayloads.Request a) {
			request = new AttackPayloads.Request(a.heavy(), a.leftHand(), a.dx(), a.dz(), a.flick(), false);
		} else if (request instanceof RollPayloads.Request r) {
			request = new RollPayloads.Request(r.dx(), r.dz(), false);
		}
		KEPT.put(player.getUUID(), new Entry(request, player.serverLevel().getGameTime() + KEEP_TICKS));
	}

	/** An action started from a press: whatever was kept is used up (FUN_140367b60 clears the slot). */
	public static void started(ServerPlayer player) {
		if (!replaying) {
			KEPT.remove(player.getUUID());
		}
	}

	public static void tick(MinecraftServer server) {
		if (KEPT.isEmpty()) {
			return;
		}
		for (var it = KEPT.entrySet().iterator(); it.hasNext(); ) {
			var e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null || !player.isAlive() || player.serverLevel().getGameTime() >= e.getValue().expires()) {
				it.remove();
				continue;
			}
			Object before = SoulsAttack.token(player);
			Object rollBefore = SoulsRoll.token(player);
			replaying = true;
			try {
				if (e.getValue().request() instanceof AttackPayloads.Request attack) {
					SoulsAttack.onRequest(player, attack);
				} else if (e.getValue().request() instanceof RollPayloads.Request roll) {
					SoulsRoll.onRequest(player, roll);
				}
			} finally {
				replaying = false;
			}
			if (SoulsAttack.token(player) != before || SoulsRoll.token(player) != rollBefore) {
				it.remove();
			}
		}
	}

	public static void forget(ServerPlayer player) {
		KEPT.remove(player.getUUID());
	}
}
