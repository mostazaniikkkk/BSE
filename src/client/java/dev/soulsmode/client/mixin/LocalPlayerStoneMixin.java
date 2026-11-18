package dev.soulsmode.client.mixin;

import dev.soulsmode.SoulsAttachments;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The local player's own body after a petrification death stays until 5.25 s (DeadTypeParam 101010 +0x1c), like the
 * server's (StatueLivingEntityMixin); Minecraft removes it at 1 s.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerStoneMixin {
	private static final int SOULSMODE$STONE_TICKS = 105;

	@Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
	private void soulsmode$petrifiedBody(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (!Boolean.TRUE.equals(self.getAttached(SoulsAttachments.PETRIFIED_DEATH))) {
			return;
		}
		ci.cancel();
		if (++self.deathTime == SOULSMODE$STONE_TICKS) {
			self.remove(Entity.RemovalReason.KILLED);
		}
	}
}
