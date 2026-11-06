package dev.soulsmode;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Boss health bars for vanilla mobs (the user's request, 2026-11-01): the ravager and the warden (tag
 * {@code soulsmode:boss_bar}, extendable by datapack). Shown to Souls Mode players within the mob's follow range,
 * with its name; the bar follows its health and goes away when it dies or the player leaves the range.
 */
public final class SoulsBossBars {
	public static final TagKey<EntityType<?>> BOSS_BAR = TagKey.create(Registries.ENTITY_TYPE, SoulsMode.id("boss_bar"));
	private static final int CHECK_TICKS = 10;

	private static final Map<LivingEntity, ServerBossEvent> BARS = new HashMap<>();

	private SoulsBossBars() {
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			// Health between checks.
			BARS.forEach((mob, bar) -> bar.setProgress(Math.max(0, mob.getHealth() / mob.getMaxHealth())));
			return;
		}
		BARS.entrySet().removeIf(e -> {
			LivingEntity mob = e.getKey();
			if (mob.isRemoved() || !mob.isAlive()) {
				e.getValue().removeAllPlayers();
				return true;
			}
			return false;
		});
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				if (!SoulsGameMode.isActive(player)) {
					continue;
				}
				List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(64),
						e -> e.isAlive() && e.getType().is(BOSS_BAR));
				for (LivingEntity mob : near) {
					BARS.computeIfAbsent(mob, m -> new ServerBossEvent(m.getDisplayName(), BossEvent.BossBarColor.RED,
							BossEvent.BossBarOverlay.PROGRESS));
				}
			}
		}
		BARS.forEach((mob, bar) -> {
			bar.setProgress(Math.max(0, mob.getHealth() / mob.getMaxHealth()));
			double range = mob.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE)
					? mob.getAttributeValue(Attributes.FOLLOW_RANGE) : 32;
			for (ServerPlayer p : List.copyOf(bar.getPlayers())) {
				if (p.level() != mob.level() || !SoulsGameMode.isActive(p) || p.distanceTo(mob) > range) {
					bar.removePlayer(p);
				}
			}
			if (mob.level() instanceof ServerLevel level) {
				for (ServerPlayer p : level.players()) {
					if (SoulsGameMode.isActive(p) && p.distanceTo(mob) <= range) {
						bar.addPlayer(p);
					}
				}
			}
		});
	}
}
