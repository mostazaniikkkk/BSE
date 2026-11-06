package dev.soulsmode;

import dev.soulsmode.network.BonfirePayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The bonfires' pictures for the travel menu (the user's design, 2026-11-02, after DS2's travel menu, which shows a
 * picture of each place): the first player who activates or rests at a bonfire without one takes a screenshot (client,
 * BonfireShotClient) and uploads it; the server keeps it with the world and sends it to whoever opens the travel menu.
 */
public final class BonfireShots {
	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
	/** How close the uploader must be to the bonfire (the use reach, SoulsBonfire). */
	private static final double MAX_DISTANCE = 8;

	private BonfireShots() {
	}

	private static Path file(MinecraftServer server, ResourceLocation dimension, BlockPos pos) {
		return server.getWorldPath(LevelResource.ROOT).resolve("soulsmode").resolve("bonfire_shots")
				.resolve(dimension.getNamespace() + "_" + dimension.getPath().replace('/', '_') + "_" + pos.getX() + "_"
						+ pos.getY() + "_" + pos.getZ() + ".png");
	}

	/** Asks this player for the picture if the bonfire has none yet. */
	public static void requestIfMissing(ServerPlayer player, ResourceLocation dimension, BlockPos pos) {
		if (!Files.exists(file(player.server, dimension, pos))) {
			ServerPlayNetworking.send(player, new BonfirePayloads.ShotRequest(dimension, pos.immutable()));
		}
	}

	public static void onUpload(ServerPlayer player, BonfirePayloads.Shot shot) {
		byte[] png = shot.png();
		if (png.length < PNG.length || png.length > BonfirePayloads.SHOT_MAX_BYTES
				|| !java.util.Arrays.equals(java.util.Arrays.copyOf(png, PNG.length), PNG)
				|| !player.level().dimension().location().equals(shot.dimension())
				|| player.distanceToSqr(shot.pos().getCenter()) > MAX_DISTANCE * MAX_DISTANCE
				|| !ActiveBonfires.get(player.server).isActive(shot.dimension(), shot.pos())) {
			return;
		}
		Path file = file(player.server, shot.dimension(), shot.pos());
		if (Files.exists(file)) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Files.write(file, png);
		} catch (IOException e) {
			SoulsMode.LOGGER.warn("[BSE] bonfire picture {}: {}", file, e.toString());
		}
	}

	/** Sends a known bonfire's picture to the player (travel menu). */
	public static void onFetch(ServerPlayer player, ResourceLocation dimension, BlockPos pos) {
		boolean known = player.getAttachedOrElse(SoulsAttachments.BONFIRES, java.util.List.<KnownBonfire>of()).stream()
				.anyMatch(b -> b.isAt(dimension, pos));
		Path file = file(player.server, dimension, pos);
		if (!known || !Files.exists(file)) {
			return;
		}
		try {
			ServerPlayNetworking.send(player, new BonfirePayloads.Shot(dimension, pos, Files.readAllBytes(file)));
		} catch (IOException e) {
			SoulsMode.LOGGER.warn("[BSE] bonfire picture {}: {}", file, e.toString());
		}
	}

	/** The campfire is gone: its picture goes with it. */
	public static void delete(MinecraftServer server, ResourceLocation dimension, BlockPos pos) {
		try {
			Files.deleteIfExists(file(server, dimension, pos));
		} catch (IOException e) {
			SoulsMode.LOGGER.warn("[BSE] bonfire picture: {}", e.toString());
		}
	}
}
