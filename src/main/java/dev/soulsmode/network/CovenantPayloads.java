package dev.soulsmode.network;

import dev.soulsmode.SoulsMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The covenant altar's menu (SoulsCovenantAltar). */
public final class CovenantPayloads {
	private CovenantPayloads() {
	}

	/** Server -> client: open (or refresh) the altar menu with the player's covenant state. */
	public record Open(int covenant, int active, int rank, int points) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(SoulsMode.id("covenant_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Open::covenant, ByteBufCodecs.VAR_INT, Open::active, ByteBufCodecs.VAR_INT, Open::rank,
				ByteBufCodecs.VAR_INT, Open::points, Open::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: 0 = join (130381(id)), 1 = abandon (130381(0)). */
	public record Action(int covenant, int action) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(SoulsMode.id("covenant_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Action::covenant, ByteBufCodecs.VAR_INT, Action::action, Action::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
