package dev.soulsmode.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.soulsmode.content.SoulsContent;
import dev.soulsmode.network.ContentPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client side of SoulsContent: collects the server's datapack content files and builds them like the server does. */
public final class ContentClient {
	private static Map<SoulsContent.Kind, Map<ResourceLocation, JsonObject>> pending;

	private ContentClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ContentPayload.TYPE, (payload, context) -> {
			switch (payload.op()) {
				case ContentPayload.BEGIN -> {
					pending = new EnumMap<>(SoulsContent.Kind.class);
					for (SoulsContent.Kind kind : SoulsContent.Kind.values()) {
						pending.put(kind, new LinkedHashMap<>());
					}
				}
				case ContentPayload.FILE -> {
					if (pending != null) {
						pending.get(SoulsContent.Kind.values()[payload.kind()]).put(payload.id(),
								JsonParser.parseString(payload.json()).getAsJsonObject());
					}
				}
				case ContentPayload.END -> {
					if (pending != null) {
						SoulsContent.apply(pending);
						pending = null;
					}
				}
				default -> {
				}
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			pending = null;
			// In single player the integrated server is the one stopping; its content goes with it.
			SoulsContent.clear();
		});
	}
}
