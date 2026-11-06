package dev.soulsmode;

import dev.soulsmode.block.BloodstainBlockEntity;
import dev.soulsmode.block.SoulsBlocks;
import dev.soulsmode.ds2.Souls;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DS2 bloodstain (see docs/research/souls.md): on death every held soul moves into a single stain,
 * replacing (and losing) the previous one; touching it gives the souls back without counting towards
 * soul memory. In the world it is a flat {@code soulsmode:bloodstain} block; right-click touches it.
 */
public final class Bloodstains {
	private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55f, 0.02f, 0.02f), 1.4f);
	/** How far below the death point we look for a floor to put the stain on. */
	private static final int FLOOR_SEARCH_DEPTH = 16;
	/** Last block each player stood on, for deaths over the void, lava or a long fall. */
	private static final Map<UUID, GlobalPos> LAST_GROUND = new HashMap<>();

	private Bloodstains() {
	}

	/** FUN_14026af40: stain.souls = held, held = 0; the previous stain's souls are gone. */
	public static void onDeath(ServerPlayer player) {
		if (!SoulsGameMode.isActive(player)) {
			return;
		}
		removeBlock(player);
		player.removeAttached(SoulsAttachments.BLOODSTAIN);
		int souls = ExperienceSouls.get(player);
		ServerLevel level = player.serverLevel();
		BlockPos spot = findFloor(level, player.blockPosition());
		if (spot == null) {
			GlobalPos ground = LAST_GROUND.get(player.getUUID());
			ServerLevel groundLevel = ground == null ? null : player.server.getLevel(ground.dimension());
			if (groundLevel != null) {
				level = groundLevel;
				spot = findFloor(level, ground.pos());
			}
		}
		if (spot == null) {
			// Design choice: with nowhere to put it, the stain (and its souls) is lost.
			player.sendSystemMessage(Component.translatable("message.soulsmode.bloodstain.lost", souls));
			return;
		}
		level.setBlockAndUpdate(spot, SoulsBlocks.BLOODSTAIN.defaultBlockState());
		if (level.getBlockEntity(spot) instanceof BloodstainBlockEntity stainEntity) {
			stainEntity.setOwner(player.getUUID());
		}
		player.setAttached(SoulsAttachments.BLOODSTAIN, new Bloodstain(level.dimension().location(), spot, souls));
	}

	/** Held souls went into the stain, so the respawned player starts with none (also under keepInventory). */
	public static void onRespawn(ServerPlayer player) {
		if (SoulsGameMode.isActive(player)) {
			ExperienceSouls.set(player, 0);
		}
	}

	/** FUN_14026b500: touching your current stain gives its souls back and removes it. */
	public static void onTouch(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.serverLevel();
		burst(level, pos);
		if (!(level.getBlockEntity(pos) instanceof BloodstainBlockEntity stainEntity)
				|| !player.getUUID().equals(stainEntity.owner())) {
			return;
		}
		Bloodstain stain = player.getAttached(SoulsAttachments.BLOODSTAIN);
		boolean current = stain != null && stain.pos().equals(pos) && stain.dimension().equals(level.dimension().location());
		level.removeBlock(pos, false);
		if (!current) {
			// An older stain of this player that couldn't be removed at death (unloaded chunk): its souls are gone.
			return;
		}
		Souls souls = new Souls(ExperienceSouls.get(player), 0);
		souls.recover(stain.souls());
		ExperienceSouls.set(player, souls.held());
		player.removeAttached(SoulsAttachments.BLOODSTAIN);
		level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 0.5f);
		player.displayClientMessage(Component.translatable("message.soulsmode.bloodstain.recovered", stain.souls()), true);
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.onGround() && !player.isInLava()) {
				LAST_GROUND.put(player.getUUID(), GlobalPos.of(player.level().dimension(), player.blockPosition()));
			}
		}
	}

	public static void forget(ServerPlayer player) {
		LAST_GROUND.remove(player.getUUID());
	}

	private static void burst(ServerLevel level, BlockPos pos) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.1;
		double z = pos.getZ() + 0.5;
		level.sendParticles(BLOOD, x, y + 0.2, z, 24, 0.35, 0.15, 0.35, 0);
		level.sendParticles(ParticleTypes.SOUL, x, y + 0.3, z, 10, 0.25, 0.3, 0.25, 0.04);
		level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.0f, 0.8f);
	}

	/** First dry, replaceable spot at or below {@code start} that sits on a sturdy top face. */
	private static BlockPos findFloor(ServerLevel level, BlockPos start) {
		BlockPos.MutableBlockPos pos = start.mutable();
		for (int i = 0; i <= FLOOR_SEARCH_DEPTH && pos.getY() > level.getMinBuildHeight(); i++, pos.move(Direction.DOWN)) {
			BlockState state = level.getBlockState(pos);
			if (!state.canBeReplaced()) {
				return null;
			}
			BlockPos below = pos.below();
			if (level.getFluidState(pos).isEmpty() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
				return pos.immutable();
			}
		}
		return null;
	}

	private static void removeBlock(ServerPlayer player) {
		Bloodstain old = player.getAttached(SoulsAttachments.BLOODSTAIN);
		if (old == null) {
			return;
		}
		ServerLevel level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, old.dimension()));
		if (level != null && level.isLoaded(old.pos()) && level.getBlockState(old.pos()).is(SoulsBlocks.BLOODSTAIN)) {
			level.removeBlock(old.pos(), false);
		}
		// If its chunk isn't loaded the block stays; touching it later just removes it.
	}
}
