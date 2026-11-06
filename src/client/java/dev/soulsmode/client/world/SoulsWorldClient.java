package dev.soulsmode.client.world;

import dev.soulsmode.SoulsWorld;
import dev.soulsmode.network.ClassPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/** The Souls-like world creation mode and the class selection screen on the client. */
public final class SoulsWorldClient {
	private static boolean pending;

	private SoulsWorldClient() {
	}

	public static WorldCreationUiState.SelectedGameMode souls() {
		return WorldCreationUiState.SelectedGameMode.valueOf("SOULS");
	}

	public static void setPending(boolean souls) {
		pending = souls;
	}

	public static void init() {
		// The integrated server of a world just created in the Souls-like mode marks it before anyone joins.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (pending) {
				pending = false;
				SoulsWorld.markSoulsLike(server);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(ClassPayloads.Open.TYPE,
				(payload, context) -> context.client().setScreen(new ClassSelectScreen()));
	}
}
