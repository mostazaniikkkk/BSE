package dev.soulsmode.client;

import dev.soulsmode.client.camera.LockOn;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The jump attack's leap (type 9, docs/research/weapons.md "Tipo 9"; ChrJumpAttackCtrl FUN_140141230 /
 * FUN_140140d40). At AttackStateControl_01 (0.5333 s in every aXX_01_0240) one velocity is set: up at vy, forward at
 * vmin..vmax interpolated by the horizontal distance d to the locked target between near..far (ChrMoveParam row 100
 * +0x248.., the "target below" set +0x25c.. when the target is more than ChrCommonParam +0x280 = 0.5 m lower; the
 * equip load factors a / b are 0 in the regulation). While airborne the horizontal speed brakes at ChrMoveParam +0x14c
 * = 1.0 m/s² (FUN_140388f00; under the 8.5 m/s cap, which the leap never reaches). The leap ends on landing.
 * <p>
 * Not from DS2: gravity is Minecraft's (DS2's is in obfuscated physics code), and the animations' own root motion is
 * not applied, so it is not counted twice. The conversion to a fall attack when landing 0.5 m below the takeoff is not
 * ported.
 */
final class JumpAttackLeap {
	/** AttackStateControl_01 of a20/a21/a25/a29 _01_0240. */
	static final float START = 0.5333f;
	private static final float BELOW = 0.5f;
	private static final float AIR_BRAKE = 1.0f;
	/** {vy, near, far, vmin, vmax} level / target below. */
	private static final float[] LEVEL = {3, 4, 6, 4, 8};
	private static final float[] TARGET_BELOW = {3, 3, 8, 2, 8};

	private static boolean airborne;
	private static float speed;
	private static double dirX;
	private static double dirZ;

	private JumpAttackLeap() {
	}

	/** FUN_140140d40: sets the leap's velocity (blocks per tick = m/s / 20). */
	static void start(LocalPlayer player) {
		float[] p = LEVEL;
		float d = 0;
		Entity target = LockOn.target();
		if (target != null) {
			double dx = target.getX() - player.getX();
			double dz = target.getZ() - player.getZ();
			d = (float) Math.sqrt(dx * dx + dz * dz);
			if (target.getY() < player.getY() - BELOW) {
				p = TARGET_BELOW;
			}
		}
		float t = d <= p[1] ? 0 : d >= p[2] ? 1 : (d - p[1]) / (p[2] - p[1]);
		speed = p[3] + (p[4] - p[3]) * t;
		double yaw = Math.toRadians(player.getYRot());
		dirX = -Math.sin(yaw);
		dirZ = Math.cos(yaw);
		player.setDeltaMovement(dirX * speed / 20, p[0] / 20, dirZ * speed / 20);
		player.setOnGround(false);
		airborne = true;
	}

	/** Each tick of the leap: keeps the horizontal speed (DS2 air braking instead of Minecraft's drag) until landing. */
	static void tick(LocalPlayer player, boolean firstTick) {
		if (!airborne) {
			return;
		}
		if (!firstTick && player.onGround()) {
			airborne = false;
			return;
		}
		speed = Math.max(0, speed - AIR_BRAKE / 20);
		player.setDeltaMovement(dirX * speed / 20, player.getDeltaMovement().y, dirZ * speed / 20);
	}

	static void stop() {
		airborne = false;
	}
}
