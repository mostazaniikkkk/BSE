package dev.soulsmode.network;

import dev.soulsmode.SoulsMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The player's own reaction to a hit (SoulsPoise, docs/research/poise.md). */
public final class PoisePayloads {
	private PoisePayloads() {
	}

	/** No reaction (a reaction playing ends). */
	public static final int STOP = 0;
	/** Reaction 1, poise held: the upper-body DmgKnockBackLight over locomotion (poise.md s.6). */
	public static final int HELD = 1;
	/** Reactions 2 / 3: BT_Damage DmgKnockBack ({@code category} 30 / 40, hit_reaction.md s.2). */
	public static final int KNOCKBACK = 2;

	/**
	 * Server -> clients: player {@code entityId} reacts. {@code thrust} / {@code up}: DmgSlashOrThrust / DmgDownOrUp;
	 * {@code angle}: DmgDirection (degrees, positive = attacker on the right); {@code heavy}: DmgDistance / 5 (the
	 * Light <-> Heavy Blend2); {@code speed}: the clip's play speed; {@code yaw}: the facing the root motion follows;
	 * {@code moveLock}: seconds without walking (the local player's own movement is client-side).
	 */
	public record Reaction(int entityId, int kind, int category, boolean thrust, boolean up, float angle, float heavy,
			float speed, float yaw, float moveLock) implements CustomPacketPayload {
		public static final Type<Reaction> TYPE = new Type<>(SoulsMode.id("player_reaction"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Reaction> CODEC = StreamCodec.of(
				(buf, r) -> {
					buf.writeVarInt(r.entityId);
					buf.writeVarInt(r.kind);
					buf.writeVarInt(r.category);
					buf.writeBoolean(r.thrust);
					buf.writeBoolean(r.up);
					buf.writeFloat(r.angle);
					buf.writeFloat(r.heavy);
					buf.writeFloat(r.speed);
					buf.writeFloat(r.yaw);
					buf.writeFloat(r.moveLock);
				},
				buf -> new Reaction(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
						buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
						buf.readFloat()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
