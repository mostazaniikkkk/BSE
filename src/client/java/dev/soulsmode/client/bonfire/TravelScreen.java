package dev.soulsmode.client.bonfire;

import dev.soulsmode.KnownBonfire;
import dev.soulsmode.network.BonfirePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Travel between the bonfires the player has rested at. The layout and the bonfire names are design choices; the
 * picture of the bonfire under the mouse (or the first) shows next to the list, like DS2's travel menu
 * (BonfireShotClient).
 */
public final class TravelScreen extends Screen {
	private static final int PER_PAGE = 5;
	private static final int BUTTON_WIDTH = 200;
	/** The picture beside the list (16:9). */
	private static final int PICTURE_WIDTH = 160;
	private static final int PICTURE_HEIGHT = 90;
	private static final int GAP = 12;
	private final java.util.Map<Button, KnownBonfire> entries = new java.util.LinkedHashMap<>();
	private final BonfirePayloads.Open data;
	private final List<KnownBonfire> destinations;
	private int page;

	public TravelScreen(BonfirePayloads.Open data) {
		this(data, 0);
	}

	private TravelScreen(BonfirePayloads.Open data, int page) {
		super(Component.translatable("gui.soulsmode.travel.title"));
		this.data = data;
		this.destinations = data.bonfires();
		this.page = page;
	}

	@Override
	protected void init() {
		entries.clear();
		int x = left();
		int y = height / 2 - 62;
		int pages = Math.max(1, (destinations.size() + PER_PAGE - 1) / PER_PAGE);
		page = Math.min(page, pages - 1);
		for (int i = page * PER_PAGE; i < Math.min(destinations.size(), (page + 1) * PER_PAGE); i++) {
			KnownBonfire bonfire = destinations.get(i);
			Button button = addRenderableWidget(Button.builder(name(i, bonfire), b -> ClientPlayNetworking.send(
					new BonfirePayloads.Travel(bonfire.dimension(), bonfire.pos()))).bounds(x, y, BUTTON_WIDTH, 20).build());
			if (!customName(i).isEmpty()) {
				// A named bonfire keeps its place as the tooltip.
				button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(place(i, bonfire)));
			}
			button.active = !isCurrent(bonfire);
			entries.put(button, bonfire);
			y += 22;
		}
		y = height / 2 + 52;
		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), b -> minecraft.setScreen(new TravelScreen(data, page - 1)))
					.bounds(x, y, 20, 20).build()).active = page > 0;
			addRenderableWidget(Button.builder(Component.literal(">"), b -> minecraft.setScreen(new TravelScreen(data, page + 1)))
					.bounds(x + BUTTON_WIDTH - 20, y, 20, 20).build()).active = page < pages - 1;
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.level_up.back"), b -> onClose())
				.bounds(x + 24, y, BUTTON_WIDTH - 48, 20).build());
	}

	/** Left edge of the list: list and picture centred together. */
	private int left() {
		return (width - BUTTON_WIDTH - GAP - PICTURE_WIDTH) / 2;
	}

	private boolean isCurrent(KnownBonfire bonfire) {
		return minecraft.level != null && bonfire.isAt(minecraft.level.dimension().location(), data.current());
	}

	/** The bonfire's name (BonfireNames), "" if it has none. */
	private String customName(int index) {
		return index < data.names().size() ? data.names().get(index) : "";
	}

	/** The name if set (the user's design), else "#n (x, y, z)". */
	private Component name(int index, KnownBonfire bonfire) {
		String custom = customName(index);
		return custom.isEmpty() ? place(index, bonfire) : Component.literal(custom);
	}

	private Component place(int index, KnownBonfire bonfire) {
		Component place = Component.translatable("gui.soulsmode.travel.entry", index + 1,
				bonfire.pos().getX(), bonfire.pos().getY(), bonfire.pos().getZ());
		ResourceLocation overworld = ResourceLocation.withDefaultNamespace("overworld");
		if (bonfire.dimension().equals(overworld)) {
			return place;
		}
		return Component.translatable("gui.soulsmode.travel.entry_dimension", place, bonfire.dimension().getPath());
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderTransparentBackground(graphics);
		int x = left();
		LevelUpScreen.panel(graphics, x - 12, height / 2 - 88, x + BUTTON_WIDTH + GAP + PICTURE_WIDTH + 12,
				height / 2 + 82);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 78, 0xFFE0C080);
		KnownBonfire shown = null;
		for (var e : entries.entrySet()) {
			if (shown == null || e.getKey().isHovered()) {
				shown = e.getValue();
			}
		}
		int px = left() + BUTTON_WIDTH + GAP;
		int py = height / 2 - 62;
		graphics.fill(px - 1, py - 1, px + PICTURE_WIDTH + 1, py + PICTURE_HEIGHT + 1, 0xFF5A4A30);
		graphics.fill(px, py, px + PICTURE_WIDTH, py + PICTURE_HEIGHT, 0xFF000000);
		ResourceLocation picture = shown == null ? null : BonfireShotClient.texture(shown.dimension(), shown.pos());
		if (picture != null) {
			graphics.blit(picture, px, py, PICTURE_WIDTH, PICTURE_HEIGHT, 0, 0, BonfireShotClient.WIDTH,
					BonfireShotClient.HEIGHT, BonfireShotClient.WIDTH, BonfireShotClient.HEIGHT);
		} else if (shown != null) {
			graphics.drawCenteredString(font, Component.translatable("gui.soulsmode.travel.no_picture"),
					px + PICTURE_WIDTH / 2, py + PICTURE_HEIGHT / 2 - 4, 0xFF808080);
		}
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
