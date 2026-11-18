package dev.soulsmode.mixin;

import dev.soulsmode.SoulsPvp;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An invasion death (SoulsPvp) drops no items and no experience: the loser keeps its items. */
@Mixin(LivingEntity.class)
public abstract class PvpDeathLootMixin {
	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
	private void soulsmode$keepOnInvasionDeath(ServerLevel level, DamageSource source, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && SoulsPvp.keepsItems(player)) {
			ci.cancel();
		}
	}
}
