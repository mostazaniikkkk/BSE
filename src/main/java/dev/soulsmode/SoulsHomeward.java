package dev.soulsmode;

import dev.soulsmode.ds2.Weapons;
import dev.soulsmode.item.SoulsItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DS2 Homeward Bone (ItemParam 60350000, docs/research/bonfire.md "Hueso de regreso"): ItemTypeParam 250 -> category 210
 * -> a00_18_0080 ItemUsePray (8.0 s, no CancelControl_Move: the player can walk); at its TAE 220000 (2.333 s) SpEffect
 * 60350000 takes the player to the last bonfire rested at (the eventMgr+0x164 record, written on resting and on
 * travelling: {@link SoulsAttachments#LAST_BONFIRE}). Souls are kept (no 100130[01] 931, unlike the Darksign). One bone
 * is consumed (ItemParam +0x52 bit 0 clear); not usable on a ladder (ItemUsageParam 1800 +5 bit 0 = 0).
 * <p>
 * No rested bonfire (DS2: the start map, m10_02_00_00) or a bonfire that is gone or out (impossible in DS2): the
 * world's spawn, the user's choice (2026-11-10). Not ported: the "Return to the last bonfire?" dialog
 * (ItemUseCheckDialogParam 60350000), 1000[03] (24, 5.0) (status bit 24, meaning not identified) and the SpEffect 754
 * removal (backlog).
 */
public final class SoulsHomeward {
	public static final ResourceLocation USE_ID = SoulsMode.id("homeward_bone_use");
	public static final Weapons.Effigy USE = Weapons.homewardBone();

	private record Using(long start, InteractionHand hand) {
	}

	private static final Map<UUID, Using> USING = new HashMap<>();
	/**
	 * SpEffect 60350000's 1000[03] (24, 5.0): status bit 24 for 5.0 s, read by FUN_14016d830 in FUN_14013a620 -> no HP,
	 * stamina, poise or status damage lands (bonfire.md "Hueso de regreso"). Game time it ends at.
	 */
	private static final Map<UUID, Long> INVULNERABLE = new HashMap<>();
	private static final int INVULNERABLE_TICKS = 100;

	private SoulsHomeward() {
	}

	/** Using a bone: the prayer animation when nothing locks it. Souls Mode only, like the other DS2 items. */
	public static boolean use(ServerPlayer player, InteractionHand hand) {
		if (!SoulsGameMode.isActive(player) || player.onClimbable() || USING.containsKey(player.getUUID())
				|| !SoulsAttack.startItemUse(player, USE_ID, USE.use())) {
			return false;
		}
		USING.put(player.getUUID(), new Using(player.serverLevel().getGameTime(), hand));
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
			if (stack.is(SoulsItems.HOMEWARD_BONE)) {
				stack.consume(1, player);
				// Both instructions of SpEffect 60350000 at its TAE 220000: 1000[03] (24, 5.0) and the warp.
				warp(player);
				INVULNERABLE.put(player.getUUID(), player.serverLevel().getGameTime() + INVULNERABLE_TICKS);
			}
			return true;
		});
	}

	/** To the last bonfire rested at, facing the fire (as travelling does); otherwise to the world's spawn. */
	private static void warp(ServerPlayer player) {
		KnownBonfire last = player.getAttached(SoulsAttachments.LAST_BONFIRE);
		ServerLevel level = last == null ? null
				: player.server.getLevel(ResourceKey.create(Registries.DIMENSION, last.dimension()));
		if (level != null && lit(level, last)) {
			Vec3 stand = Vec3.atBottomCenterOf(last.stand());
			Vec3 toFire = last.pos().getCenter().subtract(stand);
			float yaw = (float) Math.toDegrees(Math.atan2(-toFire.x, toFire.z));
			player.teleportTo(level, stand.x, stand.y, stand.z, yaw, 0);
			return;
		}
		ServerLevel overworld = player.server.overworld();
		BlockPos spawn = overworld.getSharedSpawnPos();
		Vec3 at = Vec3.atBottomCenterOf(spawn);
		player.teleportTo(overworld, at.x, at.y, at.z, overworld.getSharedSpawnAngle(), 0);
	}

	private static boolean lit(ServerLevel level, KnownBonfire bonfire) {
		BlockState state = level.getBlockState(bonfire.pos());
		return state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)
				&& ActiveBonfires.get(level.getServer()).isActive(bonfire.dimension(), bonfire.pos());
	}

	/** Inside the 5 s of status bit 24 after the bone: hits do not land (ALLOW_DAMAGE). */
	public static boolean isInvulnerable(ServerPlayer player) {
		Long until = INVULNERABLE.get(player.getUUID());
		if (until == null) {
			return false;
		}
		if (player.serverLevel().getGameTime() >= until) {
			INVULNERABLE.remove(player.getUUID());
			return false;
		}
		return true;
	}

	public static void forget(ServerPlayer player) {
		USING.remove(player.getUUID());
		INVULNERABLE.remove(player.getUUID());
	}
}
