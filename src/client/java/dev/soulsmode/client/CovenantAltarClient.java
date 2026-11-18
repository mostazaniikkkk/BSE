package dev.soulsmode.client;

import dev.soulsmode.network.CovenantPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The covenant altar's menu (SoulsCovenantAltar), in place of Saulden's: Enter covenant (npcMenu 10105) and Abandon
 * covenant (10110, the user's rule), each asking first like DS2 (MapEvent 1333 / 1343 to join, 1302 to abandon; YES /
 * NO common 200 / 201), the covenant's name (common 40000000 + id) and rank (npcMenu 90202).
 */
public final class CovenantAltarClient {
	private CovenantAltarClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(CovenantPayloads.Open.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			if (mc.screen instanceof Menu || mc.screen == null || mc.screen instanceof Confirm) {
				mc.setScreen(new Menu(payload));
			}
		});
	}

	private static final class Menu extends Screen {
		private final CovenantPayloads.Open state;

		Menu(CovenantPayloads.Open state) {
			super(Component.translatable("covenant.soulsmode." + state.covenant()));
			this.state = state;
		}

		@Override
		protected void init() {
			int y = height / 2;
			addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.covenant.enter"), b -> {
				// 1333 "Join the Way of Blue covenant?"; with another covenant 1343 "Abandon your covenant and join...?"
				String ask = state.active() != 0 && state.active() != state.covenant()
						? "gui.soulsmode.covenant.ask_switch." + state.covenant() : "gui.soulsmode.covenant.ask_join." + state.covenant();
				minecraft.setScreen(new Confirm(this, Component.translatable(ask), new CovenantPayloads.Action(state.covenant(), 0)));
			}).bounds(width / 2 - 75, y, 150, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.covenant.abandon"), b ->
					minecraft.setScreen(new Confirm(this, Component.translatable("gui.soulsmode.covenant.ask_abandon"),
							new CovenantPayloads.Action(state.covenant(), 1)))).bounds(width / 2 - 75, y + 24, 150, 20).build());
		}

		@Override
		public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			super.render(graphics, mouseX, mouseY, partialTick);
			graphics.drawCenteredString(font, title, width / 2, height / 2 - 40, 0xFFE0C080);
			if (state.active() == state.covenant()) {
				Component rank = Component.translatable("gui.soulsmode.covenant.rank").append(" " + state.rank());
				graphics.drawCenteredString(font, rank, width / 2, height / 2 - 24, 0xFFE0E0E0);
			}
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}
	}

	/** YES / NO with the cursor on NO, like DS2's dialogs (SoulsUseCheck). */
	private static final class Confirm extends Screen {
		private final Screen back;
		private final Component question;
		private final CovenantPayloads.Action action;

		Confirm(Screen back, Component question, CovenantPayloads.Action action) {
			super(question);
			this.back = back;
			this.question = question;
			this.action = action;
		}

		@Override
		protected void init() {
			int y = height / 2 + 10;
			addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.use_check.yes"), b -> {
				ClientPlayNetworking.send(action);
				minecraft.setScreen(back);
			}).bounds(width / 2 - 104, y, 100, 20).build());
			Button no = addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.use_check.no"),
					b -> minecraft.setScreen(back)).bounds(width / 2 + 4, y, 100, 20).build());
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
