package dev.soulsmode.network;

import dev.soulsmode.SoulsMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The class selection screen. */
public final class ClassPayloads {
	private ClassPayloads() {
	}

	/** Server -> client: open the class selection screen. */
	public record Open(boolean unused) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(SoulsMode.id("class_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, Open::unused, Open::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: the chosen class (StartingClass index). */
	public record Choose(int index) implements CustomPacketPayload {
		public static final Type<Choose> TYPE = new Type<>(SoulsMode.id("class_choose"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Choose::index, Choose::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
