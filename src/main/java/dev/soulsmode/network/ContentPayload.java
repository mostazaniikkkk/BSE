package dev.soulsmode.network;

import dev.soulsmode.SoulsMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: the datapack content files (dev.soulsmode.content.SoulsContent), one file per payload between a
 * {@link #BEGIN} and an {@link #END}, so no payload gets near the packet size limit.
 */
public record ContentPayload(int op, int kind, ResourceLocation id, String json) implements CustomPacketPayload {
	public static final int BEGIN = 0;
	public static final int FILE = 1;
	public static final int END = 2;
	/** Longest file accepted, in characters. */
	private static final int MAX_JSON = 262144;

	public static final Type<ContentPayload> TYPE = new Type<>(SoulsMode.id("content"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ContentPayload> CODEC = new StreamCodec<>() {
		@Override
		public ContentPayload decode(RegistryFriendlyByteBuf buf) {
			return new ContentPayload(buf.readByte(), buf.readByte(), buf.readResourceLocation(), buf.readUtf(MAX_JSON));
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, ContentPayload p) {
			buf.writeByte(p.op());
			buf.writeByte(p.kind());
			buf.writeResourceLocation(p.id());
			buf.writeUtf(p.json(), MAX_JSON);
		}
	};

	public static ContentPayload marker(int op) {
		return new ContentPayload(op, 0, SoulsMode.id("content"), "");
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
