package dev.soulsmode.client.mixin;

import dev.soulsmode.client.ZombieAnimClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.monster.AbstractIllager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DS2 clips on illagers (the parried sequence, ZombieAnimClient): separate arms instead of the crossed ones. */
@Mixin(IllagerModel.class)
public abstract class IllagerModelMixin {
	@Shadow @Final private ModelPart root;
	@Shadow @Final private ModelPart head;
	@Shadow @Final private ModelPart hat;
	@Shadow @Final private ModelPart arms;
	@Shadow @Final private ModelPart rightArm;
	@Shadow @Final private ModelPart leftArm;
	@Shadow @Final private ModelPart rightLeg;
	@Shadow @Final private ModelPart leftLeg;

	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/monster/AbstractIllager;FFFFF)V", at = @At("TAIL"))
	private void soulsmode$ds2Clip(AbstractIllager entity, float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (!ZombieAnimClient.playing(entity)) {
			return;
		}
		arms.visible = false;
		rightArm.visible = true;
		leftArm.visible = true;
		ZombieAnimClient.applyLimbs(entity, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false),
				head, hat, root.getChild("body"), rightArm, leftArm, rightLeg, leftLeg);
	}
}
