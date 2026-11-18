package dev.soulsmode.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.soulsmode.SoulsAttack;
import dev.soulsmode.SoulsTorch;
import dev.soulsmode.client.anim.SoulsAnimations;
import dev.soulsmode.api.SoulsWeapons;
import dev.soulsmode.ds2.Attack;
import dev.soulsmode.ds2.Moveset;
import dev.soulsmode.network.AttackPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import dev.soulsmode.SoulsBonfire;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Attack input and the local side of the committed attack: left click (light), right click with a weapon and the
 * heavy attack key (heavy), and two off-hand keys (left-hand light / heavy) send a request instead of the vanilla hit or use; while the attack's CancelControl_Move window runs the player cannot move.
 */
public final class AttackClient {
	private static final KeyMapping HEAVY_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.soulsmode.heavy_attack", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.soulsmode"));
	/** Off-hand attacks (DS2's left-hand light / heavy). */
	private static final KeyMapping LEFT_LIGHT_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.soulsmode.left_light_attack", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.soulsmode"));
	private static final KeyMapping LEFT_HEAVY_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.soulsmode.left_heavy_attack", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.soulsmode"));

	/** DS2 two-handing (Y / triangle): toggles the right weapon; with sneak held, the left weapon. */
	private static final KeyMapping GRIP_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.soulsmode.two_hand", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.soulsmode"));

	/** DS2 Torch item: lights the torch in the left hand, or puts it away while held. */
	private static final KeyMapping TORCH_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.soulsmode.torch", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.soulsmode"));
	/** Players holding the DS2 torch (entity ids). */
	private static final java.util.Set<Integer> TORCH_HELD = new java.util.HashSet<>();

	private static Attack current;
	private static Moveset.Type currentType;
	/** During a guard attack: the button holding the guard of the other hand. */
	private static java.util.function.BooleanSupplier guardInput;
	/** The bow's Prepare: its button's release was already sent. */
	private static boolean bowReleased;
	/** The current action is a bow / crossbow Prepare. */
	private static boolean rangedPrepare;
	/** The local player entity the current attack belongs to. */
	private static int currentPlayerId = -1;
	private static boolean leapStarted;
	/** The input that started the current action, polled to release a held guard. */
	private static java.util.function.BooleanSupplier heldInput = () -> false;
	private static java.util.function.BooleanSupplier lastInput = () -> false;
	private static boolean useLatched;
	/** The current press of the use key opened a block or rested at a bonfire. */
	private static boolean usedNearby;
	private static int ticks;
	/**
	 * Latency compensation (the user's choice, 2026-11-10): an attack started from idle plays at once on this client,
	 * before the server's Start; the server dates it from the press (SoulsAttack.start). The Start that confirms it does
	 * not restart it; a different one replaces it; none within {@link #PREDICT_TIMEOUT} ticks stops it.
	 */
	private static Attack predicted;
	private static Moveset.Type predictedType;
	private static int predictedTicks;
	/** Design choice: one second without the server's Start means the press was refused. */
	private static final int PREDICT_TIMEOUT = 20;

	private AttackClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(AttackClient::tick);
		ClientPlayNetworking.registerGlobalReceiver(AttackPayloads.TorchHeld.TYPE, (payload, context) -> {
			if (payload.held()) {
				TORCH_HELD.add(payload.entityId());
			} else {
				TORCH_HELD.remove(payload.entityId());
			}
		});
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
				(handler, client) -> TORCH_HELD.clear());
		ClientPlayNetworking.registerGlobalReceiver(AttackPayloads.GuardLayer.TYPE, (payload, context) -> {
			Entity entity = context.client().level == null ? null : context.client().level.getEntity(payload.entityId());
			if (entity instanceof AbstractClientPlayer player) {
				dev.soulsmode.client.anim.GuardLayerClient.values(player, payload.exitBlend(), payload.speed());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(AttackPayloads.Stop.TYPE, (payload, context) -> {
			if (payload.entityId() < 0) {
				// -id - 1: the guard held during a guard attack dropped (SoulsAttack.onRelease).
				Entity guard = context.client().level == null ? null
						: context.client().level.getEntity(-payload.entityId() - 1);
				if (guard instanceof AbstractClientPlayer p) {
					dev.soulsmode.client.anim.GuardLayerClient.stop(p);
				}
				return;
			}
			Entity entity = context.client().level == null ? null : context.client().level.getEntity(payload.entityId());
			if (entity instanceof AbstractClientPlayer player) {
				SoulsAnimations.stop(player);
				dev.soulsmode.client.anim.SwingClient.stopped(player.getId());
				// A held guard released: SM 0x210e leaves 0x2110 with its exit blend.
				dev.soulsmode.client.anim.GuardLayerClient.stop(player);
				if (player == context.client().player) {
					current = null;
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(AttackPayloads.Start.TYPE,
				(payload, context) -> onStart(context.client(), payload));
	}

	private static void onStart(Minecraft client, AttackPayloads.Start payload) {
		{
			Entity entity = client.level == null ? null : client.level.getEntity(payload.entityId());
			if (!(entity instanceof AbstractClientPlayer player) || payload.actionType() < 0
					|| payload.actionType() >= Moveset.Type.values().length) {
				return;
			}
			Moveset.Type type = Moveset.Type.values()[payload.actionType()];
			Moveset moveset = SoulsWeapons.moveset(payload.moveset()).orElse(null);
			Attack attack = type == Moveset.Type.ITEM_USE ? SoulsAttack.itemUse(payload.moveset())
					: type == Moveset.Type.CAST_PREPARE ? dev.soulsmode.SoulsMagic.castAttack(payload.moveset(), false)
					: type == Moveset.Type.CAST ? dev.soulsmode.SoulsMagic.castAttack(payload.moveset(), true)
					: type == Moveset.Type.RIPOSTE ? SoulsAttack.riposte(payload.moveset())
					: type == Moveset.Type.GUARD_BREAK_STAB ? SoulsAttack.guardBreakStab(payload.moveset())
					: type.powerStance() ? powerStanceAttack(payload.moveset(), type)
					: type == Moveset.Type.GUARD_ATTACK || type == Moveset.Type.GUARD_ATTACK_COMBO
							? guardAttack(payload.moveset(), type)
					: moveset == null ? null : moveset.attack(payload.weaponLeft(), payload.twoHanded(), type);
			if (attack == null) {
				return;
			}
			if (player == client.player && predicted != null) {
				// The server's answer to a predicted start: the same attack keeps playing from the press.
				boolean same = predicted == attack && predictedType == type;
				predicted = null;
				if (same) {
					return;
				}
			}
			ResourceLocation animation = ResourceLocation.parse(attack.animation());
			if (type == Moveset.Type.FALL) {
				// The plunge's start, then the airborne loop until the landing replaces it.
				Moveset.Fall fall = payload.twoHanded() ? moveset.twoHanded().fall() : moveset.hand(payload.weaponLeft()).fall();
				SoulsAnimations.playThenLoop(player, ResourceLocation.parse(fall.startAnimation()), animation);
			} else if (attack.guard()) {
				// The guard is SM 0x210e's layer over the body network (GuardLayerClient), not an action: what the
				// action layer played ends and locomotion runs under it. Back from a guard attack with the guard still
				// up, 0x2110 never left.
				SoulsAnimations.stop(player);
				if (!dev.soulsmode.client.anim.GuardLayerClient.continuing(player)) {
					dev.soulsmode.client.anim.GuardLayerClient.raise(player, attack, payload.weaponLeft(),
							payload.twoHanded());
				}
			} else if (type == Moveset.Type.CAST_PREPARE
					&& !dev.soulsmode.SoulsMagic.FAILED_CAST.equals(payload.moveset())
					|| isRangedPrepare(payload.moveset(), attack)) {
				// The bow's Prepare also holds its last frame: the drawn bow until the CF (SoulsBow).
				// A spell's Prepare: its last frame is held until the cast (state 0x1220, casting.md section 6).
				SoulsAnimations.hold(player, animation, attack.mirroredFor(payload.weaponLeft()));
			} else {
				// A wall bounce plays at its DmgHitBackDuration (SM_HitBack's 0x7d node); everything else at 1.
				SoulsAnimations.play(player, animation, attack.mirroredFor(payload.weaponLeft()),
						type == Moveset.Type.ITEM_USE ? SoulsAttack.hitBackSpeed(payload.moveset()) : 1);
			}
			// DS2's swing speed (SwingSpeed): the server's rate follows in a Swing payload.
			if (type == Moveset.Type.ITEM_USE || attack.guard() || type == Moveset.Type.FALL) {
				dev.soulsmode.client.anim.SwingClient.stopped(player.getId());
			} else {
				dev.soulsmode.client.anim.SwingClient.started(player.getId(), attack,
						attack.mirroredFor(payload.weaponLeft()));
			}
			boolean guardAttack = type == Moveset.Type.GUARD_ATTACK || type == Moveset.Type.GUARD_ATTACK_COMBO;
			if (!guardAttack && !attack.guard()) {
				// Any other action drops the guard (FUN_14038fd30 refuses it): SM 0x210e's exit blend.
				dev.soulsmode.client.anim.GuardLayerClient.stop(player);
			}
			if (player == client.player) {
				// The guard's button keeps the guard during a guard attack; its release still has to reach the server.
				if (guardAttack && current != null && current.guard()) {
					guardInput = heldInput;
				}
				boolean resumedGuard = attack.guard() && guardInput != null;
				bowReleased = false;
				rangedPrepare = isRangedPrepare(payload.moveset(), attack);
				current = attack;
				currentType = type;
				leapStarted = false;
				JumpAttackLeap.stop();
				heldInput = resumedGuard ? guardInput : lastInput;
				if (resumedGuard || !guardAttack) {
					guardInput = null;
				}
				ticks = 0;
				RollClient.cancel();
				player.setYRot(payload.yaw());
				player.yBodyRot = payload.yaw();
				player.yHeadRot = payload.yaw();
				player.setSprinting(false);
			}
		}
	}

	/**
	 * Called instead of vanilla {@code Minecraft.startAttack}. Returns false to let vanilla handle the click:
	 * outside Souls Mode, or on a block the player is allowed to break.
	 */
	public static boolean onAttackKey(Minecraft client) {
		LocalPlayer player = client.player;
		if (!SoulsAttack.ENABLED || player == null || !StaminaHud.soulsMode() || client.gameMode == null) {
			return false;
		}
		if (mines(client, player)) {
			return false;
		}
		if (client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
				&& !(hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity)) {
			// Boats, minecarts, item frames, paintings, end crystals: Minecraft's own hit breaks them (bug 2026-11-17: the
			// DS2 swing only hits living things, so a boat took several swings, or none, to break).
			return false;
		}
		if (dev.soulsmode.SoulsWater.deep(player)) {
			// Placeholder authorised by the user (2026-11-04, narrowed 2026-11-16): DS2 has no fighting in deep water, so
			// swimming or with the head under water the attack key is Minecraft's own attack until a water combat is
			// designed. With only the feet in water the Souls moveset stays (SoulsWater).
			return false;
		}
		lastInput = () -> client.options.keyAttack.isDown();
		request(player, false, false);
		return true;
	}

	/**
	 * Design choice: the attack key breaks the aimed block when the player may break it and holds no weapon (hand,
	 * shovel, blocks...) or a weapon that is the right tool for it (a pickaxe on stone, an axe on wood, a hoe on leaves or
	 * a crop: SoulsTools.harvests). Anything
	 * else is an attack, so a sword swung at the ground does not dig it.
	 */
	private static boolean mines(Minecraft client, LocalPlayer player) {
		if (!(client.hitResult instanceof BlockHitResult block) || block.getType() != HitResult.Type.BLOCK
				|| player.blockActionRestricted(player.level(), block.getBlockPos(), client.gameMode.getPlayerMode())) {
			return false;
		}
		ItemStack held = player.getMainHandItem();
		var state = client.level.getBlockState(block.getBlockPos());
		return SoulsWeapons.movesetFor(held).equals(SoulsWeapons.DEFAULT) || held.isCorrectToolForDrops(state)
				|| dev.soulsmode.SoulsTools.harvests(held, state, client.level, block.getBlockPos());
	}

	/** Holding the attack key keeps breaking a block only when {@link #mines} allows it. */
	public static boolean blocksContinueAttack(Minecraft client) {
		LocalPlayer player = client.player;
		return SoulsAttack.ENABLED && player != null && StaminaHud.soulsMode() && client.gameMode != null
				&& !mines(client, player);
	}

	/**
	 * Called instead of vanilla {@code Minecraft.startUseItem}: with a weapon in hand, right click is the heavy attack.
	 * Returns false to let vanilla use the item: outside Souls Mode, without a weapon (food, blocks, tools...), or on
	 * a block you interact with (doors, containers, buttons...). Holding the button does not repeat the attack.
	 */
	public static boolean onUseKey(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || !StaminaHud.soulsMode() || client.level == null) {
			return false;
		}
		if (dev.soulsmode.SoulsWater.deep(player)) {
			// Placeholder authorised by the user (2026-11-04): in deep water the survival moveset (vanilla use: trident,
			// bow, shield, eating...), like the attack key (onAttackKey).
			return false;
		}
		boolean aimsAtBlock = client.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
				&& (isInteractive(client.level.getBlockState(block.getBlockPos()), client, block)
				|| dev.soulsmode.SoulsTools.usesOn(player, block, client.level.getBlockState(block.getBlockPos())));
		// Holding a block aimed at another one places it: no nearby bonfire takes the click.
		boolean places = player.getMainHandItem().getItem() instanceof BlockItem
				&& client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK;
		if (!aimsAtBlock && !places && !useLatched && useNearby(client, player)) {
			useLatched = true;
			usedNearby = true;
			return true;
		}
		if (usedNearby) {
			// Vanilla retries the use while the button is down; it must not then eat or attack.
			return true;
		}
		if (SoulsAttack.ENABLED && !aimsAtBlock && dev.soulsmode.client.camera.BowAim.bowHand(player) != 0) {
			// A ranged weapon never draws like vanilla. Two-handed, the use key is the other hand's button, which only
			// aims (BowAim). One-handed, it is the right hand's heavy button: a crossbow in that hand fires (R2 = R1),
			// a bow does nothing.
			var right = dev.soulsmode.client.camera.BowAim.ranged(player, false);
			if (!dev.soulsmode.client.camera.BowAim.bowTwoHanded(player) && right != null && right.oneHanded
					&& !useLatched) {
				useLatched = true;
				lastInput = () -> client.options.keyUse.isDown();
				request(player, true, false);
			}
			return true;
		}
		if (!SoulsAttack.ENABLED || aimsAtBlock || eatsOffhand(player)
				|| SoulsWeapons.movesetFor(player.getMainHandItem()).equals(SoulsWeapons.DEFAULT)) {
			return false;
		}
		if (!useLatched) {
			useLatched = true;
			lastInput = () -> client.options.keyUse.isDown();
			request(player, true, false);
		}
		return true;
	}

	/**
	 * The use key rests at a nearby lit bonfire without aiming at it (SoulsBonfire.nearby). Blocks with a menu (chest,
	 * crafting table, furnace...) open only when aimed at: the nearby opening was removed at the user's report
	 * (2026-11-17: "los objetos interactivos están funcionando cuando no se les apunta"). Returns whether it rested.
	 */
	private static boolean useNearby(Minecraft client, LocalPlayer player) {
		return SoulsBonfire.nearby(player) != null && dev.soulsmode.client.bonfire.BonfireClient.restNearby(player);
	}

	private static boolean hasMenu(BlockState state, Minecraft client, BlockPos pos) {
		return state.getMenuProvider(client.level, pos) != null || state.getBlock() instanceof EnderChestBlock;
	}

	/**
	 * Design choice: with a weapon in the main hand and food the player can eat in the off hand, right click eats it
	 * (vanilla use) instead of the heavy attack, which stays on its own key.
	 */
	private static boolean eatsOffhand(LocalPlayer player) {
		FoodProperties food = player.getOffhandItem().get(DataComponents.FOOD);
		return food != null && player.canEat(food.canAlwaysEat());
	}

	/** Design choice: blocks with a menu or a vanilla open/press action keep their right click. */
	private static boolean isInteractive(BlockState state, Minecraft client, BlockHitResult hit) {
		Block block = state.getBlock();
		return hasMenu(state, client, hit.getBlockPos()) || block instanceof DoorBlock
				|| block instanceof TrapDoorBlock || block instanceof FenceGateBlock || block instanceof ButtonBlock
				|| block instanceof LeverBlock || block instanceof BedBlock || block instanceof BellBlock
				|| block instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
	}

	private static void tick(Minecraft client) {
		if (!client.options.keyUse.isDown()) {
			useLatched = false;
			usedNearby = false;
		}
		LocalPlayer player = client.player;
		if (player == null || player.getId() != currentPlayerId || !player.isAlive()) {
			// No player, a new player entity (respawn) or dead: nothing carries over (a plunge would keep moving locked).
			if (current != null && currentType == Moveset.Type.JUMP) {
				JumpAttackLeap.stop();
			}
			current = null;
			currentPlayerId = player == null ? -1 : player.getId();
			if (player == null) {
				return;
			}
		}
		boolean enabled = SoulsAttack.ENABLED && StaminaHud.soulsMode();
		if (predicted != null && ++predictedTicks > PREDICT_TIMEOUT) {
			// The server refused the press: undo the predicted start.
			predicted = null;
			if (current != null) {
				SoulsAnimations.stop(player);
				dev.soulsmode.client.anim.SwingClient.stopped(player.getId());
				current = null;
			}
		}
		while (HEAVY_KEY.consumeClick()) {
			if (enabled) {
				lastInput = HEAVY_KEY::isDown;
				request(player, true, false);
			}
		}
		while (LEFT_LIGHT_KEY.consumeClick()) {
			if (enabled) {
				lastInput = LEFT_LIGHT_KEY::isDown;
				request(player, false, true);
			}
		}
		while (LEFT_HEAVY_KEY.consumeClick()) {
			if (enabled) {
				lastInput = LEFT_HEAVY_KEY::isDown;
				request(player, true, true);
			}
		}
		while (TORCH_KEY.consumeClick()) {
			if (enabled) {
				ClientPlayNetworking.send(new AttackPayloads.Torch(false));
			}
		}
		while (GRIP_KEY.consumeClick()) {
			if (enabled) {
				ClientPlayNetworking.send(new AttackPayloads.Grip(player.isShiftKeyDown()));
			}
		}
		dev.soulsmode.client.camera.BowAim.tick(client, enabled
				&& (client.options.keyUse.isDown() || LEFT_LIGHT_KEY.isDown()));
		if (current != null && currentType == Moveset.Type.ITEM_USE && rangedPrepare && !bowReleased
				&& !heldInput.getAsBoolean()) {
			// The fire button let go during the Prepare: the bow stays drawn (SoulsBow).
			ClientPlayNetworking.send(new AttackPayloads.Release(false));
			bowReleased = true;
		}
		if (guardInput != null && !guardInput.getAsBoolean()) {
			ClientPlayNetworking.send(new AttackPayloads.Release(false));
			guardInput = null;
		}
		if (current != null && current.guard()) {
			// A guard lasts while its button is held (DS2 GuardDuration).
			if (!heldInput.getAsBoolean()) {
				ClientPlayNetworking.send(new AttackPayloads.Release(false));
				current = null;
			}
		} else if (current != null && currentType == Moveset.Type.JUMP) {
			// The jump attack's leap (JumpAttackLeap), then the rest of the animation.
			float t = clip(player, ++ticks);
			boolean first = !leapStarted && t >= JumpAttackLeap.START;
			if (first) {
				JumpAttackLeap.start(player);
				leapStarted = true;
			}
			JumpAttackLeap.tick(player, first);
			if (t >= current.duration()) {
				current = null;
				JumpAttackLeap.stop();
			}
		} else if (current != null && clip(player, ++ticks) >= current.duration()) {
			current = null;
		}
	}

	private static void request(LocalPlayer player, boolean heavy, boolean leftHand) {
		if (player.isUsingItem() || VictimClient.cannotAct()) {
			return;
		}
		double[] dir = RollClient.inputDirection(player);
		boolean flick = FlickInput.active();
		boolean predicted = predict(player, heavy, leftHand, flick);
		ClientPlayNetworking.send(new AttackPayloads.Request(heavy, leftHand, (float) dir[0], (float) dir[1], flick,
				predicted));
	}

	/**
	 * Plays at once what the server will start for a press from idle, when this client can tell which attack it is: one
	 * hand, a weapon's plain light / heavy (no dash, roll, backstep, kick or jump attack, no guard, cast or bow). The
	 * server may still answer with another action (a backstab, a riposte...), which then replaces it.
	 */
	private static boolean predict(LocalPlayer player, boolean heavy, boolean leftHand, boolean flick) {
		if (current != null || flick || player.isSprinting() || RollClient.movementLocked(player) || !player.onGround()
				|| dev.soulsmode.SoulsWater.shallow(player) || dev.soulsmode.SoulsWater.deep(player)
				|| dev.soulsmode.client.anim.GripClient.grip(player) != Moveset.Grip.ONE_HANDED
				|| leftHand && holdsTorch(player)) {
			return false;
		}
		ItemStack held = leftHand ? player.getOffhandItem() : player.getMainHandItem();
		if (held.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem) {
			return false;
		}
		ResourceLocation movesetId = SoulsWeapons.movesetFor(held, leftHand);
		if (leftHand && !held.isEmpty() && movesetId.equals(SoulsWeapons.DEFAULT)) {
			return false;
		}
		Moveset moveset = SoulsWeapons.moveset(movesetId).orElse(null);
		Moveset.Type type = Moveset.next(null, false, false, heavy, leftHand);
		Attack attack = moveset == null ? null : moveset.attack(leftHand, false, type);
		if (attack == null || attack.guard() || attack.isCast()) {
			return false;
		}
		onStart(Minecraft.getInstance(), new AttackPayloads.Start(player.getId(), movesetId, leftHand, false,
				type.ordinal(), player.getYRot()));
		predicted = attack;
		predictedType = type;
		predictedTicks = 0;
		return true;
	}

	/** Whether the player holds the DS2 torch, which replaces the left-hand item. */
	public static boolean holdsTorch(Entity entity) {
		return TORCH_HELD.contains(entity.getId());
	}

	/** The local attack's clip time after {@code t} ticks, with DS2's swing speed (SwingClient). */
	private static float clip(LocalPlayer player, int t) {
		return dev.soulsmode.client.anim.SwingClient.clipTime(player.getId(), current, t / 20f);
	}

	/** CancelControl_Avoid of the local player's attack (SoulsAttack.blocksDodge on the server). */
	public static boolean blocksDodge() {
		LocalPlayer player = Minecraft.getInstance().player;
		return current != null && player != null && !current.guard() && clip(player, ticks) < current.duration()
				&& current.blocksDodge(clip(player, ticks));
	}

	/** CancelControl_Move of the local player's attack. */
	public static boolean movementLocked() {
		LocalPlayer player = Minecraft.getInstance().player;
		return current != null && current.blocksMovement(player == null ? ticks / 20f : clip(player, ticks))
				|| dev.soulsmode.client.bonfire.BonfireClient.sitting() || VictimClient.cannotMove()
				|| player != null && RollClient.movementLocked(player)
				|| dev.soulsmode.client.anim.PlayerReactionClient.movementLocked();
	}

	/** Whether a Start id is a bow / crossbow Prepare (its last frame is held until the CF, SoulsBow). */
	private static boolean isRangedPrepare(ResourceLocation id, Attack attack) {
		if (dev.soulsmode.SoulsBow.clip(id) != attack || attack == null) {
			return false;
		}
		for (var weapon : java.util.List.of(dev.soulsmode.ds2.Bow.BOW, dev.soulsmode.ds2.Bow.CROSSBOW)) {
			for (String grip : java.util.List.of(dev.soulsmode.ds2.Bow.ONE_HAND_RIGHT, dev.soulsmode.ds2.Bow.ONE_HAND_LEFT,
					dev.soulsmode.ds2.Bow.TWO_HAND)) {
				var shot = weapon.shot(grip);
				if (shot != null && shot.prepare() == attack) {
					return true;
				}
			}
		}
		return false;
	}

	private static Attack guardAttack(ResourceLocation moveset, Moveset.Type type) {
		var data = dev.soulsmode.ds2.GuardAttacks.data(SoulsAttack.damageKey(moveset));
		return data == null ? null : data.attack(type);
	}

	private static Attack powerStanceAttack(ResourceLocation moveset, Moveset.Type type) {
		var data = dev.soulsmode.ds2.PowerStance.data(SoulsAttack.damageKey(moveset));
		return data == null ? null : data.attack(type);
	}

	/** Whether the local player is in an attack (QA). */
	public static boolean attacking() {
		return current != null;
	}

	/** Dodging ends the attack (the server allows it only outside CancelControl_Avoid). */
	public static void cancel() {
		current = null;
	}

	/** Runs after the movement input is read: holds the player in place during the movement lock. */
	public static void afterInput(LocalPlayer player) {
		if (StaminaHud.sprintBlocked()) {
			player.setSprinting(false);
		}
		if (!movementLocked()) {
			return;
		}
		player.input.forwardImpulse = 0;
		player.input.leftImpulse = 0;
		player.input.jumping = false;
		player.setSprinting(false);
	}
}
