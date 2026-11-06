package dev.soulsmode;

import dev.soulsmode.ds2.HitReaction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Mobs hit by a Souls Mode player react like DS2's characters (the user's rule, 2026-11-01; docs/research/
 * hit_reaction.md): for the c0001 damage reaction's lock windows at the hit's speed (HitReaction) they neither move
 * nor attack, and an attack they were making is cut. Mobs of {@code soulsmode:no_hit_reaction} (bosses and giants:
 * the user's choice) are not affected; the iron golem left the tag (2026-11-02) and reacts like DS2's Ogre.
 */
public final class SoulsHitReaction {
	public static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registries.ENTITY_TYPE,
			SoulsMode.id("no_hit_reaction"));

	/** Game tick until which the mob cannot move / attack. */
	private record Lock(long moveUntil, long attackUntil) {
	}

	private static final Map<Mob, Lock> LOCKS = new WeakHashMap<>();

	private SoulsHitReaction() {
	}

	/** A Souls Mode player's hit of reaction speed {@code speed} (PlayerDamageParam +0x2c) landed on {@code target}. */
	public static void onHit(LivingEntity target, float speed) {
		onHit(target, speed, null, null);
	}

	/**
	 * Like {@link #onHit(LivingEntity, float)}, also playing DS2's damage animation on the mobs that show DS2 clips
	 * (hit_reaction.md s.9): zombies and illagers the Hollow Soldier's (c1020 state 0x61c), iron golems the Ogre's
	 * (c3000 state 0x284, c3000_ogre.md s.6), the NPC invader the player's (c0001 BT_Damage). {@code motion}: Attack.reactionMotion of the hit, null without one.
	 */
	public static void onHit(LivingEntity target, float speed, float[] motion, net.minecraft.world.entity.Entity attacker) {
		// Datapack enemies have their own DS2 reaction (poise, HURT action: enemies.md s.7.1).
		if (!(target instanceof Mob mob) || target instanceof dev.soulsmode.entity.SoulsEnemy
				|| target.getType().is(IMMUNE) || !target.isAlive() || !(speed > 0)) {
			return;
		}
		lock(mob, speed);
		if (motion != null && attacker != null && (motion[0] == 2 || motion[0] == 3)) {
			String animation = animation(mob, speed, motion, attacker);
			if (animation != null) {
				var payload = new dev.soulsmode.network.ZombieAttackPayload(mob.getId(), animation);
				for (var player : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(mob)) {
					net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
				}
			}
		}
	}

	/**
	 * A reaction playing: the tick its DamageDuration ends, its base DmgDistance (+0x540, without the extra), and its
	 * root motion: the lower body's clips, start tick, speed, clip length, the facing it started with and the
	 * displacement (left, back) applied so far (|it| = +0x580).
	 */
	private static final class Reacting {
		final long until;
		final float base;
		final dev.soulsmode.ds2.ReactionBlend.Lower lower;
		final long start;
		final float speed;
		final float length;
		final double yaw;
		float[] moved = new float[2];
		float clipTime;

		Reacting(long until, float base, dev.soulsmode.ds2.ReactionBlend.Lower lower, long start, float speed,
				float length, double yaw) {
			this.until = until;
			this.base = base;
			this.lower = lower;
			this.start = start;
			this.speed = speed;
			this.length = length;
			this.yaw = yaw;
		}
	}

	private static final Map<Mob, Reacting> REACTING = new WeakHashMap<>();

	/** EnemyCommonParam 102000 +0x1ac (100 deg): an enemy victim's DmgDirection is front within it, else back. */
	private static final double ENEMY_FRONT = Math.toRadians(100);
	/** The Blend2 weights (0, x) of DmgDistance: c1020 and c3000 (0, 4), c0001 (0, 5). */
	private static final float C1020_HEAVY_AT = 4;
	private static final float C0001_HEAVY_AT = 5;

	/**
	 * The ZombieAnimClient reaction for this hit, or null when the mob shows none. The CPs (FUN_14013b4d0 /
	 * FUN_14013b0a0 -> FUN_14035e820): DmgDistance = PDP +0x28 x (1 + 0.01 DCP +0xbc t), t the attacker - victim
	 * distance between DCP +0xa4 and +0xa8; DmgDirection the signed horizontal angle from the victim's facing to the
	 * attacker (FUN_14031f8d0, positive = attacker on its right); DmgDownOrUp by PDP +0x43 (FUN_140138cc0).
	 */
	private static String animation(Mob mob, float speed, float[] motion, net.minecraft.world.entity.Entity attacker) {
		boolean c1020 = mob instanceof net.minecraft.world.entity.monster.Zombie
				|| mob instanceof net.minecraft.world.entity.monster.AbstractIllager;
		boolean c0001 = mob instanceof dev.soulsmode.entity.SoulsInvader;
		boolean c3000 = mob instanceof net.minecraft.world.entity.animal.IronGolem;
		if (!c1020 && !c0001 && !c3000) {
			return null;
		}
		boolean up = up((int) motion[2]);
		double d = attacker.position().distanceTo(mob.position());
		float near = motion[4];
		float far = motion[5];
		float t = d <= near ? 0 : d >= far || far <= near ? 1 : (float) ((d - near) / (far - near));
		float base = motion[3] * (1 + 0.01f * motion[6] * t);
		// Hit again while still in the reaction (b8+0x7e6, the DamageDuration event): + the previous base distance not
		// yet travelled, clamp(+0x540 - |+0x580|xz, 0, 9999.9) (FUN_14013b4d0); +0x580 sums the reaction's root motion
		// (FUN_140135dd0).
		long now = mob.level().getGameTime();
		Reacting previous = REACTING.get(mob);
		float distance = base;
		if (previous != null && now < previous.until) {
			float travelled = (float) Math.sqrt(previous.moved[0] * previous.moved[0] + previous.moved[1] * previous.moved[1]);
			distance += Math.max(0, Math.min(9999.9f, previous.base - travelled));
		}
		double yaw = Math.toRadians(mob.getYRot());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double vx = attacker.getX() - mob.getX();
		double vz = attacker.getZ() - mob.getZ();
		double dot = fx * vx + fz * vz;
		// Minecraft's right of a facing (-sin, cos) is (-cos, -sin).
		double side = -Math.cos(yaw) * vx - Math.sin(yaw) * vz;
		double cross = fx * vz - fz * vx;
		double angle = Math.atan2(Math.abs(cross), dot) * (side < 0 ? -1 : 1);
		// DamageDuration in clip seconds: c0001's 32 clips 0..1.3333; c1020 0..0.6667 (Light; the sync FeatherBlend2
		// 0x632 merges it with Heavy's 0..0.5, ending at the later one); c3000 0..0.5 in all six clips.
		float damageDuration = (c1020 ? 0.6667f : c3000 ? dev.soulsmode.ds2.C3000Attacks.damageDuration() : 1.3333f)
				/ speed;
		long until = now + Math.round(damageDuration * 20);
		if (c3000) {
			// The golem's DmgDirection: an enemy's (front within EnemyCommonParam 300000 +0x1ac = 100 deg). The
			// reaction is the column-2 one of the hit (c3000_ogre.md s.6: column 4 is not applied).
			boolean back = Math.abs(angle) >= ENEMY_FRONT;
			float heavy = Math.max(0, Math.min(1, distance / C1020_HEAVY_AT));
			REACTING.put(mob, new Reacting(until, base, dev.soulsmode.ds2.ReactionBlend.c3000Lower(up, back, heavy), now,
					speed, dev.soulsmode.ds2.ReactionBlend.C3000_LENGTH, yaw));
			return String.format(java.util.Locale.ROOT, "react:c3000:%d:%d:%.4f:%.4f", up ? 1 : 0, back ? 1 : 0, heavy,
					speed);
		}
		if (c1020) {
			boolean back = Math.abs(angle) >= ENEMY_FRONT;
			float heavy = Math.max(0, Math.min(1, distance / C1020_HEAVY_AT));
			REACTING.put(mob, new Reacting(until, base, dev.soulsmode.ds2.ReactionBlend.c1020Lower(up, back, heavy), now,
					speed, dev.soulsmode.ds2.ReactionBlend.C1020_LENGTH, yaw));
			return String.format(java.util.Locale.ROOT, "react:c1020:%d:%d:%.4f:%.4f", up ? 1 : 0, back ? 1 : 0,
					Math.max(0, Math.min(1, distance / C1020_HEAVY_AT)), speed);
		}
		Double direction = invaderDirection(angle);
		if (direction == null) {
			return null;
		}
		float[] hips = hipsRotation(mob);
		int x = 1 + ((int) motion[1] == 1 ? 2 : 0) + (up ? 1 : 0);
		REACTING.put(mob, new Reacting(until, base, dev.soulsmode.ds2.ReactionBlend.c0001Lower(x, direction.floatValue(),
				Math.max(0, Math.min(1, distance / C0001_HEAVY_AT))), now, speed,
				dev.soulsmode.ds2.ReactionBlend.C0001_LENGTH, yaw));
		return String.format(java.util.Locale.ROOT, "react:c0001:%d:%d:%d:%.2f:%.4f:%.4f:%.3f:%.3f:%.3f",
				motion[0] == 3 ? 40 : 30, (int) motion[1], up ? 1 : 0, direction,
				Math.max(0, Math.min(1, distance / C0001_HEAVY_AT)), speed, hips[0], hips[1], hips[2]);
	}

	/**
	 * EnemyCommonParam 800000..800002 (the NPC invaders) +0x30c / +0x310 / +0x314 / +0x318: D, A, B, C of the random
	 * Dmg_Rotation (degrees). c1020's HipsIK weighs it by MoveHit_Weight, 0 for an enemy, so only the invader uses it.
	 */
	private static final float HIPS_D = 45;
	private static final float HIPS_A = 10;
	private static final float HIPS_B = 20;
	private static final float HIPS_C = 15;
	/** The previous Dmg_Rotation y per character (ctrl+0x94). */
	private static final Map<Mob, Float> HIPS_Y = new WeakHashMap<>();

	/**
	 * CP Dmg_Rotation on each hit (FUN_14035e820, xorshift128 draws r in [0, 1]): x = -A + 2A r, z = -B + 2B r; y from
	 * the previous p: |p| <= C -> t = (2r - 1)(D - C), y = t - C if t <= 0 else t + C; p > C -> (C + D) r - D;
	 * p < -C -> (C + D) r - C. Degrees.
	 */
	private static float[] hipsRotation(Mob mob) {
		var random = mob.getRandom();
		float x = -HIPS_A + 2 * HIPS_A * random.nextFloat();
		float z = -HIPS_B + 2 * HIPS_B * random.nextFloat();
		float p = HIPS_Y.getOrDefault(mob, 0f);
		float r = random.nextFloat();
		float y;
		if (p >= -HIPS_C && p <= HIPS_C) {
			float t = (2 * r - 1) * (HIPS_D - HIPS_C);
			y = t <= 0 ? t - HIPS_C : t + HIPS_C;
		} else if (p > HIPS_C) {
			y = (HIPS_C + HIPS_D) * r - HIPS_D;
		} else {
			y = (HIPS_C + HIPS_D) * r - HIPS_C;
		}
		HIPS_Y.put(mob, y);
		return new float[] {x, y, z};
	}

	/**
	 * DmgDownOrUp by PDP +0x43 (FUN_140138cc0): 0 down, 1 up; 2 up when the hit point is less than CommonParam +0x1a8
	 * (0.8 m) above the victim's origin (vt+0x148, the feet). Who supplies the point for a melee hit (hit +0x110 with
	 * +0x140) is not established; without one the point is the origin itself, which gives up: the user's choice
	 * (2026-11-01) is that branch.
	 */
	private static boolean up(int mode) {
		return mode != 0;
	}

	/**
	 * The invader's DmgDirection in degrees: an NPC player-type character (chr+0x54 = 4, FUN_140355930) passes
	 * FUN_140203c10, so FUN_14013b0a0 keeps the full signed angle like a player's.
	 */
	private static Double invaderDirection(double angle) {
		return Math.toDegrees(angle);
	}

	private static void lock(Mob mob, float speed) {
		long now = mob.level().getGameTime();
		LOCKS.put(mob, new Lock(now + Math.round(HitReaction.moveLock(speed) * 20),
				now + Math.round(HitReaction.attackLock(speed) * 20)));
		mob.getNavigation().stop();
		// The reaction cuts what it was doing.
		SoulsZombies.interrupt(mob);
		SoulsGolems.interrupt(mob);
		SoulsIllagers.interrupt(mob);
		if (mob instanceof dev.soulsmode.entity.SoulsInvader invader) {
			invader.interrupt();
		}
	}

	/**
	 * Each server tick: the reactions' root motion (the blended clips' trajectories, nsa.trajectory), moved in the
	 * facing the mob had when hit (the trajectory has no turn). Model space X left, Y back.
	 */
	public static void tick(net.minecraft.server.MinecraftServer server) {
		var it = REACTING.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			Mob mob = e.getKey();
			Reacting r = e.getValue();
			long now = mob.level().getGameTime();
			float t = Math.min(r.length, (now - r.start) / 20f * r.speed);
			if (!mob.isAlive() || mob.isRemoved()) {
				it.remove();
				continue;
			}
			// The frame's delta (FUN_140616980), rotated by the facing (FUN_1404d9eb0; no turn in these clips).
			float[] step = dev.soulsmode.ds2.ReactionBlend.delta(r.lower, r.clipTime, t);
			r.clipTime = t;
			float dl = step[0];
			float db = step[1];
			if (dl != 0 || db != 0) {
				// Left of a facing (-sin, cos) is (cos, sin); back is (sin, -cos).
				double lx = Math.cos(r.yaw);
				double lz = Math.sin(r.yaw);
				double bx = Math.sin(r.yaw);
				double bz = -Math.cos(r.yaw);
				mob.move(net.minecraft.world.entity.MoverType.SELF,
						new net.minecraft.world.phys.Vec3(dl * lx + db * bx, 0, dl * lz + db * bz));
				r.moved = new float[] {r.moved[0] + dl, r.moved[1] + db};
			}
			if (t >= r.length && now >= r.until) {
				it.remove();
			}
		}
	}

	/** Whether the mob's damage clip still plays (clip length at the hit's speed). */
	public static boolean clipPlaying(Mob mob) {
		Reacting r = REACTING.get(mob);
		return r != null && (mob.level().getGameTime() - r.start) / 20f * r.speed < r.length;
	}

	/** Mob.serverAiStep: no goals (moving, attacking) while the reaction locks it. */
	public static boolean frozen(Mob mob) {
		Lock lock = LOCKS.get(mob);
		if (lock == null) {
			return false;
		}
		long now = mob.level().getGameTime();
		if (now >= Math.max(lock.moveUntil(), lock.attackUntil())) {
			LOCKS.remove(mob);
			return false;
		}
		return true;
	}
}
