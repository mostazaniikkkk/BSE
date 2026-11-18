package dev.soulsmode.client.mixin;

import dev.soulsmode.client.StoneTextures;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A petrified statue or a petrified player's body (StoneTextures): drawn in stone, frozen (no idle bob) and, for the
 * body, without Minecraft's falling-over death turn.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class StoneRendererMixin<T extends LivingEntity> {
	@Inject(method = "getRenderType", at = @At("RETURN"), cancellable = true)
	private void soulsmode$stone(T entity, boolean bodyVisible, boolean translucent, boolean glowing,
			CallbackInfoReturnable<RenderType> cir) {
		if (cir.getReturnValue() != null && StoneTextures.stone(entity)) {
			cir.setReturnValue(RenderType.entityCutoutNoCull(StoneTextures.of(texture(entity))));
		}
	}

	@SuppressWarnings("unchecked")
	private ResourceLocation texture(T entity) {
		return ((net.minecraft.client.renderer.entity.EntityRenderer<T>) (Object) this).getTextureLocation(entity);
	}

	@Inject(method = "getBob", at = @At("HEAD"), cancellable = true)
	private void soulsmode$stoneStill(T entity, float partialTick, CallbackInfoReturnable<Float> cir) {
		if (StoneTextures.stone(entity)) {
			cir.setReturnValue(0f);
		}
	}

	/** Stone does not flash red when hit or dying. */
	@Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
	private static void soulsmode$stoneNoRed(LivingEntity entity, float whiteOverlay,
			CallbackInfoReturnable<Integer> cir) {
		if (StoneTextures.stone(entity)) {
			cir.setReturnValue(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
		}
	}

	@Redirect(method = "setupRotations", at = @At(value = "FIELD",
			target = "Lnet/minecraft/world/entity/LivingEntity;deathTime:I"))
	private int soulsmode$stoneNoDeathTurn(LivingEntity entity) {
		return StoneTextures.stone(entity) ? 0 : entity.deathTime;
	}
}
