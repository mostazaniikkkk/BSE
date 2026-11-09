package dev.soulsmode.item;

import dev.soulsmode.SoulsMode;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Item components that tie a stack to content defined by datapacks (dev.soulsmode.content.SoulsContent), so third
 * parties can add weapons, armour and consumables without new items: any stack, vanilla or {@link SoulsItems#WEAPON} /
 * {@link SoulsItems#CONSUMABLE}, can carry one, e.g.
 * {@code /give @s soulsmode:weapon[soulsmode:weapon="mypack:katana",custom_model_data=1]}.
 */
public final class SoulsComponents {
	/** The weapon (moveset) the stack attacks with: a datapack weapon or a built-in one ({@code soulsmode:longsword}). */
	public static final DataComponentType<ResourceLocation> WEAPON = register("weapon");
	/** The datapack armour piece the stack counts as when worn. */
	public static final DataComponentType<ResourceLocation> ARMOR = register("armor");
	/** The datapack consumable the stack is used as. */
	public static final DataComponentType<ResourceLocation> CONSUMABLE = register("consumable");
	/** The datapack spell a {@link SoulsItems#SPELL} stack is. */
	public static final DataComponentType<ResourceLocation> SPELL = register("spell");
	/** Uses left on an owned, not attuned spell (DS2 entry +0x20); absent = full (a spell is obtained full). */
	public static final DataComponentType<Integer> SPELL_USES = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			SoulsMode.id("spell_uses"), DataComponentType.<Integer>builder().persistent(com.mojang.serialization.Codec.INT)
					.networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());

	/** Titanite reinforcement level of a weapon or armour piece (dev.soulsmode.SoulsUpgrade); absent = +0. */
	public static final DataComponentType<Integer> REINFORCEMENT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			SoulsMode.id("reinforcement"), DataComponentType.<Integer>builder().persistent(com.mojang.serialization.Codec.INT)
					.networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());

	private SoulsComponents() {
	}

	private static DataComponentType<ResourceLocation> register(String name) {
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, SoulsMode.id(name),
				DataComponentType.<ResourceLocation>builder().persistent(ResourceLocation.CODEC)
						.networkSynchronized(ResourceLocation.STREAM_CODEC).build());
	}

	public static void init() {
	}
}
