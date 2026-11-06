package dev.soulsmode.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.soulsmode.ascetic.SoulsAscetic;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Bonfire Ascetic zones: from cycle 1 a player inside a zone counts as not having slept for ever, so vanilla's
 * insomnia roll ({@code random.nextInt(timeSinceRest) >= 72000}) passes; night, sky, cadence and local difficulty stay.
 */
@Mixin(PhantomSpawner.class)
public abstract class AsceticPhantomSpawnerMixin {
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(III)I"))
	private int soulsmode$asceticInsomnia(int timeSinceRest, @Local ServerPlayer player) {
		return SoulsAscetic.ignoresInsomnia(player) ? Integer.MAX_VALUE : timeSinceRest;
	}
}
