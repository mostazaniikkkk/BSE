package dev.soulsmode.client.bonfire;

import dev.soulsmode.ascetic.AsceticRules;
import dev.soulsmode.item.SoulsItems;
import dev.soulsmode.network.BonfirePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The bonfire menu: level up, travel, attune, rename (the user's design), burn a Bonfire Ascetic (the user's design,
 * dev.soulsmode.ascetic.SoulsAscetic) or leave. The layout is a design choice. The title is the bonfire's name if it
 * has one.
 */
public final class BonfireScreen extends Screen {
	private static final int BUTTON_WIDTH = 160;
	private final BonfirePayloads.Open data;

	public BonfireScreen(BonfirePayloads.Open data) {
		super(title(data));
		this.data = data;
	}

	private static Component title(BonfirePayloads.Open data) {
		var level = net.minecraft.client.Minecraft.getInstance().level;
		String name = level == null ? "" : data.currentName(level.dimension().location());
		return name.isEmpty() ? Component.translatable("gui.soulsmode.bonfire.title") : Component.literal(name);
	}

	@Override
	protected void init() {
		int x = (width - BUTTON_WIDTH) / 2;
		int y = height / 2 - 55;
		Button levelUp = addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.bonfire.level_up"),
				button -> minecraft.setScreen(new LevelUpScreen(data))).bounds(x, y, BUTTON_WIDTH, 20).build());
		if (data.attributes().isEmpty()) {
			levelUp.active = false;
			levelUp.setTooltip(Tooltip.create(Component.translatable("gui.soulsmode.bonfire.no_class")));
		}
		Button travel = addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.bonfire.travel"),
				button -> minecraft.setScreen(new TravelScreen(data))).bounds(x, y + 24, BUTTON_WIDTH, 20).build());
		travel.active = data.bonfires().size() > 1;
		Button attune = addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.bonfire.attune"),
				button -> minecraft.setScreen(new AttuneScreen(data))).bounds(x, y + 48, BUTTON_WIDTH, 20).build());
		attune.active = !data.attributes().isEmpty();
		addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.bonfire.rename"),
				button -> minecraft.setScreen(new RenameScreen(data))).bounds(x, y + 72, BUTTON_WIDTH, 20).build());
		Button burn = addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.bonfire.burn_ascetic"),
				button -> confirmBurn()).bounds(x, y + 96, BUTTON_WIDTH, 20).build());
		burn.active = AsceticRules.ENABLED && minecraft.player != null && minecraft.player.getInventory()
				.contains(new net.minecraft.world.item.ItemStack(SoulsItems.BONFIRE_ASCETIC));
		if (!burn.active) {
			burn.setTooltip(Tooltip.create(Component.translatable("gui.soulsmode.bonfire.burn_none")));
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.bonfire.leave"), button -> onClose())
				.bounds(x, y + 120, BUTTON_WIDTH, 20).build());
	}

	/** DS2's bonfire burn confirmation (bofire.fmg 90100, "Quemar %s. / Aceptar?") with the item's name. */
	private void confirmBurn() {
		minecraft.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				ClientPlayNetworking.send(new BonfirePayloads.BurnAscetic());
			}
			minecraft.setScreen(new BonfireScreen(data));
		}, title, Component.translatable("gui.soulsmode.bonfire.burn_confirm",
				Component.translatable("item.soulsmode.bonfire_ascetic"))));
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// No blur: the player should see their character sitting at the fire.
		renderTransparentBackground(graphics);
		int x = (width - BUTTON_WIDTH) / 2;
		LevelUpScreen.panel(graphics, x - 12, height / 2 - 95, x + BUTTON_WIDTH + 12, height / 2 + 95);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 83, 0xFFE0C080);
	}

	@Override
	public void onClose() {
		BonfireClient.leave();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
