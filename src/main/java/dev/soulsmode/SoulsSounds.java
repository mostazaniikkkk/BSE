package dev.soulsmode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * DS2 sound effects of the player's attack animations (docs/research/sounds.md). Data: data/soulsmode/ds2/
 * attack_sounds.json, written by tools/re/sounds.py from c000100_snd.tae and frpg2.fev.
 * <ul>
 * <li>TAE 202 (ChrMorphemeTimeActTrackSoundCtrl FUN_1403278d0, case 0xca -> FUN_1404a5c30): weapon swing
 * {@code c(6000 + 10 x WeaponTypeParam +0x37 + swing)}, type 11 = +110; the weapon is the right hand's for hand 0
 * (chr +0x7b = WeaponTypeParam of weapon -1, FUN_14037e210) and the left hand's for hand 1 (+0x7a, weapon -2); with a
 * two-handed grip both are the held weapon (grip modes 2 / 3 of FUN_14037e210).</li>
 * <li>TAE 201 whose name needs no runtime value (no armour / ground variant, no voice): {@code c<id>}.</li>
 * </ul>
 * Each FMOD event plays all its layers (one Minecraft sound each). What the game's FMOD applies on a start, measured in
 * fmod_event64.dll: event volume x (1 - volumeRandomization x u), a whole number of semitones round(U(-steps, steps))
 * for the whole event, and per layer a volume in [min, max] and a pitch in +-pitchSpread semitones around the pitch
 * baked into its .ogg (event pitch + layer centre).
 */
public final class SoulsSounds {
	/** Master switch of the DS2 sounds. */
	public static final boolean ENABLED = true;
	/**
	 * The user's mix (2026-11-03): DS2's sounds came out a bit louder than Minecraft's, so every DS2 volume is scaled by
	 * this. A design value, not DS2 data.
	 */
	public static final float MIX = 0.7f;

	private static final String RESOURCE = "/data/soulsmode/ds2/attack_sounds.json";

	record Layer(SoundEvent sound, float pitchSpread, float volumeMin, float volumeMax) {
	}

	record Event(float length, float volume, float volumeRandomization, int pitchSteps, List<Layer> layers) {
	}

	/**
	 * One TAE sound event: {@code event} for a fixed 201; {@code id} + the armour sound material of {@code piece} (0..3
	 * head, chest, arms, legs) for a 201 with variant source 2..5; otherwise the swing (202) of {@code hand}.
	 */
	record Cue(float time, String event, int swing, int hand, int id, int piece) {
	}

	private static final Map<String, Event> EVENTS = new HashMap<>();
	private static final Map<Integer, List<Cue>> ANIMATIONS = new HashMap<>();
	private static final Map<String, Integer> SWING_TYPE = new HashMap<>();
	/** ArmorParam +0x48 of each piece (head, chest, arms, legs) of the mod's DS2 armour sets; null = no row. */
	private static final Map<String, Integer[]> ARMOR_SOUND = new HashMap<>();
	private static int swingBase;
	/** Sounds of bullet FFX (attack_sounds.json bulletSounds): key -> event. */
	private static final Map<String, String> BULLETS = new HashMap<>();

	private SoulsSounds() {
	}

