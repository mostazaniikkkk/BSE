package dev.soulsmode.ds2;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Starting classes: index -> PlayerStatusParam row (FUN_140203e80), row -> attribute slots
 * (FUN_1401fbc00) and name text 11010000 + index (common.fmg). See docs/research/stats.md.
 */
public final class StartingClasses {
	public static final String RESOURCE = "/data/soulsmode/ds2/starting_classes.json";

	/** {@code paramSoulLevel} is the SL stored in the param row (+0x04), kept to cross-check the formula. */
	public record StartingClass(int index, String name, int paramSoulLevel, Attributes attributes) {
		public String id() {
			return name.toLowerCase(Locale.ROOT);
		}
	}

	private final List<StartingClass> classes;

	private StartingClasses(List<StartingClass> classes) {
		this.classes = List.copyOf(classes);
	}

	public static StartingClasses load() {
		try (InputStream in = StartingClasses.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("missing " + RESOURCE);
			}
			JsonArray json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonArray("classes");
			List<StartingClass> classes = new ArrayList<>();
			for (JsonElement e : json) {
				JsonObject c = e.getAsJsonObject();
				JsonArray a = c.getAsJsonArray("attributes");
				short[] slots = new short[a.size()];
				for (int i = 0; i < slots.length; i++) {
					slots[i] = a.get(i).getAsShort();
				}
				classes.add(new StartingClass(c.get("index").getAsInt(), c.get("name").getAsString(),
						c.get("soulLevel").getAsInt(), new Attributes(slots)));
			}
			return new StartingClasses(classes);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public List<StartingClass> all() {
		return classes;
	}

	public Optional<StartingClass> byId(String id) {
		return classes.stream().filter(c -> c.id().equals(id)).findFirst();
	}

	public Optional<StartingClass> byIndex(int index) {
		return classes.stream().filter(c -> c.index() == index).findFirst();
	}
}
