package dev.soulsmode;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PvP invasions between players (docs/research/covenants.md "Invasiones PvP en BSE"). DS2's matchmaking runs on
 * FromSoftware's server, so the rules here are the user's (2026-11-10), not DS2's:
 * <ul>
 * <li>matching: random among the Souls Mode players online; an invader may invade anyone of equal or higher soul level,
 * and a lower one only within {@link #LEVEL_DOWN} levels;</li>
 * <li>the invader goes near the host as a dark spirit and comes back exactly where it was when it ends;</li>
 * <li>it ends when the host or the invader dies, when the host rests at a bonfire (the invader is sent home), or when
 * the invader leaves (Black Separation Crystal);</li>
 * <li>whoever dies loses its souls (experience) and keeps its items; its killer, if a participant, gains twice the
 * experience it had;</li>
 * <li>Way of Blue (covenant 4): when a member is invaded, the other members online are asked; one who accepts goes as a
 * blue spirit and comes back at the end; killing the invader gives it one covenant point.</li>
 * </ul>
 */
public final class SoulsPvp {
	/** The user's rule: a higher-level invader may go down at most this many soul levels. */
	public static final int LEVEL_DOWN = 10;
	/** Design values (not DS2): how far from the host the spirits arrive, in blocks. */
	private static final int ARRIVE_MIN = 24;
	private static final int ARRIVE_MAX = 48;
	/** DS2 covenant id of the Way of Blue (VowParam row 4). */
	public static final int WAY_OF_BLUE = 4;

	/** Where a spirit came from: it goes back there. */
	private record Origin(ResourceKey<Level> dimension, Vec3 pos, float yRot, float xRot) {
	}

	/** One invasion: its host, invader, the defenders that came, and who was asked. */
	private static final class Invasion {
		final UUID host;
		final UUID invader;
		final Set<UUID> defenders = new HashSet<>();
		final Set<UUID> asked = new HashSet<>();

		Invasion(UUID host, UUID invader) {
			this.host = host;
			this.invader = invader;
		}
	}

	private static final Map<UUID, Invasion> BY_PLAYER = new HashMap<>();
	private static final Map<UUID, Origin> ORIGINS = new HashMap<>();
	/** Players whose death is an invasion death: it keeps the inventory and drops nothing. */
	private static final Set<UUID> KEEP_ITEMS = new HashSet<>();

	private SoulsPvp() {
	}

	public static int soulLevel(ServerPlayer player) {
		return SoulsPlayer.data(player).map(d -> d.toAttributes().soulLevel()).orElse(1);
	}

	/** The user's matching rule. */
	public static boolean canInvade(int invaderLevel, int hostLevel) {
		return hostLevel >= invaderLevel || invaderLevel - hostLevel <= LEVEL_DOWN;
	}

	public static boolean inInvasion(ServerPlayer player) {
		return BY_PLAYER.containsKey(player.getUUID());
	}

	/** The invader / defender role in an invasion (not the host). */
	public static boolean isSpirit(ServerPlayer player) {
		Invasion inv = BY_PLAYER.get(player.getUUID());
		return inv != null && !inv.host.equals(player.getUUID());
	}

	/** Tries to start an invasion by {@code invader}; false when no world was found (nothing is spent then). */
	public static boolean invade(ServerPlayer invader) {
		if (!SoulsGameMode.isActive(invader) || inInvasion(invader)) {
			return false;
		}
		int level = soulLevel(invader);
		List<ServerPlayer> hosts = new ArrayList<>();
		for (ServerPlayer p : invader.server.getPlayerList().getPlayers()) {
			if (p != invader && p.isAlive() && !p.isSpectator() && SoulsGameMode.isActive(p) && !inInvasion(p)
					&& canInvade(level, soulLevel(p))) {
				hosts.add(p);
			}
		}
		if (hosts.isEmpty()) {
			invader.displayClientMessage(Component.translatable("message.soulsmode.pvp.no_world"), true);
			return false;
		}
		ServerPlayer host = hosts.get(invader.getRandom().nextInt(hosts.size()));
		Invasion inv = new Invasion(host.getUUID(), invader.getUUID());
		BY_PLAYER.put(host.getUUID(), inv);
		BY_PLAYER.put(invader.getUUID(), inv);
		sendNear(invader, host);
		host.sendSystemMessage(Component.translatable("message.soulsmode.pvp.invaded", invader.getDisplayName())
				.withStyle(ChatFormatting.RED));
		invader.sendSystemMessage(Component.translatable("message.soulsmode.pvp.invading", host.getDisplayName())
				.withStyle(ChatFormatting.RED));
		askDefenders(inv, host);
		return true;
	}

	/** Way of Blue: the other members online are asked to defend (a clickable chat line). */
	private static void askDefenders(Invasion inv, ServerPlayer host) {
		if (SoulsCovenants.active(host) != WAY_OF_BLUE) {
			return;
		}
		for (ServerPlayer p : host.server.getPlayerList().getPlayers()) {
			if (p == host || inInvasion(p) || !p.isAlive() || !SoulsGameMode.isActive(p)
					|| SoulsCovenants.active(p) != WAY_OF_BLUE) {
				continue;
			}
			inv.asked.add(p.getUUID());
			Component accept = Component.translatable("message.soulsmode.pvp.defend_accept").withStyle(style -> style
					.withColor(ChatFormatting.AQUA).withUnderlined(true)
					.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/souls defend " + host.getGameProfile().getName()))
					.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
							Component.translatable("message.soulsmode.pvp.defend_hover"))));
			p.sendSystemMessage(Component.translatable("message.soulsmode.pvp.defend_ask", host.getDisplayName())
					.withStyle(ChatFormatting.AQUA).append(" ").append(accept));
		}
	}

	/** {@code /souls defend <host>}: a Way of Blue member asked for that invasion goes as a blue spirit. */
	public static boolean defend(ServerPlayer defender, ServerPlayer host) {
		Invasion inv = host == null ? null : BY_PLAYER.get(host.getUUID());
		if (inv == null || !inv.host.equals(host.getUUID()) || !inv.asked.contains(defender.getUUID()) || inInvasion(defender)
				|| SoulsCovenants.active(defender) != WAY_OF_BLUE) {
			defender.displayClientMessage(Component.translatable("message.soulsmode.pvp.defend_late"), true);
			return false;
		}
		inv.asked.remove(defender.getUUID());
		inv.defenders.add(defender.getUUID());
		BY_PLAYER.put(defender.getUUID(), inv);
		// IngameSystem 1130001 "Travelling to another world to help a blue apostle in need..."
		defender.sendSystemMessage(t("message.soulsmode.pvp.defend_travel"));
		sendNear(defender, host);
		host.sendSystemMessage(Component.translatable("message.soulsmode.pvp.defender_came", defender.getDisplayName())
				.withStyle(ChatFormatting.AQUA));
		return true;
	}

	/** Saves where the spirit is and puts it on the surface 24-48 blocks from the host. */
	private static void sendNear(ServerPlayer spirit, ServerPlayer host) {
		ORIGINS.put(spirit.getUUID(), new Origin(spirit.level().dimension(), spirit.position(), spirit.getYRot(), spirit.getXRot()));
		ServerLevel level = host.serverLevel();
		var random = spirit.getRandom();
		double a = random.nextDouble() * Math.PI * 2;
		double d = ARRIVE_MIN + random.nextDouble() * (ARRIVE_MAX - ARRIVE_MIN);
		int x = (int) Math.floor(host.getX() + Math.cos(a) * d);
		int z = (int) Math.floor(host.getZ() + Math.sin(a) * d);
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		// Under a roof (the Nether, caves) the surface is not reachable: next to the host.
		Vec3 at = y >= level.getMaxBuildHeight() - 1 || host.level().dimensionType().hasCeiling()
				? host.position() : new Vec3(x + 0.5, y, z + 0.5);
		float yaw = (float) Math.toDegrees(Math.atan2(-(host.getX() - at.x), host.getZ() - at.z));
		spirit.teleportTo(level, at.x, at.y, at.z, yaw, 0);
	}

	private static void sendBack(ServerPlayer spirit) {
		Origin o = ORIGINS.remove(spirit.getUUID());
		ServerLevel level = o == null ? null : spirit.server.getLevel(o.dimension());
		if (level != null) {
			spirit.teleportTo(level, o.pos().x, o.pos().y, o.pos().z, o.yRot(), o.xRot());
		}
	}

	private static Component t(String key, Object... args) {
		return Component.translatable(key, args);
	}

	/** Ends an invasion: every spirit goes home with {@code spiritMsg}, the host gets {@code hostMsg} (null: none). */
	private static void close(MinecraftServer server, Invasion inv, Component hostMsg, Component spiritMsg) {
		List<UUID> spirits = new ArrayList<>(inv.defenders);
		spirits.add(inv.invader);
		for (UUID id : spirits) {
			BY_PLAYER.remove(id);
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				sendBack(p);
				if (spiritMsg != null) {
					p.sendSystemMessage(spiritMsg);
				}
			}
		}
		BY_PLAYER.remove(inv.host);
		ServerPlayer host = server.getPlayerList().getPlayer(inv.host);
		if (host != null && hostMsg != null) {
			host.sendSystemMessage(hostMsg);
		}
	}

	/** The host rested at a bonfire: the invader is sent home (the user's rule). */
	public static void onHostRest(ServerPlayer host) {
		Invasion inv = BY_PLAYER.get(host.getUUID());
		if (inv != null && inv.host.equals(host.getUUID())) {
			close(host.server, inv, t("message.soulsmode.pvp.rest_host"), t("message.soulsmode.pvp.rest_spirit"));
		}
	}

	/** The invader used the Black Separation Crystal: it goes home, nobody wins. */
	public static boolean leave(ServerPlayer invader) {
		Invasion inv = BY_PLAYER.get(invader.getUUID());
		if (inv == null || inv.host.equals(invader.getUUID())) {
			return false;
		}
		if (!inv.invader.equals(invader.getUUID())) {
			// A defender leaves alone.
			inv.defenders.remove(invader.getUUID());
			BY_PLAYER.remove(invader.getUUID());
			sendBack(invader);
			return true;
		}
		close(invader.server, inv, t("message.soulsmode.pvp.left_host", invader.getDisplayName()), null);
		return true;
	}

	/** Total experience points of a player (its level and the progress bar). */
	private static int points(ServerPlayer p) {
		int total = 0;
		for (int l = 0; l < p.experienceLevel; l++) {
			total += xpForLevel(l);
		}
		return total + Math.round(p.experienceProgress * p.getXpNeededForNextLevel());
	}

	/** Minecraft's experience to go from level {@code l} to l + 1 (Player.getXpNeededForNextLevel). */
	private static int xpForLevel(int l) {
		return l >= 30 ? 112 + (l - 30) * 9 : l >= 15 ? 37 + (l - 15) * 5 : 7 + l * 2;
	}

	/**
	 * ALLOW_DEATH: a participant died. The loser loses its souls and keeps its items, its killer (a participant) gains
	 * twice them. A spirit does not really die: it is healed and sent home. The host dies (respawning as usual) without
	 * dropping anything. Returns whether the death goes on.
	 */
	public static boolean onDeath(ServerPlayer dead, DamageSource source) {
		Invasion inv = BY_PLAYER.get(dead.getUUID());
		if (inv == null) {
			return true;
		}
		MinecraftServer server = dead.server;
		int lost = points(dead);
		Entity killerEntity = source.getEntity();
		ServerPlayer killer = killerEntity instanceof ServerPlayer k && BY_PLAYER.get(k.getUUID()) == inv ? k : null;
		dead.setExperienceLevels(0);
		dead.setExperiencePoints(0);
		dead.experienceProgress = 0;
		if (killer != null && lost > 0) {
			killer.giveExperiencePoints(lost * 2);
		}
		if (inv.defenders.contains(dead.getUUID())) {
			// A defender falls: it goes home, the invasion goes on.
			inv.defenders.remove(dead.getUUID());
			BY_PLAYER.remove(dead.getUUID());
			dead.setHealth(dead.getMaxHealth());
			sendBack(dead);
			// IngameSystem 1220001 "You have been vanquished. Returning to your world..."
			dead.sendSystemMessage(t("message.soulsmode.pvp.lost"));
			ServerPlayer host = server.getPlayerList().getPlayer(inv.host);
			if (host != null) {
				// 1221103 "Blue spirit %s has been vanquished."
				host.sendSystemMessage(t("message.soulsmode.pvp.blue_vanquished", dead.getDisplayName()));
			}
			return false;
		}
		if (inv.invader.equals(dead.getUUID())) {
			if (killer != null && inv.defenders.contains(killer.getUUID())) {
				// Way of Blue: the defender who beat the invader gains one point (1221303 "...with devotion to your
				// covenant").
				SoulsCovenants.addPoints(killer, WAY_OF_BLUE, 1);
				killer.sendSystemMessage(t("message.soulsmode.pvp.guard_won", dead.getDisplayName()));
				inv.defenders.remove(killer.getUUID());
				BY_PLAYER.remove(killer.getUUID());
				sendBack(killer);
			}
			dead.setHealth(dead.getMaxHealth());
			// 1221104 "Dark spirit %s has been vanquished." to the host; 1221301 "...Guardianship ended" to the other
			// defenders.
			close(server, inv, t("message.soulsmode.pvp.dark_vanquished", dead.getDisplayName()),
					t("message.soulsmode.pvp.guard_ended", dead.getDisplayName()));
			dead.sendSystemMessage(t("message.soulsmode.pvp.lost"));
			return false;
		}
		// The host died: the invader wins (1221007 "Defeated world master %s"; 1221001 "World master %s has died" when
		// someone else killed it).
		KEEP_ITEMS.add(dead.getUUID());
		Component toInvader = killer != null ? t("message.soulsmode.pvp.host_defeated", dead.getDisplayName())
				: t("message.soulsmode.pvp.host_died", dead.getDisplayName());
		ServerPlayer invader = server.getPlayerList().getPlayer(inv.invader);
		for (UUID id : inv.defenders) {
			ServerPlayer d = server.getPlayerList().getPlayer(id);
			if (d != null) {
				// 210257 "Assistance cancelled. The world master has died."
				d.sendSystemMessage(t("message.soulsmode.pvp.help_cancelled"));
			}
		}
		close(server, inv, null, null);
		if (invader != null) {
			invader.sendSystemMessage(toInvader);
		}
		return true;
	}

	/** Whether this player's death drops nothing (LivingEntity.dropAllDeathLoot, see PvpDeathMixin). */
	public static boolean keepsItems(ServerPlayer player) {
		return KEEP_ITEMS.contains(player.getUUID());
	}

	/** COPY_FROM after an invasion death: the new player keeps the inventory (the experience stays lost). */
	public static void copyFrom(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (!alive && KEEP_ITEMS.remove(oldPlayer.getUUID())) {
			newPlayer.getInventory().replaceWith(oldPlayer.getInventory());
		}
	}

	/** A participant leaves the server: the invasion ends; a spirit is sent home first. */
	public static void onDisconnect(ServerPlayer player) {
		Invasion inv = BY_PLAYER.get(player.getUUID());
		if (inv == null) {
			return;
		}
		if (inv.defenders.contains(player.getUUID())) {
			inv.defenders.remove(player.getUUID());
			BY_PLAYER.remove(player.getUUID());
			sendBack(player);
			return;
		}
		close(player.server, inv, t("message.soulsmode.pvp.ended"), t("message.soulsmode.pvp.ended"));
	}

	/** The host's position for the spirits' compass (BlockPos), or null. */
	public static BlockPos hostPos(ServerPlayer spirit) {
		Invasion inv = BY_PLAYER.get(spirit.getUUID());
		ServerPlayer host = inv == null ? null : spirit.server.getPlayerList().getPlayer(inv.host);
		return host == null ? null : host.blockPosition();
	}
}
