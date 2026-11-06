package dev.soulsmode.client;

import dev.soulsmode.block.SoulsBlocks;
import dev.soulsmode.client.anim.AnimationDebugCommand;
import dev.soulsmode.client.anim.Locomotion;
import dev.soulsmode.client.anim.SoulsAnimations;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.renderer.RenderType;

public class SoulsModeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SoulsAnimations.init();
		dev.soulsmode.client.qa.QaDriver.init();
		Locomotion.init();
		dev.soulsmode.client.anim.AirClient.init();
		dev.soulsmode.client.anim.LandingShakeClient.init();
		dev.soulsmode.client.anim.GripClient.init();
		dev.soulsmode.client.anim.PlayerReactionClient.init();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				AnimationDebugCommand.register(dispatcher));
		// The stain texture has transparent pixels around the splatter.
		BlockRenderLayerMap.INSTANCE.putBlock(SoulsBlocks.BLOODSTAIN, RenderType.cutout());
		StaminaHud.init();
		RollClient.init();
		AttackClient.init();
		FlickInput.init();
		InventoryStats.init();
		PresenceClient.init();
		ContentClient.init();
		SpellClient.init();
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				dev.soulsmode.entity.SoulsEntities.BULLET, BulletRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				dev.soulsmode.entity.SoulsEntities.THROWN_ITEM, net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				dev.soulsmode.entity.SoulsEntities.ENEMY, EnemyRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				dev.soulsmode.entity.SoulsEntities.INVADER, InvaderRenderer::new);
		dev.soulsmode.SoulsConsumables.clientSoulsMode = StaminaHud::soulsMode;
		VictimClient.init();
		dev.soulsmode.client.anim.GuardLayerClient.init();
		dev.soulsmode.client.anim.PowerStanceClient.init();
		ZombieAnimClient.init();
		dev.soulsmode.client.world.SoulsWorldClient.init();
		net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
				(type, renderer, helper, context) -> {
					if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer player) {
						helper.register(new BackItemsLayer(player));
					}
					if (renderer instanceof net.minecraft.client.renderer.entity.GiantMobRenderer giant) {
						helper.register(new GiantLogLayer(giant));
					}
				});
		dev.soulsmode.client.camera.LockOn.init();
		dev.soulsmode.client.camera.LockOnHud.init();
		dev.soulsmode.client.bonfire.BonfireClient.init();
		dev.soulsmode.client.bonfire.BonfireShotClient.init();
		MapHud.init();
	}
}
