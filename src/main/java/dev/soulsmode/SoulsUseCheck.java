package dev.soulsmode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.soulsmode.item.SoulsItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * DS2's use confirmation (ItemUseCheckDialogParam, docs/research/bonfire.md "Hueso de regreso"): FUN_140500c40 asks the
 * row's message (common.fmg) with YES / NO (common 200 / 201), the cursor on NO, before the use animation; only YES
 * uses the item (FUN_1400c73c0 sets chr+200 +0xfc 0x800). Rows used here have condition 1 (always) in slot 0; the
 * network conditions 2..4 do not apply to the mod's items.
 * <p>
 * In Minecraft the client asks (SoulsUseCheckClient) instead of sending the vanilla use, and {@link Confirm} starts
 * the use on the server.
 */
public final class SoulsUseCheck {
	private static final String RESOURCE = "/data/soulsmode/ds2/weapons.json";
	/** The mod's items with an ItemParam row (soul_items.md, bonfire.md). */
	private static final Map<Item, Integer> ITEM_PARAM = Map.of(
			SoulsItems.HOMEWARD_BONE, 60350000,
			SoulsItems.SOUL_OF_A_GIANT, 50920000,
			SoulsItems.SOUL_OF_THE_LAST_GIANT, 64010000,
			SoulsItems.CRACKED_RED_EYE_ORB, 62060000,
			SoulsItems.BLACK_SEPARATION_CRYSTAL, 62160000);
	private static final Map<Integer, Integer> MESSAGE = new HashMap<>();

	static {
		try (InputStream in = SoulsUseCheck.class.getResourceAsStream(RESOURCE)) {
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("itemUseCheck").entrySet()) {
				var row = e.getValue().getAsJsonObject();
				// Condition 1 (always) in slot 0: its message (0 = use at once). The Black Separation Crystal's row has
				// network conditions 4 / 2 / 3 with 30000004 "Return to your world?": the mod asks it whenever the
				// crystal can be used (only a spirit can).
				var conds = row.getAsJsonArray("conditions");
				var msgs = row.getAsJsonArray("messages");
				for (int i = 0; i < conds.size(); i++) {
					if (conds.get(i).getAsInt() != 0 && msgs.get(i).getAsInt() != 0) {
						MESSAGE.put(Integer.parseInt(e.getKey()), msgs.get(i).getAsInt());
						break;
					}
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private SoulsUseCheck() {
	}

	/** The common.fmg message to ask before using this item, or 0 for none. */
	public static int message(Item item) {
		Integer row = ITEM_PARAM.get(item);
		return row == null ? 0 : MESSAGE.getOrDefault(row, 0);
	}

	/** YES in the dialog: the client asks the server to use the item in that hand. */
	public record Confirm(boolean offhand) implements CustomPacketPayload {
		public static final Type<Confirm> TYPE = new Type<>(SoulsMode.id("use_check_confirm"));
		public static final StreamCodec<ByteBuf, Confirm> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, Confirm::offhand, Confirm::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The confirmed use: the item's own start (its checks still apply). */
	public static void onConfirm(ServerPlayer player, Confirm confirm) {
		InteractionHand hand = confirm.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		Item item = player.getItemInHand(hand).getItem();
		if (message(item) == 0) {
			return;
		}
		if (item == SoulsItems.HOMEWARD_BONE) {
			SoulsHomeward.use(player, hand);
		} else if (item instanceof dev.soulsmode.item.SoulItem) {
			SoulsSoulItems.use(player, hand);
		} else if (item instanceof dev.soulsmode.item.MultiplayItem) {
			SoulsMultiplayItems.use(player, hand);
		}
	}
}
