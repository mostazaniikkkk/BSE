package dev.soulsmode.entity;

import dev.soulsmode.SoulsMode;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class SoulsEntities {
	/** Spell bullets (SoulsBullet); the server simulates them, clients only see their particles. */
	public static final EntityType<SoulsBullet> BULLET = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			SoulsMode.id("bullet"), EntityType.Builder.<SoulsBullet>of(SoulsBullet::new, MobCategory.MISC)
					.sized(0.1f, 0.1f).clientTrackingRange(8).updateInterval(1).noSave().build("bullet"));

	/** Datapack enemies (SoulsEnemy); which one is in its {@code BseEnemy} tag. */
	/** A thrown DS2 item (Lloyd's Talisman...): flies like a snowball, its item decides what the landing does. */
	public static final EntityType<ThrownSoulsItem> THROWN_ITEM = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			SoulsMode.id("thrown_item"), EntityType.Builder.<ThrownSoulsItem>of(ThrownSoulsItem::new, MobCategory.MISC)
					.sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(10).build("thrown_item"));
	public static final EntityType<SoulsEnemy> ENEMY = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			SoulsMode.id("enemy"), EntityType.Builder.<SoulsEnemy>of(SoulsEnemy::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f).clientTrackingRange(10).build("enemy"));

	/** NPC invaders (SoulsInvader, SoulsInvasions): a player-sized character with Steve's look. */
	public static final EntityType<SoulsInvader> INVADER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			SoulsMode.id("invader"), EntityType.Builder.<SoulsInvader>of(SoulsInvader::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f).clientTrackingRange(10).build("invader"));

	private SoulsEntities() {
	}

	public static void init() {
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(ENEMY, SoulsEnemy.attributes());
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(INVADER,
				SoulsInvader.attributes());
	}
}
