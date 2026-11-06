package dev.soulsmode.client.bonfire;

import dev.soulsmode.ds2.Attributes;
import dev.soulsmode.ds2.DerivedStats;
import dev.soulsmode.ds2.Ds2Data;
import dev.soulsmode.ds2.LevelUpMenu;
import dev.soulsmode.network.BonfirePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The level-up screen. Rows and their order come from FUN_1400c7a80 (level, then the slots 0 1 2 3 4 5 8 6 7) and
 * the +/- / confirm rules from {@link LevelUpMenu}; the layout and colours are design choices.
 */
public final class LevelUpScreen extends Screen {
	/** FUN_1400c7a80: row -> attribute slot. */
	private static final int[] ROW_SLOTS = {0, 1, 2, 3, 4, 5, 8, 6, 7};
	private static final int PANEL_WIDTH = 326;
	private static final int LINE = 13;
	private static final int TEXT = 0xFFE0E0E0;
	private static final int LABEL = 0xFFB0A080;
	private static final int CHANGED = 0xFF70B0FF;
	private static final int TOO_EXPENSIVE = 0xFFE05050;

	private final BonfirePayloads.Open data;
	private final LevelUpMenu menu;
	private final List<Button[]> rowButtons = new ArrayList<>();
	private Button confirm;
	private int left;
	private int top;

	public LevelUpScreen(BonfirePayloads.Open data) {
		super(Component.translatable("gui.soulsmode.level_up.title"));
		this.data = data;
		short[] slots = new short[Attributes.SLOT_COUNT];
		for (int i = 0; i < slots.length; i++) {
			slots[i] = data.attributes().get(i).shortValue();
		}
		// Experience levels: going up to soul level N costs N (the user's exception, LevelUpSoulsTable.EXPERIENCE_LEVELS).
		this.menu = new LevelUpMenu(new Attributes(slots), data.souls(), dev.soulsmode.ds2.LevelUpSoulsTable.EXPERIENCE_LEVELS);
	}

