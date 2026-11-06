package dev.soulsmode.mixin;

import dev.soulsmode.ascetic.SoulsAscetic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bonfire Ascetic zones (SoulsAscetic): hostile mobs' damage, extra loot rolls and soul item drops. */
@Mixin(LivingEntity.class)
public abstract class AsceticLivingEntityMixin {
	@Shadow
	protected Player lastHurtByPlayer;

	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
	private float soulsmode$asceticDamage(float amount, DamageSource source) {
		return SoulsAscetic.scaleDamage(amount, source);
	}

	/** Same parameters as vanilla's roll (LivingEntity.dropFromLootTable), rolled again per cycle. */
	@Inject(method = "dropFromLootTable", at = @At("TAIL"))
	private void soulsmode$asceticLoot(DamageSource source, boolean recentlyHitByPlayer, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self.level() instanceof ServerLevel level) || SoulsAscetic.cycleAt(self) == 0) {
			return;
		}
		LootTable table = level.getServer().reloadableRegistries().getLootTable(self.getLootTable());
		LootParams.Builder builder = new LootParams.Builder(level)
				.withParameter(LootContextParams.THIS_ENTITY, self)
				.withParameter(LootContextParams.ORIGIN, self.position())
				.withParameter(LootContextParams.DAMAGE_SOURCE, source)
				.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
				.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity());
		Player killer = recentlyHitByPlayer ? lastHurtByPlayer : null;
		if (killer != null) {
			builder = builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer).withLuck(killer.getLuck());
		}
		SoulsAscetic.afterLoot(self, table, builder.create(LootContextParamSets.ENTITY), killer);
	}
}
