package dev.soulsmode.network;

import dev.soulsmode.SoulsMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Server -> client: data for the Souls Mode stats panel in the inventory (attributes, soul memory, hollowing, weight). */
public record StatsPayload(List<Integer> attributes, int soulMemory, int hollowing, float weight)
		implements CustomPacketPayload {
	public static final Type<StatsPayload> TYPE = new Type<>(SoulsMode.id("stats"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StatsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), StatsPayload::attributes,
			ByteBufCodecs.VAR_INT, StatsPayload::soulMemory,
			ByteBufCodecs.VAR_INT, StatsPayload::hollowing,
			ByteBufCodecs.FLOAT, StatsPayload::weight,
			StatsPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
