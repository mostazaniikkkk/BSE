package dev.soulsmode.mixin;

import com.mojang.datafixers.util.Pair;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** The hoe's tillable blocks, so the use key can leave tilling to vanilla (SoulsTools). */
@Mixin(HoeItem.class)
public interface HoeItemAccessor {
	@Accessor("TILLABLES")
	static Map<Block, Pair<Predicate<UseOnContext>, Consumer<UseOnContext>>> soulsmode$tillables() {
		throw new AssertionError();
	}
}
