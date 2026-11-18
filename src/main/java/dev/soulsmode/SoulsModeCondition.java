package dev.soulsmode;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

import java.util.Set;

/**
 * Loot condition / predicate "is in Souls Mode" (asked for by DarkTwo, 2026-11-16; docs/datapacks.md):
 * {@code {"condition": "soulsmode:souls_mode", "entity": "this"}} ({@code entity}: this, attacker, direct_attacker,
 * attacking_player; default this). True when that entity is a player in Souls Mode.
 */
public record SoulsModeCondition(LootContext.EntityTarget entity) implements LootItemCondition {
	public static final MapCodec<SoulsModeCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			LootContext.EntityTarget.CODEC.optionalFieldOf("entity", LootContext.EntityTarget.THIS)
					.forGetter(SoulsModeCondition::entity)).apply(i, SoulsModeCondition::new));
	public static final LootItemConditionType TYPE = new LootItemConditionType(CODEC);

	public static void init() {
		Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, SoulsMode.id("souls_mode"), TYPE);
	}

	@Override
	public LootItemConditionType getType() {
		return TYPE;
	}

	@Override
	public Set<LootContextParam<?>> getReferencedContextParams() {
		return Set.of(entity.getParam());
	}

	@Override
	public boolean test(LootContext context) {
		return context.getParamOrNull(entity.getParam()) instanceof ServerPlayer p && SoulsGameMode.isActive(p);
	}
}
