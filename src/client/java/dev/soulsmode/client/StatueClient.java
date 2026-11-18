package dev.soulsmode.client;

import dev.soulsmode.item.SoulsItems;
import dev.soulsmode.network.StatuePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

/**
 * The statue dialog (dev.soulsmode.SoulsStatues): DS2's MapEvent 1002 "Use %s?" with the branch's name; yes sends the
 * answer, no closes it.
 */
public final class StatueClient {
	private StatueClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(StatuePayloads.Ask.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			mc.setScreen(new ConfirmScreen(yes -> {
				if (yes) {
					ClientPlayNetworking.send(new StatuePayloads.Use(payload.entityId()));
				}
				mc.setScreen(null);
			}, Component.empty(), Component.translatable("message.soulsmode.statue.use",
					Component.translatable(SoulsItems.FRAGRANT_BRANCH.getDescriptionId()))));
		});
	}
}
