package dev.soulsmode.client.mixin;

import dev.soulsmode.SoulsTorch;
import dev.soulsmode.client.AttackClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** First person: a held DS2 torch replaces the left-hand item. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack soulsmode$torch(LocalPlayer player) {
		return AttackClient.holdsTorch(player) ? SoulsTorch.STACK : player.getOffhandItem();
	}
}
