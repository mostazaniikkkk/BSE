package dev.soulsmode.client.mixin;

import dev.soulsmode.client.ZombieAnimClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DS2 clips on skeletons (the parried sequence, ZombieAnimClient). */
@Mixin(SkeletonModel.class)
public abstract class SkeletonModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/Mob;FFFFF)V", at = @At("TAIL"))
	private void soulsmode$ds2Clip(Mob entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, CallbackInfo ci) {
		HumanoidModel<?> m = (HumanoidModel<?>) (Object) this;
		ZombieAnimClient.applyLimbs(entity, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false),
				m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg);
	}
}
