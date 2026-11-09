package dev.soulsmode.network;

import dev.soulsmode.SoulsMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class AttackPayloads {
	private AttackPayloads() {
	}

	/**
	 * Client -> server: attack input with the main hand or, if {@code leftHand}, the off hand; (dx, dz) is the held
	 * movement direction, (0, 0) for none; {@code flick}: the forward push of the movement input that the jump attack
	 * and the kick need (FUN_140396e60, read on the client where the input is).
	 */
	public record Request(boolean heavy, boolean leftHand, float dx, float dz, boolean flick) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(SoulsMode.id("attack_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, Request::heavy,
				ByteBufCodecs.BOOL, Request::leftHand,
				ByteBufCodecs.FLOAT, Request::dx,
				ByteBufCodecs.FLOAT, Request::dz,
				ByteBufCodecs.BOOL, Request::flick,
				Request::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> clients: a player started an attack of a weapon ({@code actionType} = Moveset.Type ordinal) held in the
	 * left hand if {@code weaponLeft} (the animation is mirrored), with both hands if {@code twoHanded}, facing {@code yaw}.
	 */
	public record Start(int entityId, ResourceLocation moveset, boolean weaponLeft, boolean twoHanded, int actionType,
			float yaw) implements CustomPacketPayload {
		public static final Type<Start> TYPE = new Type<>(SoulsMode.id("attack_start"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Start> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Start::entityId,
				ResourceLocation.STREAM_CODEC, Start::moveset,
				ByteBufCodecs.BOOL, Start::weaponLeft,
				ByteBufCodecs.BOOL, Start::twoHanded,
				ByteBufCodecs.VAR_INT, Start::actionType,
				ByteBufCodecs.FLOAT, Start::yaw,
				Start::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> clients: a player's held guard ended. */
	public record Stop(int entityId) implements CustomPacketPayload {
		public static final Type<Stop> TYPE = new Type<>(SoulsMode.id("attack_stop"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Stop> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Stop::entityId, Stop::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: the button holding a guard was released. */
	public record Release(boolean unused) implements CustomPacketPayload {
		public static final Type<Release> TYPE = new Type<>(SoulsMode.id("attack_release"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Release> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, Release::unused, Release::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: toggle holding the right (or, if {@code leftWeapon}, the left) weapon with both hands. */
	public record Grip(boolean leftWeapon) implements CustomPacketPayload {
		public static final Type<Grip> TYPE = new Type<>(SoulsMode.id("grip_toggle"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Grip> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, Grip::leftWeapon, Grip::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> clients, before a guard's Start: the player's guard values (weapon_arts.md s.8.5), which depend on their
	 * stats. {@code exitBlend} is SM 0x210e's exit blend for the guard's GuardCancelSpeedCategory, in seconds;
	 * {@code speed} is GuardSpeed, the guard clip's playback speed.
	 */
	public record GuardLayer(int entityId, float exitBlend, float speed) implements CustomPacketPayload {
		public static final Type<GuardLayer> TYPE = new Type<>(SoulsMode.id("guard_layer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, GuardLayer> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, GuardLayer::entityId,
				ByteBufCodecs.FLOAT, GuardLayer::exitBlend,
				ByteBufCodecs.FLOAT, GuardLayer::speed,
				GuardLayer::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> clients: the swing speed of the attack the player just started (dev.soulsmode.ds2.SwingSpeed): its clip
	 * runs at {@code rate} inside the AttackSpeedControl 151 window. Sent after Start, only when the rate is not 1.
	 */
	public record Swing(int entityId, float rate) implements CustomPacketPayload {
		public static final Type<Swing> TYPE = new Type<>(SoulsMode.id("attack_swing"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Swing> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Swing::entityId,
				ByteBufCodecs.FLOAT, Swing::rate,
				Swing::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> clients: the player's grip ({@link dev.soulsmode.ds2.Moveset.Grip} ordinal) changed. */
	public record GripState(int entityId, int grip) implements CustomPacketPayload {
		public static final Type<GripState> TYPE = new Type<>(SoulsMode.id("grip_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, GripState> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, GripState::entityId,
				ByteBufCodecs.VAR_INT, GripState::grip,
				GripState::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Server -> clients: the player starts the grip change animation for a weapon put away at {@code storage}
	 * (WeaponTypeParam +0x24); {@code mirrored} when it is the right weapon (SM_ChangeWeapon_R); {@code speed} is its
	 * WeaponChangeSpeed (Agility).
	 */
	public record GripChange(int entityId, int storage, boolean mirrored, float speed) implements CustomPacketPayload {
		public static final Type<GripChange> TYPE = new Type<>(SoulsMode.id("grip_change"));
		public static final StreamCodec<RegistryFriendlyByteBuf, GripChange> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, GripChange::entityId,
				ByteBufCodecs.VAR_INT, GripChange::storage,
				ByteBufCodecs.BOOL, GripChange::mirrored,
				ByteBufCodecs.FLOAT, GripChange::speed,
				GripChange::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: whether the player is locked on (the lock-on runs on the client). */
	public record LockState(boolean locked, int target) implements CustomPacketPayload {
		public static final Type<LockState> TYPE = new Type<>(SoulsMode.id("lock_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LockState> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, LockState::locked, ByteBufCodecs.VAR_INT, LockState::target, LockState::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client -> server: the torch key (light the torch, or put it away while lit). */
	public record Torch(boolean unused) implements CustomPacketPayload {
		public static final Type<Torch> TYPE = new Type<>(SoulsMode.id("torch"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Torch> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, Torch::unused, Torch::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> clients: whether a player holds the torch in the left hand (it replaces the left weapon). */
	public record TorchHeld(int entityId, boolean held) implements CustomPacketPayload {
		public static final Type<TorchHeld> TYPE = new Type<>(SoulsMode.id("torch_held"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TorchHeld> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, TorchHeld::entityId,
				ByteBufCodecs.BOOL, TorchHeld::held,
				TorchHeld::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
