package dev.soulsmode.client.camera;

import com.mojang.blaze3d.platform.InputConstants;
import dev.soulsmode.client.AttackClient;
import dev.soulsmode.client.StaminaHud;
import dev.soulsmode.ds2.LockOnData;
import dev.soulsmode.ds2.Movement;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * DS2 lock-on (PlayerLockTargetCtrl, docs/research/lockon.md), client side:
 * <ul>
 * <li>The key locks the best target (FUN_140382630 / FUN_140383110 / FUN_140382f40) or releases the lock. With no
 * target it recenters the Souls camera behind the player (FUN_14049cbf0 / FUN_14049df40).</li>
 * <li>The lock is kept while the target is within the list radius; past it the best visible candidate replaces it, or
 * the lock ends. Out of sight (camera raycast) for the target's CommonParam +0x360 the same happens. A dead target
 * ends the lock.</li>
 * <li>While locked the mouse does not move the camera; the camera tracks the target (FUN_14049df40) and the body
 * faces it while walking (side walk).</li>
 * </ul>
 * Not ported: switching targets (DS2's stick flick has no mouse equivalent yet), the locked camera's position follow
 * and LockOnCameraOffsetParam (row 10030, which most enemies and players use, overrides nothing).
 */
public final class LockOn {
	public static final LockOnData DATA = LockOnData.load();
	/**
	 * DS2 R3. The key is a design choice: one no vanilla binding uses (Minecraft keeps one mapping per key, so a shared
	 * key such as Tab would not reach this one).
	 */
	private static final KeyMapping KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.soulsmode.lock_on", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.soulsmode"));

	private static Entity target;
	/** ctrl+0x134: seconds the current target has been out of sight. */
	private static float lostSight;
	/** Real time the lock started, for the tracking ramp (+0x118). */
	private static long lockedAt;
	/** Last frame time, for the per-frame tracking. */
	private static long lastFrame;
	/** Previous screen zone of each candidate (the close-range visibility needs zone 0). */
	private static final Map<Integer, Integer> ZONES = new HashMap<>();
	/** Recenter in progress: start yaw, yaw change, duration, start time; NaN when none. */
	private static float recenterFrom = Float.NaN;
	private static float recenterDelta;
	private static float recenterTime;
	private static long recenterAt;

	private LockOn() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(LockOn::tick);
	}

	public static boolean isLocked() {
		return target != null;
	}

	public static Entity target() {
		return target;
	}

	private static boolean enabled(Minecraft mc) {
		return mc.player != null && mc.level != null && StaminaHud.soulsMode() && !mc.player.isSpectator();
	}

	private static void tick(Minecraft mc) {
		if (!enabled(mc) || !mc.player.isAlive()) {
			release();
			ZONES.clear();
			while (KEY.consumeClick()) {
				// Swallow presses outside Souls Mode.
			}
			return;
		}
		LocalPlayer player = mc.player;
		Camera camera = mc.gameRenderer.getMainCamera();
		Map<Integer, Integer> zones = new HashMap<>();
		Entity best = null;
		float bestScore = Float.NEGATIVE_INFINITY;
		boolean keep = false;
		boolean targetOccluded = false;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || !isCandidate(player, living)) {
				continue;
			}
			Vec3 point = lockPoint(living);
			float distance = (float) player.position().add(0, player.getBbHeight() / 2, 0).distanceTo(point);
			int zone = screenZone(camera, point);
			zones.put(e.getId(), zone);
			boolean occluded = occluded(mc, camera, point);
			if (e == target) {
				keep = true;
				targetOccluded = occluded;
				continue;
			}
			if (!occluded && visible(camera, point, distance, ZONES.getOrDefault(e.getId(), 0))) {
				float score = LockOnData.score(distance, zone);
				if (score > bestScore) {
					bestScore = score;
					best = e;
				}
			}
		}
		ZONES.clear();
		ZONES.putAll(zones);

		while (KEY.consumeClick()) {
			if (target != null) {
				release();
			} else if (best != null) {
				lock(best);
				keep = true;
				targetOccluded = false;
			} else {
				recenter(player);
			}
		}
		syncLockState();
		if (target == null) {
			return;
		}
		tickSwitch(mc, camera);
		if (!target.isAlive() || target.isRemoved()) {
			// A dead target forces a release (the +0x584 check), not a switch.
			release();
			return;
		}
		if (!keep) {
			// Past the list radius: the best visible candidate, or no lock.
			switchOrRelease(best);
			return;
		}
		if (targetOccluded) {
			lostSight += 0.05f;
			LockOnData.Target values = target instanceof Player ? DATA.player() : DATA.mob();
			if (lostSight >= values.lostSightTime()) {
				switchOrRelease(best);
			}
		} else {
			lostSight = 0;
		}
	}

	/**
	 * Mouse as DS2's right stick (the user's choice): the turn the mouse asks for in one tick, divided by
	 * {@link #FLICK_DEGREES} (design choice: a fast flick reaches 1), is the stick. Above 0.75 it switches targets
	 * (FUN_140383640), then waits 0.5 s; the wait clears once the stick drops under 0.5.
	 */
	private static final double FLICK_DEGREES = 30;
	private static final double SWITCH_AT = 0.75;
	private static final double REARM_BELOW = 0.5;
	private static final long SWITCH_COOLDOWN_NANOS = 500_000_000L;
	private static double stickX;
	private static double stickY;
	private static long switchedAt;
	private static boolean switchArmed = true;

	/** Mouse turn while locked (Entity.turn's arguments, degrees x 1/0.15). */
	public static void mouse(double yRot, double xRot) {
		stickX += yRot * 0.15 / FLICK_DEGREES;
		stickY -= xRot * 0.15 / FLICK_DEGREES;
	}

	/** Once per tick: reads the accumulated stick and switches if it was flicked. */
	private static void tickSwitch(Minecraft mc, Camera camera) {
		double x = stickX;
		double y = stickY;
		stickX = 0;
		stickY = 0;
		double magnitude = Math.sqrt(x * x + y * y);
		if (magnitude < REARM_BELOW) {
			switchArmed = true;
			return;
		}
		if (target == null || magnitude < SWITCH_AT
				|| (!switchArmed && System.nanoTime() - switchedAt < SWITCH_COOLDOWN_NANOS)) {
			return;
		}
		double[] from = project(camera, lockPoint(target));
		if (from == null) {
			return;
		}
		// FUN_140383760: on screen, on the stick's side, the candidate with the largest (d . s) / |d|^2.
		Entity best = null;
		double bestScore = 0;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == target || !(e instanceof LivingEntity living) || !isCandidate(mc.player, living)
					|| occluded(mc, camera, lockPoint(living))) {
				continue;
			}
			double[] to = project(camera, lockPoint(living));
			if (to == null || Math.abs(to[0]) > 1 || Math.abs(to[1]) > 1) {
				continue;
			}
			double dx = to[0] - from[0];
			double dy = to[1] - from[1];
			double dot = dx * x + dy * y;
			double len2 = dx * dx + dy * dy;
			if (dot <= 0 || len2 < 1e-9) {
				continue;
			}
			double score = dot / len2;
			if (score > bestScore) {
				bestScore = score;
				best = e;
			}
		}
		if (best != null) {
			lock(best);
			switchedAt = System.nanoTime();
			switchArmed = false;
		}
	}

	private static void switchOrRelease(Entity best) {
		if (best != null) {
			lock(best);
		} else {
			release();
		}
	}

	private static void lock(Entity entity) {
		if (target == null) {
			lockedAt = System.nanoTime();
		}
		target = entity;
		lostSight = 0;
		recenterFrom = Float.NaN;
	}

	private static boolean sentLocked;

	/** Tells the server whether the player is locked on (its locked sprint drain). */
	private static int sentTarget = -1;

	private static void syncLockState() {
		boolean locked = target != null;
		int id = locked ? target.getId() : -1;
		// The target is also sent: spell bullets home on it (docs/research/casting.md section 7).
		if ((locked != sentLocked || id != sentTarget) && net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
				.canSend(dev.soulsmode.network.AttackPayloads.LockState.TYPE)) {
			sentLocked = locked;
			sentTarget = id;
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
					new dev.soulsmode.network.AttackPayloads.LockState(locked, id));
		}
	}

	public static void release() {
		target = null;
		lostSight = 0;
	}

	/**
	 * The candidate list: another living thing, not allied, alive, outside the target's exclusion zone (|horizontal|
	 * &lt; +0xc4 and |dy| &lt; +0xc8) and within the list radius. Which Minecraft entities are "enemies" is a choice: every
	 * living entity except allies and armor stands.
	 */
	private static boolean isCandidate(LocalPlayer player, LivingEntity e) {
		if (e == player || !e.isAlive() || e instanceof ArmorStand || e.isSpectator() || ally(player, e)) {
			return false;
		}
		LockOnData.Target values = e instanceof Player ? DATA.player() : DATA.mob();
		double dx = e.getX() - player.getX();
		double dz = e.getZ() - player.getZ();
		double dy = e.getY() - player.getY();
		if (Math.sqrt(dx * dx + dz * dz) < values.exclusionRadius() && Math.abs(dy) < values.exclusionHeight()) {
			return false;
		}
		return player.position().distanceTo(e.position()) < DATA.listRadius();
	}

	/**
	 * An ally is not a candidate: a tamed pet, or a player of the same team, unless that team allows friendly fire.
	 * Servers that put every player in one team for name tags (SAM) leave friendly fire on, and those players can still
	 * hurt each other, so they can be locked on (bug 2026-11-16).
	 */
	private static boolean ally(LocalPlayer player, LivingEntity e) {
		if (!player.isAlliedTo(e)) {
			return false;
		}
		return !(e instanceof Player && player.getTeam() != null && player.getTeam().isAllowFriendlyFire());
	}

	/** The point the lock aims at. DS2 uses the target's lock point (vfunc +0x50); here the middle of its box (a choice). */
	public static Vec3 lockPoint(Entity e) {
		return e.position().add(0, e.getBbHeight() / 2, 0);
	}

	/**
	 * FUN_140383110: inside the camera cone (yaw, pitch of the camera-to-target direction against the camera's
	 * forward) and within the acquire distance, or within close range while it was off screen.
	 */
	private static boolean visible(Camera camera, Vec3 point, float distance, int previousZone) {
		Vec3 d = point.subtract(camera.getPosition());
		double yaw = Math.toDegrees(Math.atan2(-d.x, d.z));
		double pitch = -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		boolean inCone = Math.abs(Mth.wrapDegrees(yaw - camera.getYRot())) < Math.toDegrees(DATA.coneYaw())
				&& Math.abs(pitch - camera.getXRot()) < Math.toDegrees(DATA.conePitch());
		if (inCone && distance <= DATA.acquireDistance()) {
			return true;
		}
		return distance <= DATA.closeRange() && previousZone == 0;
	}

	/** Raycast from the camera to the lock point (blocks only). */
	private static boolean occluded(Minecraft mc, Camera camera, Vec3 point) {
		return mc.level.clip(new ClipContext(camera.getPosition(), point, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, mc.player)).getType() != HitResult.Type.MISS;
	}

	private static int screenZone(Camera camera, Vec3 point) {
		double[] ndc = project(camera, point);
		return ndc == null ? 0 : DATA.zone(ndc[0], ndc[1]);
	}

	/** Normalized device coordinates {x right, y up} of a world point, or null behind the camera. */
	public static double[] project(Camera camera, Vec3 point) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 d = point.subtract(camera.getPosition());
		Vec3 forward = new Vec3(camera.getLookVector());
		Vec3 up = new Vec3(camera.getUpVector());
		Vec3 left = new Vec3(camera.getLeftVector());
		double z = d.dot(forward);
		if (z <= 1e-3) {
			return null;
		}
		double fov = SoulsCamera.isActive() && SoulsCamera.USE_DS2_FOV ? SoulsCamera.FOV_DEGREES
				: mc.options.fov().get();
		double tanY = Math.tan(Math.toRadians(fov) / 2);
		double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
		return new double[] {-d.dot(left) / (z * tanY * aspect), d.dot(up) / (z * tanY)};
	}

	private static void recenter(LocalPlayer player) {
		if (!SoulsCamera.isActive()) {
			return;
		}
		// Target yaw: the player's facing (cam+0x630, probable). The target pitch (cam+0x6f0) is not traced: kept.
		float delta = Mth.wrapDegrees(player.getYRot() - SoulsCamera.yaw());
		recenterFrom = SoulsCamera.yaw();
		recenterDelta = delta;
		LockOnData.Camera c = DATA.camera();
		recenterTime = c.recenterTimeMin() + (c.recenterTimeMax() - c.recenterTimeMin()) * Math.abs(delta) / 180f;
		recenterAt = System.nanoTime();
	}

	/**
	 * Per rendered frame, before the camera is placed: the locked camera tracks the target, or the recenter runs.
	 * FUN_14049df40 moves the angle by the excess over the +0xbc dead zone times a factor that ramps from 0 to +0xb8
	 * over +0x118 s, once per DS2 frame; at another frame rate it is applied as at 60 fps (assumed DS2 frame rate).
	 */
	public static void frame(float partialTick) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		boolean souls = SoulsCamera.isActive();
		if (target == null) {
			if (!Float.isNaN(recenterFrom) && souls) {
				float t = (now - recenterAt) / 1e9f;
				float s = 1 - (float) Math.pow(1 - Math.min(1, t / recenterTime), DATA.camera().recenterPower());
				SoulsCamera.setYaw(recenterFrom + recenterDelta * s);
				if (t >= recenterTime) {
					recenterFrom = Float.NaN;
				}
			}
			return;
		}
		LockOnData.Camera c = DATA.camera();
		Vec3 eye = souls
				? new Vec3(Mth.lerp(partialTick, player.xo, player.getX()),
						Mth.lerp(partialTick, player.yo, player.getY()) + SoulsCamera.PIVOT_HEIGHT,
						Mth.lerp(partialTick, player.zo, player.getZ()))
				: player.getEyePosition(partialTick);
		Vec3 point = new Vec3(Mth.lerp(partialTick, target.xo, target.getX()),
				Mth.lerp(partialTick, target.yo, target.getY()) + target.getBbHeight() / 2,
				Mth.lerp(partialTick, target.zo, target.getZ()));
		Vec3 d = point.subtract(eye);
		float wantYaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float wantPitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		float ramp = Math.min(1, (now - lockedAt) / 1e9f / c.trackRamp()) * c.trackRate();
		float factor = 1 - (float) Math.pow(1 - ramp, dt * 60);
		if (souls) {
			// +0xc0 x FOV x 0.5 added to the pitch (the extra asin term is not ported). Its sign is not confirmed: here it
			// looks further down, with the same provisional pitch sign as SoulsCamera.
			wantPitch += (float) Math.toDegrees(c.aimOffset() * c.fov() * 0.5);
			float[] limits = DATA.pitchLimits(d.y);
			wantPitch = Mth.clamp(wantPitch, (float) Math.toDegrees(limits[0]), (float) Math.toDegrees(limits[1]));
			SoulsCamera.setYaw(SoulsCamera.yaw() + track(Mth.wrapDegrees(wantYaw - SoulsCamera.yaw()), factor, c));
			SoulsCamera.setPitch(SoulsCamera.pitch() + track(wantPitch - SoulsCamera.pitch(), factor, c));
		} else {
			// First person has no DS2 camera: the view tracks the target the same way, without the aim offset (a choice).
			player.setYRot(player.getYRot() + track(Mth.wrapDegrees(wantYaw - player.getYRot()), factor, c));
			player.setXRot(Mth.clamp(player.getXRot() + track(wantPitch - player.getXRot(), factor, c), -90, 90));
			player.yHeadRot = player.getYRot();
		}
	}

	private static float track(float delta, float factor, LockOnData.Camera c) {
		float dead = (float) Math.toDegrees(c.deadZone());
		if (Math.abs(delta) <= dead) {
			return 0;
		}
		return (delta - Math.signum(delta) * dead) * factor;
	}

	/**
	 * Third person while locked: the body turns to face the target at the DS2 movement turn rate (the lock's own rate
	 * is not traced) and the movement keys walk sideways / backwards relative to it. Sprinting is not locked (whether DS2
	 * keeps the side walk while dashing is not confirmed).
	 *
	 * @return true when the lock handled the movement
	 */
	public static boolean afterInput(LocalPlayer player, double moveX, double moveZ) {
		if (target == null || player.isSprinting() || AttackClient.movementLocked()) {
			return false;
		}
		double dx = target.getX() - player.getX();
		double dz = target.getZ() - player.getZ();
		float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
		var motion = player.getDeltaMovement();
		float speed = (float) Math.sqrt(motion.x * motion.x + motion.z * motion.z) * 20f;
		float maxStep = (float) Math.toDegrees(Movement.turnRate(speed, false))
				* dev.soulsmode.client.InventoryStats.turnFactor(player.isSprinting(), true) * 0.05f;
		float step = Mth.clamp(Mth.wrapDegrees(want - player.getYRot()), -maxStep, maxStep);
		player.setYRot(player.getYRot() + step);
		player.yHeadRot = player.getYRot();
		player.yBodyRot = player.getYRot();
		if (moveX != 0 || moveZ != 0) {
			// World direction -> the body's frame (Minecraft: forward = (-sin, cos), left = (cos, sin)).
			double yaw = Math.toRadians(player.getYRot());
			double forward = moveX * -Math.sin(yaw) + moveZ * Math.cos(yaw);
			double left = moveX * Math.cos(yaw) + moveZ * Math.sin(yaw);
			float magnitude = Math.min(1f, (float) Math.sqrt(player.input.forwardImpulse * player.input.forwardImpulse
					+ player.input.leftImpulse * player.input.leftImpulse));
			player.input.forwardImpulse = (float) forward * magnitude;
			player.input.leftImpulse = (float) left * magnitude;
		}
		return true;
	}
}
