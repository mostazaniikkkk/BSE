package dev.soulsmode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * DS2's covenant state (docs/research/covenants.md section 1): one active covenant (status +0x1ad), points per covenant
 * (+0x1c2 + 2 id; all kept when changing or leaving), rank = how many VowParam thresholds the points reach (0..3,
 * FUN_140204270). Points are only added to the active covenant (FUN_140203af0). In BSE only the Way of Blue exists.
 */
public final class SoulsCovenants {
	private static final String RESOURCE = "/data/soulsmode/ds2/weapons.json";
	private static final Map<Integer, int[]> THRESHOLDS = new HashMap<>();

	static {
		try (InputStream in = SoulsCovenants.class.getResourceAsStream(RESOURCE)) {
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("vowThresholds").entrySet()) {
				var a = e.getValue().getAsJsonArray();
				THRESHOLDS.put(Integer.parseInt(e.getKey()), new int[] {a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()});
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private SoulsCovenants() {
	}

	/** The active covenant id, 0 for none. */
	public static int active(ServerPlayer player) {
		return player.getAttachedOrElse(SoulsAttachments.COVENANT, 0);
	}

	public static int points(ServerPlayer player, int covenant) {
		return player.getAttachedOrElse(SoulsAttachments.COVENANT_POINTS, Map.<String, Integer>of())
				.getOrDefault(String.valueOf(covenant), 0);
	}

	/** FUN_140204270: how many of VowParam[covenant]'s thresholds the points reach. */
	public static int rank(ServerPlayer player, int covenant) {
		int[] t = THRESHOLDS.get(covenant);
		if (t == null) {
			return 0;
		}
		int p = points(player, covenant);
		int r = 0;
		for (int v : t) {
			if (p >= v) {
				r++;
			}
		}
		return r;
	}

	/** FUN_140203af0: points only go to the active covenant; a new rank says so (MapEvent 1308, else 1307). */
	public static void addPoints(ServerPlayer player, int covenant, int n) {
		if (active(player) != covenant || n == 0) {
			return;
		}
		int before = rank(player, covenant);
		Map<String, Integer> all = new HashMap<>(player.getAttachedOrElse(SoulsAttachments.COVENANT_POINTS, Map.of()));
		all.put(String.valueOf(covenant), Math.max(0, points(player, covenant) + n));
		player.setAttached(SoulsAttachments.COVENANT_POINTS, Map.copyOf(all));
		boolean up = rank(player, covenant) > before;
		player.sendSystemMessage(Component.translatable(up ? "message.soulsmode.covenant.rank_up"
				: "message.soulsmode.covenant.devotion"));
	}

	/** 130381(id): the active covenant changes (0 = leave); points and ranks stay. */
	public static void setActive(ServerPlayer player, int covenant) {
		player.setAttached(SoulsAttachments.COVENANT, covenant);
	}
}
