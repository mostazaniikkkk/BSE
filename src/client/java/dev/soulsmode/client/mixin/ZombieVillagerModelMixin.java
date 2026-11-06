package dev.soulsmode.client.mixin;

import dev.soulsmode.client.ZombieAnimClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.ZombieVillagerModel;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DS2 clips on zombie villagers (ZombieAnimClient), which do not use AbstractZombieModel. */
@Mixin(ZombieVillagerModel.class)
public abstract class ZombieVillagerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/monster/Zombie;FFFFF)V", at = @At("TAIL"))
	private void soulsmode$ds2Clip(Zombie entity, float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch, CallbackInfo ci) {
		HumanoidModel<?> m = (HumanoidModel<?>) (Object) this;
		ZombieAnimClient.applyLimbs(entity, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false),
				m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg);
	}
}
