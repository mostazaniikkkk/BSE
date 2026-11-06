package dev.soulsmode.mixin;

import dev.soulsmode.SoulsGolems;
import dev.soulsmode.SoulsHollow;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Iron golems attack hollow players (SoulsHollow) and fight Souls Mode players like DS2's Ogre (SoulsGolems). */
@Mixin(IronGolem.class)
public abstract class IronGolemMixin extends AbstractGolem {
	protected IronGolemMixin(EntityType<? extends AbstractGolem> type, Level level) {
		super(type, level);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void soulsmode$ogreTurn(EntityType<? extends IronGolem> type, Level level, CallbackInfo ci) {
		this.moveControl = new SoulsGolems.OgreMoveControl(this);
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void soulsmode$attackHollow(CallbackInfo ci) {
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
				target -> target instanceof Player player && SoulsHollow.isHollow(player)));
		// Above the vanilla melee goal (priority 1, MOVE + LOOK): the Ogre's fight loop against a Souls Mode player.
		this.goalSelector.addGoal(0, new SoulsGolems.OgreGoal((IronGolem) (Object) this));
	}

	/** Against a Souls Mode player the vanilla hit only lands in an Ogre attack's window. */
	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void soulsmode$ogreHit(Entity target, CallbackInfoReturnable<Boolean> cir) {
		IronGolem golem = (IronGolem) (Object) this;
		if (!golem.level().isClientSide() && !SoulsGolems.allowHit(golem) && SoulsGolems.applies(target)) {
			cir.setReturnValue(false);
		}
	}
}
