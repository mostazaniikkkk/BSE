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

	private SoulsAttachments() {
	}

	public static void init() {
		// Forces class loading so the attachment types register during mod init.
	}
}
