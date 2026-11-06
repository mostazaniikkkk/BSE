package dev.soulsmode.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.ds2.Attack;
import dev.soulsmode.ds2.Ds2Damage;
import dev.soulsmode.ds2.Spells;
import dev.soulsmode.ds2.Weapons;
import dev.soulsmode.item.SoulsComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Content added by third parties through datapacks (docs/datapacks.md): weapons, armour pieces, consumables and weight
 * rules under {@code data/<namespace>/soulsmode/<kind>/<name>.json}, id {@code <namespace>:<name>}. The mod's own
 * content stays in the jar; this is added on top and, where both apply, wins.
 * <p>
 * The server reads the files ({@link ContentReloader}) and sends them as they are to every client, and both sides build
 * the same definitions with {@link #apply}: players only need the mod, the server's datapack and resource pack bring
 * the rest. A bad file is logged and skipped; it never stops the others.
 */
public final class SoulsContent {
	public enum Kind {
		WEAPON("weapon"), ARMOR("armor"), CONSUMABLE("consumable"), WEIGHT("weight"), SPELL("spell"), ENEMY("enemy");

		public final String dir;

		Kind(String dir) {
			this.dir = dir;
		}
	}

	/** An armour piece: body slot (0 head, 1 chest, 2 arms, 3 legs), its DS2 piece data and its weight. */
	public record ArmorDef(int slot, Ds2Damage.Piece piece, float weight) {
	}

	/** How a consumable is used: its item-use animation and when its effects apply. */
	public record Use(Attack attack, float effectAt) {
	}

	public record ConsumableDef(Use use, boolean consume, List<JsonObject> effects, Float weight) {
	}

	/** Which stacks a definition applies to without a component: items and item tags. */
	private static final class Bindings {
		final Map<Item, ResourceLocation> items = new HashMap<>();
		final List<Map.Entry<TagKey<Item>, ResourceLocation>> tags = new ArrayList<>();

		void add(JsonObject json, ResourceLocation id) {
			if (!json.has("items")) {
				return;
			}
			for (JsonElement e : json.getAsJsonArray("items")) {
				String s = e.getAsString();
				if (s.startsWith("#")) {
					tags.add(Map.entry(TagKey.create(Registries.ITEM, ResourceLocation.parse(s.substring(1))), id));
				} else {
					ResourceLocation itemId = ResourceLocation.parse(s);
					Item item = BuiltInRegistries.ITEM.getOptional(itemId)
							.orElseThrow(() -> new IllegalArgumentException("unknown item " + itemId));
					items.put(item, id);
				}
			}
		}

		ResourceLocation find(ItemStack stack) {
			ResourceLocation id = items.get(stack.getItem());
			if (id != null) {
				return id;
			}
			for (Map.Entry<TagKey<Item>, ResourceLocation> tag : tags) {
				if (stack.is(tag.getKey())) {
					return tag.getValue();
				}
			}
			return null;
		}
	}

	private record WeightRule(Bindings bindings, float weight) {
	}

	private record State(Map<Kind, Map<ResourceLocation, JsonObject>> sources, Map<ResourceLocation, Weapons.Def> weapons,
			Bindings weaponBindings, Map<ResourceLocation, ArmorDef> armor, Bindings armorBindings,
			Map<ResourceLocation, ConsumableDef> consumables, Bindings consumableBindings, List<WeightRule> weights,
			Map<ResourceLocation, Spells.Spell> spells, Map<String, Spells.CastAnimation> castAnimations) {
	}

	private static volatile State state = empty();
	/** Datapack enemies (dev.soulsmode.entity.SoulsEnemy). */
	private static volatile Map<ResourceLocation, dev.soulsmode.ds2.Enemies.Def> enemies = Map.of();

	public static dev.soulsmode.ds2.Enemies.Def enemy(ResourceLocation id) {
		return id == null ? null : enemies.get(id);
	}

	public static java.util.Set<ResourceLocation> enemyIds() {
		return enemies.keySet();
	}

	private SoulsContent() {
	}

	private static State empty() {
		Map<Kind, Map<ResourceLocation, JsonObject>> sources = new EnumMap<>(Kind.class);
		for (Kind kind : Kind.values()) {
			sources.put(kind, Map.of());
		}
		return new State(sources, Map.of(), new Bindings(), Map.of(), new Bindings(), Map.of(), new Bindings(), List.of(),
				Map.of(), Map.of());
	}

	/** The files as last applied, to send to clients. */
	public static Map<Kind, Map<ResourceLocation, JsonObject>> sources() {
		return state.sources();
	}

	/** Drops all datapack content (a client leaving a server). */
	public static synchronized void clear() {
		state = empty();
		enemies = Map.of();
		Ds2Damage.setPack(Map.of());
	}

	/** Builds every definition from the files; the previous content is replaced as a whole. */
	public static synchronized void apply(Map<Kind, Map<ResourceLocation, JsonObject>> sources) {
		Map<ResourceLocation, JsonObject> weaponFiles = sources.getOrDefault(Kind.WEAPON, Map.of());
		// Animation data: the mod's DS2 table plus every pack's "animations" (keys: TAE ids or animation ids).
		JsonObject animations = new JsonObject();
		Weapons.raw().getAsJsonObject("animations").entrySet().forEach(e -> animations.add(e.getKey(), e.getValue()));
		for (Map.Entry<ResourceLocation, JsonObject> e : weaponFiles.entrySet()) {
			if (e.getValue().has("animations")) {
				e.getValue().getAsJsonObject("animations").entrySet().forEach(a -> animations.add(a.getKey(), a.getValue()));
			}
		}
		Map<ResourceLocation, Weapons.Def> weapons = new LinkedHashMap<>();
		Map<String, Ds2Damage.WeaponData> damage = new HashMap<>();
		Bindings weaponBindings = new Bindings();
		for (Map.Entry<ResourceLocation, JsonObject> e : weaponFiles.entrySet()) {
			ResourceLocation id = e.getKey();
			try {
				Weapons.Def def = Weapons.parse(resolveWeapon(id, weaponFiles, new HashSet<>()), animations);
				weaponBindings.add(e.getValue(), id);
				weapons.put(id, def);
				damage.put(id.toString(), def.attack());
			} catch (RuntimeException ex) {
				error(Kind.WEAPON, id, ex);
			}
		}
		Map<ResourceLocation, ArmorDef> armor = new LinkedHashMap<>();
		Bindings armorBindings = new Bindings();
		sources.getOrDefault(Kind.ARMOR, Map.of()).forEach((id, json) -> {
			try {
				armor.put(id, armor(json));
				armorBindings.add(json, id);
			} catch (RuntimeException ex) {
				error(Kind.ARMOR, id, ex);
			}
		});
		Map<ResourceLocation, ConsumableDef> consumables = new LinkedHashMap<>();
		Bindings consumableBindings = new Bindings();
		sources.getOrDefault(Kind.CONSUMABLE, Map.of()).forEach((id, json) -> {
			try {
				consumables.put(id, consumable(json));
				consumableBindings.add(json, id);
			} catch (RuntimeException ex) {
				error(Kind.CONSUMABLE, id, ex);
			}
		});
		List<WeightRule> weights = new ArrayList<>();
		sources.getOrDefault(Kind.WEIGHT, Map.of()).forEach((id, json) -> {
			try {
				Bindings b = new Bindings();
				b.add(json, id);
				weights.add(new WeightRule(b, json.get("weight").getAsFloat()));
			} catch (RuntimeException ex) {
				error(Kind.WEIGHT, id, ex);
			}
		});
		// Spells (SpellParam rows, docs/datapacks.md): cast animations not in the mod's DS2 table come with the spell file.
		Map<ResourceLocation, Spells.Spell> spells = new LinkedHashMap<>();
		Map<String, Spells.CastAnimation> castAnimations = new HashMap<>();
		sources.getOrDefault(Kind.SPELL, Map.of()).forEach((id, json) -> {
			try {
				Map<String, Spells.CastAnimation> own = new HashMap<>();
				if (json.has("castAnimations")) {
					json.getAsJsonObject("castAnimations").entrySet().forEach(e ->
							own.put(e.getKey(), Spells.castAnimation(e.getKey(), e.getValue().getAsJsonObject())));
				}
				Spells.Spell spell = Spells.parse(json);
				for (String key : List.of(spell.prepare(), spell.cast())) {
					if (!own.containsKey(key) && !castAnimations.containsKey(key) && Spells.builtInCastAnimation(key) == null) {
						throw new IllegalArgumentException("no cast animation data for " + key);
					}
				}
				castAnimations.putAll(own);
				spells.put(id, spell);
			} catch (RuntimeException ex) {
				error(Kind.SPELL, id, ex);
			}
		});
		Map<ResourceLocation, dev.soulsmode.ds2.Enemies.Def> enemyDefs = new LinkedHashMap<>();
		sources.getOrDefault(Kind.ENEMY, Map.of()).forEach((id, json) -> {
			try {
				enemyDefs.put(id, dev.soulsmode.ds2.Enemies.parse(json));
			} catch (RuntimeException ex) {
				error(Kind.ENEMY, id, ex);
			}
		});
		enemies = Map.copyOf(enemyDefs);
		Map<Kind, Map<ResourceLocation, JsonObject>> copy = new EnumMap<>(Kind.class);
		for (Kind kind : Kind.values()) {
			copy.put(kind, Map.copyOf(sources.getOrDefault(kind, Map.of())));
		}
		Ds2Damage.setPack(damage);
		state = new State(copy, Map.copyOf(weapons), weaponBindings, Map.copyOf(armor), armorBindings,
				Map.copyOf(consumables), consumableBindings, List.copyOf(weights), Map.copyOf(spells),
				Map.copyOf(castAnimations));
		SoulsMode.LOGGER.info("[BSE] datapack content: {} weapons, {} armour pieces, {} consumables, {} weight rules, {} spells, {} enemies",
				weapons.size(), armor.size(), consumables.size(), weights.size(), spells.size(), enemyDefs.size());
	}

	private static void error(Kind kind, ResourceLocation id, RuntimeException ex) {
		SoulsMode.LOGGER.error("[BSE] skipped {} {}: {}", kind.dir, id, ex.toString());
	}

	/**
	 * A weapon file with its {@code extends} chain applied: the parent (another datapack weapon, or a built-in one as
	 * {@code soulsmode:<name>}) with this file's fields laid over it, objects merged key by key.
	 */
	private static JsonObject resolveWeapon(ResourceLocation id, Map<ResourceLocation, JsonObject> files,
			Set<ResourceLocation> visiting) {
		if (!visiting.add(id)) {
			throw new IllegalArgumentException("extends cycle at " + id);
		}
		JsonObject file = files.get(id);
		JsonObject out;
		if (file.has("extends")) {
			ResourceLocation parent = ResourceLocation.parse(file.get("extends").getAsString());
			JsonObject builtIn = SoulsMode.MOD_ID.equals(parent.getNamespace())
					? Weapons.raw().getAsJsonObject("weapons").getAsJsonObject(parent.getPath()) : null;
			if (files.containsKey(parent)) {
				out = resolveWeapon(parent, files, visiting);
			} else if (builtIn != null) {
				out = builtIn.deepCopy();
			} else {
				throw new IllegalArgumentException("unknown weapon to extend: " + parent);
			}
		} else {
			out = new JsonObject();
		}
		for (Map.Entry<String, JsonElement> e : file.entrySet()) {
			if (!Set.of("extends", "items", "animations").contains(e.getKey())) {
				merge(out, e.getKey(), e.getValue());
			}
		}
		return out;
	}

	private static void merge(JsonObject into, String key, JsonElement value) {
		JsonElement old = into.get(key);
		if (old != null && old.isJsonObject() && value.isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : value.getAsJsonObject().entrySet()) {
				merge(old.getAsJsonObject(), e.getKey(), e.getValue());
			}
		} else {
			into.add(key, value.deepCopy());
		}
	}

	private static final List<String> SLOTS = List.of("head", "chest", "arms", "legs");

	private static ArmorDef armor(JsonObject json) {
		int slot = SLOTS.indexOf(json.get("slot").getAsString());
		if (slot < 0) {
			throw new IllegalArgumentException("slot must be one of " + SLOTS);
		}
		float[] defence = floats(json.getAsJsonArray("defence"), 4);
		float[] absorption = floats(json.getAsJsonArray("absorption"), 4);
		return new ArmorDef(slot, new Ds2Damage.Piece(json.get("factor").getAsFloat(), defence, absorption),
				json.get("weight").getAsFloat());
	}

	private static final Set<String> EFFECTS = Set.of("restore_humanity", "weapon_buff", "mob_effect", "function");

	private static ConsumableDef consumable(JsonObject json) {
		JsonElement useJson = json.get("use");
		Use use;
		if (useJson.isJsonPrimitive()) {
			Weapons.Effigy builtIn = switch (useJson.getAsString()) {
				case "effigy" -> Weapons.effigy();
				case "resin" -> Weapons.resin();
				default -> throw new IllegalArgumentException("unknown use animation " + useJson.getAsString());
			};
			use = new Use(builtIn.use(), builtIn.effectAt());
		} else {
			JsonObject u = useJson.getAsJsonObject();
			float duration = u.get("duration").getAsFloat();
			float[] avoid = floats(u.getAsJsonArray("avoidLock"), 2);
			float attackLock = u.get("attackLock").getAsFloat();
			float moveLock = u.has("moveLock") ? u.get("moveLock").getAsFloat() : 0;
			Attack attack = new Attack(Weapons.animationName(u.get("animation").getAsString()), 0, duration, List.of(),
					avoid[0], avoid[1], moveLock, attackLock, attackLock, attackLock, attackLock, 0, 0, false,
					u.has("leftAuthored") && u.get("leftAuthored").getAsBoolean(), List.of(), List.of(), 0, List.of(),
					List.of());
			use = new Use(attack, u.get("effectAt").getAsFloat());
		}
		List<JsonObject> effects = new ArrayList<>();
		for (JsonElement e : json.getAsJsonArray("effects")) {
			JsonObject effect = e.getAsJsonObject();
			String type = effect.get("type").getAsString();
			if (!EFFECTS.contains(type)) {
				throw new IllegalArgumentException("unknown effect type " + type + ", expected one of " + EFFECTS);
			}
			effects.add(effect);
		}
		return new ConsumableDef(use, !json.has("consume") || json.get("consume").getAsBoolean(), List.copyOf(effects),
				json.has("weight") ? json.get("weight").getAsFloat() : null);
	}

	private static float[] floats(JsonArray a, int size) {
		if (a.size() != size) {
			throw new IllegalArgumentException("expected " + size + " numbers, got " + a.size());
		}
		float[] out = new float[size];
		for (int i = 0; i < size; i++) {
			out[i] = a.get(i).getAsFloat();
		}
		return out;
	}

	// Lookups.

	public static Weapons.Def weapon(ResourceLocation id) {
		return id == null ? null : state.weapons().get(id);
	}

	public static Set<ResourceLocation> weaponIds() {
		return state.weapons().keySet();
	}

	/** The datapack weapon a stack is bound to by its item or a tag (the component is read by SoulsWeapons). */
	public static ResourceLocation boundWeapon(ItemStack stack) {
		ResourceLocation id = state.weaponBindings().find(stack);
		return id != null && state.weapons().containsKey(id) ? id : null;
	}

	/** The datapack armour piece a stack counts as, by component or binding; null if none. */
	public static ArmorDef armor(ItemStack stack) {
		State s = state;
		ResourceLocation id = stack.get(SoulsComponents.ARMOR);
		ArmorDef def = id == null ? null : s.armor().get(id);
		if (def != null) {
			return def;
		}
		id = s.armorBindings().find(stack);
		return id == null ? null : s.armor().get(id);
	}

	/** The datapack consumable a stack is used as, by component or binding; null if none. */
	public static ResourceLocation consumableId(ItemStack stack) {
		State s = state;
		ResourceLocation id = stack.get(SoulsComponents.CONSUMABLE);
		if (id != null && s.consumables().containsKey(id)) {
			return id;
		}
		id = s.consumableBindings().find(stack);
		return id != null && s.consumables().containsKey(id) ? id : null;
	}

	public static ConsumableDef consumable(ResourceLocation id) {
		return id == null ? null : state.consumables().get(id);
	}

	/**
	 * The particle a spell's bullets leave (the spell file's {@code particle}, a simple particle type id): DS2's bullet
	 * SFX can't be ported, so how a bullet looks is the pack's choice; none when absent.
	 */
	public static net.minecraft.core.particles.ParticleOptions spellParticle(ResourceLocation spell) {
		JsonObject json = spell == null ? null : state.sources().get(Kind.SPELL).get(spell);
		if (json == null || !json.has("particle")) {
			return null;
		}
		var type = BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse(json.get("particle").getAsString()));
		return type instanceof net.minecraft.core.particles.SimpleParticleType simple ? simple : null;
	}

	public static Spells.Spell spell(ResourceLocation id) {
		return id == null ? null : state.spells().get(id);
	}

	/** A spell's cast animation: one its datapack file brought, else the mod's DS2 table. */
	public static Spells.CastAnimation castAnimation(String key) {
		Spells.CastAnimation own = state.castAnimations().get(key);
		return own != null ? own : Spells.builtInCastAnimation(key);
	}

	/** The spell a stack holds (its {@code soulsmode:spell} component), if a datapack defines it. */
	public static ResourceLocation spellId(ItemStack stack) {
		ResourceLocation id = stack.get(SoulsComponents.SPELL);
		return id != null && state.spells().containsKey(id) ? id : null;
	}

	/**
	 * A stack's weight from datapack content, or null to use the mod's own: armour piece, consumable, a weight rule
	 * (first match), then the datapack weapon it attacks with.
	 */
	public static Float weight(ItemStack stack, ResourceLocation weapon) {
		State s = state;
		ArmorDef armor = armor(stack);
		if (armor != null) {
			return armor.weight();
		}
		ConsumableDef consumable = consumable(consumableId(stack));
		if (consumable != null && consumable.weight() != null) {
			return consumable.weight();
		}
		for (WeightRule rule : s.weights()) {
			if (rule.bindings().find(stack) != null) {
				return rule.weight();
			}
		}
		Weapons.Def def = weapon == null ? null : s.weapons().get(weapon);
		return def != null ? def.weight() : null;
	}
}
