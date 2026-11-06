package dev.soulsmode.mixin;

import dev.soulsmode.ascetic.SoulsAscetic;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bonfire Ascetic zones: a spawner in a zone (AsceticSpawnerMixin) spawns monsters at spots whose light is at most
 * the cycle's limit, whatever vanilla's light rules say. The two light checks a spawner's monster goes through are
 * Monster.isDarkEnoughToSpawn (SpawnPlacements) and Monster.getWalkTargetValue (Mob.checkSpawnRules).
 */
@Mixin(Monster.class)
public abstract class AsceticMonsterMixin {
	@Inject(method = "isDarkEnoughToSpawn", at = @At("HEAD"), cancellable = true)
	private static void soulsmode$asceticDark(ServerLevelAccessor level, BlockPos pos, RandomSource random,
			CallbackInfoReturnable<Boolean> cir) {
		if (SoulsAscetic.spawnerLightAllows(level, pos)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getWalkTargetValue", at = @At("HEAD"), cancellable = true)
	private void soulsmode$asceticWalkTarget(BlockPos pos, LevelReader level, CallbackInfoReturnable<Float> cir) {
		if (SoulsAscetic.spawnerLightAllows(level, pos)) {
			cir.setReturnValue(0f);
		}
	}
}
