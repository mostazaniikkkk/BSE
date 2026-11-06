package dev.soulsmode;

import dev.soulsmode.network.BonfirePayloads;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A lit campfire is a bonfire in Souls Mode: right click rests at it and opens the bonfire menu (level up).
 * Which block is the bonfire and that it levels up are design choices (in DS2 the Emerald Herald levels up);
 * what resting does is in docs/research/bonfire.md.
 */
public final class SoulsBonfire {
	/** Design choice: how far the player may get from the campfire before the rest ends. */
	private static final double MAX_DISTANCE = 6.0;
	/** Design choice: how close to a campfire the use key rests at it. */
	private static final double REACH = 2.5;
	/** EventBonfireManager +0x4c: the sit phase lasts 2.9 s before the menu opens (FUN_14017ed90). */
	private static final int SIT_TICKS = 58;
	private static final Map<UUID, Rest> RESTING = new HashMap<>();

	private static final class Rest {
		final BlockPos bonfire;
		int ticksToMenu = SIT_TICKS;

		Rest(BlockPos bonfire) {
			this.bonfire = bonfire;
		}
	}

	private SoulsBonfire() {
	}

	/**
	 * Design choice: a right click with the main hand on a lit campfire, unless it would cook the held item or put
	 * the fire out (shovel), or the player sneaks. Shared by the client (to keep vanilla from acting) and the server.
	 */
	public static boolean isBonfireUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || player.isSpectator()) {
			return false;
		}
		BlockState state = level.getBlockState(hit.getBlockPos());
		if (!(state.getBlock() instanceof CampfireBlock) || !state.getValue(CampfireBlock.LIT)) {
			return false;
		}
		ItemStack held = player.getMainHandItem();
		return !(held.getItem() instanceof ShovelItem) && level.getRecipeManager()
				.getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(held), level).isEmpty();
	}

	/**
	 * Design choice: in third person the Souls camera does not aim the crosshair at low blocks, so the use key also
	 * rests at the closest lit campfire within {@link #REACH} blocks of the feet (DS2's bonfire action range was not
	 * traced). Returns null if there is none.
	 */
	public static BlockPos nearby(Player player) {
		BlockPos feet = player.blockPosition();
		BlockPos best = null;
		double bestDistance = REACH * REACH;
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 1, 2))) {
			BlockState state = player.level().getBlockState(pos);
			double distance = player.distanceToSqr(pos.getBottomCenter());
			if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT) && distance <= bestDistance) {
				best = pos.immutable();
				bestDistance = distance;
			}
		}
		return best;
	}

	public static void onRestRequest(ServerPlayer player, BlockPos pos) {
		if (SoulsGameMode.isActive(player) && player.isAlive() && pos.equals(nearby(player))) {
			use(player, pos);
		}
	}

	/**
	 * The user's rule (2026-11-02): a campfire is only a bonfire once a player spends one experience level on it; then
	 * it works for everyone (ActiveBonfires). The first use activates it (or says a level is needed); later uses rest.
	 */
	private static void use(ServerPlayer player, BlockPos pos) {
		ActiveBonfires active = ActiveBonfires.get(player.server);
		ResourceLocation dimension = player.level().dimension().location();
		if (active.isActive(dimension, pos)) {
			rest(player, pos);
			BonfireShots.requestIfMissing(player, dimension, pos);
			return;
		}
		if (player.experienceLevel < ACTIVATION_LEVELS && !player.getAbilities().instabuild) {
			player.displayClientMessage(Component.translatable("message.soulsmode.bonfire.need_level", ACTIVATION_LEVELS),
					true);
			return;
		}
		if (!player.getAbilities().instabuild) {
			player.giveExperienceLevels(-ACTIVATION_LEVELS);
		}
		active.activate(dimension, pos.immutable());
		BonfireShots.requestIfMissing(player, dimension, pos);
		player.level().playSound(null, pos, net.minecraft.sounds.SoundEvents.FIRECHARGE_USE,
				net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
		player.displayClientMessage(Component.translatable("message.soulsmode.bonfire.activated"), true);
	}

	/** The user's rule: experience levels a campfire costs to become a bonfire. */
	public static final int ACTIVATION_LEVELS = 1;

	public static InteractionResult onUseBlock(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
		if (!SoulsGameMode.isActive(player) || !isBonfireUse(player, player.level(), hand, hit)) {
			return InteractionResult.PASS;
		}
		use(player, hit.getBlockPos());
		return InteractionResult.SUCCESS;
	}

	/**
	 * FUN_14017f610 (sit down): SpEffect 110000010 is applied at once, then the menu opens after the sit phase.
	 * FUN_1401cb950 also records the bonfire as the place to return to (FUN_14044fe30).
	 */
	private static void rest(ServerPlayer player, BlockPos bonfire) {
		if (isResting(player)) {
			return;
		}
		RESTING.put(player.getUUID(), new Rest(bonfire.immutable()));
		remember(player, bonfire);
		restore(player);
		// Design choice: the respawn point is where the player stands next to the campfire (the campfire itself
		// is not a free spot to respawn in).
		player.setRespawnPosition(player.level().dimension(), player.blockPosition(), player.getYRot(), true, false);
		sendPose(player, true);
	}

	/**
	 * The confirmed parts of SpEffect 110000010 (docs/research/bonfire.md) that have a Minecraft counterpart:
	 * <ul>
	 * <li>100110[02] (FUN_140230d70): every equipped item that is not broken gets its full durability back;</li>
	 * <li>event 871: abnormal states 0..11 are cleared; of those, poison and burn exist in Minecraft.</li>
	 * </ul>
	 * Not ported: the HP heal (101050, the healed amount comes from a field not yet confirmed as max HP) and the Estus refill (no flask yet, and its code was not found).
	 */
	private static void restore(ServerPlayer player) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.isDamageableItem()) {
				stack.setDamageValue(0);
			}
		}
		player.removeEffect(MobEffects.POISON);
		player.clearFire();
		// 100060[00] (100.0, 1): every spell's uses (docs/research/spells.md "Recuperar").
		SoulsMagic.restore(player, 100);
	}

	/** Adds the bonfire to the travel list the first time the player rests at it. */
	private static void remember(ServerPlayer player, BlockPos bonfire) {
		ResourceLocation dimension = player.level().dimension().location();
		List<KnownBonfire> known = new ArrayList<>(player.getAttachedOrElse(SoulsAttachments.BONFIRES, List.of()));
		if (known.stream().noneMatch(b -> b.isAt(dimension, bonfire))) {
			known.add(new KnownBonfire(dimension, bonfire.immutable(), player.blockPosition()));
			player.setAttached(SoulsAttachments.BONFIRES, List.copyOf(known));
		}
	}

	/**
	 * Travel menu (FUN_14017fdb0): from the menu of the bonfire being rested at, go to another bonfire the player
	 * knows; the destination becomes the place to return to (FUN_14044fe30). A campfire that is gone or out is
	 * dropped from the list (design choice). Arriving stands the player up there.
	 */
	public static void onTravel(ServerPlayer player, ResourceLocation dimension, BlockPos pos) {
		Rest rest = RESTING.get(player.getUUID());
		if (rest == null || rest.ticksToMenu > 0 || !SoulsGameMode.isActive(player)) {
			return;
		}
		List<KnownBonfire> known = player.getAttachedOrElse(SoulsAttachments.BONFIRES, List.of());
		KnownBonfire target = known.stream().filter(b -> b.isAt(dimension, pos)).findFirst().orElse(null);
		ServerLevel level = target == null ? null
				: player.server.getLevel(ResourceKey.create(Registries.DIMENSION, target.dimension()));
		if (level == null) {
			return;
		}
		BlockState state = level.getBlockState(target.pos());
		if (!(state.getBlock() instanceof CampfireBlock)) {
			ActiveBonfires.get(player.server).forget(target.dimension(), target.pos());
			BonfireShots.delete(player.server, target.dimension(), target.pos());
		}
		if (!(state.getBlock() instanceof CampfireBlock) || !state.getValue(CampfireBlock.LIT)
				|| !ActiveBonfires.get(player.server).isActive(target.dimension(), target.pos())) {
			List<KnownBonfire> left = new ArrayList<>(known);
			left.remove(target);
			player.setAttached(SoulsAttachments.BONFIRES, List.copyOf(left));
			player.sendSystemMessage(Component.translatable("message.soulsmode.bonfire.gone"));
			sendMenu(player);
			return;
		}
		RESTING.remove(player.getUUID());
		Vec3 stand = Vec3.atBottomCenterOf(target.stand());
		Vec3 toFire = target.pos().getCenter().subtract(stand);
		float yaw = (float) Math.toDegrees(Math.atan2(-toFire.x, toFire.z));
		player.teleportTo(level, stand.x, stand.y, stand.z, yaw, 0);
		player.setRespawnPosition(level.dimension(), target.stand(), yaw, true, false);
		sendPose(player, false);
	}

	public static boolean isResting(ServerPlayer player) {
		return RESTING.containsKey(player.getUUID());
	}

	public static void onLevelUp(ServerPlayer player, List<Integer> attributes) {
		Rest rest = RESTING.get(player.getUUID());
		if (rest != null && rest.ticksToMenu == 0 && SoulsGameMode.isActive(player)) {
			SoulsPlayer.levelUpTo(player, attributes);
			// Sent even when refused, so the screen starts again from the real values.
			sendMenu(player);
		}
	}

	/** State 3 of FUN_14017ed90: leaving the bonfire stands the player up. */
	public static void onLeave(ServerPlayer player) {
		if (RESTING.remove(player.getUUID()) != null) {
			sendPose(player, false);
		}
	}

	public static void forget(ServerPlayer player) {
		RESTING.remove(player.getUUID());
	}

	/** Opens the menu after the sit phase; ends the rest if the player dies, leaves Souls Mode, moves away or the fire goes out. */
	public static void tick(MinecraftServer server) {
		RESTING.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				return true;
			}
			Rest rest = entry.getValue();
			BlockState state = player.level().getBlockState(rest.bonfire);
			if (!(state.getBlock() instanceof CampfireBlock)) {
				ActiveBonfires.get(server).forget(player.level().dimension().location(), rest.bonfire);
				BonfireShots.delete(server, player.level().dimension().location(), rest.bonfire);
			}
			if (!player.isAlive() || !SoulsGameMode.isActive(player)
					|| !(state.getBlock() instanceof CampfireBlock) || !state.getValue(CampfireBlock.LIT)
					|| player.distanceToSqr(rest.bonfire.getCenter()) > MAX_DISTANCE * MAX_DISTANCE) {
				sendPose(player, false);
				return true;
			}
			if (rest.ticksToMenu > 0 && --rest.ticksToMenu == 0) {
				sendMenu(player);
			}
			return false;
		});
	}

	private static void sendPose(ServerPlayer player, boolean sitting) {
		BonfirePayloads.Pose pose = new BonfirePayloads.Pose(player.getId(), sitting);
		ServerPlayNetworking.send(player, pose);
		for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(viewer, pose);
		}
	}

	public static void sendMenu(ServerPlayer player) {
		var data = SoulsPlayer.data(player);
		Rest rest = RESTING.get(player.getUUID());
		List<KnownBonfire> known = player.getAttachedOrElse(SoulsAttachments.BONFIRES, List.of());
		ActiveBonfires active = ActiveBonfires.get(player.server);
		ServerPlayNetworking.send(player, new BonfirePayloads.Open(
				data.map(SoulsData::attributes).orElse(List.of()), player.experienceLevel,
				data.map(SoulsData::soulMemory).orElse(0), known,
				rest == null ? BlockPos.ZERO : rest.bonfire,
				known.stream().map(b -> active.name(b.dimension(), b.pos())).toList()));
	}

	/** The bonfire whose menu this player has open (resting, past the sit phase), or null. */
	public static BlockPos menuBonfire(ServerPlayer player) {
		Rest rest = RESTING.get(player.getUUID());
		return rest == null || rest.ticksToMenu > 0 || !SoulsGameMode.isActive(player) ? null : rest.bonfire;
	}

	/** Names the bonfire the player rests at (the user's design, 2026-11-03; BonfireNames.sanitize). */
	public static void onRename(ServerPlayer player, String name) {
		BlockPos bonfire = menuBonfire(player);
		if (bonfire == null) {
			return;
		}
		ActiveBonfires.get(player.server).setName(player.level().dimension().location(), bonfire,
				BonfireNames.sanitize(name));
		sendMenu(player);
	}
}
