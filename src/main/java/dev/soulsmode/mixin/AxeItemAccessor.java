package dev.soulsmode.mixin;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** The axe's strippable blocks, so the use key can leave stripping to vanilla (SoulsTools). */
@Mixin(AxeItem.class)
public interface AxeItemAccessor {
	@Accessor("STRIPPABLES")
	static Map<Block, Block> soulsmode$strippables() {
		throw new AssertionError();
	}
}