	/** Registers the sound events (must run in onInitialize, before the registries freeze). */
	public static void init() {
		JsonObject json;
		try (InputStream in = SoulsSounds.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("missing " + RESOURCE);
			}
			json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new IllegalStateException("cannot read " + RESOURCE, e);
		}
		swingBase = json.get("swingBase").getAsInt();
		json.getAsJsonObject("bulletSounds").entrySet().forEach(e -> BULLETS.put(e.getKey(), e.getValue().getAsString()));
		json.getAsJsonObject("swingType").entrySet().forEach(e -> SWING_TYPE.put(e.getKey(), e.getValue().getAsInt()));
		for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("armorSound").entrySet()) {
			var a = e.getValue().getAsJsonArray();
			Integer[] v = new Integer[4];
			for (int i = 0; i < 4; i++) {
				v[i] = a.get(i).isJsonNull() ? null : a.get(i).getAsInt();
			}
			ARMOR_SOUND.put(e.getKey(), v);
		}
		for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("events").entrySet()) {
			JsonObject ev = e.getValue().getAsJsonObject();
			float range = ev.get("range").getAsFloat();
			List<Layer> layers = new ArrayList<>();
			for (JsonElement l : ev.getAsJsonArray("layers")) {
				JsonObject lo = l.getAsJsonObject();
				ResourceLocation id = SoulsMode.id(lo.get("sound").getAsString());
				SoundEvent sound = BuiltInRegistries.SOUND_EVENT.containsKey(id) ? BuiltInRegistries.SOUND_EVENT.get(id)
						: Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, range));
				var vol = lo.getAsJsonArray("volume");
				layers.add(new Layer(sound, lo.get("pitchSpread").getAsFloat(), vol.get(0).getAsFloat(),
						vol.get(1).getAsFloat()));
			}
			EVENTS.put(e.getKey(), new Event(ev.get("length").getAsFloat(), ev.get("volume").getAsFloat(), ev.get("volumeRandomization").getAsFloat(),
					ev.get("pitchSteps").getAsInt(), List.copyOf(layers)));
		}
		for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("animations").entrySet()) {
			List<Cue> cues = new ArrayList<>();
			for (JsonElement c : e.getValue().getAsJsonArray()) {
				JsonObject co = c.getAsJsonObject();
				float t = co.get("t").getAsFloat();
				cues.add(co.has("event") ? new Cue(t, co.get("event").getAsString(), -1, -1, 0, -1)
						: co.has("piece") ? new Cue(t, null, -1, -1, co.get("id").getAsInt(), co.get("piece").getAsInt())
						: new Cue(t, null, co.get("swing").getAsInt(), co.get("hand").getAsInt(), 0, -1));
			}
			ANIMATIONS.put(Integer.parseInt(e.getKey()), List.copyOf(cues));
		}
		// The death banner's sting (tools/re/you_died.py, sounds.json "menu.<name>"), played by the client.
		try (InputStream yd = SoulsSounds.class.getResourceAsStream("/data/soulsmode/ds2/you_died.json")) {
			if (yd != null) {
				String name = JsonParser.parseReader(new InputStreamReader(yd, StandardCharsets.UTF_8)).getAsJsonObject()
						.get("sound").getAsString();
				ResourceLocation id = SoulsMode.id("menu." + name);
				if (!BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
					Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
				}
			}
		} catch (java.io.IOException e) {
			throw new IllegalStateException("cannot read you_died.json", e);
		}
		SoulsMode.LOGGER.info("[BSE] DS2 sounds: {} events, {} sound events registered, {} animations with cues",
				EVENTS.size(), soundIds().size(), ANIMATIONS.size());
	}

	/** Every sound id registered here (for tests). */
	public static List<ResourceLocation> soundIds() {
		List<ResourceLocation> ids = new ArrayList<>();
		EVENTS.values().forEach(ev -> ev.layers().forEach(l -> ids.add(l.sound().getLocation())));
		return ids;
	}

	/** FUN_1404a5c30: the swing event of a weapon type and a TAE 202 swing id. */
	static String swingEvent(int type, int swing) {
		int id = type == 11 ? swingBase + 110 + swing : swingBase + 10 * type + swing;
		return String.format("c%09d", id);
	}

	/**
	 * The TAE sound events of animation {@code taeId} in (from, to] seconds (from < 0: from the start, time 0
	 * included). {@code rightWeapon} / {@code leftWeapon}: Ds2Damage keys of the weapons swung by hand 0 / 1;
	 * {@code armorSets}: the mod's DS2 armour set worn per piece (head, chest, arms, legs), "" = empty slot, null =
	 * unknown (a datapack piece).
	 */
	public static void attack(ServerPlayer player, int taeId, float from, float to, String rightWeapon,
			String leftWeapon, String[] armorSets) {
		if (!ENABLED) {
			return;
		}
		List<Cue> cues = ANIMATIONS.get(taeId);
		if (cues == null) {
			return;
		}
		for (Cue cue : cues) {
			if (!(cue.time() <= to && (from < 0 ? cue.time() >= 0 : cue.time() > from))) {
				continue;
			}
			String name = cue.event();
			if (cue.piece() >= 0) {
				String set = armorSets[cue.piece()];
				if (set == null) {
					continue; // a datapack armour piece: no DS2 sound material
				}
				// FUN_14037e0f0: an empty slot (no ArmorParam row) gives 1.
				Integer material = set.isEmpty() ? Integer.valueOf(1)
						: ARMOR_SOUND.containsKey(set) ? ARMOR_SOUND.get(set)[cue.piece()] : null;
				if (material == null) {
					continue;
				}
				name = String.format("c%09d", cue.id() + material);
			} else if (name == null) {
				Integer type = SWING_TYPE.get(cue.hand() == 1 ? leftWeapon : rightWeapon);
				if (type == null) {
					continue; // a weapon without DS2 data (datapack): its swing sound is unknown
				}
				name = swingEvent(type, cue.swing());
			}
			play(player, name);
		}
	}

	/** The event of a bullet FFX's sound (e.g. "alluring_skull_impact"), or null. */
	public static String bullet(String key) {
		return BULLETS.get(key);
	}

	/** Length in seconds of the event's (first) wave, 0 if unknown. */
	public static float length(String name) {
		Event ev = name == null ? null : EVENTS.get(name);
		return ev == null ? 0 : ev.length();
	}

	/** Plays a DS2 event at a point (a bullet's FFX), heard by everyone in range. */
	public static void playAt(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 at, String name) {
		Event ev = name == null ? null : EVENTS.get(name);
		if (ev == null) {
			return;
		}
		RandomSource random = level.getRandom();
		float volume = MIX * ev.volume() * (1 - ev.volumeRandomization() * random.nextFloat());
		float steps = Math.round((random.nextFloat() * 2 - 1) * ev.pitchSteps());
		for (Layer layer : ev.layers()) {
			float semitones = steps + (random.nextFloat() * 2 - 1) * layer.pitchSpread();
			float layerVolume = layer.volumeMin() + (layer.volumeMax() - layer.volumeMin()) * random.nextFloat();
			level.playSound(null, at.x, at.y, at.z, layer.sound(), SoundSource.PLAYERS, volume * layerVolume,
					(float) Math.pow(2, semitones / 12.0));
		}
	}

	/** Plays a DS2 event on {@code player}: all its layers, following the player, heard by everyone in range. */
	public static void play(ServerPlayer player, String name) {
		Event ev = EVENTS.get(name);
		if (ev == null) {
			return;
		}
		RandomSource random = player.getRandom();
		float volume = MIX * ev.volume() * (1 - ev.volumeRandomization() * random.nextFloat());
		float steps = Math.round((random.nextFloat() * 2 - 1) * ev.pitchSteps());
		for (Layer layer : ev.layers()) {
			float semitones = steps + (random.nextFloat() * 2 - 1) * layer.pitchSpread();
			float layerVolume = layer.volumeMin() + (layer.volumeMax() - layer.volumeMin()) * random.nextFloat();
			player.level().playSound(null, player, layer.sound(), SoundSource.PLAYERS, volume * layerVolume,
					(float) Math.pow(2, semitones / 12.0));
		}
	}
}
