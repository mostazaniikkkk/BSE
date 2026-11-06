package dev.soulsmode;

import dev.soulsmode.ds2.C1020Attacks;
import dev.soulsmode.network.ZombieAttackPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Zombies attack like DS2's Hollow Soldier (the user's request, 2026-11-01; docs/research/c1020_anims.md): when the
 * vanilla melee goal would hit, the zombie instead picks an attack with ai102000's table (S2147483585, enemy_ai.md
 * s.9.2), plays its c1020 animation and stands; the vanilla hit (the zombie's own damage and effects) lands at each
 * TAE 2200 window if the target is still within the attack's reach and angle. f5004 / f5102 / f5018 are not
 * identified: their branches take the other side; combos play their first step.
 */
public final class SoulsZombies {
	private static final class Acting {
		final C1020Attacks.Attack attack;
		final LivingEntity target;
		final long start;
		int window = -1;

		Acting(C1020Attacks.Attack attack, LivingEntity target, long start) {
			this.attack = attack;
			this.target = target;
			this.start = start;
		}
	}

	private static final Map<Zombie, Acting> ACTING = new HashMap<>();
	/** The zombie whose window hit is running, so the mixin lets it through. */
	private static Zombie hitting;

	private SoulsZombies() {
	}

	/** Zombie.doHurtTarget goes through only for a window's hit. */
	public static boolean allowHit(Zombie zombie) {
		return zombie == hitting;
	}

	/** The vanilla attack request: starts an attack unless one is playing. */
	public static void request(Zombie zombie, Entity target) {
		if (!(zombie.level() instanceof ServerLevel level) || ACTING.containsKey(zombie)
				|| !(target instanceof LivingEntity living)) {
			return;
		}
		C1020Attacks.Attack attack = C1020Attacks.action(choose(zombie, living));
		if (attack == null) {
			return;
		}
		ACTING.put(zombie, new Acting(attack, living, level.getGameTime()));
		zombie.getNavigation().stop();
		send(zombie, attack.animation());
	}

	/** S2147483585: r0 = RandInt(0, 20); the near (< 3.5 m) or far branch. */
	private static int choose(Zombie zombie, LivingEntity target) {
		double d = zombie.distanceTo(target);
		double a = angle(zombie, target);
		int r = zombie.getRandom().nextInt(21);
		if (d < 3.5) {
			if (a >= 110 && d > 0 && d < 3) {
				return 13;
			}
			if (r > 0 && r < 10) {
				return 14;
			}
			if (r > 0 && r < 15) {
				return 13;
			}
			return 50;
		}
		if (r > 0 && r < 17) {
			return 51;
		}
		if (r > 0 && r < 18) {
			return 16;
		}
		if (r > 0 && r < 19) {
			return 15;
		}
		if (r > 0 && r < 20) {
			return 17;
		}
		return 13;
	}

	/** f7002: absolute angle in degrees between the zombie's front and the target. */
	private static double angle(Zombie zombie, Entity target) {
		double toYaw = Math.toDegrees(Math.atan2(-(target.getX() - zombie.getX()), target.getZ() - zombie.getZ()));
		return Math.abs(Mth.wrapDegrees(toYaw - zombie.getYRot()));
	}

	public static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<Zombie, Acting>> it = ACTING.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Zombie, Acting> e = it.next();
			Zombie zombie = e.getKey();
			Acting acting = e.getValue();
			if (zombie.isRemoved() || !zombie.isAlive()) {
				it.remove();
				continue;
			}
			float t = (zombie.level().getGameTime() - acting.start) / 20f;
			if (t >= acting.attack.duration()) {
				it.remove();
				send(zombie, "");
				continue;
			}
			zombie.getNavigation().stop();
			int window = -1;
			for (int i = 0; i < acting.attack.hits().size(); i++) {
				float[] h = acting.attack.hits().get(i);
				if (t >= h[0] && t < h[1]) {
					window = i;
				}
			}
			if (window >= 0 && window != acting.window) {
				acting.window = window;
				LivingEntity target = acting.target;
				if (target.isAlive() && zombie.distanceTo(target) - target.getBbWidth() / 2 < acting.attack.reach()
						&& angle(zombie, target) < acting.attack.angle()) {
					hitting = zombie;
					try {
						if (zombie.doHurtTarget(target) && target instanceof net.minecraft.server.level.ServerPlayer player) {
							// The Hollow Soldier's EnemyDamageParam row (poise.md s.4.2).
							float[] h = acting.attack.hits().get(window);
							SoulsPoise.onHit(player, dev.soulsmode.ds2.Poise.enemyHit("c1020",
									Float.floatToRawIntBits(h[2])), zombie);
						}
					} finally {
						hitting = null;
					}
				}
			}
		}
	}

	/** A damage reaction cut the zombie's attack (SoulsHitReaction). */
	public static void interrupt(net.minecraft.world.entity.Mob mob) {
		if (mob instanceof Zombie zombie && ACTING.remove(zombie) != null) {
			send(zombie, "");
		}
	}

	private static void send(Zombie zombie, String animation) {
		ZombieAttackPayload payload = new ZombieAttackPayload(zombie.getId(), animation);
		for (ServerPlayer player : PlayerLookup.tracking(zombie)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
