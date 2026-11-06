package dev.soulsmode.client.bonfire;

import dev.soulsmode.BonfireNames;
import dev.soulsmode.network.BonfirePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Names the bonfire being rested at (the user's design, 2026-11-03): up to 32 characters, shared by every player;
 * an empty name clears it. The server sanitizes it (BonfireNames). The layout is a design choice.
 */
public final class RenameScreen extends Screen {
	private static final int WIDTH = 200;
	private final BonfirePayloads.Open data;
	private EditBox field;

	public RenameScreen(BonfirePayloads.Open data) {
		super(Component.translatable("gui.soulsmode.bonfire.rename"));
		this.data = data;
	}

	@Override
	protected void init() {
		int x = (width - WIDTH) / 2;
		int y = height / 2 - 20;
		field = addRenderableWidget(new EditBox(font, x, y, WIDTH, 20, title));
		field.setMaxLength(BonfireNames.MAX_LENGTH);
		var level = minecraft.level;
		field.setValue(level == null ? "" : data.currentName(level.dimension().location()));
		setInitialFocus(field);
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> save())
				.bounds(x, y + 28, WIDTH / 2 - 2, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
				.bounds(x + WIDTH / 2 + 2, y + 28, WIDTH / 2 - 2, 20).build());
	}

	private void save() {
		// The server answers with the menu data (SoulsBonfire.onRename), which reopens the bonfire menu with the name.
		ClientPlayNetworking.send(new BonfirePayloads.Rename(field.getValue()));
		minecraft.setScreen(new BonfireScreen(data));
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			save();
			return true;
		}
		return super.keyPressed(key, scanCode, modifiers);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderTransparentBackground(graphics);
		int x = (width - WIDTH) / 2;
		LevelUpScreen.panel(graphics, x - 12, height / 2 - 48, x + WIDTH + 12, height / 2 + 40);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 38, 0xFFE0C080);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(new BonfireScreen(data));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
