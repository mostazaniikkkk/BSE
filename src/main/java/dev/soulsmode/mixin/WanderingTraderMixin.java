package dev.soulsmode.mixin;

import dev.soulsmode.SoulsHollow;
import net.minecraft.world.entity.npc.WanderingTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every wandering trader sells the Human Effigy (SoulsHollow), the Bonfire Ascetic (SoulsAscetic) and Lloyd's Talisman
 * (SoulsThrown), 2 emeralds each.
 */
@Mixin(WanderingTrader.class)
public abstract class WanderingTraderMixin {
	@Inject(method = "updateTrades", at = @At("RETURN"))
	private void soulsmode$humanEffigy(CallbackInfo ci) {
		((WanderingTrader) (Object) this).getOffers().add(SoulsHollow.traderOffer());
		if (dev.soulsmode.ascetic.AsceticRules.ENABLED) {
			((WanderingTrader) (Object) this).getOffers().add(dev.soulsmode.ascetic.SoulsAscetic.traderOffer());
		}
		((WanderingTrader) (Object) this).getOffers().add(dev.soulsmode.SoulsThrown.traderOffer());
		((WanderingTrader) (Object) this).getOffers().add(dev.soulsmode.SoulsThrown.firebombOffer());
	}
}
