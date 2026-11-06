package dev.soulsmode.client.mixin;

import dev.soulsmode.client.ZombieAnimClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DS2 clips on mobs drawn with the player model (NPC invaders, ZombieAnimClient). Real players have no entry there
 * (they use Player Animation Library), so nothing changes for them.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void soulsmode$ds2Clip(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (!ZombieAnimClient.playing(entity)) {
			return;
		}
		PlayerModel<?> m = (PlayerModel<?>) (Object) this;
		ZombieAnimClient.applyLimbs(entity, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false),
				m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg);
		// The outer layer follows the limbs, as PlayerModel.setupAnim does at its end.
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		m.jacket.copyFrom(m.body);
	}
}
