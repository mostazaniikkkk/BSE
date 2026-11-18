package dev.soulsmode;

import dev.soulsmode.ds2.Weapons;
import dev.soulsmode.item.SoulsItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The invasion items (docs/research/covenants.md "Invasiones PvP en BSE"): the Cracked Red Eye Orb (ItemParam
 * 62060000) and the Black Separation Crystal (62160000). Both are ItemTypeParam 200 / 270 -> use category 80 ->
 * a00_18_0080 ItemUsePray (the same clip as the Homeward Bone, 8.0 s), their SpEffect at TAE 220000 (2.333 s):
 * the orb's 100180[01] (1) starts an invasion (SoulsPvp.invade) and is consumed (ItemParam +0x52 bit 0 clear), the
 * crystal's 100180[03] ends it for the spirit (SoulsPvp.leave) and is kept (+0x52 bit 0 set). Not on a ladder
 * (ItemUsageParam 2120 / 2610 +5 bit 0 = 0). They ask first (SoulsUseCheck).
 */
public final class SoulsMultiplayItems {
	public static final ResourceLocation USE_ID = SoulsMode.id("multiplay_item_use");
	/** a00_18_0080 ItemUsePray (weapons.json homeward_bone, the same TAE 180080). */
	public static final Weapons.Effigy USE = Weapons.homewardBone();

	private record Using(long start, InteractionHand hand, Item item) {
	}

	private static final Map<UUID, Using> USING = new HashMap<>();

	private SoulsMultiplayItems() {
	}

	/** Whether the item may be used now: the orb outside an invasion, the crystal only as a spirit. */
	public static boolean usable(ServerPlayer player, Item item) {
		if (item == SoulsItems.CRACKED_RED_EYE_ORB) {
			return !SoulsPvp.inInvasion(player);
		}
		return item == SoulsItems.BLACK_SEPARATION_CRYSTAL && SoulsPvp.isSpirit(player);
	}

	public static boolean use(ServerPlayer player, InteractionHand hand) {
		Item item = player.getItemInHand(hand).getItem();
		if (!SoulsGameMode.isActive(player) || player.onClimbable() || USING.containsKey(player.getUUID())
				|| !usable(player, item) || !SoulsAttack.startItemUse(player, USE_ID, USE.use())) {
			return false;
		}
		USING.put(player.getUUID(), new Using(player.serverLevel().getGameTime(), hand, item));
		return true;
	}

	public static void tick(MinecraftServer server) {
		USING.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || !SoulsAttack.isDoing(player, USE.use())) {
				return true;
			}
			Using using = entry.getValue();
			if ((player.serverLevel().getGameTime() - using.start()) / 20f < USE.effectAt()) {
				return false;
			}
			ItemStack stack = player.getItemInHand(using.hand());
			if (!stack.is(using.item())) {
				return true;
			}
			if (using.item() == SoulsItems.CRACKED_RED_EYE_ORB) {
				// Spent only when a world was found (DS2 deducts it on use; when is not traced).
				if (SoulsPvp.invade(player)) {
					stack.consume(1, player);
				}
			} else {
				SoulsPvp.leave(player);
			}
			return true;
		});
	}

	public static void forget(ServerPlayer player) {
		USING.remove(player.getUUID());
	}
}
