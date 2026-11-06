package dev.soulsmode.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.soulsmode.ds2.Enemies;
import dev.soulsmode.entity.SoulsEnemy;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Datapack enemies (SoulsEnemy): the GeckoLib model, texture and animations named by the enemy's definition, which
 * the server pack's resource pack brings (docs/datapacks.md, "Enemigos"). Nothing is drawn until the definition has
 * synced.
 */
public class EnemyRenderer extends GeoEntityRenderer<SoulsEnemy> {
	public EnemyRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
	}

	@Override
	public void render(SoulsEnemy entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
			int light) {
		if (entity.def() != null) {
			super.render(entity, yaw, partialTick, poseStack, buffers, light);
		}
	}

	private static final class Model extends GeoModel<SoulsEnemy> {
		@Override
		public ResourceLocation getModelResource(SoulsEnemy enemy) {
			return ResourceLocation.parse(enemy.def().model());
		}

		@Override
		public ResourceLocation getTextureResource(SoulsEnemy enemy) {
			return ResourceLocation.parse(enemy.def().texture());
		}

		@Override
		public ResourceLocation getAnimationResource(SoulsEnemy enemy) {
			Enemies.Def d = enemy.def();
			return ResourceLocation.parse(d.animationFile());
		}
	}
}
