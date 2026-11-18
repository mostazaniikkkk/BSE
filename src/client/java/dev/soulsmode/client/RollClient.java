package dev.soulsmode.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.client.anim.SoulsAnimations;
import dev.soulsmode.client.camera.SoulsCamera;
import dev.soulsmode.ds2.Dodge;
import dev.soulsmode.network.RollPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

/**
 * Dodge input, local movement and animation. Timings and distances come from DS2 ({@link Dodge}; the server sends the
 * distance for the player's equip load as the length of the direction). How the distance is spread over time (the
 * push length) is still a provisional design value: the root motion curve is not ported.
 */
public final class RollClient {
	/** Provisional, not from DS2: how long the push lasts for a roll / a backstep. */
	private static final float ROLL_PUSH_SECONDS = Dodge.ROLL_PUSH_SECONDS;
	private static final float STEP_PUSH_SECONDS = Dodge.STEP_PUSH_SECONDS;

	private static final KeyMapping ROLL_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.soulsmode.roll", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "key.categories.soulsmode"));

	private static Dodge current;
	/** Playback speed of the current dodge: its windows run on the animation clock. */
	private static float speed = 1;
	private static int ticks;
	private static double dirX;
	private static double dirZ;
	/**
	 * Latency compensation (the user's choice, 2026-11-10): the dodge plays at once on this client, computed as the
	 * server does (SoulsRoll.onRequest: direction, equip load); the server's Start for it does not restart it, a
	 * different one replaces it, none within {@link #PREDICT_TIMEOUT} ticks stops it.
	 */
	private static Dodge predicted;
	private static int predictedTicks;
	/** Design choice: one second without the server's Start means the press was refused. */
	private static final int PREDICT_TIMEOUT = 20;

	private RollClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(RollClient::tick);
		ClientPlayNetworking.registerGlobalReceiver(RollPayloads.Start.TYPE, (payload, context) -> {
			Entity entity = context.client().level == null ? null : context.client().level.getEntity(payload.entityId());
			if (!(entity instanceof AbstractClientPlayer player) || payload.kind() < 0 || payload.kind() >= Dodge.values().length) {
				return;
			}
			Dodge kind = Dodge.values()[payload.kind()];
			if (player == context.client().player && predicted != null) {
				boolean same = predicted == kind;
				predicted = null;
				if (same) {
					return;
				}
			}
			start(player, payload);
		});
	}

	private static void start(AbstractClientPlayer player, RollPayloads.Start payload) {
		{
			Dodge kind = Dodge.values()[payload.kind()];
			SoulsAnimations.play(player, SoulsMode.id(kind.animation()), false, payload.speed());
			if (player == Minecraft.getInstance().player) {
				AttackClient.cancel();
				current = kind;
				speed = payload.speed();
				ticks = 0;
				dirX = payload.dx();
				dirZ = payload.dz();
			}
		}
	}

	/** Starts locally the dodge the server will start (SoulsRoll.onRequest), with the synced equip load. */
	private static boolean predict(LocalPlayer player, double[] dir) {
		if (!StaminaHud.soulsMode() || StaminaHud.empty() || AttackClient.blocksDodge()
				|| dev.soulsmode.client.anim.PlayerReactionClient.movementLocked()) {
			return false;
		}
		float ratio = InventoryStats.loadRatio();
		double length = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1]);
		Dodge kind = Dodge.of(length > 1e-3, ratio);
		double dx;
		double dz;
		if (length > 1e-3) {
			dx = dir[0] / length;
			dz = dir[1] / length;
		} else {
			double yaw = Math.toRadians(player.getYRot());
			dx = Math.sin(yaw);
			dz = -Math.cos(yaw);
		}
		float distance = kind.distance(ratio, dev.soulsmode.SoulsEquipLoad.ROWS);
		start(player, new RollPayloads.Start(player.getId(), kind.ordinal(), (float) (dx * distance),
				(float) (dz * distance), kind.speed(ratio, dev.soulsmode.SoulsEquipLoad.ROWS)));
		predicted = kind;
		predictedTicks = 0;
		return true;
	}

	private static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		while (ROLL_KEY.consumeClick()) {
			boolean locked = current != null && ticks / 20f * speed < current.rerollLock;
			if (player.onGround() && !VictimClient.cannotAct()) {
				// Inside a lock the press still goes to the server: DS2's input buffer may keep it (SoulsInputBuffer).
				double[] dir = inputDirection(player);
				boolean predictedNow = !locked && predict(player, dir);
				ClientPlayNetworking.send(new RollPayloads.Request((float) dir[0], (float) dir[1], predictedNow));
			}
		}
		if (predicted != null && ++predictedTicks > PREDICT_TIMEOUT) {
			// The server refused the dodge: stop the predicted one.
			predicted = null;
			if (current != null) {
				SoulsAnimations.stop(player);
				current = null;
			}
		}
		if (current == null) {
			return;
		}
		float push = current.isRoll() ? ROLL_PUSH_SECONDS : STEP_PUSH_SECONDS;
		if (ticks / 20f * speed < push) {
			// (dirX, dirZ) has the dodge's length in blocks.
			double scale = speed / (push * 20);
			player.setDeltaMovement(dirX * scale, player.getDeltaMovement().y, dirZ * scale);
		}
		ticks++;
		if (ticks / 20f * speed >= current.duration) {
			current = null;
		}
	}

	/**
	 * The dodge holds the movement keys until it gives movement back (Dodge.movementBack): walking during the roll would
	 * add to its push and undo the heavy rolls' shorter distance.
	 */
	public static boolean movementLocked(LocalPlayer player) {
		if (current == null) {
			return false;
		}
		double[] dir = inputDirection(player);
		boolean held = Math.abs(dir[0]) > 1e-3 || Math.abs(dir[1]) > 1e-3;
		return ticks / 20f * speed < current.movementBack(held);
	}

	/** An attack started: stop the dodge push. */
	public static void cancel() {
		current = null;
	}

	/** Movement input rotated by the camera yaw, or (0, 0) without input (the server then picks a backstep). */
	static double[] inputDirection(LocalPlayer player) {
		if (SoulsCamera.isActive()) {
			// The Souls camera rewrites the input; use the camera-relative direction it computed.
			double[] dir = SoulsCamera.moveDirection();
			return dir == null ? new double[] {0, 0} : dir;
		}
		// Read the keys, not the impulses: those are zeroed during an attack's movement lock.
		double forward = axis(player.input.up, player.input.down);
		double left = axis(player.input.left, player.input.right);
		if (Math.abs(forward) < 1e-3 && Math.abs(left) < 1e-3) {
			return new double[] {0, 0};
		}
		double yaw = Math.toRadians(player.getYRot());
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		double x = left * cos - forward * sin;
		double z = forward * cos + left * sin;
		double length = Math.sqrt(x * x + z * z);
		return new double[] {x / length, z / length};
	}

	private static double axis(boolean positive, boolean negative) {
		return positive == negative ? 0 : positive ? 1 : -1;
	}
}
