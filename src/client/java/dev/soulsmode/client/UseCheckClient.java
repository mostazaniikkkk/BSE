package dev.soulsmode.client;

import dev.soulsmode.SoulsUseCheck;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

/**
 * DS2's use confirmation on the client (SoulsUseCheck): using such an item in Souls Mode opens the question instead of
 * the vanilla use; YES sends {@link SoulsUseCheck.Confirm}. The cursor starts on NO (FUN_140510740, 7th argument 0 ->
 * the answer that does not use the item); NO or Esc does nothing.
 */
public final class UseCheckClient {
	private UseCheckClient() {
	}

	public static void init() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!level.isClientSide() || !StaminaHud.soulsMode()) {
				return InteractionResultHolder.pass(stack);
			}
			int message = SoulsUseCheck.message(stack.getItem());
			if (message == 0) {
				return InteractionResultHolder.pass(stack);
			}
			Minecraft.getInstance().setScreen(new Ask(message, stack.getHoverName(), hand == InteractionHand.OFF_HAND));
			return InteractionResultHolder.fail(stack);
		});
	}

	private static final class Ask extends Screen {
		private final Component question;
		private final boolean offhand;

		Ask(int message, Component item, boolean offhand) {
			super(Component.translatable("gui.soulsmode.use_check." + message, item));
			this.question = getTitle();
			this.offhand = offhand;
		}

		@Override
		protected void init() {
			int y = height / 2 + 10;
			Button yes = addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.use_check.yes"), b -> {
				ClientPlayNetworking.send(new SoulsUseCheck.Confirm(offhand));
				onClose();
			}).bounds(width / 2 - 104, y, 100, 20).build());
			Button no = addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.use_check.no"),
					b -> onClose()).bounds(width / 2 + 4, y, 100, 20).build());
			yes.setTabOrderGroup(1);
			setInitialFocus(no);
		}

		@Override
		public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			super.render(graphics, mouseX, mouseY, partialTick);
			int y = height / 2 - 20;
			for (var line : font.split(question, width - 40)) {
				graphics.drawCenteredString(font, line, width / 2, y, 0xFFE0E0E0);
				y += font.lineHeight + 2;
			}
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}
	}
}
