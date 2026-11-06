package dev.soulsmode;

import dev.soulsmode.api.SoulsWeapons;
import dev.soulsmode.ds2.Attack;
import dev.soulsmode.ds2.Weapons;
import dev.soulsmode.network.AttackPayloads;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The DS2 torch (docs/research/weapons.md, "Antorcha"):
 * <ul>
 * <li>The torch burns torch time (PlayerItemData TorchTime). Minecraft torches stay placeable items; with no time
 * left, the torch key turns one of them into time (see {@link #refill}).</li>
 * <li>The torch key plays a00_18_0220 ItemUseTorchMake: at its TAE 210099 the torch (WeaponParam 6000000) replaces
 * the left weapon, at 210100 it is lit (FUN_14016c9f0).</li>
 * <li>Time only burns while lit (FUN_1401bbc90). It goes out (FUN_140141d90 / FUN_1401476b0 / FUN_14036b9d0) when the
 * time runs out, when the player gets wet, or when the left weapon is changed; pressing the key while lit stands for
 * that weapon change. An unlit torch leaves the hand (FUN_140141890).</li>
 * </ul>
 * Not ported: the light (PlayerLight 17), the 27 m reveal (FUN_1401e6eb0), going out on some ground materials while
 * rolling (GroundMaterialParam names unknown).
 */
public final class SoulsTorch {
	public static final Weapons.Torch DATA = Weapons.torch();
	/** The lit torch's weapon (WeaponParam 6000000, WeaponActionCategoryParam 6000). */
	public static final ResourceLocation MOVESET = SoulsWeapons.id("torch");
	public static final ResourceLocation MAKE = SoulsMode.id("torch_make");
	/** The left-hand item while the torch is held: rendered in the hand and used for vanilla damage. */
	public static final ItemStack STACK = new ItemStack(Items.TORCH);
	/** The Minecraft item that is a DS2 Torch. */
	private static final Item TORCH = Items.TORCH;

	private static final class State {
		/** Game tick the lighting animation started, or -1. */
		long making = -1;
		boolean held;
		boolean lit;
		/** The real left item when the torch went into the hand: changing it is a weapon change. */
		Item leftItem;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private SoulsTorch() {
	}

	/** The item-use animation for an id sent in an {@link AttackPayloads.Start}, or null. */
	public static Attack itemUse(ResourceLocation id) {
		return MAKE.equals(id) ? DATA.make() : null;
	}

	public static boolean isHeld(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		return state != null && state.held;
	}

	public static float time(ServerPlayer player) {
		return player.getAttachedOrElse(SoulsAttachments.TORCH_TIME, 0f);
	}

	/** The torch key: lights the torch, or puts it away while it is held. */
	public static void onKey(ServerPlayer player) {
		if (!SoulsGameMode.isActive(player)) {
			return;
		}
		State state = STATES.computeIfAbsent(player.getUUID(), k -> new State());
		if (state.held) {
			putOut(player, state);
		} else if (state.making < 0 && !SoulsGrip.blocksItemUse(player) && (time(player) > 0 || refill(player))
				&& SoulsAttack.startItemUse(player, MAKE, DATA.make())) {
			state.making = player.serverLevel().getGameTime();
		}
		player.displayClientMessage(Component.translatable("message.soulsmode.torch_time", format(time(player))), true);
	}

	private static String format(float seconds) {
		int s = (int) Math.max(0, seconds);
		return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			State state = STATES.get(player.getUUID());
			if (!SoulsGameMode.isActive(player) || !player.isAlive()) {
				if (state != null) {
					putOut(player, state);
				}
				continue;
			}
			if (state != null) {
				tick(player, state);
			}
		}
	}

	/**
	 * With no torch time left, the torch key uses up one Minecraft torch from the inventory (user's choice,
	 * 2026-10-30: torches stay placeable items, so they are only turned into time when needed). The time it gives is
	 * DS2's for a torch picked up with none left (FUN_1401bba90, PlayerCommonParam +0xc8). Returns whether it did.
	 */
	private static boolean refill(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(TORCH)) {
				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}
				player.setAttached(SoulsAttachments.TORCH_TIME, clamp(DATA.firstAdd()));
				return true;
			}
		}
		return false;
	}

	/** FUN_1401bbd70: within +-(limit - FLT_EPSILON). */
	private static float clamp(float time) {
		float limit = DATA.limit() - Math.ulp(1f);
		return Math.max(-limit, Math.min(limit, time));
	}

	private static void tick(ServerPlayer player, State state) {
		if (state.making >= 0) {
			float t = (player.serverLevel().getGameTime() - state.making) / 20f;
			if (!SoulsAttack.isDoing(player, DATA.make()) && t < DATA.lightAt()) {
				// Cut short before TAE 210100: never lit, so the torch leaves the hand.
				state.making = -1;
				setHeld(player, state, false);
				return;
			}
			if (t >= DATA.equipAt() && !state.held) {
				// FUN_14034f300 starts by going back to one hand (FUN_14034f470).
				SoulsGrip.reset(player);
				state.leftItem = player.getOffhandItem().getItem();
				setHeld(player, state, true);
			}
			if (t >= DATA.lightAt()) {
				state.making = -1;
				state.lit = true;
			}
			return;
		}
		if (!state.lit) {
			return;
		}
		float time = time(player);
		// FUN_140141d90: no time left puts it out; otherwise it burns (FUN_1401bbc90 / FUN_1401bbcd0).
		if (time <= 0 || player.isInWater() || player.getOffhandItem().getItem() != state.leftItem) {
			putOut(player, state);
			return;
		}
		player.setAttached(SoulsAttachments.TORCH_TIME, clamp(time - 1 / 20f));
	}

	private static void putOut(ServerPlayer player, State state) {
		state.making = -1;
		state.lit = false;
		setHeld(player, state, false);
	}

	private static void setHeld(ServerPlayer player, State state, boolean held) {
		if (state.held == held) {
			return;
		}
		state.held = held;
		AttackPayloads.TorchHeld packet = new AttackPayloads.TorchHeld(player.getId(), held);
		ServerPlayNetworking.send(player, packet);
		for (ServerPlayer other : PlayerLookup.tracking(player)) {
			if (other != player) {
				ServerPlayNetworking.send(other, packet);
			}
		}
	}

	/** Tells a player who starts seeing {@code player} that it holds the torch. */
	public static void onStartTracking(ServerPlayer player, ServerPlayer viewer) {
		if (isHeld(player)) {
			ServerPlayNetworking.send(viewer, new AttackPayloads.TorchHeld(player.getId(), true));
		}
	}

	public static void forget(ServerPlayer player) {
		STATES.remove(player.getUUID());
	}
}
