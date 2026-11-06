package dev.soulsmode.ds2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Hollow Soldier's attacks (docs/research/c1020_anims.md, data/soulsmode/ds2/c1020_attacks.json): animation,
 * length, TAE 2200 hit windows, the reach / angle of ai102000's attack subroutine. Only a combo's first step is kept
 * (how a combo advances is not traced).
 */
public final class C1020Attacks {
	public static final String RESOURCE = "/data/soulsmode/ds2/c1020_attacks.json";

	public record Attack(int action, String animation, float duration, float reach, float angle, List<float[]> hits) {
	}

	private static Map<Integer, Attack> byAction;

	private C1020Attacks() {
	}

	/** The attack of an ai102000 action (13..17, 50, 51; a combo's first step), or null. */
	public static synchronized Attack action(int action) {
		if (byAction == null) {
			byAction = new LinkedHashMap<>();
			try (InputStream in = C1020Attacks.class.getResourceAsStream(RESOURCE)) {
				JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				for (JsonElement e : json.getAsJsonArray("attacks")) {
					JsonObject a = e.getAsJsonObject();
					if (a.has("comboStep") && a.get("comboStep").getAsInt() != 0) {
						continue;
					}
					List<float[]> hits = new ArrayList<>();
					for (JsonElement h : a.getAsJsonArray("hits")) {
						JsonObject o = h.getAsJsonObject();
						// {start, end, EnemyDamageParam id} (the id as a float is exact: 9 digits < 2^24 is not
						// guaranteed, so it is kept as the int's bits).
						hits.add(new float[] {o.get("start").getAsFloat(), o.get("end").getAsFloat(),
								Float.intBitsToFloat(o.get("enemyDamageParam").getAsInt())});
					}
					byAction.put(a.get("action").getAsInt(), new Attack(a.get("action").getAsInt(),
							a.get("animation").getAsString(), a.get("duration").getAsFloat(), a.get("reach").getAsFloat(),
							a.get("angle").getAsFloat(), List.copyOf(hits)));
				}
			} catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}
		return byAction.get(action);
	}
}
