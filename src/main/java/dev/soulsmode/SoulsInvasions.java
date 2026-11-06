package dev.soulsmode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * NPC invasions (the user's design, 2026-11-01; not a DS2 mechanic, whose NPC invasions are placed by map events):
 * <ul>
 * <li>A roll every 5 minutes for each Souls Mode player of soul level 10 or more.</li>
 * <li>Chance by place: inside a trial chamber 5 %, a mineshaft or a biome of {@code soulsmode:invasion_high} (dark
 * forest) 3 %, a cave biome ({@code soulsmode:invasion_cave}) 2 %;
 * in a village or a biome of {@code soulsmode:invasion_very_low} 0.25 %; never in {@code soulsmode:invasion_none}
 * (mushroom fields, cherry grove); 1 % elsewhere.</li>
 * <li>Having slept within the last day (Minecraft's time since rest) means no invasion; each day without sleep
 * multiplies the chance by 1.5 from the first, up to x5.</li>
 * <li>The invader's level is random from 1 to the player's soul level (SoulsInvader).</li>
 * </ul>
 */
public final class SoulsInvasions {
	public static final TagKey<Biome> NONE = TagKey.create(Registries.BIOME, SoulsMode.id("invasion_none"));
	public static final TagKey<Biome> VERY_LOW = TagKey.create(Registries.BIOME, SoulsMode.id("invasion_very_low"));
	public static final TagKey<Biome> CAVE = TagKey.create(Registries.BIOME, SoulsMode.id("invasion_cave"));
	public static final TagKey<Biome> HIGH = TagKey.create(Registries.BIOME, SoulsMode.id("invasion_high"));
	private static final ResourceKey<Structure> TRIAL_CHAMBERS = BuiltinStructures.TRIAL_CHAMBERS;
	private static final int ROLL_TICKS = 5 * 60 * 20;
	private static final int DAY_TICKS = 24000;
	private static final int MIN_SOUL_LEVEL = 10;
	private static final float MEDIUM = 0.05f;
	private static final float MINESHAFT = 0.03f;
	private static final float CAVE_CHANCE = 0.02f;
	private static final float LOW = 0.01f;
	private static final float VERY_LOW_CHANCE = 0.0025f;
	private static final float SLEEP_FACTOR = 1.5f;
	private static final float SLEEP_CAP = 5f;

	private SoulsInvasions() {
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % ROLL_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			float chance = chance(player);
			if (chance > 0 && player.getRandom().nextFloat() < chance) {
				invade(player);
			}
		}
	}

	/** This roll's chance for the player, 0 when it cannot be invaded now. */
	public static float chance(ServerPlayer player) {
		if (!SoulsGameMode.isActive(player) || player.isCreative() || player.isSpectator() || !player.isAlive()
				|| isInvaded(player)) {
			return 0;
		}
		int level = SoulsPlayer.data(player).map(d -> d.toAttributes().soulLevel()).orElse(0);
		if (level < MIN_SOUL_LEVEL) {
			return 0;
		}
		int sinceRest = player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
		int days = sinceRest / DAY_TICKS;
		if (days < 1) {
			return 0;
		}
		float sleep = (float) Math.min(SLEEP_CAP, Math.pow(SLEEP_FACTOR, days - 1));
		return place(player) * sleep;
	}

	private static float place(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		BlockPos pos = player.blockPosition();
		var biome = level.getBiome(pos);
		if (biome.is(NONE)) {
			return 0;
		}
		var structures = level.structureManager();
		var trial = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(TRIAL_CHAMBERS);
		if (trial != null && structures.getStructureWithPieceAt(pos, trial).isValid()) {
			return MEDIUM;
		}
		if (structures.getStructureWithPieceAt(pos, StructureTags.MINESHAFT).isValid() || biome.is(HIGH)) {
			return MINESHAFT;
		}
		if (biome.is(CAVE)) {
			return CAVE_CHANCE;
		}
		if (structures.getStructureWithPieceAt(pos, StructureTags.VILLAGE).isValid() || biome.is(VERY_LOW)) {
			return VERY_LOW_CHANCE;
		}
		return LOW;
	}

	// The invader.

	/** Hosts with an invader alive: host -> invader entity id. */
	private static final java.util.Map<java.util.UUID, Integer> INVADED = new java.util.HashMap<>();

	public static boolean isInvaded(ServerPlayer player) {
		return INVADED.containsKey(player.getUUID());
	}

	/**
	 * Spawns an invader 8..12 blocks from the player on solid ground, out of its sight (the user's choice), and shows
	 * DS2's text 1210006 "Invaded by dark spirit %s!".
	 */
	public static boolean invade(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		var random = player.getRandom();
		for (int tries = 0; tries < 40; tries++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = 8 + random.nextDouble() * 4;
			int x = net.minecraft.util.Mth.floor(player.getX() + Math.cos(angle) * distance);
			int z = net.minecraft.util.Mth.floor(player.getZ() + Math.sin(angle) * distance);
			for (int dy = 4; dy >= -4; dy--) {
				BlockPos feet = new BlockPos(x, player.getBlockY() + dy, z);
				if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
						|| !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
						|| !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
					continue;
				}
				var invader = dev.soulsmode.entity.SoulsEntities.INVADER.create(level);
				if (invader == null) {
					return false;
				}
				invader.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, random.nextFloat() * 360, 0);
				if (player.hasLineOfSight(invader)) {
					break;
				}
				int hostLevel = SoulsPlayer.data(player).map(d -> d.toAttributes().soulLevel()).orElse(1);
				invader.build(player, hostLevel);
				level.addFreshEntity(invader);
				INVADED.put(player.getUUID(), invader.getId());
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.soulsmode.invaded",
						invader.getDisplayName()));
				return true;
			}
		}
		return false;
	}

	/** The invasion ends: the invader died ({@code defeated}: DS2's text 1221104) or the host is gone. */
	public static void end(dev.soulsmode.entity.SoulsInvader invader, boolean defeated) {
		java.util.UUID host = invader.host();
		if (host != null) {
			INVADED.remove(host);
			ServerPlayer player = invader.level().getServer() == null ? null
					: invader.level().getServer().getPlayerList().getPlayer(host);
			if (defeated && player != null) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.soulsmode.invader_defeated",
						invader.getDisplayName()));
			}
		}
		if (!defeated && !invader.isRemoved()) {
			invader.discard();
		}
	}
}
