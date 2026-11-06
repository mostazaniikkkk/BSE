package dev.soulsmode.mixin;

import dev.soulsmode.SoulsHollow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Villagers do not trade with a hollow player (SoulsHollow): they shake their head instead. */
@Mixin(Villager.class)
public abstract class VillagerMixin {
	@Shadow
	protected abstract void setUnhappy();

	@Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
	private void soulsmode$noTradeWhenHollow(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (SoulsHollow.isHollow(player)) {
			setUnhappy();
			cir.setReturnValue(InteractionResult.CONSUME);
		}
	}
}
