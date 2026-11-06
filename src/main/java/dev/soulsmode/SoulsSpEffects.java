package dev.soulsmode;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.soulsmode.content.SoulsContent;
import dev.soulsmode.ds2.Attributes;
import dev.soulsmode.ds2.Bullets;
import dev.soulsmode.ds2.Spells;
import dev.soulsmode.entity.SoulsBullet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The SpEffects of spells (docs/research/speffects.md), the instructions the mod runs:
 * <ul>
 * <li>{@code statMultiplier} (100040[00], FUN_14023cbd0): clamp(f_lo + (stat - lo) / (hi - lo) x (f_hi - f_lo)) of the
 * caster's effective stat; key 1 scales heal amounts, key 0 durations (only with {@code durationScales},
 * 100040[06]);</li>
 * <li>{@code statusSelectBullet} (100120[06], FUN_140216ad0): fires bullet k, the last threshold the caster's stat
 * reaches, from the caster's dummy poly 9 (bone Master: the feet);</li>
 * <li>{@code heal} (101050[00] through MustSub event 850): N HP (x the key-1 multiplier, truncated to u16) over t s at
 * N / t per second, instant under 0.01 s; with the flag, it stops when HP is full. Every application is a new heal
 * (HealSub always adds an entry).</li>
 * </ul>
 * HP is DS2's, converted with SoulsStats.DS2_HP_PER_HEALTH; the fraction is carried to the next tick
 * (FUN_14014b810).
 * <p>
 * Buffs (speffects.md s.11), on players: {@code split} (100080[01]) and {@code attack} (100090[02]/[03]) feed the
 * attack rating of a hand, {@code defence} (100090[04]/[05]) the player defence and absorption, {@code statRate} index
 * 26 the damage received (A+0x78), {@code statAdd} index 1 the equip load weight, {@code movementRate} the speed. Each
 * lasts its seconds x the key-0 multiplier when the SpEffect has {@code durationScales}. The damage row's slot
 * (PDP +0x4d) groups them: mode 2 replaces what the slot held; slot 26 is the left weapon, 27 the right one, and
 * changing a hand's weapon clears its slot. Not ported: indices 19, 21, 33 (readers not identified).
 */
public final class SoulsSpEffects {
	/** HealSub entry (FUN_1402313c0): total, rate, what was given, time left, stop when full. */
	private static final class Heal {
		final float total;
		final float rate;
		float given;
		float left;
		final boolean stopWhenFull;

		Heal(float total, float seconds, boolean stopWhenFull) {
			this.total = total;
			this.rate = seconds < 0.01f ? Float.POSITIVE_INFINITY : total / seconds;
			this.left = seconds;
			this.stopWhenFull = stopWhenFull;
		}
	}

	private static final class Target {
		final LivingEntity entity;
		final List<Heal> heals = new ArrayList<>();
		/** grupo+0x20: healing not yet applied (its fraction is kept). */
		float pool;

		Target(LivingEntity entity) {
			this.entity = entity;
		}
	}

	private static final Map<Integer, Target> TARGETS = new HashMap<>();

	/** One timed buff value. */
	private record Buff(String type, int key, float value, long until) {
	}

	/** Buffs of a slot, with the hand's item when the slot is 26 / 27. */
	private static final class Slot {
		final List<Buff> buffs = new ArrayList<>();
		net.minecraft.world.item.Item handItem;
		long serial;
	}

	private static final Map<java.util.UUID, Map<Integer, Slot>> BUFFS = new HashMap<>();
	public static final int LEFT_WEAPON_SLOT = 26;
	public static final int RIGHT_WEAPON_SLOT = 27;
	private static final ResourceLocation SPEED = SoulsMode.id("spell_speed");
	/** Each application gets a new serial (FUN_1402277b0); mode 2 clears the slot once per application. */
	private static long serials;

	private SoulsSpEffects() {
	}

