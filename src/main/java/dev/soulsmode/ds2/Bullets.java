package dev.soulsmode.ds2;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DS2 bullets (BulletParam + their PlayerDamageParam row), docs/research/casting.md sections 3 and 7. Units: metres
 * (blocks), seconds, radians; everything is integrated with dt in seconds (FUN_14012f080 / FUN_1401305b0).
 */
public final class Bullets {
	/**
	 * The PlayerDamageParam fields a spell hit reads: flat damage +0x0c into element +0x19 (1 magic, 2 lightning,
	 * 3 fire, 4 dark), floor +0x18, rate +0x1c, guard stamina damage +0x24, hit volume +0x33 / +0x34 / +0x38 growing
	 * to +0x74 over +0x78 s, origin dummy poly +0x31 and team bits +0x40.
	 */
	public record Damage(int flat, int flatElement, int floor, float rate, int staminaDamage, boolean capsule,
			float radius, float length, float radiusEnd, float radiusTime, int dummyPoly, int teams, int spEffect,
			boolean spEffectOnHit, int effectSlot, int effectMode, float reactionSpeed, int reaction) {
		/**
		 * The hit reaction a mob gets (hit_reaction.md s.8): PDP +0x2c playback speed when the reaction after the remap
		 * is 2 or 3 (DmgKnockBack, DmgCategory 30 / 40, locked 0.9 / v like a melee hit); NaN otherwise (reaction 1 and
		 * the rest have no established lock, the support bullets have none). Files without the fields: NaN.
		 */
		public float knockBackSpeed() {
			return reaction == 2 || reaction == 3 ? reactionSpeed : Float.NaN;
		}

		/** PlayerDamageParam +0x40 (FUN_14013a6a0): bit 4 the owner itself, 8 an ally, 0x10 a hostile. */
		public boolean hits(boolean self, boolean ally) {
			return self ? (teams & 4) != 0 : ally ? (teams & 8) != 0 : (teams & 0x10) != 0;
		}

		/** FUN_140445970: the radius grows linearly from +0x34 to +0x74 over +0x78 s when +0x78 > 0. */
		public float radiusAt(float t) {
			return radiusTime > 0 ? radius + (radiusEnd - radius) * Math.min(1, t / radiusTime) : radius;
		}
	}

	/** One BulletParam row with the fields that have a confirmed reader. */
	public record Def(String id, Damage damage, float life, float speed, float accelFrom, float accel, float maxSpeed,
			float gravity, float gravityFrom, float gravityScale, float homingTurn, float homingFrom, float homingDelay,
			float homingDistance, float homingAngle, float homingStopNear, float forwardOffset, float aimYaw,
			float aimPitch, boolean leadHeight, boolean leadHorizontal, boolean homeWithoutLock, int[] contact,
			boolean childrenOnHit, boolean childrenOnExpiry, float childDelay, List<String> children,
			int perShot, int shots, float interval, float[] fan, float[] spread, float upOffset, float sideOffset,
			int originMode, int directionMode) {
		/**
		 * Bullet i of shot n (FUN_1404404a0, casting.md section 8): vertical angle a = +0x14 + i +0x30 + n +0x3c
		 * (positive = down) and horizontal b = +0x18 + i +0x34 + n +0x40 (positive = the frame's +X), in radians;
		 * the direction in the emitter frame (X = up x forward, Y = up, Z = forward) is (cos a sin b, -sin a,
		 * cos a cos b).
		 */
		public double[] fanDirection(int i, int n) {
			double a = fan[0] + i * fan[2] + n * fan[4];
			double b = fan[1] + i * fan[3] + n * fan[5];
			return new double[] {Math.cos(a) * Math.sin(b), -Math.sin(a), Math.cos(a) * Math.cos(b)};
		}

		/** Total shots: max(1, +0x2f); a negative +0x2f (no limit) is not used by any BulletParam row. */
		public int shotCount() {
			return Math.max(1, shots);
		}
	}

	private Bullets() {
	}

