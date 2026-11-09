package dev.soulsmode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/**
 * The campfires activated as bonfires (the user's rule, 2026-11-02: campfires are cheap, so one only works as a DS2
 * bonfire after a player spends an experience level on it; once activated it works for everyone). Saved with the
 * world, in the overworld's data storage.
 */
public final class ActiveBonfires extends SavedData {
	private static final String NAME = "soulsmode_bonfires";
	private final Set<String> active = new HashSet<>();
	/** Bonfire names (the user's design, 2026-11-03, BonfireNames): shared by every player. */
	private final java.util.Map<String, String> names = new java.util.HashMap<>();
	/** To send the clients the new list on each change (the sword in active bonfires). */
	private MinecraftServer server;

	private static String key(ResourceLocation dimension, BlockPos pos) {
		return dimension + "|" + pos.asLong();
	}

	public static ActiveBonfires get(MinecraftServer server) {
		ActiveBonfires out = server.overworld().getDataStorage().computeIfAbsent(
				new SavedData.Factory<>(ActiveBonfires::new, ActiveBonfires::load, null), NAME);
		out.server = server;
		return out;
	}

	/** The list the clients get (BonfirePayloads.Active). */
	public dev.soulsmode.network.BonfirePayloads.Active payload() {
		return new dev.soulsmode.network.BonfirePayloads.Active(java.util.List.copyOf(active));
	}

	private void broadcast() {
		if (server != null) {
			var payload = payload();
			for (var player : server.getPlayerList().getPlayers()) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
			}
		}
	}

	public boolean isActive(ResourceLocation dimension, BlockPos pos) {
		return active.contains(key(dimension, pos));
	}

	/** The active bonfires of a dimension. */
	public java.util.List<BlockPos> positions(ResourceLocation dimension) {
		String prefix = dimension + "|";
		java.util.List<BlockPos> out = new java.util.ArrayList<>();
		for (String k : active) {
			if (k.startsWith(prefix)) {
				out.add(BlockPos.of(Long.parseLong(k.substring(prefix.length()))));
			}
		}
		return out;
	}

	public void activate(ResourceLocation dimension, BlockPos pos) {
		if (active.add(key(dimension, pos))) {
			setDirty();
			broadcast();
		}
	}

	/** The campfire block is gone: a new one there has to be activated again (and its name goes too). */
	public void forget(ResourceLocation dimension, BlockPos pos) {
		boolean removed = active.remove(key(dimension, pos));
		if (names.remove(key(dimension, pos)) != null || removed) {
			setDirty();
		}
		if (removed) {
			broadcast();
		}
	}

	/** The bonfire's name, or "" if it has none. */
	public String name(ResourceLocation dimension, BlockPos pos) {
		return names.getOrDefault(key(dimension, pos), "");
	}

	/** Sets the name (already sanitized, BonfireNames); empty clears it. */
	public void setName(ResourceLocation dimension, BlockPos pos, String name) {
		String key = key(dimension, pos);
		String old = name.isEmpty() ? names.remove(key) : names.put(key, name);
		if (!name.equals(old == null ? "" : old)) {
			setDirty();
		}
	}

	private static ActiveBonfires load(CompoundTag tag, HolderLookup.Provider registries) {
		ActiveBonfires out = new ActiveBonfires();
		ListTag list = tag.getList("active", Tag.TAG_STRING);
		for (int i = 0; i < list.size(); i++) {
			out.active.add(list.getString(i));
		}
		CompoundTag named = tag.getCompound("names");
		for (String key : named.getAllKeys()) {
			out.names.put(key, named.getString(key));
		}
		return out;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		active.forEach(k -> list.add(net.minecraft.nbt.StringTag.valueOf(k)));
		tag.put("active", list);
		CompoundTag named = new CompoundTag();
		names.forEach(named::putString);
		tag.put("names", named);
		return tag;
	}
}
