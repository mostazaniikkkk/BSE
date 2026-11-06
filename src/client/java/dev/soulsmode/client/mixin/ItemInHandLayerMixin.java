package dev.soulsmode.client.mixin;

import dev.soulsmode.SoulsTorch;
import dev.soulsmode.client.AttackClient;
import dev.soulsmode.client.anim.GripClient;
import dev.soulsmode.ds2.Moveset;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A held DS2 torch replaces the left-hand item (WeaponParam 6000000 override): it is drawn instead. While one weapon is
 * held with both hands the other one is put away (WeaponStorage); where DS2 draws it was not found, so it is not drawn.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack soulsmode$torch(LivingEntity entity) {
		if (GripClient.grip(entity) == Moveset.Grip.TWO_HANDED_RIGHT) {
			return ItemStack.EMPTY;
		}
		return AttackClient.holdsTorch(entity) ? SoulsTorch.STACK : entity.getOffhandItem();
	}

	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack soulsmode$stored(LivingEntity entity) {
		return GripClient.grip(entity) == Moveset.Grip.TWO_HANDED_LEFT ? ItemStack.EMPTY : entity.getMainHandItem();
	}
}
