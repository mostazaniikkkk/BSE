package dev.soulsmode.item;

import com.mojang.serialization.MapCodec;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.SoulsUpgrade;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

/**
 * Titanite reinforcement at the smithing table (the user's design, 2026-11-09; SoulsUpgrade): no template, the weapon
 * or armour piece as the base and its next level's material as the addition, at least as many as that level asks for.
 * The extra materials and the experience levels are taken by dev.soulsmode.mixin.SmithingMenuMixin.
 */
public final class ReinforceRecipe implements SmithingRecipe {
	public static final ReinforceRecipe INSTANCE = new ReinforceRecipe();
	public static final RecipeSerializer<ReinforceRecipe> SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
			SoulsMode.id("reinforce"), new RecipeSerializer<>() {
				@Override
				public MapCodec<ReinforceRecipe> codec() {
					return MapCodec.unit(INSTANCE);
				}

				@Override
				public StreamCodec<RegistryFriendlyByteBuf, ReinforceRecipe> streamCodec() {
					return StreamCodec.unit(INSTANCE);
				}
			});

	private ReinforceRecipe() {
	}

	public static void init() {
	}

	@Override
	public boolean matches(SmithingRecipeInput input, Level level) {
		if (!input.template().isEmpty()) {
			return false;
		}
		SoulsUpgrade.Step step = SoulsUpgrade.next(input.base());
		return step != null && input.addition().is(step.material()) && input.addition().getCount() >= step.count();
	}

	@Override
	public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
		SoulsUpgrade.Step step = SoulsUpgrade.next(input.base());
		return step == null ? ItemStack.EMPTY : SoulsUpgrade.reinforced(input.base(), step.level());
	}

	@Override
	public ItemStack getResultItem(HolderLookup.Provider registries) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	@Override
	public boolean isTemplateIngredient(ItemStack stack) {
		return false;
	}

	@Override
	public boolean isBaseIngredient(ItemStack stack) {
		return SoulsUpgrade.maxLevel(stack) > 0;
	}

	@Override
	public boolean isAdditionIngredient(ItemStack stack) {
		return stack.is(SoulsItems.TITANITE_SHARD) || stack.is(SoulsItems.LARGE_TITANITE_SHARD)
				|| stack.is(SoulsItems.TITANITE_CHUNK) || stack.is(SoulsItems.TITANITE_SLAB)
				|| stack.is(SoulsItems.TWINKLING_TITANITE);
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SERIALIZER;
	}
}
