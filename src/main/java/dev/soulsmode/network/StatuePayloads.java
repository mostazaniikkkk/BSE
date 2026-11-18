package dev.soulsmode.network;

import dev.soulsmode.SoulsMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Petrified statues (dev.soulsmode.SoulsStatues): the "Use Fragrant Branch of Yore?" dialog and its answer. */
public final class StatuePayloads {
	private StatuePayloads() {
	}

	/** Server -> client: ask whether to use a branch on the statue (MapEvent 1002). */
	public record Ask(int entityId) implements CustomPacketPayload {
		public static final Type<Ask> TYPE = new Type<>(SoulsMode.id("statue_ask"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ask> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Ask::entityId, Ask::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: the player answered yes. */
	public record Use(int entityId) implements CustomPacketPayload {
		public static final Type<Use> TYPE = new Type<>(SoulsMode.id("statue_use"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Use> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Use::entityId, Use::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
