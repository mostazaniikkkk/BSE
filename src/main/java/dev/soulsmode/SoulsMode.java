package dev.soulsmode;

import dev.soulsmode.api.SoulsWeapons;
import dev.soulsmode.block.SoulsBlocks;
import dev.soulsmode.network.AttackPayloads;
import dev.soulsmode.network.BonfirePayloads;
import dev.soulsmode.network.RollPayloads;
import dev.soulsmode.network.StaminaPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoulsMode implements ModInitializer {
	public static final String MOD_ID = "soulsmode";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SoulsAttachments.init();
		SoulsBlocks.init();
		// Items on the ground (GroundItemBlock): the user's 1 in 8 chunks of the overworld.
		net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
				net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld(),
				net.minecraft.world.level.levelgen.GenerationStep.Decoration.VEGETAL_DECORATION,
				net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE,
						id("ground_item")));
		dev.soulsmode.item.SoulsComponents.init();
		dev.soulsmode.item.SoulsItems.init();
		dev.soulsmode.entity.SoulsEntities.init();
		dev.soulsmode.content.ContentReloader.init();
		SoulsConsumables.init();
		SoulsHollow.init();
		dev.soulsmode.ascetic.SoulsAscetic.init();
		SoulsThrown.init();
		SoulsResin.init();
		SoulsSounds.init();
		// Loads the DS2 movesets and the vanilla weapon assignments.
		SoulsWeapons.movesets();
		PayloadTypeRegistry.playS2C().register(StaminaPayload.TYPE, StaminaPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(dev.soulsmode.network.ZombieAttackPayload.TYPE,
				dev.soulsmode.network.ZombieAttackPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(dev.soulsmode.network.MagicPayloads.State.TYPE,
				dev.soulsmode.network.MagicPayloads.State.CODEC);
		PayloadTypeRegistry.playC2S().register(dev.soulsmode.network.MagicPayloads.Attune.TYPE,
				dev.soulsmode.network.MagicPayloads.Attune.CODEC);
		PayloadTypeRegistry.playC2S().register(dev.soulsmode.network.MagicPayloads.Unattune.TYPE,
				dev.soulsmode.network.MagicPayloads.Unattune.CODEC);
		PayloadTypeRegistry.playC2S().register(dev.soulsmode.network.MagicPayloads.Cycle.TYPE,
				dev.soulsmode.network.MagicPayloads.Cycle.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(dev.soulsmode.network.MagicPayloads.Attune.TYPE,
				(payload, context) -> SoulsMagic.onAttune(context.player(), payload.slot()));
		ServerPlayNetworking.registerGlobalReceiver(dev.soulsmode.network.MagicPayloads.Unattune.TYPE,
				(payload, context) -> SoulsMagic.onUnattune(context.player(), payload.index()));
		ServerPlayNetworking.registerGlobalReceiver(dev.soulsmode.network.MagicPayloads.Cycle.TYPE,
				(payload, context) -> SoulsMagic.onCycle(context.player()));
		PayloadTypeRegistry.playS2C().register(dev.soulsmode.network.ContentPayload.TYPE,
				dev.soulsmode.network.ContentPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RollPayloads.Request.TYPE, RollPayloads.Request.CODEC);
		PayloadTypeRegistry.playS2C().register(RollPayloads.Start.TYPE, RollPayloads.Start.CODEC);
		PayloadTypeRegistry.playS2C().register(dev.soulsmode.network.PoisePayloads.Reaction.TYPE,
				dev.soulsmode.network.PoisePayloads.Reaction.CODEC);
		PayloadTypeRegistry.playC2S().register(AttackPayloads.Request.TYPE, AttackPayloads.Request.CODEC);
		PayloadTypeRegistry.playS2C().register(AttackPayloads.Start.TYPE, AttackPayloads.Start.CODEC);
		PayloadTypeRegistry.playS2C().register(AttackPayloads.Stop.TYPE, AttackPayloads.Stop.CODEC);
		PayloadTypeRegistry.playS2C().register(AttackPayloads.GuardLayer.TYPE, AttackPayloads.GuardLayer.CODEC);
		PayloadTypeRegistry.playC2S().register(AttackPayloads.Release.TYPE, AttackPayloads.Release.CODEC);
		PayloadTypeRegistry.playC2S().register(dev.soulsmode.network.BowAimPayload.TYPE,
				dev.soulsmode.network.BowAimPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AttackPayloads.Grip.TYPE, AttackPayloads.Grip.CODEC);
		PayloadTypeRegistry.playC2S().register(AttackPayloads.Torch.TYPE, AttackPayloads.Torch.CODEC);
		PayloadTypeRegistry.playS2C().register(dev.soulsmode.network.ClassPayloads.Open.TYPE,
				dev.soulsmode.network.ClassPayloads.Open.CODEC);
		PayloadTypeRegistry.playC2S().register(dev.soulsmode.network.ClassPayloads.Choose.TYPE,
				dev.soulsmode.network.ClassPayloads.Choose.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(dev.soulsmode.network.ClassPayloads.Choose.TYPE,
				(payload, context) -> SoulsWorld.onChoose(context.player(), payload.index()));
		PayloadTypeRegistry.playC2S().register(AttackPayloads.LockState.TYPE, AttackPayloads.LockState.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AttackPayloads.LockState.TYPE,
				(payload, context) -> {
					SoulsStamina.setLocked(context.player(), payload.locked());
					SoulsMagic.setLockTarget(context.player(), payload.target());
				});
		PayloadTypeRegistry.playS2C().register(AttackPayloads.TorchHeld.TYPE, AttackPayloads.TorchHeld.CODEC);
		PayloadTypeRegistry.playS2C().register(AttackPayloads.GripState.TYPE, AttackPayloads.GripState.CODEC);
		PayloadTypeRegistry.playS2C().register(dev.soulsmode.network.VictimPayload.TYPE,
				dev.soulsmode.network.VictimPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(dev.soulsmode.network.PresencePayload.TYPE,
				dev.soulsmode.network.PresencePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(dev.soulsmode.network.StatsPayload.TYPE, dev.soulsmode.network.StatsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(AttackPayloads.GripChange.TYPE, AttackPayloads.GripChange.CODEC);
		PayloadTypeRegistry.playS2C().register(BonfirePayloads.Open.TYPE, BonfirePayloads.Open.CODEC);
		PayloadTypeRegistry.playC2S().register(BonfirePayloads.LevelUp.TYPE, BonfirePayloads.LevelUp.CODEC);
		PayloadTypeRegistry.playS2C().register(BonfirePayloads.Pose.TYPE, BonfirePayloads.Pose.CODEC);
		PayloadTypeRegistry.playC2S().register(BonfirePayloads.Leave.TYPE, BonfirePayloads.Leave.CODEC);
		PayloadTypeRegistry.playC2S().register(BonfirePayloads.Rest.TYPE, BonfirePayloads.Rest.CODEC);
		PayloadTypeRegistry.playC2S().register(BonfirePayloads.Travel.TYPE, BonfirePayloads.Travel.CODEC);
		PayloadTypeRegistry.playS2C().register(BonfirePayloads.ShotRequest.TYPE, BonfirePayloads.ShotRequest.CODEC);
		PayloadTypeRegistry.playC2S().register(BonfirePayloads.Shot.TYPE, BonfirePayloads.Shot.CODEC);
		PayloadTypeRegistry.playS2C().register(BonfirePayloads.Shot.TYPE, BonfirePayloads.Shot.CODEC);
		PayloadTypeRegistry.playC2S().register(BonfirePayloads.ShotFetch.TYPE, BonfirePayloads.ShotFetch.CODEC);
		PayloadTypeRegistry.playC2S().register(BonfirePayloads.Rename.TYPE, BonfirePayloads.Rename.CODEC);
		PayloadTypeRegistry.playC2S().register(BonfirePayloads.BurnAscetic.TYPE, BonfirePayloads.BurnAscetic.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BonfirePayloads.Rename.TYPE,
				(payload, context) -> SoulsBonfire.onRename(context.player(), payload.name()));
		ServerPlayNetworking.registerGlobalReceiver(BonfirePayloads.BurnAscetic.TYPE,
				(payload, context) -> dev.soulsmode.ascetic.SoulsAscetic.onBurnRequest(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(BonfirePayloads.Shot.TYPE,
				(payload, context) -> BonfireShots.onUpload(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(BonfirePayloads.ShotFetch.TYPE,
				(payload, context) -> BonfireShots.onFetch(context.player(), payload.dimension(), payload.pos()));
		ServerPlayNetworking.registerGlobalReceiver(BonfirePayloads.Travel.TYPE,
				(payload, context) -> SoulsBonfire.onTravel(context.player(), payload.dimension(), payload.pos()));
		ServerPlayNetworking.registerGlobalReceiver(BonfirePayloads.Rest.TYPE,
				(payload, context) -> SoulsBonfire.onRestRequest(context.player(), payload.pos()));
		ServerPlayNetworking.registerGlobalReceiver(BonfirePayloads.LevelUp.TYPE,
				(payload, context) -> SoulsBonfire.onLevelUp(context.player(), payload.attributes()));
		ServerPlayNetworking.registerGlobalReceiver(BonfirePayloads.Leave.TYPE,
				(payload, context) -> SoulsBonfire.onLeave(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(AttackPayloads.Request.TYPE,
				(payload, context) -> SoulsAttack.onRequest(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(AttackPayloads.Release.TYPE,
				(payload, context) -> SoulsAttack.onRelease(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(AttackPayloads.Grip.TYPE,
				(payload, context) -> SoulsGrip.onKey(context.player(), payload.leftWeapon()));
		ServerPlayNetworking.registerGlobalReceiver(AttackPayloads.Torch.TYPE,
				(payload, context) -> SoulsTorch.onKey(context.player()));
		net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register((entity, viewer) -> {
			if (entity instanceof ServerPlayer player) {
				SoulsTorch.onStartTracking(player, viewer);
				SoulsGrip.onStartTracking(player, viewer);
				SoulsPresence.onStartTracking(player, viewer);
			}
		});
		// A lit campfire is a bonfire (SoulsBonfire).
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) ->
				player instanceof ServerPlayer serverPlayer ? SoulsBonfire.onUseBlock(serverPlayer, hand, hit)
						: net.minecraft.world.InteractionResult.PASS);
		ServerPlayNetworking.registerGlobalReceiver(RollPayloads.Request.TYPE,
				(payload, context) -> SoulsRoll.onRequest(context.player(), payload));
		// DS2 dodge i-frames (TAE 110100 and the Agility share of 110300, agility.md): hits are rejected.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer player && (SoulsRoll.isInvincible(player) || SoulsAttack.isInvulnerable(player)
						|| SoulsAttack.parries(player, source)))
						&& !SoulsAttack.isVictimInvulnerable(entity)
						&& !SoulsAttack.isHeld(source.getEntity()));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				{
					SoulsCommands.register(dispatcher);
					dev.soulsmode.ascetic.SoulsAscetic.register(dispatcher);
				});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof net.minecraft.world.entity.monster.Giant giant && SoulsGiants.isLastGiant(giant)) {
				SoulsGiants.onDeath(giant, source.getEntity());
			}
			if (entity instanceof ServerPlayer player) {
				Bloodstains.onDeath(player);
				SoulsHollow.onDeath(player);
				SoulsAttack.reset(player);
				SoulsPoise.forget(player);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			Bloodstains.onRespawn(newPlayer);
			SoulsAttack.reset(newPlayer);
			SoulsPoise.forget(newPlayer);
			// The new player entity starts one-handed (design choice: what DS2 does on death is not traced).
			SoulsGrip.forget(newPlayer);
			SoulsStamina.reset(newPlayer);
			SoulsStats.apply(newPlayer);
			// Respawning applies SpEffect 110000010 like resting (docs/research/bonfire.md).
			SoulsMagic.restore(newPlayer, 100);
			if (!alive) {
				newPlayer.setHealth(newPlayer.getMaxHealth());
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			SoulsWorld.onJoin(handler.getPlayer());
			SoulsGameMode.updateBase(handler.getPlayer());
			SoulsStats.apply(handler.getPlayer());
		});
		ServerTickEvents.END_SERVER_TICK.register(Bloodstains::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsStamina::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsAttack::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsHitReaction::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsPoise::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsThrown::tick);
		// Breaking an active bonfire: the giant comes (the user's design, SoulsGiants).
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
			if (player instanceof ServerPlayer serverPlayer && SoulsGameMode.isActive(serverPlayer)
					&& ActiveBonfires.get(serverPlayer.server).isActive(world.dimension().location(), pos)) {
				ActiveBonfires.get(serverPlayer.server).forget(world.dimension().location(), pos);
				SoulsGiants.onBonfireBroken(serverPlayer, pos);
			}
		});
		net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register(SoulsGiants::onStartTracking);
		ServerTickEvents.END_SERVER_TICK.register(SoulsTorch::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsGrip::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsHollow::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsResin::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsStick::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsSoulItems::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsZombies::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsBow::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsBossBars::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsIllagers::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsInvasions::tick);
		ServerPlayNetworking.registerGlobalReceiver(dev.soulsmode.network.BowAimPayload.TYPE,
				(payload, context) -> SoulsBow.setAiming(context.player(), payload.aiming()));
		ServerTickEvents.END_SERVER_TICK.register(SoulsConsumables::tick);
		ServerTickEvents.END_SERVER_TICK.register(dev.soulsmode.entity.SoulsBullet::tickEmitters);
		ServerTickEvents.END_SERVER_TICK.register(SoulsSpEffects::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsEquipLoad::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsPresence::tick);
		ServerTickEvents.END_SERVER_TICK.register(SoulsBonfire::tick);
		ServerTickEvents.END_SERVER_TICK.register(dev.soulsmode.ascetic.SoulsAscetic::tick);
		// Bonfire Ascetic zones: a hostile mob gets its zone's cycle as soon as it is added to the level.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof net.minecraft.world.entity.Mob mob) {
				dev.soulsmode.ascetic.SoulsAscetic.updateMob(mob);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(SoulsPerception::tick);
		// DS2 perception: a mob hurt by a Souls Mode player perceives it (vanilla's hurt-by targeting stays).
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) ->
				SoulsPerception.onHurt(entity, source));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Bloodstains.forget(handler.getPlayer());
			SoulsStamina.forget(handler.getPlayer());
			SoulsRoll.forget(handler.getPlayer());
			SoulsAttack.forget(handler.getPlayer());
			SoulsPoise.forget(handler.getPlayer());
			SoulsThrown.forget(handler.getPlayer());
			SoulsTorch.forget(handler.getPlayer());
			SoulsGrip.forget(handler.getPlayer());
			SoulsHollow.forget(handler.getPlayer());
			SoulsResin.forget(handler.getPlayer());
			SoulsSoulItems.forget(handler.getPlayer());
			SoulsBow.forget(handler.getPlayer());
			SoulsConsumables.forget(handler.getPlayer());
			SoulsMagic.forget(handler.getPlayer());
			SoulsPresence.forget(handler.getPlayer());
			SoulsBonfire.forget(handler.getPlayer());
			SoulsPerception.forget(handler.getPlayer());
		});
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
