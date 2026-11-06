package dev.soulsmode.mixin;

import dev.soulsmode.SoulsGiants;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The penalty giant (SoulsGiants): targets players and fights with The Last Giant's AI. Vanilla giants stay idle. */
@Mixin(Giant.class)
public abstract class GiantMixin extends Monster {
	protected GiantMixin(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void soulsmode$lastGiant(EntityType<? extends Giant> type, Level level, CallbackInfo ci) {
		Giant giant = (Giant) (Object) this;
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(giant, Player.class, 10, false, false,
				target -> SoulsGiants.isLastGiant(giant)));
		this.goalSelector.addGoal(0, new SoulsGiants.GiantGoal(giant));
	}
}
