package dev.soulsmode.mixin;

import dev.soulsmode.SoulsHollow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Villagers treat a nearby hollow player as a hostile, so they panic and flee (SoulsHollow). */
@Mixin(VillagerHostilesSensor.class)
public abstract class VillagerHostilesSensorMixin {
	@Inject(method = "isMatchingEntity", at = @At("HEAD"), cancellable = true)
	private void soulsmode$fleeHollow(LivingEntity villager, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		if (target instanceof Player player && SoulsHollow.isHollow(player)) {
			cir.setReturnValue(villager.distanceToSqr(target)
					<= SoulsHollow.VILLAGER_FLEE_DISTANCE * SoulsHollow.VILLAGER_FLEE_DISTANCE);
		}
	}
}
