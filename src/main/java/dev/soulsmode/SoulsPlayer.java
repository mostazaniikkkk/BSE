package dev.soulsmode;

import dev.soulsmode.ds2.Attributes;
import dev.soulsmode.ds2.Ds2Data;
import dev.soulsmode.ds2.LevelUpMenu;
import dev.soulsmode.ds2.LevelUpSoulsTable;
import dev.soulsmode.ds2.Souls;
import dev.soulsmode.ds2.StartingClasses.StartingClass;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/** Souls Mode progression on a Minecraft player: the bridge between the ds2 rules and vanilla state. */
public final class SoulsPlayer {
	public enum LevelUpResult { OK, NO_CLASS, MAX_VALUE, NOT_ENOUGH_SOULS }

	private SoulsPlayer() {
	}

	public static Optional<SoulsData> data(ServerPlayer player) {
		return Optional.ofNullable(player.getAttached(SoulsAttachments.SOULS_DATA)).filter(d -> d.classIndex() != 0);
	}

	/** Picks a starting class: attributes reset to the class row, souls and soul memory are kept. */
	public static void chooseClass(ServerPlayer player, StartingClass startingClass) {
		int memory = Optional.ofNullable(player.getAttached(SoulsAttachments.SOULS_DATA)).map(SoulsData::soulMemory).orElse(0);
		player.setAttached(SoulsAttachments.SOULS_DATA, SoulsData.of(startingClass.index(), startingClass.attributes(), memory));
		SoulsStats.apply(player);
	}

	/** One level-up step on a slot: pays cost(current SL) like FUN_1401fb970, then raises the slot by one. */
	public static LevelUpResult levelUp(ServerPlayer player, int slot) {
		Optional<SoulsData> data = data(player);
		if (data.isEmpty()) {
			return LevelUpResult.NO_CLASS;
		}
		Attributes attributes = data.get().toAttributes();
		if (attributes.get(slot) >= Attributes.MAX_VALUE) {
			return LevelUpResult.MAX_VALUE;
		}
		int soulLevel = attributes.soulLevel();
		// The user's exception: going up to soul level N costs N experience levels (LevelUpSoulsTable.EXPERIENCE_LEVELS).
		if (player.experienceLevel < LevelUpSoulsTable.EXPERIENCE_LEVELS.cost(soulLevel)) {
			return LevelUpResult.NOT_ENOUGH_SOULS;
		}
		player.giveExperienceLevels(-LevelUpSoulsTable.EXPERIENCE_LEVELS.cost(soulLevel));
		player.setAttached(SoulsAttachments.SOULS_DATA, data.get().withAttributes(attributes.with(slot, attributes.get(slot) + 1)));
		SoulsStats.apply(player);
		return LevelUpResult.OK;
	}

	/**
	 * Confirms the level-up screen: replays the "+" presses that turn the current attributes into {@code target}
	 * through {@link LevelUpMenu} and commits like FUN_14038b1a0. Returns false (nothing changes) if the menu
	 * would not allow it.
	 */
	public static boolean levelUpTo(ServerPlayer player, List<Integer> target) {
		Optional<SoulsData> data = data(player);
		if (data.isEmpty() || target.size() != Attributes.SLOT_COUNT) {
			return false;
		}
		Attributes current = data.get().toAttributes();
		LevelUpMenu menu = new LevelUpMenu(current, player.experienceLevel, LevelUpSoulsTable.EXPERIENCE_LEVELS);
		for (int slot = 0; slot < Attributes.SLOT_COUNT; slot++) {
			int steps = target.get(slot) - current.get(slot);
			if (steps < 0) {
				return false;
			}
			for (int i = 0; i < steps; i++) {
				if (!menu.raise(slot)) {
					return false;
				}
			}
		}
		if (!menu.canConfirm()) {
			return false;
		}
		player.giveExperienceLevels(-menu.spent());
		player.setAttached(SoulsAttachments.SOULS_DATA, data.get().withAttributes(menu.attributes()));
		SoulsStats.apply(player);
		return true;
	}

	/** Called for every positive XP gain while in Souls Mode: soul memory grows like FUN_14038ab40. */
	public static void onSoulsGained(ServerPlayer player, int amount) {
		data(player).ifPresent(d -> {
			Souls souls = new Souls(0, d.soulMemory());
			souls.gain(amount);
			player.setAttached(SoulsAttachments.SOULS_DATA, d.withSoulMemory(souls.memory()));
		});
	}
}
