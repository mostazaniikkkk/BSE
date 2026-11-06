package dev.soulsmode.network;

import dev.soulsmode.SoulsMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: whether the player is in Souls Mode, and their DS2 stamina ({@code max == 0} while no class
 * is chosen: no bar yet), and whether sprinting is refused (no stamina, or the cooldown after running out).
 */
public record StaminaPayload(boolean soulsMode, float current, float max, boolean sprintBlocked)
		implements CustomPacketPayload {
	public static final Type<StaminaPayload> TYPE = new Type<>(SoulsMode.id("stamina"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StaminaPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, StaminaPayload::soulsMode,
			ByteBufCodecs.FLOAT, StaminaPayload::current,
			ByteBufCodecs.FLOAT, StaminaPayload::max,
			ByteBufCodecs.BOOL, StaminaPayload::sprintBlocked,
			StaminaPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
