package dev.soulsmode;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * A crafting recipe only for Souls Mode players (asked for by DarkTwo, 2026-11-16; docs/datapacks.md "Recetas solo
 * del Modo Souls"): {@code {"type": "soulsmode:souls_mode", "recipe": {<any crafting recipe>}}}. The crafting table
 * and the inventory grid give its result only to a Souls Mode player; the crafter (no player) never makes it.
 */
public record SoulsModeRecipe(CraftingRecipe recipe) implements CraftingRecipe {
	public static final MapCodec<SoulsModeRecipe> CODEC = Recipe.CODEC
			.<CraftingRecipe>comapFlatMap(r -> r instanceof CraftingRecipe c ? DataResult.success(c)
					: DataResult.error(() -> "soulsmode:souls_mode needs a crafting recipe"), r -> r)
			.fieldOf("recipe").xmap(SoulsModeRecipe::new, SoulsModeRecipe::recipe);
	public static final StreamCodec<RegistryFriendlyByteBuf, SoulsModeRecipe> STREAM_CODEC = Recipe.STREAM_CODEC
			.map(r -> new SoulsModeRecipe((CraftingRecipe) r), SoulsModeRecipe::recipe);
	public static final RecipeSerializer<SoulsModeRecipe> SERIALIZER = new RecipeSerializer<>() {
		@Override
		public MapCodec<SoulsModeRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, SoulsModeRecipe> streamCodec() {
			return STREAM_CODEC;
		}
	};

	public static void init() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, SoulsMode.id("souls_mode"), SERIALIZER);
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return recipe.matches(input, level);
	}

	@Override
	public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider) {
		return recipe.assemble(input, provider);
	}

	@Override
	public boolean canCraftInDimensions(int w, int h) {
		return recipe.canCraftInDimensions(w, h);
	}

	@Override
	public ItemStack getResultItem(HolderLookup.Provider provider) {
		return recipe.getResultItem(provider);
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		return recipe.getRemainingItems(input);
	}

	@Override
	public NonNullList<Ingredient> getIngredients() {
		return recipe.getIngredients();
	}

	@Override
	public boolean isSpecial() {
		return recipe.isSpecial();
	}

	@Override
	public boolean showNotification() {
		return recipe.showNotification();
	}

	@Override
	public String getGroup() {
		return recipe.getGroup();
	}

	@Override
	public CraftingBookCategory category() {
		return recipe.category();
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SERIALIZER;
	}
}
