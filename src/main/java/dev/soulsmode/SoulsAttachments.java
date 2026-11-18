package dev.soulsmode;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * Per-player data stored through Fabric's Data Attachment API (saved with the player NBT).
 */
public final class SoulsAttachments {
	/** Whether the player is in Souls Mode. Survives death so respawning keeps the mode. */
	public static final AttachmentType<Boolean> SOULS_MODE = AttachmentRegistry.create(
			SoulsMode.id("souls_mode"),
			builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	/** Class, attributes and soul memory. Kept across deaths and when leaving the mode. */
	public static final AttachmentType<SoulsData> SOULS_DATA = AttachmentRegistry.create(
			SoulsMode.id("souls_data"),
			builder -> builder.persistent(SoulsData.CODEC).copyOnDeath());

	/** The last bloodstain. A new death overwrites it, losing the souls it held. */
	public static final AttachmentType<Bloodstain> BLOODSTAIN = AttachmentRegistry.create(
			SoulsMode.id("bloodstain"),
			builder -> builder.persistent(Bloodstain.CODEC).copyOnDeath());

	/** DS2 torch time in seconds (PlayerItemData TorchTime, saved with the items). Kept across deaths. */
	public static final AttachmentType<Float> TORCH_TIME = AttachmentRegistry.create(
			SoulsMode.id("torch_time"),
			builder -> builder.persistent(Codec.FLOAT).copyOnDeath());

	/** Bonfires the player has rested at, in the order they were first found. Kept across deaths. */
	public static final AttachmentType<java.util.List<KnownBonfire>> BONFIRES = AttachmentRegistry.create(
			SoulsMode.id("bonfires"),
			builder -> builder.persistent(KnownBonfire.CODEC.listOf()).copyOnDeath());

	/** DS2 hollowing counter H, 0..32 (status +0x1ac, SoulsHollow). Kept across deaths; only has an effect in Souls Mode. */
	public static final AttachmentType<Integer> HOLLOWING = AttachmentRegistry.create(
			SoulsMode.id("hollowing"),
			builder -> builder.persistent(Codec.INT).copyOnDeath());

	/** Bonfires this player has kindled (SoulsBonfire: each one costs one more experience level than the last). */
	public static final AttachmentType<Integer> BONFIRES_KINDLED = AttachmentRegistry.create(
			SoulsMode.id("bonfires_kindled"),
			builder -> builder.persistent(Codec.INT).copyOnDeath());

	/** Active bonfires this player has broken in Souls Mode (SoulsGiants: every third one brings the Last Giant). */
	public static final AttachmentType<Integer> BONFIRES_BROKEN = AttachmentRegistry.create(
			SoulsMode.id("bonfires_broken"),
			builder -> builder.persistent(Codec.INT).copyOnDeath());

	/**
	 * The last bonfire rested at or travelled to (DS2's eventMgr+0x164 record, FUN_14044fe30, bonfire.md): where the
	 * Homeward Bone goes (SoulsHomeward). Kept across deaths.
	 */
	public static final AttachmentType<KnownBonfire> LAST_BONFIRE = AttachmentRegistry.create(
			SoulsMode.id("last_bonfire"),
			builder -> builder.persistent(KnownBonfire.CODEC).copyOnDeath());

	/** DS2's active covenant id (status +0x1ad; 0 = none; docs/research/covenants.md). Kept across deaths. */
	public static final AttachmentType<Integer> COVENANT = AttachmentRegistry.create(
			SoulsMode.id("covenant"),
			builder -> builder.persistent(Codec.INT).copyOnDeath());

	/** DS2's covenant points by covenant id (status +0x1c2 + 2 id); all kept when changing covenant. */
	public static final AttachmentType<java.util.Map<String, Integer>> COVENANT_POINTS = AttachmentRegistry.create(
			SoulsMode.id("covenant_points"),
			builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

	/** Covenant rewards already given (covenant id x 10 + rank; x 10 + 0 = the joining lot), like DS2's lot flags. */
	public static final AttachmentType<java.util.List<Integer>> COVENANT_REWARDS = AttachmentRegistry.create(
			SoulsMode.id("covenant_rewards"),
			builder -> builder.persistent(Codec.INT.listOf()).copyOnDeath());

	/** The player has already joined this Souls-like world once (SoulsWorld). */
	public static final AttachmentType<Boolean> SOULS_LIKE_JOINED = AttachmentRegistry.create(
			SoulsMode.id("souls_like_joined"),
			builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	/**
	 * Attuned spells in order (DS2's 14-entry array, docs/research/spells.md): each one an owned copy with its uses
	 * left. Kept across deaths, like the inventory's spells in DS2.
	 */
	public static final AttachmentType<java.util.List<SoulsMagic.Attuned>> ATTUNED = AttachmentRegistry.create(
			SoulsMode.id("attuned"),
			builder -> builder.persistent(SoulsMagic.Attuned.CODEC.listOf()).copyOnDeath());

	/** Index of the active spell in {@link #ATTUNED}, -1 for none (inv+0x259ef). */
	public static final AttachmentType<Integer> ACTIVE_SPELL = AttachmentRegistry.create(
			SoulsMode.id("active_spell"),
			builder -> builder.persistent(Codec.INT).copyOnDeath());

	/**
	 * A petrified statue (SoulsStatues): a mob held in stone until a Fragrant Branch of Yore frees it. Saved with the
	 * mob and synced so every client draws it in stone.
	 */
	public static final AttachmentType<Boolean> PETRIFIED = AttachmentRegistry.create(
			SoulsMode.id("petrified"),
			builder -> builder.persistent(Codec.BOOL)
					.syncWith(net.minecraft.network.codec.ByteBufCodecs.BOOL,
							net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.all()));

	/**
	 * The player died of petrification (SoulsStatus): the body is drawn in stone until it is removed. Not saved and not
	 * copied on death (the respawned player is a new entity).
	 */
	public static final AttachmentType<Boolean> PETRIFIED_DEATH = AttachmentRegistry.create(
			SoulsMode.id("petrified_death"),
			builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.BOOL,
					net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.all()));

	/**
	 * {petrification meter, curse meter (0..100), curse entry left (0..1)} for the player's HUD (SoulsStatus); synced to
	 * that player only.
	 */
	public static final AttachmentType<java.util.List<Float>> STATUS_METERS = AttachmentRegistry.create(
			SoulsMode.id("status_meters"),
			builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.FLOAT
							.apply(net.minecraft.network.codec.ByteBufCodecs.list()),
					net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.targetOnly()));

	private SoulsAttachments() {
	}

	public static void init() {
		// Forces class loading so the attachment types register during mod init.
	}
}
