package dev.soulsmode.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The furnace's smelted-recipe counts (their experience is popped when a player takes the result). */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceRecipesAccessor {
	@Accessor("recipesUsed")
	Object2IntOpenHashMap<ResourceLocation> soulsmode$recipesUsed();
}
