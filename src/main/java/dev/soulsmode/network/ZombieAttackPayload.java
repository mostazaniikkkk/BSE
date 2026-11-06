package dev.soulsmode.network;

import dev.soulsmode.SoulsMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> clients: a zombie starts a c1020 attack animation ("" = none). */
public record ZombieAttackPayload(int entityId, String animation) implements CustomPacketPayload {
	public static final Type<ZombieAttackPayload> TYPE = new Type<>(SoulsMode.id("zombie_attack"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ZombieAttackPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ZombieAttackPayload::entityId, ByteBufCodecs.STRING_UTF8, ZombieAttackPayload::animation,
			ZombieAttackPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