	public static void apply(ServerPlayer owner, LivingEntity victim, ResourceLocation spellId, int effect,
			Map<String, Bullets.Def> bullets, float[] attack, String catalystKey, int slot, int mode) {
		long serial = ++serials;
		Spells.Spell spell = SoulsContent.spell(spellId);
		List<JsonObject> instructions = spell == null ? null : spell.spEffects().get(effect);
		if (instructions == null) {
			return;
		}
		Attributes stats = SoulsPlayer.data(owner).map(SoulsData::toAttributes).orElse(null);
		float amountRate = 1;
		float timeRate = 1;
		boolean timeScales = false;
		for (JsonObject i : instructions) {
			switch (i.get("type").getAsString()) {
				case "statMultiplier" -> {
					float m = multiplier(stats, i);
					if (i.get("key").getAsInt() == 1) {
						amountRate *= m;
					} else {
						timeRate *= m;
					}
				}
				case "durationScales" -> timeScales = true;
				default -> {
				}
			}
		}
		for (JsonObject i : instructions) {
			switch (i.get("type").getAsString()) {
				case "heal" -> {
					int amount = (int) (i.get("amount").getAsInt() * amountRate) & 0xffff;
					float seconds = i.get("duration").getAsFloat() * (timeScales ? timeRate : 1);
					if (amount > 0 && victim.isAlive()) {
						TARGETS.computeIfAbsent(victim.getId(), id -> new Target(victim)).heals
								.add(new Heal(amount, seconds, i.get("stopWhenFull").getAsBoolean()));
					}
				}
				case "split", "attack", "defence", "movementRate", "statRate", "statAdd" -> {
					if (victim instanceof ServerPlayer player) {
						float seconds = i.get("duration").getAsFloat() * (timeScales ? timeRate : 1);
						String type = i.get("type").getAsString();
						int key = type.startsWith("stat") ? i.get("index").getAsInt()
								: type.equals("movementRate") ? 0 : i.get("damageType").getAsInt();
						float value = type.equals("movementRate") ? i.get("rate").getAsFloat() : i.get("value").getAsFloat();
						buffSlot(player, slot, effect, mode, serial).buffs.add(new Buff(type, key, value,
								player.serverLevel().getGameTime() + Math.round(seconds * 20)));
					}
				}
				case "statusSelectBullet" -> {
					JsonArray thresholds = i.getAsJsonArray("thresholds");
					int s = stat(stats, i.get("stat").getAsInt());
					int k = 0;
					for (int t = 0; t < thresholds.size(); t++) {
						if (thresholds.get(t).getAsInt() <= s) {
							k = t;
						}
					}
					JsonArray variants = i.getAsJsonArray("bullets");
					Bullets.Def def = k < variants.size() ? bullets.get(variants.get(k).getAsString()) : null;
					if (def != null) {
						double yaw = Math.toRadians(owner.getYRot());
						Vec3 facing = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
						Vec3 feet = owner.position();
						SoulsBullet.emit(owner.serverLevel(), owner, def, bullets, () -> new SoulsBullet.Frame(feet, facing),
								attack, catalystKey, null, -1, spellId);
					}
				}
				default -> {
				}
			}
		}
	}

	/**
	 * FUN_14022fe50: a slot in 0x12..0xfd groups by slot, any other by SpEffect id; mode 2 clears what was there before
	 * this application.
	 */
	private static Slot buffSlot(ServerPlayer player, int slot, int effect, int mode, long serial) {
		int key = slot >= 0x12 && slot <= 0xfd ? slot : -effect;
		Map<Integer, Slot> slots = BUFFS.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
		Slot s = slots.get(key);
		if (s == null || mode == 2 && s.serial != serial) {
			s = new Slot();
			slots.put(key, s);
			if (key == RIGHT_WEAPON_SLOT) {
				// Resins use the same slot (speffects.md s.11.6): the newer one replaces the other.
				SoulsResin.forget(player);
			}
		}
		s.serial = serial;
		if (key == LEFT_WEAPON_SLOT) {
			s.handItem = player.getOffhandItem().getItem();
		} else if (key == RIGHT_WEAPON_SLOT) {
			s.handItem = player.getMainHandItem().getItem();
		}
		return s;
	}

	/** A resin took slot 27: the spell buffs there end. */
	public static void clearSlot(ServerPlayer player, int slot) {
		Map<Integer, Slot> slots = BUFFS.get(player.getUUID());
		if (slots != null) {
			slots.remove(slot);
		}
	}

	private static List<Buff> active(ServerPlayer player, java.util.function.IntPredicate slots) {
		List<Buff> out = new ArrayList<>();
		Map<Integer, Slot> map = BUFFS.get(player.getUUID());
		if (map == null) {
			return out;
		}
		long now = player.serverLevel().getGameTime();
		map.forEach((k, v) -> {
			if (slots.test(k)) {
				v.buffs.stream().filter(b -> b.until() > now).forEach(out::add);
			}
		});
		return out;
	}

	/**
	 * A hand's weapon buffs {attack add per type, split add per type} (FUN_14038f3b0 / FUN_14038f440): the effects of
	 * that hand's slot and of the slots that are not a hand's.
	 */
	public static float[][] weaponBuffs(ServerPlayer player, boolean leftHand) {
		int other = leftHand ? RIGHT_WEAPON_SLOT : LEFT_WEAPON_SLOT;
		float[][] out = new float[2][dev.soulsmode.ds2.Ds2Damage.TYPES];
		for (Buff b : active(player, k -> k != other)) {
			if (b.key() < 0 || b.key() >= out[0].length) {
				continue;
			}
			if (b.type().equals("attack")) {
				out[0][b.key()] += b.value();
			} else if (b.type().equals("split")) {
				out[1][b.key()] += 0.01f * b.value();
			}
		}
		return out;
	}

