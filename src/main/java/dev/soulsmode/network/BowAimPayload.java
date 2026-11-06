package dev.soulsmode.network;

import dev.soulsmode.SoulsMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the bow's aim mode started or ended (the other hand's button held, SoulsBow). */
public record BowAimPayload(boolean aiming) implements CustomPacketPayload {
	public static final Type<BowAimPayload> TYPE = new Type<>(SoulsMode.id("bow_aim"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BowAimPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, BowAimPayload::aiming, BowAimPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
