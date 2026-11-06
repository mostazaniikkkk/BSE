package dev.soulsmode.mixin;

import dev.soulsmode.ascetic.SoulsAscetic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bonfire Ascetic zones: while a monster spawner in a zone ticks, its light limit applies (AsceticMonsterMixin). */
@Mixin(BaseSpawner.class)
public abstract class AsceticSpawnerMixin {
	@Inject(method = "serverTick", at = @At("HEAD"))
	private void soulsmode$asceticStart(ServerLevel level, BlockPos pos, CallbackInfo ci) {
		SoulsAscetic.spawnerTick(level, pos);
	}

	@Inject(method = "serverTick", at = @At("RETURN"))
	private void soulsmode$asceticEnd(ServerLevel level, BlockPos pos, CallbackInfo ci) {
		SoulsAscetic.spawnerTickEnd();
	}
}
