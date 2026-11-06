package dev.soulsmode.ds2;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bullet motion with the reference rows of docs/research/casting.md (Fireball 33010000, Lightning Spear 32140000). */
class BulletsTest {
	private static Bullets.Def bullet(String fields) {
		String json = """
				{"b": {"damage": null, "life": 2.0, "speed": 0, "accelFrom": 0, "accel": 0, "maxSpeed": 50,
				 "gravity": 0, "gravityFrom": 0, "gravityScale": 0, "homingTurn": 0, "homingFrom": 0, "homingDelay": 0,
				 "homingDistance": 0, "homingAngle": 0, "homingStopNear": 0, "perShot": 1, "shots": 1, "interval": 0,
				 "fan": [0, 0, 0, 0, 0, 0], "spread": [0, 0], "originMode": 0, "directionMode": 0,
				 "origin": [0, 0, 0], "aimYaw": 0, "aimPitch": 0, "leadHeight": false, "leadHorizontal": false,
				 "homeWithoutLock": false, "contact": [0, 0, 0, 0], "onWall": 1, "childrenOnHit": false,
				 "childrenOnExpiry": false, "childDelay": 0, "children": [], %s}}""".formatted(fields);
		Map<String, Bullets.Def> map = Bullets.parse(JsonParser.parseString(json).getAsJsonObject());
		return map.get("b");
	}

	@Test
	void fireballFallsAfterItsDelay() {
		Bullets.Def fireball = bullet("\"speed\": 17, \"gravity\": 6, \"gravityFrom\": 0.3, \"gravityScale\": 1.8");
		Bullets.Motion m = new Bullets.Motion(fireball, 0, 0, 0, 0, 0, 1, -1);
		for (int i = 0; i < 5; i++) {
			m.step(0.05f, null);
		}
		// 0.25 s: straight, 17 m/s.
		assertEquals(4.25, m.z, 1e-6);
		assertEquals(0, m.y, 1e-9);
		for (int i = 0; i < 4; i++) {
			m.step(0.05f, null);
		}
		assertTrue(m.vy < 0);
	}

	@Test
	void lightningSpearAccelerates() {
		Bullets.Def spear = bullet("\"speed\": 30, \"accel\": 10, \"maxSpeed\": 50");
		Bullets.Motion m = new Bullets.Motion(spear, 0, 0, 0, 1, 0, 0, -1);
		for (int i = 0; i < 20; i++) {
			m.step(0.05f, null);
		}
		assertEquals(40, m.vx, 1e-4);
	}

	@Test
	void homingTurnsAtMostItsRate() {
		Bullets.Def arrow = bullet("\"speed\": 25, \"homingTurn\": 0.349, \"homingAngle\": 1.0472");
		Bullets.Motion m = new Bullets.Motion(arrow, 0, 0, 0, 0, 0, 1, 18);
		m.step(0.05f, new double[] {5, 0, 10});
		double angle = Math.atan2(m.vx, m.vz);
		assertEquals(0.349 * 0.05, angle, 1e-4);
	}

	@Test
	void lightningSparksFanOutHorizontally() {
		// 32140031: 3 bullets, horizontal base 60 deg, step 120 deg (casting.md section 8.6).
		Bullets.Def sparks = bullet("\"perShot\": 3, \"fan\": [0, 1.0472, 0, 2.0944, 0, 0]");
		double[] d0 = sparks.fanDirection(0, 0);
		double[] d1 = sparks.fanDirection(1, 0);
		assertEquals(Math.sin(1.0472), d0[0], 1e-4);
		assertEquals(0, d0[1], 1e-9);
		assertEquals(Math.PI, Math.abs(Math.atan2(d1[0], d1[2])), 1e-3);
	}

	@Test
	void positiveVerticalAngleGoesDown() {
		Bullets.Def down = bullet("\"fan\": [1.5708, 0, 0, 0, 0, 0]");
		assertEquals(-1, down.fanDirection(0, 0)[1], 1e-4);
	}

	@Test
	void unknownChildrenAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> bullet("\"children\": [\"x\"]"));
	}
}
