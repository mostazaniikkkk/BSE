package dev.soulsmode;

import dev.soulsmode.api.SoulsWeapons;
import dev.soulsmode.ds2.Attack;
import dev.soulsmode.ds2.Moveset;
import dev.soulsmode.network.ZombieAttackPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Illagers against Souls Mode players (the user's request, 2026-11-01):
 * <ul>
 * <li>The vindicator swings its axe with the player's DS2 attacks: the moveset its held item maps to (iron axe: the
 * Battle Axe), light attacks chained into the light combo inside the combo window like the player's
 * (Moveset.next); the vanilla hit (its damage and effects) lands at each TAE 2200 window if the target is within the
 * mob's melee reach and in front.</li>
 * <li>The pillager keeps its distance: with the crossbow it backs away from a target closer than its crossbow range
 * (8 blocks, vanilla's RangedCrossbowAttackGoal), and only as an emergency, the target within 4 blocks, takes an iron
 * sword; beyond 6 it goes back to the crossbow. With the sword it attacks the same way (iron sword: the
 * Longsword).</li>
 * </ul>
 * Against anyone else they stay vanilla. Which attack an illager picks is the user's design, not DS2 data.
 */
public final class SoulsIllagers {
	private static final double SWORD_IN = 4;
	private static final double SWORD_OUT = 6;
	/** Vanilla RangedCrossbowAttackGoal's attack radius: closer than this the pillager backs away. */
	public static final double KEEP_DISTANCE = 8;
	/** Same frontal check as the player's hits (SoulsAttack: +-60 deg). */
	private static final double HALF_ARC = Math.toRadians(60);

	private static final class Acting {
		final Moveset.Type type;
		final Attack attack;
		final LivingEntity target;
		final long start;
		int window = -1;

		Acting(Moveset.Type type, Attack attack, LivingEntity target, long start) {
			this.type = type;
			this.attack = attack;
			this.target = target;
			this.start = start;
		}
	}

	private static final Map<Mob, Acting> ACTING = new HashMap<>();
	/** The last attack of each illager, for the combo chain. */
	private static final Map<Mob, Acting> LAST = new HashMap<>();
	/** Pillagers holding the sword: the crossbow they put away and its drop chance. */
	private record Stored(ItemStack crossbow, float dropChance) {
	}

	private static final Map<Pillager, Stored> SWORDS = new HashMap<>();
	private static Mob hitting;

	private SoulsIllagers() {
	}

	/** A vindicator, or a pillager holding the sword, attacking a Souls Mode player. */
	public static boolean applies(Mob mob, net.minecraft.world.entity.Entity target) {
		return (mob instanceof Vindicator || mob instanceof Pillager p && SWORDS.containsKey(p))
				&& target instanceof ServerPlayer player && SoulsGameMode.isActive(player);
	}

	public static boolean allowHit(Mob mob) {
		return mob == hitting;
	}

	/** The vanilla attack request: starts an attack unless one is playing. */
	public static void request(Mob mob, LivingEntity target) {
		if (!(mob.level() instanceof ServerLevel level) || ACTING.containsKey(mob)) {
			return;
		}
		Moveset moveset = SoulsWeapons.moveset(SoulsWeapons.movesetFor(mob.getMainHandItem())).orElse(null);
		if (moveset == null) {
			return;
		}
		long now = level.getGameTime();
		Acting last = LAST.get(mob);
		boolean inCombo = last != null && last.attack.inCombo((now - last.start) / 20f);
		Moveset.Type type = Moveset.next(last == null ? null : last.type, false, inCombo, false, false);
		Attack attack = moveset.attack(false, false, type);
		if (attack == null || attack.isCast()) {
			return;
		}
		Acting acting = new Acting(type, attack, target, now);
		ACTING.put(mob, acting);
		LAST.put(mob, acting);
		mob.getNavigation().stop();
		send(mob, "player:" + attack.animation().substring(attack.animation().indexOf(':') + 1));
	}

	public static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<Mob, Acting>> it = ACTING.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Mob, Acting> e = it.next();
			Mob mob = e.getKey();
			Acting acting = e.getValue();
			if (mob.isRemoved() || !mob.isAlive()) {
				it.remove();
				LAST.remove(mob);
				continue;
			}
			float t = (mob.level().getGameTime() - acting.start) / 20f;
			if (t >= acting.attack.duration()) {
				it.remove();
				send(mob, "");
				continue;
			}
			mob.getNavigation().stop();
			int window = acting.attack.hitWindow(t);
			if (window >= 0 && window != acting.window) {
				acting.window = window;
				LivingEntity target = acting.target;
				if (target.isAlive() && mob.isWithinMeleeAttackRange(target) && inFront(mob, target)) {
					hitting = mob;
					try {
						if (mob.doHurtTarget(target) && target instanceof net.minecraft.server.level.ServerPlayer player) {
							// The illager swings a player weapon moveset: that swing's PDP slot DCP row (poise.md s.4.3).
							SoulsPoise.onHit(player, dev.soulsmode.ds2.Poise.weaponHit(
									SoulsAttack.damageKey(SoulsWeapons.movesetFor(mob.getMainHandItem())), acting.attack,
									acting.attack.hitSlot(window)), mob);
						}
					} finally {
						hitting = null;
					}
				}
			}
		}
		LAST.keySet().removeIf(m -> m.isRemoved() || !m.isAlive());
		for (ServerLevel level : server.getAllLevels()) {
			if (server.getTickCount() % 5 != 0) {
				break;
			}
			for (ServerPlayer player : level.players()) {
				if (!SoulsGameMode.isActive(player)) {
					continue;
				}
				for (Pillager pillager : level.getEntitiesOfClass(Pillager.class, player.getBoundingBox().inflate(16))) {
					swapWeapon(pillager);
				}
			}
		}
		SWORDS.keySet().removeIf(p -> p.isRemoved() || !p.isAlive());
	}

	/** Sword within 4 blocks of a Souls Mode target, crossbow again beyond 6 (or without one). */
	private static void swapWeapon(Pillager pillager) {
		LivingEntity target = pillager.getTarget();
		boolean souls = target instanceof ServerPlayer player && SoulsGameMode.isActive(player) && target.isAlive();
		double distance = souls ? pillager.distanceTo(target) : Double.MAX_VALUE;
		Stored stored = SWORDS.get(pillager);
		if (stored == null && souls && distance <= SWORD_IN && pillager.getMainHandItem().is(Items.CROSSBOW)) {
			EquipmentSlot slot = EquipmentSlot.MAINHAND;
			SWORDS.put(pillager, new Stored(pillager.getMainHandItem().copy(), ((dev.soulsmode.mixin.MobAccessor) pillager).soulsmode$getEquipmentDropChance(slot)));
			pillager.setItemSlot(slot, new ItemStack(Items.IRON_SWORD));
			// The sword is the crossbow put away: it does not drop in its place.
			pillager.setDropChance(slot, 0);
		} else if (stored != null && (distance > SWORD_OUT || !pillager.isAlive())) {
			restore(pillager, stored);
		}
	}

	private static void restore(Pillager pillager, Stored stored) {
		if (ACTING.containsKey(pillager)) {
			return;
		}
		SWORDS.remove(pillager);
		pillager.setItemSlot(EquipmentSlot.MAINHAND, stored.crossbow());
		pillager.setDropChance(EquipmentSlot.MAINHAND, stored.dropChance());
	}

	/** A pillager with its crossbow and a Souls Mode target too close: it backs away (KeepDistanceGoal). */
	public static boolean shouldBackAway(Pillager pillager) {
		LivingEntity target = pillager.getTarget();
		return !SWORDS.containsKey(pillager) && pillager.getMainHandItem().is(Items.CROSSBOW)
				&& target instanceof ServerPlayer player && SoulsGameMode.isActive(player) && target.isAlive()
				&& pillager.distanceTo(target) < KEEP_DISTANCE;
	}

	/** Whether the pillager holds the sword (its melee goal runs then). */
	public static boolean holdsSword(Pillager pillager) {
		return SWORDS.containsKey(pillager);
	}

	private static boolean inFront(Mob mob, LivingEntity target) {
		double dx = target.getX() - mob.getX();
		double dz = target.getZ() - mob.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d < 1e-3) {
			return true;
		}
		double yaw = Math.toRadians(mob.getYRot());
		double cos = (dx * -Math.sin(yaw) + dz * Math.cos(yaw)) / d;
		return Math.acos(Mth.clamp(cos, -1, 1)) <= HALF_ARC;
	}

	/** A damage reaction cut the illager's attack and its combo (SoulsHitReaction). */
	public static void interrupt(Mob mob) {
		LAST.remove(mob);
		if (ACTING.remove(mob) != null) {
			send(mob, "");
		}
	}

	private static void send(Mob mob, String animation) {
		ZombieAttackPayload payload = new ZombieAttackPayload(mob.getId(), animation);
		for (ServerPlayer player : PlayerLookup.tracking(mob)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
