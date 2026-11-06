package dev.soulsmode.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int soulsmode$getLeftPos();

	@Accessor("leftPos")
	void soulsmode$setLeftPos(int leftPos);

	@Accessor("hoveredSlot")
	net.minecraft.world.inventory.Slot soulsmode$getHoveredSlot();
}
