package dev.soulsmode.client.mixin;

import dev.soulsmode.client.ZombieAnimClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DS2 clips on endermen (the parried sequence, ZombieAnimClient). */
@Mixin(EndermanModel.class)
public abstract class EndermanModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void soulsmode$ds2Clip(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch, CallbackInfo ci) {
		HumanoidModel<?> m = (HumanoidModel<?>) (Object) this;
		ZombieAnimClient.applyLimbs(entity, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false),
				m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg);
	}
}
