package dev.soulsmode.client.mixin;

import dev.soulsmode.client.AttackClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In Souls Mode the attack key requests a DS2 light attack, and right click with a weapon a heavy one. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void soulsmode$soulsAttack(CallbackInfoReturnable<Boolean> cir) {
		if (AttackClient.onAttackKey((Minecraft) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	/** Holding the attack key with a weapon does not dig the aimed block (see AttackClient.mines). */
	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void soulsmode$noDigging(boolean leftClick, CallbackInfo ci) {
		Minecraft self = (Minecraft) (Object) this;
		if (leftClick && AttackClient.blocksContinueAttack(self)) {
			self.gameMode.stopDestroyBlock();
			ci.cancel();
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void soulsmode$heavyAttack(CallbackInfo ci) {
		if (AttackClient.onUseKey((Minecraft) (Object) this)) {
			ci.cancel();
		}
	}
}
