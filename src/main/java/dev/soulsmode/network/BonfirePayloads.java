package dev.soulsmode.network;

import dev.soulsmode.KnownBonfire;
import dev.soulsmode.SoulsMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class BonfirePayloads {
	private BonfirePayloads() {
	}

	/**
	 * Server -> client: the player is resting at a bonfire (or the data changed while resting). {@code attributes}
	 * is empty while no class is chosen. {@code names} runs parallel to {@code bonfires}: each one's name, "" if unnamed.
	 */
	public record Open(List<Integer> attributes, int souls, int soulMemory, List<KnownBonfire> bonfires,
			BlockPos current, List<String> names) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(SoulsMode.id("bonfire_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Open::attributes,
				ByteBufCodecs.VAR_INT, Open::souls,
				ByteBufCodecs.VAR_INT, Open::soulMemory,
				KnownBonfire.STREAM_CODEC.apply(ByteBufCodecs.list()), Open::bonfires,
				BlockPos.STREAM_CODEC, Open::current,
				ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list()), Open::names,
				Open::new);

		/** The name of the bonfire being rested at, "" if it has none. */
		public String currentName(ResourceLocation dimension) {
			for (int i = 0; i < bonfires.size() && i < names.size(); i++) {
				if (bonfires.get(i).isAt(dimension, current)) {
					return names.get(i);
				}
			}
			return "";
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Largest bonfire picture accepted (PNG bytes). */
	public static final int SHOT_MAX_BYTES = 512 * 1024;

	/**
	 * Server -> client: take this bonfire's picture now (the user's design, 2026-11-02: like DS2's travel menu, each
	 * bonfire shows a picture of its place; the first player who uses it takes it).
	 */
	/** Server -> client: every active bonfire ("dimension|BlockPos.asLong"), for the sword in its model. */
	public record Active(List<String> keys) implements CustomPacketPayload {
		public static final Type<Active> TYPE = new Type<>(SoulsMode.id("bonfire_active"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Active> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Active::keys, Active::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record ShotRequest(ResourceLocation dimension, BlockPos pos) implements CustomPacketPayload {
		public static final Type<ShotRequest> TYPE = new Type<>(SoulsMode.id("bonfire_shot_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ShotRequest> CODEC = StreamCodec.composite(
				ResourceLocation.STREAM_CODEC, ShotRequest::dimension,
				BlockPos.STREAM_CODEC, ShotRequest::pos,
				ShotRequest::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: the picture of a bonfire (PNG), or server -> client: a stored one for the travel menu. */
	public record Shot(ResourceLocation dimension, BlockPos pos, byte[] png) implements CustomPacketPayload {
		public static final Type<Shot> TYPE = new Type<>(SoulsMode.id("bonfire_shot"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Shot> CODEC = StreamCodec.composite(
				ResourceLocation.STREAM_CODEC, Shot::dimension,
				BlockPos.STREAM_CODEC, Shot::pos,
				ByteBufCodecs.byteArray(SHOT_MAX_BYTES), Shot::png,
				Shot::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: send me the picture of this known bonfire, if there is one. */
	public record ShotFetch(ResourceLocation dimension, BlockPos pos) implements CustomPacketPayload {
		public static final Type<ShotFetch> TYPE = new Type<>(SoulsMode.id("bonfire_shot_fetch"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ShotFetch> CODEC = StreamCodec.composite(
				ResourceLocation.STREAM_CODEC, ShotFetch::dimension,
				BlockPos.STREAM_CODEC, ShotFetch::pos,
				ShotFetch::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: confirm the level-up screen with these attribute values. */
	public record LevelUp(List<Integer> attributes) implements CustomPacketPayload {
		public static final Type<LevelUp> TYPE = new Type<>(SoulsMode.id("bonfire_level_up"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LevelUp> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), LevelUp::attributes,
				LevelUp::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> clients: a player sat down at a bonfire ({@code sitting}) or stood up. */
	public record Pose(int entityId, boolean sitting) implements CustomPacketPayload {
		public static final Type<Pose> TYPE = new Type<>(SoulsMode.id("bonfire_pose"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Pose> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Pose::entityId,
				ByteBufCodecs.BOOL, Pose::sitting,
				Pose::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: rest at the lit campfire at {@code pos} (the use key near it, see SoulsBonfire.nearby). */
	public record Rest(BlockPos pos) implements CustomPacketPayload {
		public static final Type<Rest> TYPE = new Type<>(SoulsMode.id("bonfire_rest"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Rest> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, Rest::pos,
				Rest::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: travel from the bonfire the player rests at to a known one. */
	public record Travel(ResourceLocation dimension, BlockPos pos) implements CustomPacketPayload {
		public static final Type<Travel> TYPE = new Type<>(SoulsMode.id("bonfire_travel"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Travel> CODEC = StreamCodec.composite(
				ResourceLocation.STREAM_CODEC, Travel::dimension,
				BlockPos.STREAM_CODEC, Travel::pos,
				Travel::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: the player left the bonfire menu. */
	public record Leave() implements CustomPacketPayload {
		public static final Type<Leave> TYPE = new Type<>(SoulsMode.id("bonfire_leave"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Leave> CODEC = StreamCodec.unit(new Leave());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: name the bonfire the player rests at (BonfireNames; "" clears it). */
	public record Rename(String name) implements CustomPacketPayload {
		public static final Type<Rename> TYPE = new Type<>(SoulsMode.id("bonfire_rename"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Rename> CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(256), Rename::name,
				Rename::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: burn a Bonfire Ascetic at the bonfire the player rests at (dev.soulsmode.ascetic.SoulsAscetic). */
	public record BurnAscetic() implements CustomPacketPayload {
		public static final Type<BurnAscetic> TYPE = new Type<>(SoulsMode.id("bonfire_burn_ascetic"));
		public static final StreamCodec<RegistryFriendlyByteBuf, BurnAscetic> CODEC = StreamCodec.unit(new BurnAscetic());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
