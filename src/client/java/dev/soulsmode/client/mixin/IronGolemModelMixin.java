package dev.soulsmode.client.mixin;

import dev.soulsmode.client.ZombieAnimClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.animal.IronGolem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DS2 clips on iron golems (ZombieAnimClient): the parried sequence and the Ogre's (c3000) clips, the latter with the
 * arms turned about the shoulders; the golem's own proportions stay.
 */
@Mixin(IronGolemModel.class)
public abstract class IronGolemModelMixin {
	@Shadow @Final private ModelPart root;
	@Shadow @Final private ModelPart head;
	@Shadow @Final private ModelPart rightArm;
	@Shadow @Final private ModelPart leftArm;
	@Shadow @Final private ModelPart rightLeg;
	@Shadow @Final private ModelPart leftLeg;

	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/animal/IronGolem;FFFFF)V", at = @At("TAIL"))
	private void soulsmode$ds2Clip(IronGolem entity, float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch, CallbackInfo ci) {
		float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		ZombieAnimClient.applyLimbs(entity, partialTick, head, head, root.getChild("body"), rightArm, leftArm, rightLeg,
				leftLeg);
		ZombieAnimClient.golemShoulders(entity, partialTick, rightArm, leftArm);
	}
}
