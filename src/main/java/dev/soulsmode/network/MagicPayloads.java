package dev.soulsmode.network;

import dev.soulsmode.SoulsMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Spells and attunement (dev.soulsmode.SoulsMagic). */
public final class MagicPayloads {
	private MagicPayloads() {
	}

	public record Entry(ResourceLocation spell, int uses, int max, int slots) {
		public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
				ResourceLocation.STREAM_CODEC, Entry::spell,
				ByteBufCodecs.VAR_INT, Entry::uses,
				ByteBufCodecs.VAR_INT, Entry::max,
				ByteBufCodecs.VAR_INT, Entry::slots,
				Entry::new);
	}

	/** Server -> client: the attuned spells, the active one (-1: none) and the attunement capacity. */
	public record State(List<Entry> attuned, int active, int capacity) implements CustomPacketPayload {
		public static final Type<State> TYPE = new Type<>(SoulsMode.id("spell_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(
				Entry.CODEC.apply(ByteBufCodecs.list()), State::attuned,
				ByteBufCodecs.VAR_INT, State::active,
				ByteBufCodecs.VAR_INT, State::capacity,
				State::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server, at a bonfire: attune the spell in this inventory slot. */
	public record Attune(int slot) implements CustomPacketPayload {
		public static final Type<Attune> TYPE = new Type<>(SoulsMode.id("spell_attune"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Attune> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Attune::slot, Attune::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server, at a bonfire: take the attuned spell at this index back to the inventory. */
	public record Unattune(int index) implements CustomPacketPayload {
		public static final Type<Unattune> TYPE = new Type<>(SoulsMode.id("spell_unattune"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Unattune> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Unattune::index, Unattune::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: switch to the next attuned spell. */
	public record Cycle() implements CustomPacketPayload {
		public static final Type<Cycle> TYPE = new Type<>(SoulsMode.id("spell_cycle"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Cycle> CODEC = StreamCodec.unit(new Cycle());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
