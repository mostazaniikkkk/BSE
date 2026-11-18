package dev.soulsmode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The DS2 sounds (SoulsSounds): every sound the data can play has a sounds.json entry, and every file of those entries
 * exists and is a mono Ogg Vorbis stream (what Minecraft needs for a positional sound).
 */
class SoundsDataTest {
	private static JsonObject json(String path) throws IOException {
		try (InputStream in = SoundsDataTest.class.getResourceAsStream(path)) {
			assertNotNull(in, path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	@Test
	void everySoundResolves() throws IOException {
		JsonObject data = json("/data/soulsmode/ds2/attack_sounds.json");
		JsonObject sounds = json("/assets/soulsmode/sounds.json");
		JsonObject events = data.getAsJsonObject("events");
		Set<String> referenced = new HashSet<>();
		// Every event a TAE cue can name: its 201 events and the swing of every weapon type for every swing id.
		Set<Integer> swings = new HashSet<>();
		for (var anim : data.getAsJsonObject("animations").entrySet()) {
			for (JsonElement c : anim.getValue().getAsJsonArray()) {
				JsonObject cue = c.getAsJsonObject();
				if (cue.has("event")) {
					assertTrue(events.has(cue.get("event").getAsString()), anim.getKey() + " " + cue);
				} else if (cue.has("piece")) {
					// An armour cue: some worn set must give an event (the others are silent in DS2 too).
					int id = cue.get("id").getAsInt();
					int piece = cue.get("piece").getAsInt();
					boolean any = false;
					for (var set : data.getAsJsonObject("armorSound").entrySet()) {
						JsonElement v = set.getValue().getAsJsonArray().get(piece);
						any |= !v.isJsonNull() && events.has(String.format("c%09d", id + v.getAsInt()));
					}
					assertTrue(any, anim.getKey() + " " + cue);
				} else {
					swings.add(cue.get("swing").getAsInt());
				}
			}
		}
		int base = data.get("swingBase").getAsInt();
		Set<String> swingEvents = new HashSet<>();
		for (var w : data.getAsJsonObject("swingType").entrySet()) {
			int type = w.getValue().getAsInt();
			for (int s : swings) {
				String name = String.format("c%09d", type == 11 ? base + 110 + s : base + 10 * type + s);
				swingEvents.add(name);
			}
		}
		assertFalse(swingEvents.stream().noneMatch(events::has), "no swing event at all");
		for (var ev : events.entrySet()) {
			JsonObject e = ev.getValue().getAsJsonObject();
			assertFalse(e.getAsJsonArray("layers").isEmpty(), ev.getKey());
			for (JsonElement l : e.getAsJsonArray("layers")) {
				String id = l.getAsJsonObject().get("sound").getAsString();
				assertTrue(sounds.has(id), "sounds.json lacks " + id);
				referenced.add(id);
			}
		}
		// The death banner's sting (you_died.json, tools/re/you_died.py): a 2D menu sound, stereo like DS2's.
		try (InputStream in = SoundsDataTest.class.getResourceAsStream("/data/soulsmode/ds2/you_died.json")) {
			assertNotNull(in, "missing you_died.json");
			String menu = "menu." + JsonParser.parseReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8))
					.getAsJsonObject().get("sound").getAsString();
			assertTrue(sounds.has(menu), "sounds.json lacks " + menu);
			referenced.add(menu);
		}
		for (var entry : sounds.entrySet()) {
			assertTrue(referenced.contains(entry.getKey()), "unused sounds.json entry " + entry.getKey());
			for (JsonElement s : entry.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
				String name = s.getAsJsonObject().get("name").getAsString();
				assertTrue(name.startsWith("soulsmode:"), name);
				assertTrue(name.matches("[a-z0-9_.:/-]+"), name);
				checkOgg("/assets/soulsmode/sounds/" + name.substring("soulsmode:".length()) + ".ogg",
						!entry.getKey().startsWith("menu."));
			}
		}
	}

	/** First Ogg page: "OggS", then the Vorbis identification header (0x01 "vorbis", version 0, channels = 1). */
	private static void checkOgg(String path, boolean mono) throws IOException {
		try (InputStream in = SoundsDataTest.class.getResourceAsStream(path)) {
			assertNotNull(in, "missing " + path);
			byte[] head = in.readNBytes(64);
			assertEquals("OggS", new String(head, 0, 4, StandardCharsets.ISO_8859_1), path);
			int segments = head[26] & 0xff;
			int p = 27 + segments;
			assertEquals(1, head[p], path);
			assertEquals("vorbis", new String(head, p + 1, 6, StandardCharsets.ISO_8859_1), path);
			if (mono) {
				assertEquals(1, head[p + 11], path + ": not mono");
			}
		}
	}
}