	@Override
	protected void init() {
		rowButtons.clear();
		left = (width - PANEL_WIDTH) / 2;
		top = Math.max(26, (height - (5 + ROW_SLOTS.length) * LINE) / 2 + 6);
		int rowTop = top + 5 * LINE;
		for (int row = 0; row < ROW_SLOTS.length; row++) {
			int slot = ROW_SLOTS[row];
			int y = rowTop + row * LINE - 2;
			Button minus = addRenderableWidget(Button.builder(Component.literal("-"), b -> {
				menu.lower(slot);
				refresh();
			}).bounds(left + 136, y, 12, 12).build());
			Button plus = addRenderableWidget(Button.builder(Component.literal("+"), b -> {
				menu.raise(slot);
				refresh();
			}).bounds(left + 150, y, 12, 12).build());
			rowButtons.add(new Button[] {minus, plus});
		}
		// Confirm and back go under the stats panel, so the screen fits a small window.
		int buttonsY = top + 10 * LINE + 10;
		confirm = addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.level_up.confirm"),
				b -> ClientPlayNetworking.send(new BonfirePayloads.LevelUp(attributesList())))
				.bounds(left + 180, buttonsY, 146, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.level_up.back"), b -> onClose())
				.bounds(left + 180, buttonsY + 24, 146, 20).build());
		refresh();
	}

	private void refresh() {
		for (int row = 0; row < ROW_SLOTS.length; row++) {
			int slot = ROW_SLOTS[row];
			rowButtons.get(row)[0].active = menu.canLower(slot);
			rowButtons.get(row)[1].active = menu.canRaise(slot);
		}
		confirm.active = menu.canConfirm();
	}

	private List<Integer> attributesList() {
		Attributes attributes = menu.attributes();
		List<Integer> list = new ArrayList<>(Attributes.SLOT_COUNT);
		for (int slot = 0; slot < Attributes.SLOT_COUNT; slot++) {
			list.add(attributes.get(slot));
		}
		return list;
	}

	/** Two framed panels: level, souls and attributes on the left, the stats they give on the right. */
	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderTransparentBackground(graphics);
		int bottom = top + (5 + ROW_SLOTS.length) * LINE + 2;
		panel(graphics, left - 8, top - 8, left + 170, bottom);
		panel(graphics, left + 180, top + 5 * LINE - 8, left + 326, top + 10 * LINE + 2);
	}

	public static void panel(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
		graphics.fill(x0, y0, x1, y1, 0xD0101010);
		graphics.renderOutline(x0, y0, x1 - x0, y1 - y0, 0xFF8A7650);
		graphics.renderOutline(x0 + 2, y0 + 2, x1 - x0 - 4, y1 - y0 - 4, 0x80584A30);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, top - 22, 0xFFE0C080);
		int y = top;
		boolean changed = menu.level() != menu.original().soulLevel();
		line(graphics, "gui.soulsmode.level_up.level", String.valueOf(menu.level()), y, changed ? CHANGED : TEXT);
		line(graphics, "gui.soulsmode.level_up.souls", String.valueOf(menu.souls()), y += LINE, changed ? CHANGED : TEXT);
		line(graphics, "gui.soulsmode.level_up.required", String.valueOf(menu.cost()), y += LINE,
				menu.cost() > menu.souls() ? TOO_EXPENSIVE : TEXT);
		line(graphics, "gui.soulsmode.level_up.soul_memory", String.valueOf(data.soulMemory()), y += LINE, TEXT);
		y += 2 * LINE;
		Attributes attributes = menu.attributes();
		for (int slot : ROW_SLOTS) {
			line(graphics, "gui.soulsmode.attribute." + Attributes.NAMES.get(slot), String.valueOf(attributes.get(slot)), y,
					menu.added(slot) > 0 ? CHANGED : TEXT);
			y += LINE;
		}

		DerivedStats before = DerivedStats.compute(menu.original(), Ds2Data.physicalStats());
		DerivedStats after = DerivedStats.compute(attributes, Ds2Data.physicalStats());
		int x = left + 188;
		y = top + 5 * LINE;
		derived(graphics, "gui.soulsmode.level_up.hp", before.maxHp(), after.maxHp(), x, y);
		derived(graphics, "gui.soulsmode.level_up.stamina", (int) before.maxStamina(), (int) after.maxStamina(), x, y += LINE);
		graphics.drawString(font, Component.translatable("gui.soulsmode.level_up.equip_load"), x, y += LINE, LABEL);
		String load = String.format("%.1f", after.maxEquipLoad());
		graphics.drawString(font, load, x + 130 - font.width(load), y,
				after.maxEquipLoad() != before.maxEquipLoad() ? CHANGED : TEXT);
		derived(graphics, "gui.soulsmode.level_up.attunement_slots", before.attunementSlots(), after.attunementSlots(),
				x, y += LINE);
		// FUN_1400cc620 row 0xb (common.fmg 909038): MenuStatsParam[(ATN + 3 ADP) / 4] +0x32, current and preview.
		derived(graphics, "gui.soulsmode.level_up.agility", dev.soulsmode.ds2.Agility.shown(menu.original()),
				dev.soulsmode.ds2.Agility.shown(attributes), x, y + LINE);
	}

	private void line(GuiGraphics graphics, String key, String value, int y, int color) {
		graphics.drawString(font, Component.translatable(key), left, y, LABEL);
		graphics.drawString(font, value, left + 130 - font.width(value), y, color);
	}

	private void derived(GuiGraphics graphics, String key, int before, int after, int x, int y) {
		graphics.drawString(font, Component.translatable(key), x, y, LABEL);
		String value = String.valueOf(after);
		graphics.drawString(font, value, x + 130 - font.width(value), y, after != before ? CHANGED : TEXT);
	}

	/** Leaving drops the unconfirmed points (nothing was paid yet) and goes back to the bonfire menu. */
	@Override
	public void onClose() {
		minecraft.setScreen(new BonfireScreen(data));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
