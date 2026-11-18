package dev.soulsmode.ds2;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-attack DS2 data read from a weapon slot and its animation entry (built-in weapons.json or a datapack weapon),
 * kept beside the {@link Attack} like HitBack / SwingSpeed (docs/research/weapons.md "Contraataque", "Balas de los
 * ataques"; docs/datapacks.md "Armas"):
 * <ul>
 * <li>{@code counter} (animation): TAE 111600 windows (DamageActionCtrl, {@code c0+0xf4 |= 0x20}): a hit taken inside
 * one is multiplied by the attacker's PlayerDamageParam +0x20 x its WeaponTypeParam +0x6c (FUN_140138d50).</li>
 * <li>{@code counterRates} (slot): PlayerDamageParam +0x20 of the slot's PDP 0..2.</li>
 * <li>{@code shots} (animation): TAE 2300 events {time, bullet slot} (FUN_140325880 fires the slot).</li>
 * <li>{@code bullets} (slot): the bullet of each slot 0..2 of the weapon's half of {@code chr+0x230 +0x760}, filled
 * from WeaponAttackMotionParam +0x38..+0x4c when the attack starts (FUN_140392a60); a key of the weapon's
 * {@code bullets} map, null = no bullet.</li>
 * <li>{@code poise} (slot): a datapack's damage rows of the slot's PDP 0..2 in poise.json's {@code playerWeapons}
 * hit format; built-in attacks keep poise.json.</li>
 * <li>{@code superArmor} / {@code noReaction} / {@code poiseFactor} (animation): a datapack animation's TAE 110500 /
 * 110600 / 111900 windows; built-in animations keep poise.json's {@code taeWindows}.</li>
 * <li>{@code weaponBones} (animation): a datapack animation's R_Weapon samples (HitBack format), for the bullet origin
 * and the wall bounce.</li>
 * </ul>
 */
public final class AttackData {
	/** One attack's extra data; empty lists / arrays when absent. */
	public record Data(List<float[]> counter, float[] counterRates, List<float[]> shots, String[] bullets,
			Poise.Hit[] poise, List<float[]> superArmor, List<float[]> noReaction, List<float[]> poiseFactor,
			List<HitBack.Bone> bones) {
	}

	private static final Data NONE = new Data(List.of(), new float[0], List.of(), new String[0], null, List.of(),
			List.of(), List.of(), List.of());
	private static final Map<Attack, Data> DATA = Collections.synchronizedMap(new IdentityHashMap<>());

	private AttackData() {
	}

	static void register(Attack attack, JsonObject slot, JsonObject animation) {
		if (attack == null) {
			return;
		}
		float[] rates = new float[0];
		if (slot.has("counterRates") && slot.get("counterRates").isJsonArray()) {
			JsonArray a = slot.getAsJsonArray("counterRates");
			rates = new float[a.size()];
			for (int i = 0; i < rates.length; i++) {
				rates[i] = a.get(i).isJsonNull() ? Float.NaN : a.get(i).getAsFloat();
			}
		}
		String[] bullets = new String[0];
		if (slot.has("bullets") && slot.get("bullets").isJsonArray()) {
			JsonArray a = slot.getAsJsonArray("bullets");
			bullets = new String[a.size()];
			for (int i = 0; i < bullets.length; i++) {
				bullets[i] = a.get(i).isJsonNull() ? null : a.get(i).getAsString();
			}
		}
		Poise.Hit[] poise = null;
		if (slot.has("poise") && slot.get("poise").isJsonArray()) {
			JsonArray a = slot.getAsJsonArray("poise");
			poise = new Poise.Hit[a.size()];
			for (int i = 0; i < poise.length; i++) {
				poise[i] = a.get(i).isJsonNull() ? null : Poise.hit(a.get(i).getAsJsonObject());
			}
		}
		List<float[]> shots = new ArrayList<>();
		if (animation.has("shots") && animation.get("shots").isJsonArray()) {
			for (JsonElement e : animation.getAsJsonArray("shots")) {
				shots.add(new float[] {e.getAsJsonArray().get(0).getAsFloat(), e.getAsJsonArray().get(1).getAsFloat()});
			}
		}
		// Built-in animations' samples are HitBack's (by TAE id); a datapack's own animation (TAE id 0) brings its own.
		List<HitBack.Bone> bones = attack.taeId() == 0 && animation.has("weaponBones")
				&& animation.get("weaponBones").isJsonArray()
				? HitBack.parseBones(animation.getAsJsonArray("weaponBones")) : List.of();
		Data d = new Data(windows(animation, "counter"), rates, List.copyOf(shots), bullets, poise,
				windows(animation, "superArmor"), windows(animation, "noReaction"), windows(animation, "poiseFactor"),
				bones);
		if (!d.counter().isEmpty() || rates.length > 0 || !shots.isEmpty() || bullets.length > 0 || poise != null
				|| !d.superArmor().isEmpty() || !d.noReaction().isEmpty() || !d.poiseFactor().isEmpty()
				|| !bones.isEmpty()) {
			DATA.put(attack, d);
		}
	}

	private static List<float[]> windows(JsonObject animation, String key) {
		if (!animation.has(key) || !animation.get(key).isJsonArray()) {
			return List.of();
		}
		List<float[]> out = new ArrayList<>();
		for (JsonElement e : animation.getAsJsonArray(key)) {
			out.add(new float[] {e.getAsJsonArray().get(0).getAsFloat(), e.getAsJsonArray().get(1).getAsFloat()});
		}
		return List.copyOf(out);
	}

	public static Data of(Attack attack) {
		Data d = attack == null ? null : DATA.get(attack);
		return d == null ? NONE : d;
	}

	public static boolean inside(List<float[]> windows, float t) {
		for (float[] w : windows) {
			if (t >= w[0] && t < w[1]) {
				return true;
			}
		}
		return false;
	}

	/** Inside a TAE 111600 window of the attack at {@code t}. */
	public static boolean counterWindow(Attack attack, float t) {
		return inside(of(attack).counter(), t);
	}

	/** PlayerDamageParam +0x20 of hit window {@code window}'s PDP slot, NaN when the slot has no value. */
	public static float counterRate(Attack attack, int window) {
		float[] rates = of(attack).counterRates();
		int slot = window < 0 ? -1 : attack.hitSlot(window);
		return slot < 0 || slot >= rates.length ? Float.NaN : rates[slot];
	}
}
