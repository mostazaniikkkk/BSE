package dev.soulsmode;

import dev.soulsmode.ds2.Ds2Damage;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * DS2's bleed (docs/research/bleed.md), for the Bleed infusion:
 * <ul>
 * <li>Each hit adds its bleed attack in meter points (value[6] x 100 / 2505, FUN_140391fe0) x (1 - the victim's
 * resistance) x Z (1.0 without other players in a session, ChrMultiplayParam) to a meter of 100 (FUN_140145c60).</li>
 * <li>A hit that would reach 100 bursts instead: the meter goes to 0, the hit's points are dropped, and SpEffect 900200
 * deals PDP 9020's 200 flat HP (no defence, no absorption; 5 Minecraft health at the mod's 40 HP per point) and
 * starts 900210's 10 s bleed state: no build-up meanwhile, and a player's max stamina x 0.75.</li>
 * <li>0.5 s after a hit the meter drains: 100 in 25 s for players (ChrCommonParam 100 +0x2c4 / +0x2c8), 120 s for
 * creatures (EnemyCommonParam, 213 rows).</li>
 * <li>Resting at a bonfire clears it (SpEffect 110000010: the meters' multiplier x 100 for 3 s).</li>
 * </ul>
 * Resistance: a Souls Mode player's armour (ARP +0x24, by level) and stats (PSPL +0x48); the user's table for
 * Minecraft's creatures (2026-11-09): tag {@code soulsmode:bleed_immune} 100 %, {@code soulsmode:bleed_resist_15}
 * 15 % (zombies, DS2's Hollow Soldier), any other 30 % (the most common in DS2); a datapack enemy its
 * {@code bleedResistance}. The burst shows red dust particles (the user's choice: DS2's SFX 651 has no counterpart).
 */
public final class SoulsBleed {
	/** The meter's threshold (DAT_1410acb18). */
	private static final float THRESHOLD = 100;
	/** Max HP with every stat at 99 (FUN_14038d290: 1945 + 8 x 70): the attack-to-points divisor. */
	private static final float POINTS_DIVISOR = 2505;
	/** PDP 9020, type 5: the burst's flat damage in DS2 HP (bleed.md section 4). */
	private static final float BURST_HP = 200;
	/** SpEffect 900210: the bleed state's length. */
	private static final int BLEEDING_TICKS = 10 * 20;
	/** FUN_1401462a0: the delay before the meter drains. */
	private static final int DELAY_TICKS = 10;
	private static final float PLAYER_DRAIN_SECONDS = 25;
	private static final float CREATURE_DRAIN_SECONDS = 120;
	/** SpEffect 900210: a player's max stamina while bleeding. */
	public static final float BLEEDING_STAMINA = 0.75f;

	public static final TagKey<EntityType<?>> IMMUNE = TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
			SoulsMode.id("bleed_immune"));
	public static final TagKey<EntityType<?>> RESIST_15 = TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
			SoulsMode.id("bleed_resist_15"));

	private static final class Meter {
		float value;
		int delay;
		int bleeding;
	}

	private static final Map<LivingEntity, Meter> METERS = new WeakHashMap<>();
	/** QA only (-Dsoulsmode.qa.log=true on the server): logs every build-up and burst. */
	private static final boolean LOG = Boolean.getBoolean("soulsmode.qa.log");

	private SoulsBleed() {
	}

	/** After a hit that went in: the weapon's bleed build-up on the target. */
	public static void onHit(ServerPlayer player, ItemStack weapon, LivingEntity target, boolean leftHand,
			boolean twoHanded, float staminaLack) {
		String key = SoulsUpgrade.weaponKey(weapon);
		var stats = SoulsPlayer.data(player).map(SoulsData::toAttributes).orElse(null);
		if (key == null || stats == null || !target.isAlive()) {
			return;
		}
		float attack = Ds2Damage.bleedAttack(key, SoulsUpgrade.ds2Level(weapon), SoulsUpgrade.infusion(weapon), stats,
				twoHanded, leftHand, staminaLack) * SoulsUpgrade.materialFactor(weapon);
		if (attack <= 0) {
			return;
		}
		float points = attack * 100 / POINTS_DIVISOR * (1 - resistance(target));
		if (points <= 0) {
			return;
		}
		Meter meter = METERS.computeIfAbsent(target, t -> new Meter());
		if (meter.bleeding > 0) {
			return;
		}
		if (LOG) {
			SoulsMode.LOGGER.info("[BSE QA] bleed: {} attack {} points {} meter {} -> {}", target.getType().toShortString(),
					attack, points, meter.value, Math.min(THRESHOLD, meter.value + points));
		}
		if (meter.value + points >= THRESHOLD) {
			meter.value = 0;
			burst(player, target, meter);
			return;
		}
		meter.value += points;
		meter.delay = DELAY_TICKS;
	}

	private static void burst(ServerPlayer attacker, LivingEntity target, Meter meter) {
		meter.bleeding = BLEEDING_TICKS;
		if (LOG) {
			SoulsMode.LOGGER.info("[BSE QA] bleed: burst on {} health {}", target.getType().toShortString(), target.getHealth());
		}
		target.hurt(SoulsAttack.soulsDamage(attacker), (float) (BURST_HP / SoulsStats.DS2_HP_PER_HEALTH));
		if (target.level() instanceof ServerLevel level) {
			level.sendParticles(new DustParticleOptions(new Vector3f(0.7f, 0, 0), 1.5f), target.getX(),
					target.getY() + target.getBbHeight() * 0.6, target.getZ(), 40, target.getBbWidth() * 0.4,
					target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0);
		}
		if (target instanceof ServerPlayer victim) {
			SoulsStamina.apply(victim);
		}
	}

	/** Whether the entity is in the 10 s bleed state (a player's max stamina is x 0.75 meanwhile). */
	public static boolean isBleeding(LivingEntity entity) {
		Meter meter = METERS.get(entity);
		return meter != null && meter.bleeding > 0;
	}

	/** Resting at a bonfire: the meter and the state end (SpEffect 110000010). */
	public static void clear(ServerPlayer player) {
		Meter meter = METERS.remove(player);
		if (meter != null && meter.bleeding > 0) {
			SoulsStamina.apply(player);
		}
	}

	/** The victim's bleed resistance (0..1, 1 = immune). */
	public static float resistance(LivingEntity target) {
		if (target instanceof ServerPlayer victim && SoulsGameMode.isActive(victim)) {
			var stats = SoulsPlayer.data(victim).map(SoulsData::toAttributes).orElse(null);
			float total = stats == null ? 0 : Ds2Damage.bleedStatAbsorption(stats);
			EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.FEET, EquipmentSlot.LEGS};
			for (int i = 0; i < 4; i++) {
				ItemStack stack = victim.getItemBySlot(slots[i]);
				if (stack.getItem() instanceof ArmorItem armor) {
					total += Ds2Damage.pieceBleed(SoulsUpgrade.armorSet(armor.getMaterial()), i, SoulsUpgrade.level(stack));
				}
			}
			return Math.max(0, Math.min(1, total));
		}
		if (target instanceof dev.soulsmode.entity.SoulsEnemy enemy && enemy.enemyDef() != null) {
			return enemy.enemyDef().bleedResistance();
		}
		if (target.getType().is(IMMUNE)) {
			return 1;
		}
		return target.getType().is(RESIST_15) ? 0.15f : 0.30f;
	}

	public static void tick(MinecraftServer server) {
		var it = METERS.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			LivingEntity entity = e.getKey();
			Meter meter = e.getValue();
			if (!entity.isAlive()) {
				it.remove();
				continue;
			}
			if (meter.bleeding > 0 && --meter.bleeding == 0 && entity instanceof ServerPlayer player) {
				SoulsStamina.apply(player);
			}
			if (meter.delay > 0) {
				meter.delay--;
				continue;
			}
			float seconds = entity instanceof ServerPlayer ? PLAYER_DRAIN_SECONDS : CREATURE_DRAIN_SECONDS;
			meter.value = Math.max(0, meter.value - THRESHOLD / seconds / 20f);
			if (meter.value <= 0 && meter.bleeding <= 0) {
				it.remove();
			}
		}
	}
}