	/** Defence buffs (FUN_14031fe20): {flat physical, absorption magic, lightning, fire, dark}. */
	public static float[] defenceBuffs(ServerPlayer player) {
		float[] out = new float[5];
		for (Buff b : active(player, k -> true)) {
			if (b.type().equals("defence") && b.key() >= 0 && b.key() <= 4) {
				out[b.key()] += b.key() == 0 ? b.value() : 0.01f * b.value();
			}
		}
		return out;
	}

	/** b8+0x3bc index 26 (A+0x78 in FUN_1401373b0): the damage the player receives is multiplied by it. */
	public static float damageTakenRate(ServerPlayer player) {
		float r = 1;
		for (Buff b : active(player, k -> true)) {
			if (b.type().equals("statRate") && b.key() == 26) {
				r *= b.value();
			}
		}
		return r;
	}

	/** b8+0x306 (1000[00] index 1): added to the equipped weight (FUN_14037e430). */
	public static float extraWeight(ServerPlayer player) {
		float w = 0;
		for (Buff b : active(player, k -> true)) {
			if (b.type().equals("statAdd") && b.key() == 1) {
				w += b.value();
			}
		}
		return w;
	}

	/** Buffs: expiry, weapon change (FUN_14034b6b0 clears that hand's slot) and the speed rate (b8+0x11c[6]). */
	private static void tickBuffs(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Map<Integer, Slot> slots = BUFFS.get(player.getUUID());
			float speed = 1;
			if (slots != null) {
				long now = player.serverLevel().getGameTime();
				slots.entrySet().removeIf(e -> {
					Slot s = e.getValue();
					s.buffs.removeIf(b -> b.until() <= now);
					if (e.getKey() == LEFT_WEAPON_SLOT && player.getOffhandItem().getItem() != s.handItem
							|| e.getKey() == RIGHT_WEAPON_SLOT && player.getMainHandItem().getItem() != s.handItem) {
						return true;
					}
					return s.buffs.isEmpty();
				});
				for (Slot s : slots.values()) {
					for (Buff b : s.buffs) {
						if (b.type().equals("movementRate")) {
							speed *= b.value();
						}
					}
				}
			}
			var attribute = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
			if (attribute != null) {
				if (speed == 1) {
					attribute.removeModifier(SPEED);
				} else {
					attribute.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
							SPEED, speed - 1,
							net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				}
			}
		}
	}

	/** Stat code n = attribute slot n - 1 (effective stats); no stats (outside Souls Mode): 0. */
	private static int stat(Attributes stats, int code) {
		return stats == null || code < 1 || code > Attributes.SLOT_COUNT ? 0 : stats.get(code - 1);
	}

	/** FUN_14023cbd0 (the category factor FUN_14023cb60 is 1.0 in every row the mod exports). */
	private static float multiplier(Attributes stats, JsonObject i) {
		int code = i.get("stat").getAsInt();
		if (code == 0) {
			return 1;
		}
		int lo = i.get("lo").getAsInt();
		int hi = i.get("hi").getAsInt();
		float low = i.get("low").getAsFloat();
		float high = i.get("high").getAsFloat();
		if (hi == lo || high == low) {
			return low;
		}
		int s = stats == null ? lo : stat(stats, code);
		float m = low + (s - lo) / (float) (hi - lo) * (high - low);
		return Math.max(Math.min(low, high), Math.min(Math.max(low, high), m));
	}

	/** FUN_140231470 per entry, then FUN_14014b810: the integer part of the pool heals, the fraction waits. */
	public static void tick(MinecraftServer server) {
		tickBuffs(server);
		float dt = 0.05f;
		Iterator<Target> it = TARGETS.values().iterator();
		while (it.hasNext()) {
			Target target = it.next();
			LivingEntity e = target.entity;
			if (e.isRemoved() || !e.isAlive()) {
				it.remove();
				continue;
			}
			Iterator<Heal> heals = target.heals.iterator();
			while (heals.hasNext()) {
				Heal h = heals.next();
				float chunk = Math.min(h.rate * dt, h.total - h.given);
				h.given += chunk;
				h.left -= dt;
				target.pool += chunk;
				if (h.left <= 0 || h.given >= h.total) {
					heals.remove();
				}
			}
			int n = (int) target.pool;
			target.pool -= n;
			if (n > 0) {
				double max = e.getMaxHealth() * SoulsStats.DS2_HP_PER_HEALTH;
				double hp = e.getHealth() * SoulsStats.DS2_HP_PER_HEALTH;
				if (hp + n >= max) {
					target.heals.removeIf(h -> h.stopWhenFull);
				}
				e.heal((float) (n / SoulsStats.DS2_HP_PER_HEALTH));
			}
			if (target.heals.isEmpty() && target.pool < 1) {
				it.remove();
			}
		}
	}
}