	/** The spell file's {@code bullets} map; every child must be in it. */
	public static Map<String, Def> parse(JsonObject bullets) {
		Map<String, Def> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : bullets.entrySet()) {
			JsonObject b = e.getValue().getAsJsonObject();
			if (b.get("shots").getAsInt() < 0) {
				throw new IllegalArgumentException("bullet " + e.getKey() + ": emitters without a shot limit are not supported");
			}
			if (!b.has("fan")) {
				throw new IllegalArgumentException("bullet " + e.getKey() + ": no emitter data (export it again with spell_export.py)");
			}
			Damage damage = null;
			if (b.has("damage") && !b.get("damage").isJsonNull()) {
				JsonObject d = b.getAsJsonObject("damage");
				damage = new Damage(d.get("flat").getAsInt(), d.get("flatElement").getAsInt(), d.get("floor").getAsInt(),
						d.get("rate").getAsFloat(), d.get("staminaDamage").getAsInt(), d.get("capsule").getAsBoolean(),
						d.get("radius").getAsFloat(), d.get("length").getAsFloat(), d.get("radiusEnd").getAsFloat(),
						d.get("radiusTime").getAsFloat(), d.get("dummyPoly").getAsInt(), d.get("teams").getAsInt(),
						d.has("spEffect") ? d.get("spEffect").getAsInt() : 0,
						d.has("spEffectOnHit") && d.get("spEffectOnHit").getAsBoolean(),
						d.has("effectSlot") ? d.get("effectSlot").getAsInt() : 0,
						d.has("effectMode") ? d.get("effectMode").getAsInt() : 0,
						d.has("reactionSpeed") ? d.get("reactionSpeed").getAsFloat() : Float.NaN,
						d.has("reaction") ? d.get("reaction").getAsInt() : -1);
			}
			List<String> children = new ArrayList<>();
			for (JsonElement c : b.getAsJsonArray("children")) {
				children.add(c.getAsString());
			}
			JsonArray contact = b.getAsJsonArray("contact");
			out.put(e.getKey(), new Def(e.getKey(), damage, f(b, "life"), f(b, "speed"), f(b, "accelFrom"), f(b, "accel"),
					f(b, "maxSpeed"), f(b, "gravity"), f(b, "gravityFrom"), f(b, "gravityScale"), f(b, "homingTurn"),
					f(b, "homingFrom"), f(b, "homingDelay"), f(b, "homingDistance"), f(b, "homingAngle"),
					f(b, "homingStopNear"), b.getAsJsonArray("origin").get(2).getAsFloat(), f(b, "aimYaw"),
					f(b, "aimPitch"), b.get("leadHeight").getAsBoolean(), b.get("leadHorizontal").getAsBoolean(),
					b.get("homeWithoutLock").getAsBoolean(),
					new int[] {contact.get(0).getAsInt(), contact.get(1).getAsInt(), contact.get(2).getAsInt(),
							contact.get(3).getAsInt()},
					b.get("childrenOnHit").getAsBoolean(), b.get("childrenOnExpiry").getAsBoolean(), f(b, "childDelay"),
					List.copyOf(children), Math.max(1, b.get("perShot").getAsInt()), b.get("shots").getAsInt(),
					f(b, "interval"), floats(b.getAsJsonArray("fan")), floats(b.getAsJsonArray("spread")),
					b.getAsJsonArray("origin").get(0).getAsFloat(), b.getAsJsonArray("origin").get(1).getAsFloat(),
					b.get("originMode").getAsInt(), b.get("directionMode").getAsInt()));
		}
		for (Def d : out.values()) {
			for (String c : d.children()) {
				if (!out.containsKey(c)) {
					throw new IllegalArgumentException("bullet " + d.id() + ": child " + c + " is not in bullets");
				}
			}
		}
		return out;
	}

	private static float[] floats(JsonArray a) {
		float[] out = new float[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsFloat();
		}
		return out;
	}

	private static float f(JsonObject o, String key) {
		return o.get(key).getAsFloat();
	}

	/**
	 * A bullet's movement (BulletShotMotion, FUN_14012f080 -> FUN_1401305b0; request multipliers 1.0 for catalysts).
	 * Velocity along the bullet's direction: v0 = +0x4c; from t >= +0x50 it gains +0x54 per second (a negative value
	 * only while v0 + (t - +0x50) x +0x54 > 0); |v| is clamped to [0, +0x58]; from t >= +0x60 gravity +0x5c x +0x64
	 * pulls it down. Homing (FUN_14012f2d0), with a target only: the turn per step is at most +0x98 x dt, after
	 * +0x9c (and +0xa0 when set), until it has travelled +0x90 (> 0), the target is +0x94 or more off its course, or
	 * it is closer than +0x12c; also not past {@code homingRange}.
	 */
	public static final class Motion {
		public double x;
		public double y;
		public double z;
		public double vx;
		public double vy;
		public double vz;
		public float t;
		public double travelled;
		private boolean homing = true;
		private final Def def;
		private final float homingRange;

		public Motion(Def def, double x, double y, double z, double dirX, double dirY, double dirZ, float homingRange) {
			this.def = def;
			this.x = x;
			this.y = y;
			this.z = z;
			double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
			double s = len > 1e-9 ? def.speed() / len : 0;
			this.vx = dirX * s;
			this.vy = dirY * s;
			this.vz = dirZ * s;
			this.homingRange = homingRange;
		}

		/** Advances dt seconds; {@code target} is the homing target point or null. */
		public void step(float dt, double[] target) {
			t += dt;
			double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
			if (t >= def.accelFrom() && def.accel() != 0
					&& (def.accel() > 0 || def.speed() + (t - def.accelFrom()) * def.accel() > 0) && speed > 1e-9) {
				double next = speed + def.accel() * dt;
				vx *= next / speed;
				vy *= next / speed;
				vz *= next / speed;
			}
			speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
			if (speed > def.maxSpeed() && speed > 1e-9) {
				double k = Math.max(0, def.maxSpeed()) / speed;
				vx *= k;
				vy *= k;
				vz *= k;
			}
			if (t >= def.gravityFrom() && def.gravity() != 0) {
				vy -= def.gravity() * def.gravityScale() * dt;
			}
			if (target != null && homing) {
				home(dt, target);
			}
			double dx = vx * dt;
			double dy = vy * dt;
			double dz = vz * dt;
			x += dx;
			y += dy;
			z += dz;
			travelled += Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		private void home(float dt, double[] target) {
			if (def.homingTurn() <= 0 || t <= def.homingFrom() || def.homingDelay() != 0 && t <= def.homingDelay()) {
				return;
			}
			double tx = target[0] - x;
			double ty = target[1] - y;
			double tz = target[2] - z;
			double dist = Math.sqrt(tx * tx + ty * ty + tz * tz);
			double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
			if (def.homingDistance() > 0 && travelled >= def.homingDistance() || homingRange >= 0 && dist > homingRange
					|| dist < def.homingStopNear() || dist < 1e-6 || speed < 1e-9) {
				homing = false;
				return;
			}
			double cos = (vx * tx + vy * ty + vz * tz) / (speed * dist);
			double angle = Math.acos(Math.max(-1, Math.min(1, cos)));
			if (angle >= def.homingAngle()) {
				homing = false;
				return;
			}
			double turn = Math.min(angle, def.homingTurn() * dt);
			if (angle < 1e-9) {
				return;
			}
			// Rotate the velocity towards the target by `turn` in the plane they span.
			double ux = vx / speed;
			double uy = vy / speed;
			double uz = vz / speed;
			double wx = tx / dist - ux * cos;
			double wy = ty / dist - uy * cos;
			double wz = tz / dist - uz * cos;
			double wl = Math.sqrt(wx * wx + wy * wy + wz * wz);
			wx /= wl;
			wy /= wl;
			wz /= wl;
			double c = Math.cos(turn);
			double s = Math.sin(turn);
			vx = (ux * c + wx * s) * speed;
			vy = (uy * c + wy * s) * speed;
			vz = (uz * c + wz * s) * speed;
		}
	}
}
