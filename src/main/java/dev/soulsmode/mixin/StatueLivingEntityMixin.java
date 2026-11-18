package dev.soulsmode.mixin;

import dev.soulsmode.SoulsAttachments;
import dev.soulsmode.SoulsStatues;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Petrification (docs/research/petrification.md): a statue cannot be pushed; a player who died petrified stays in stone
 * until 5.25 s (DeadTypeParam 101010 +0x1c, when DS2 removes the stone FullBodySfx), instead of Minecraft's 1 s.
 */
@Mixin(LivingEntity.class)
public abstract class StatueLivingEntityMixin {
	/** DeadTypeParam 101010 +0x1c = 5.25 s. */
	private static final int SOULSMODE$STONE_TICKS = 105;

	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void soulsmode$statueNotPushable(CallbackInfoReturnable<Boolean> cir) {
		if (SoulsStatues.isPetrified((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
	private void soulsmode$petrifiedBody(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof Player) || !Boolean.TRUE.equals(self.getAttached(SoulsAttachments.PETRIFIED_DEATH))) {
			return;
		}
		ci.cancel();
		self.deathTime++;
		if (self.deathTime >= SOULSMODE$STONE_TICKS && !self.level().isClientSide() && !self.isRemoved()) {
			self.level().broadcastEntityEvent(self, (byte) 60);
			self.remove(Entity.RemovalReason.KILLED);
		}
	}
}
