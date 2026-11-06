package dev.soulsmode.network;

import dev.soulsmode.SoulsMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> clients: a player became the victim of a critical or a parry. Its animations: {@code first}, then
 * {@code second} from {@code secondAt} s (looping if {@code secondLoops}), then {@code third} from {@code thirdAt} s (empty: none); the victim
 * cannot move until {@code moveAt} s and cannot act until {@code actAt} s.
 */
public record VictimPayload(int entityId, String first, String second, float secondAt, boolean secondLoops, String third, float thirdAt,
		float moveAt, float actAt) implements CustomPacketPayload {
	public static final Type<VictimPayload> TYPE = new Type<>(SoulsMode.id("victim"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VictimPayload> CODEC = new StreamCodec<>() {
		@Override
		public VictimPayload decode(RegistryFriendlyByteBuf buf) {
			return new VictimPayload(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readFloat(), buf.readBoolean(), buf.readUtf(),
					buf.readFloat(), buf.readFloat(), buf.readFloat());
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, VictimPayload p) {
			buf.writeVarInt(p.entityId());
			buf.writeUtf(p.first());
			buf.writeUtf(p.second());
			buf.writeFloat(p.secondAt());
			buf.writeBoolean(p.secondLoops());
			buf.writeUtf(p.third());
			buf.writeFloat(p.thirdAt());
			buf.writeFloat(p.moveAt());
			buf.writeFloat(p.actAt());
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
