package dev.soulsmode.network;

import dev.soulsmode.SoulsMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class RollPayloads {
	private RollPayloads() {
	}

	/** Client -> server: dodge input with the horizontal movement direction; (0, 0) means no direction. */
	/** {@code predicted}: the client already plays the dodge from the press (latency compensation, SoulsRoll). */
	public record Request(float dx, float dz, boolean predicted) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(SoulsMode.id("roll_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
				ByteBufCodecs.FLOAT, Request::dx,
				ByteBufCodecs.FLOAT, Request::dz,
				ByteBufCodecs.BOOL, Request::predicted,
				Request::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> clients: a player started a dodge ({@code kind} = Dodge ordinal) towards (dx, dz). */
	/** {@code speed}: the dodge animation's playback speed (Dodge.speed). */
	public record Start(int entityId, int kind, float dx, float dz, float speed) implements CustomPacketPayload {
		public static final Type<Start> TYPE = new Type<>(SoulsMode.id("roll_start"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Start> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Start::entityId,
				ByteBufCodecs.VAR_INT, Start::kind,
				ByteBufCodecs.FLOAT, Start::dx,
				ByteBufCodecs.FLOAT, Start::dz,
				ByteBufCodecs.FLOAT, Start::speed,
				Start::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
