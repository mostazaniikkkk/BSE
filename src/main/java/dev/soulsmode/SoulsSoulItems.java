package dev.soulsmode;

import dev.soulsmode.ds2.Weapons;
import dev.soulsmode.item.SoulItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Using a DS2 soul item (docs/research/soul_items.md): ItemTypeParam 550 -> a00_18_0090 ItemUseDeamonsSoul; at TAE
 * 220000 (1.7 s) the item's SpEffect fires 930 with its souls, which go to souls held and soul memory like enemy
 * souls (FUN_14038ab40); here, experience points. Cut short before that, nothing is spent.
 */
public final class SoulsSoulItems {
	public static final ResourceLocation USE_ID = SoulsMode.id("soul_item_use");
	public static final Weapons.Effigy USE = Weapons.soulItem();

	private record Using(long start, InteractionHand hand) {
	}

	private static final Map<UUID, Using> USING = new HashMap<>();

	private SoulsSoulItems() {
	}

	public static boolean use(ServerPlayer player, InteractionHand hand) {
		if (!SoulsGameMode.isActive(player) || USING.containsKey(player.getUUID())
				|| !SoulsAttack.startItemUse(player, USE_ID, USE.use())) {
			return false;
		}
		USING.put(player.getUUID(), new Using(player.serverLevel().getGameTime(), hand));
		return true;
	}

	public static void tick(MinecraftServer server) {
		USING.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || !SoulsGameMode.isActive(player)
					|| !SoulsAttack.isDoing(player, USE.use())) {
				return true;
			}
			float t = (player.serverLevel().getGameTime() - entry.getValue().start()) / 20f;
			if (t < USE.effectAt()) {
				return false;
			}
			ItemStack stack = player.getItemInHand(entry.getValue().hand());
			if (stack.getItem() instanceof SoulItem item) {
				int souls = item.souls();
				stack.consume(1, player);
				player.giveExperiencePoints(souls);
			}
			return true;
		});
	}

	public static void forget(ServerPlayer player) {
		USING.remove(player.getUUID());
	}
}
