package dev.soulsmode.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.soulsmode.SoulsThrown;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lloyd's Talisman: the food regeneration does not even start inside its zone, so it costs no exhaustion while the
 * healing itself is cancelled (HealBlockMixin).
 */
@Mixin(FoodData.class)
public abstract class FoodHealBlockMixin {
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;isHurt()Z"))
	private boolean soulsmode$notWhileBlocked(boolean hurt, Player player) {
		return hurt && !SoulsThrown.healBlocked(player);
	}
}
