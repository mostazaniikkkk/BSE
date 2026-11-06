package dev.soulsmode.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.network.ContentPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server side of {@link SoulsContent}: reads {@code data/<namespace>/soulsmode/<kind>/*.json} on every datapack (re)load
 * and sends the files to each player on join and to everyone after {@code /reload}.
 */
public final class ContentReloader {
	private ContentReloader() {
	}

	public static void init() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return SoulsMode.id("content");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				SoulsContent.apply(read(manager));
				dev.soulsmode.SoulsScripts.load(scripts(manager));
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				send(player);
			}
		});
	}

	private static Map<SoulsContent.Kind, Map<ResourceLocation, JsonObject>> read(ResourceManager manager) {
		Map<SoulsContent.Kind, Map<ResourceLocation, JsonObject>> out = new EnumMap<>(SoulsContent.Kind.class);
		for (SoulsContent.Kind kind : SoulsContent.Kind.values()) {
			String dir = SoulsMode.MOD_ID + "/" + kind.dir + "/";
			Map<ResourceLocation, JsonObject> files = new LinkedHashMap<>();
			for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(SoulsMode.MOD_ID + "/" + kind.dir,
					path -> path.getPath().endsWith(".json")).entrySet()) {
				String path = e.getKey().getPath();
				ResourceLocation id = ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(),
						path.substring(dir.length(), path.length() - ".json".length()));
				try (Reader reader = e.getValue().openAsReader()) {
					files.put(id, JsonParser.parseReader(reader).getAsJsonObject());
				} catch (Exception ex) {
					SoulsMode.LOGGER.error("[BSE] could not read {} {}: {}", kind.dir, id, ex.toString());
				}
			}
			out.put(kind, files);
		}
		return out;
	}

	/** {@code data/<ns>/soulsmode/script/<name>.lua} -> id {@code <ns>:<name>} (server only, never sent to clients). */
	private static Map<ResourceLocation, String> scripts(ResourceManager manager) {
		String dir = SoulsMode.MOD_ID + "/script/";
		Map<ResourceLocation, String> out = new LinkedHashMap<>();
		manager.listResources(SoulsMode.MOD_ID + "/script", p -> p.getPath().endsWith(".lua")).forEach((loc, res) -> {
			String path = loc.getPath();
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(loc.getNamespace(),
					path.substring(dir.length(), path.length() - ".lua".length()));
			try (Reader reader = res.openAsReader()) {
				out.put(id, new java.io.BufferedReader(reader).lines().collect(java.util.stream.Collectors.joining("\n")));
			} catch (Exception ex) {
				SoulsMode.LOGGER.error("[BSE] could not read script {}: {}", id, ex.toString());
			}
		});
		return out;
	}

	public static void send(ServerPlayer player) {
		ServerPlayNetworking.send(player, ContentPayload.marker(ContentPayload.BEGIN));
		SoulsContent.sources().forEach((kind, files) -> files.forEach((id, json) ->
				ServerPlayNetworking.send(player, new ContentPayload(ContentPayload.FILE, kind.ordinal(), id, json.toString()))));
		ServerPlayNetworking.send(player, ContentPayload.marker(ContentPayload.END));
	}
}
