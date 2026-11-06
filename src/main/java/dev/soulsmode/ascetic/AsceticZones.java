package dev.soulsmode.ascetic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * The zones made by burning a Bonfire Ascetic (the user's design): one per bonfire, centred on its chunk, with the
 * number of burns as its cycle (AsceticRules). Saved with the world, in the overworld's data storage.
 */
public final class AsceticZones extends SavedData {
	private static final String NAME = "soulsmode_ascetic_zones";
	private final List<AsceticRules.Zone> zones = new ArrayList<>();
	/** Only read on the server thread; replaced on every change. */
	private List<AsceticRules.Zone> snapshot = List.of();

	public static AsceticZones get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(
				new SavedData.Factory<>(AsceticZones::new, AsceticZones::load, null), NAME);
	}

	public List<AsceticRules.Zone> zones() {
		return snapshot;
	}

	/** One more burn at this bonfire: its zone's cycle goes up by one (capped). Returns the new cycle. */
	public int burn(Level level, BlockPos bonfire) {
		String dimension = level.dimension().location().toString();
		int cx = bonfire.getX() >> 4;
		int cz = bonfire.getZ() >> 4;
		for (int i = 0; i < zones.size(); i++) {
			AsceticRules.Zone zone = zones.get(i);
			if (zone.dimension().equals(dimension) && zone.chunkX() == cx && zone.chunkZ() == cz) {
				int cycle = AsceticRules.nextCycle(zone.cycle());
				zones.set(i, new AsceticRules.Zone(dimension, cx, cz, cycle));
				changed();
				return cycle;
			}
		}
		zones.add(new AsceticRules.Zone(dimension, cx, cz, AsceticRules.nextCycle(0)));
		changed();
		return AsceticRules.nextCycle(0);
	}

	public int cycleAt(Level level, BlockPos pos) {
		if (!AsceticRules.ENABLED || snapshot.isEmpty()) {
			return 0;
		}
		return AsceticRules.cycleAt(snapshot, level.dimension().location().toString(), pos.getX() >> 4, pos.getZ() >> 4);
	}

	private void changed() {
		snapshot = List.copyOf(zones);
		setDirty();
	}

	private static AsceticZones load(CompoundTag tag, HolderLookup.Provider registries) {
		AsceticZones out = new AsceticZones();
		ListTag list = tag.getList("zones", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag z = list.getCompound(i);
			out.zones.add(new AsceticRules.Zone(z.getString("dimension"), z.getInt("x"), z.getInt("z"),
					Math.max(1, Math.min(AsceticRules.MAX_CYCLE, z.getInt("cycle")))));
		}
		out.snapshot = List.copyOf(out.zones);
		return out;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (AsceticRules.Zone zone : zones) {
			CompoundTag z = new CompoundTag();
			z.putString("dimension", zone.dimension());
			z.putInt("x", zone.chunkX());
			z.putInt("z", zone.chunkZ());
			z.putInt("cycle", zone.cycle());
			list.add(z);
		}
		tag.put("zones", list);
		return tag;
	}
}
