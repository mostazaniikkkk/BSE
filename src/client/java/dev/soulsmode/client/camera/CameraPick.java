package dev.soulsmode.client.camera;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Design choice: with the Souls camera the targeted block or entity is the one under the center of the screen (a ray
 * from the camera), not the one along the body's facing, so blocks can be aimed at to break, place or use them. The
 * vanilla reach still applies, measured from the player's eyes.
 */
public final class CameraPick {
	private CameraPick() {
	}

	/** Replaces the vanilla pick while the Souls camera is active. */
	public static void pick(Minecraft mc) {
		LocalPlayer player = mc.player;
		Camera camera = mc.gameRenderer.getMainCamera();
		if (player == null || mc.level == null || !camera.isInitialized() || !SoulsCamera.isActive()) {
			return;
		}
		Vec3 eyes = player.getEyePosition();
		Vec3 from = camera.getPosition();
		Vec3 look = new Vec3(camera.getLookVector());
		double blockRange = player.blockInteractionRange();
		double entityRange = player.entityInteractionRange();
		double length = from.distanceTo(eyes) + Math.max(blockRange, entityRange);
		Vec3 to = from.add(look.scale(length));

		HitResult result = BlockHitResult.miss(to, Direction.getNearest(look.x, look.y, look.z), player.blockPosition());
		double resultDistance = length * length;
		BlockHitResult block = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
				player));
		if (block.getType() == HitResult.Type.BLOCK) {
			resultDistance = from.distanceToSqr(block.getLocation());
			if (block.getLocation().distanceToSqr(eyes) <= blockRange * blockRange) {
				result = block;
			}
		}
		AABB box = new AABB(from, to).inflate(1.0);
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, from, to, box,
				e -> e != player && !e.isSpectator() && e.isPickable(), resultDistance);
		if (entity != null && entity.getLocation().distanceToSqr(eyes) <= entityRange * entityRange) {
			result = entity;
		}
		mc.hitResult = result;
		mc.crosshairPickEntity = result instanceof EntityHitResult e ? e.getEntity() : null;
	}
}
