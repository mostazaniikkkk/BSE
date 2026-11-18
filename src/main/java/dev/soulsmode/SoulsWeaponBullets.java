package dev.soulsmode;

import com.google.gson.JsonObject;
import dev.soulsmode.ds2.Attack;
import dev.soulsmode.ds2.AttackData;
import dev.soulsmode.ds2.Bullets;
import dev.soulsmode.ds2.HitBack;
import dev.soulsmode.ds2.Weapons;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bullets of melee attacks (docs/research/weapons.md "Balas de los ataques"; DarkTwo espadones.md section 10): when an
 * attack starts, FUN_140392a60 fills the weapon's half of {@code chr+0x230 +0x760} (slot base 0 right, 5 left / power
 * stance) with the (bullet, PlayerDamageParam) pairs of WeaponAttackMotionParam +0x38..+0x4c; each TAE 2300 event
 * (FUN_140325880) fires the slot it names, id 0 fires nothing. The bullet's attack values are the weapon's attack
 * rating (FUN_140391fe0).
 * <p>
 * Origin: the bullet's PDP +0x31 dummy on the weapon model ({@code hitDummies}, else {@code dummyPolys}) placed on the
 * animated R_Weapon bone at the event time ({@code weaponBones}), as the spells' origin. Direction: the character's
 * flat facing; no homing target. Not confirmed: the bullet's direction / origin modes (BulletParam), homing of weapon
 * bullets, desc+0x29 / +0x2a (the fill condition), whether slots 5..7 keep an old bullet in a one-handed attack.
 */
public final class SoulsWeaponBullets {
	/** A weapon entry's {@code bullets} map, cached per entry object (a reload makes new entries). */
	private record Cached(JsonObject entry, Map<String, Bullets.Def> bullets) {
	}

	private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();
	private static final java.util.Set<String> REPORTED = ConcurrentHashMap.newKeySet();
	/** Metres of the DS2 rig to Minecraft blocks (SoulsMagic, tools/re/ds2_anims.py PX_PER_M). */
	private static final double BLOCKS_PER_METRE = 12.0 / 0.97 / 16.0;

	private SoulsWeaponBullets() {
	}

	static Map<String, Bullets.Def> bullets(String weapon) {
		JsonObject entry = Weapons.entry(weapon);
		if (entry == null || !entry.has("bullets")) {
			return Map.of();
		}
		Cached c = CACHE.get(weapon);
		if (c == null || c.entry() != entry) {
			c = new Cached(entry, Bullets.parse(entry.getAsJsonObject("bullets")));
			CACHE.put(weapon, c);
		}
		return c.bullets();
	}

	/** Whether the attack has TAE 2300 events with a bullet in their slot (worth computing the attack values for). */
	static boolean has(Attack attack) {
		AttackData.Data d = AttackData.of(attack);
		return !d.shots().isEmpty() && d.bullets().length > 0;
	}

	/** Fires the attack's TAE 2300 events in ({@code from}, {@code to}]. */
	static void fire(ServerPlayer player, Attack attack, String weapon, boolean weaponLeft, float from, float to,
			float[] attackValues) {
		AttackData.Data d = AttackData.of(attack);
		for (float[] shot : d.shots()) {
			if (shot[0] <= from || shot[0] > to) {
				continue;
			}
			int index = (int) shot[1] % 5;
			String id = index < d.bullets().length ? d.bullets()[index] : null;
			if (id == null) {
				continue;
			}
			Bullets.Def def = bullets(weapon).get(id);
			if (def == null) {
				report(weapon, "bullet " + id + " is not in the weapon's bullets");
				continue;
			}
			Vec3 origin = origin(player, attack, weapon, weaponLeft, def, shot[0]);
			if (origin == null) {
				report(weapon, "no origin for bullet " + id + " (weaponBones at " + shot[0] + " s or its dummy missing)");
				continue;
			}
			double yaw = Math.toRadians(player.getYRot());
			Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
			dev.soulsmode.entity.SoulsBullet.emit(player.serverLevel(), player, def, bullets(weapon),
					() -> new dev.soulsmode.entity.SoulsBullet.Frame(origin, forward), attackValues.clone(), weapon, null, 0,
					null);
		}
	}

	private static Vec3 origin(ServerPlayer player, Attack attack, String weapon, boolean weaponLeft, Bullets.Def def,
			float time) {
		HitBack.Bone bone = HitBack.bone(attack, time);
		float[] dummy = def.damage() == null ? null : dummy(weapon, def.damage().dummyPoly());
		if (bone == null || dummy == null) {
			return null;
		}
		double[] luf = new double[3];
		for (int i = 0; i < 3; i++) {
			float[] r = bone.rot()[i];
			luf[i] = bone.pos()[i] + r[0] * dummy[0] + r[1] * dummy[1] + r[2] * dummy[2];
		}
		double side = attack.mirroredFor(weaponLeft) ? -1 : 1;
		double left = luf[0] * side * BLOCKS_PER_METRE;
		double up = luf[1] * BLOCKS_PER_METRE;
		double forward = luf[2] * BLOCKS_PER_METRE;
		double yaw = Math.toRadians(player.getYRot());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double lx = Math.cos(yaw);
		double lz = Math.sin(yaw);
		return new Vec3(player.getX() + fx * forward + lx * left, player.getY() + up, player.getZ() + fz * forward + lz * left);
	}

	/** The weapon model dummy {@code id}: hitDummies (weapon hits), else dummyPolys (catalysts). */
	private static float[] dummy(String weapon, int id) {
		HitBack.Dummy d = HitBack.dummy(weapon, id);
		if (d != null) {
			return d.pos();
		}
		JsonObject entry = Weapons.entry(weapon);
		Map<Integer, float[]> polys = entry == null ? Map.of() : Weapons.dummyPolys(entry);
		return polys.get(id);
	}

	private static void report(String weapon, String what) {
		if (REPORTED.add(weapon + what)) {
			SoulsMode.LOGGER.error("[BSE] weapon {}: {}", weapon, what);
		}
	}
}
