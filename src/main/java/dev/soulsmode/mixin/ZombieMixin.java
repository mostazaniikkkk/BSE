package dev.soulsmode.mixin;

import dev.soulsmode.SoulsZombies;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zombies attacking a Souls Mode player use the Hollow Soldier's attacks and timing (SoulsZombies): the vanilla hit
 * waits for the animation's window. Against anyone else they stay vanilla.
 */
@Mixin(Zombie.class)
public class ZombieMixin {
	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void soulsmode$hollowSoldierAttack(Entity target, CallbackInfoReturnable<Boolean> cir) {
		Zombie zombie = (Zombie) (Object) this;
		// Only against a Souls Mode player; everyone else meets the vanilla zombie.
		if (!zombie.level().isClientSide() && !SoulsZombies.allowHit(zombie)
				&& target instanceof net.minecraft.server.level.ServerPlayer player
				&& dev.soulsmode.SoulsGameMode.isActive(player)) {
			SoulsZombies.request(zombie, target);
			cir.setReturnValue(false);
		}
	}
}
