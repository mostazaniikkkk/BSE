package dev.soulsmode.client.mixin;

import dev.soulsmode.SoulsUpgrade;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.SmithingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Titanite reinforcement at the smithing table (SoulsUpgrade): the experience cost of the next level, drawn like the
 * anvil's cost (green, red when the player cannot pay it).
 */
@Mixin(SmithingScreen.class)
public abstract class SmithingScreenMixin extends ItemCombinerScreen<SmithingMenu> {
	private SmithingScreenMixin(SmithingMenu menu, Inventory inventory, Component title, ResourceLocation texture) {
		super(menu, inventory, title, texture);
	}

	@Inject(method = "renderBg", at = @At("TAIL"))
	private void soulsmode$reinforceCost(GuiGraphics graphics, float partial, int mouseX, int mouseY, CallbackInfo ci) {
		if (!menu.getSlot(3).hasItem() || !menu.getSlot(0).getItem().isEmpty() || minecraft == null || minecraft.player == null) {
			return;
		}
		SoulsUpgrade.Step step = SoulsUpgrade.next(menu.getSlot(1).getItem());
		if (step == null || !menu.getSlot(2).getItem().is(step.material())) {
			return;
		}
		Component text = Component.translatable(step.experience() == 1 ? "gui.soulsmode.reinforce.cost_one"
				: "gui.soulsmode.reinforce.cost", step.level(), step.experience());
		boolean pays = minecraft.player.getAbilities().instabuild || minecraft.player.experienceLevel >= step.experience();
		int x = leftPos + imageWidth - 8 - font.width(text) - 2;
		int y = topPos + 69;
		graphics.fill(x - 2, y - 2, leftPos + imageWidth - 8, y + 10, 0x4F000000);
		graphics.drawString(font, text, x, y, pays ? 0x80FF20 : 0xFF6060);
	}
}
