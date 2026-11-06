package dev.soulsmode.client.bonfire;

import dev.soulsmode.SoulsBonfire;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.client.StaminaHud;
import dev.soulsmode.client.anim.SoulsAnimations;
import dev.soulsmode.network.BonfirePayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;

/** Bonfire rest on the client: the DS2 sit / stand animations and the bonfire menu. */
public final class BonfireClient {
	private static final ResourceLocation START = SoulsMode.id("ds2_bonfire_start");
	private static final ResourceLocation LOOP = SoulsMode.id("ds2_bonfire_loop");
	private static final ResourceLocation END = SoulsMode.id("ds2_bonfire_end");
	/** Length of a76_01_0030 BonfireEnd (.nsa duration 1.8333 s), in ticks: the player stays put while standing up. */
	private static final int STAND_UP_TICKS = 37;

	private static boolean sitting;
	private static int standingTicks;

	private BonfireClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(BonfirePayloads.Open.TYPE, (payload, context) -> {
			Minecraft client = context.client();
			// After a confirmed level-up the level-up screen starts again from the new values, like FUN_1400c9f50.
			if (client.screen instanceof LevelUpScreen) {
				client.setScreen(new LevelUpScreen(payload));
			} else if (client.screen instanceof TravelScreen) {
				client.setScreen(new TravelScreen(payload));
			} else {
				client.setScreen(new BonfireScreen(payload));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(BonfirePayloads.Pose.TYPE, (payload, context) -> {
			Minecraft client = context.client();
			if (!(client.level.getEntity(payload.entityId()) instanceof AbstractClientPlayer player)) {
				return;
			}
			if (payload.sitting()) {
				SoulsAnimations.playThenLoop(player, START, LOOP);
			} else {
				SoulsAnimations.play(player, END);
			}
			if (player == client.player) {
				sitting = payload.sitting();
				standingTicks = payload.sitting() ? 0 : STAND_UP_TICKS;
				if (!sitting && (client.screen instanceof BonfireScreen || client.screen instanceof LevelUpScreen
						|| client.screen instanceof TravelScreen || client.screen instanceof RenameScreen)) {
					client.setScreen(null);
				}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (standingTicks > 0) {
				standingTicks--;
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			sitting = false;
			standingTicks = 0;
		});
		// Keeps the client from also using the held item on the campfire; Fabric still sends the click to the server.
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				level.isClientSide() && StaminaHud.soulsMode() && SoulsBonfire.isBonfireUse(player, level, hand, hit)
						? InteractionResult.SUCCESS : InteractionResult.PASS);
	}

	/** While sitting at a bonfire or standing up from it the local player does not move. */
	public static boolean sitting() {
		return sitting || standingTicks > 0;
	}

	/** The use key near a lit campfire rests at it instead of attacking. Returns whether it did. */
	public static boolean restNearby(AbstractClientPlayer player) {
		var pos = SoulsBonfire.nearby(player);
		if (pos == null || sitting()) {
			return false;
		}
		ClientPlayNetworking.send(new BonfirePayloads.Rest(pos));
		return true;
	}

	static void leave() {
		ClientPlayNetworking.send(new BonfirePayloads.Leave());
	}
}
