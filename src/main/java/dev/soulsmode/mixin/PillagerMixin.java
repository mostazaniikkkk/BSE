package dev.soulsmode.mixin;

import dev.soulsmode.entity.IllagerGoals;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The pillager's goals against Souls Mode players (SoulsIllagers): keep the distance, the emergency sword. */
@Mixin(Pillager.class)
public abstract class PillagerMixin extends AbstractIllager {
	protected PillagerMixin(EntityType<? extends AbstractIllager> type, Level level) {
		super(type, level);
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void soulsmode$soulsGoals(CallbackInfo ci) {
		Pillager self = (Pillager) (Object) this;
		goalSelector.addGoal(2, new IllagerGoals.KeepDistance(self));
		goalSelector.addGoal(2, new IllagerGoals.Sword(self));
	}
}
