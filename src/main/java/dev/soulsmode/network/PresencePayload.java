package dev.soulsmode.network;

import dev.soulsmode.SoulsMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> clients: a player's Souls Mode flag and whether it carries arrows (SoulsPresence). */
public record PresencePayload(int entityId, boolean soulsMode, boolean arrows) implements CustomPacketPayload {
	public static final Type<PresencePayload> TYPE = new Type<>(SoulsMode.id("presence"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PresencePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PresencePayload::entityId,
			ByteBufCodecs.BOOL, PresencePayload::soulsMode,
			ByteBufCodecs.BOOL, PresencePayload::arrows,
			PresencePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
