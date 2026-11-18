package dev.soulsmode;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Souls Mode is a layer over a vanilla game type rather than a new {@link GameType} value:
 * the enum is baked into vanilla networking and saves, so extending it would break clients.
 * The vanilla base handles block interaction rules; our mechanics key off the attachment.
 */
public final class SoulsGameMode {
	/** Survival, so blocks can be broken and placed (asked for by the user, 2026-10-30; it was adventure). */
	public static final GameType BASE_GAME_TYPE = GameType.SURVIVAL;

	private SoulsGameMode() {
	}

	public static boolean isActive(ServerPlayer player) {
		return player.getAttachedOrElse(SoulsAttachments.SOULS_MODE, false);
	}

	public static void enter(ServerPlayer player) {
		// setGameMode clears the flag (see ServerPlayerMixin), so set it afterwards.
		player.setGameMode(BASE_GAME_TYPE);
		player.setAttached(SoulsAttachments.SOULS_MODE, true);
		syncTag(player);
		SoulsStats.apply(player);
	}

	/**
	 * Scoreboard tag of Souls Mode players (asked for by DarkTwo, 2026-11-16; docs/datapacks.md): functions,
	 * advancements and predicates can select {@code @a[tag=soulsmode.souls_mode]}. Kept in step with the mode on enter,
	 * leave, join and respawn.
	 */
	public static final String TAG = "soulsmode.souls_mode";

	public static void syncTag(ServerPlayer player) {
		if (isActive(player)) {
			player.addTag(TAG);
		} else {
			player.removeTag(TAG);
		}
	}

	/** Moves players who entered Souls Mode on an older base game type onto the current one. */
	public static void updateBase(ServerPlayer player) {
		syncTag(player);
		if (isActive(player) && player.gameMode.getGameModeForPlayer() != BASE_GAME_TYPE) {
			enter(player);
		}
	}

	public static void leave(ServerPlayer player) {
		if (player.removeAttached(SoulsAttachments.SOULS_MODE) != null) {
			SoulsStats.apply(player);
		}
		syncTag(player);
	}
}
