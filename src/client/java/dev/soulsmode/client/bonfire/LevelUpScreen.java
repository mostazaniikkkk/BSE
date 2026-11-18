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
 * the +/- / confirm rules from {@link LevelUpMenu}. The stats beside them are DS2's grid (FUN_1400cc620, docs/research/
 * stats.md "Pantalla de subir nivel"): 3 columns x 13 rows, every cell "current ⇒ preview" with the previewed
 * attributes and the same equipment. The layout and colours are design choices.
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
		left = Math.max(12, (width - (178 + GAP + gridWidth())) / 2 + 8);
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
		// Confirm and back go under the attributes panel, so the screen fits a small window.
		int buttonsY = top + (5 + ROW_SLOTS.length) * LINE + 8;
		confirm = addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.level_up.confirm"),
				b -> ClientPlayNetworking.send(new BonfirePayloads.LevelUp(attributesList())))
				.bounds(left - 8, buttonsY, 88, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.level_up.back"), b -> onClose())
				.bounds(left + 82, buttonsY, 88, 20).build());
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
		float g = gridScale();
		panel(graphics, gridX() - 8, top - 8, gridX() + (int) Math.ceil(gridWidth() * g) + 8,
				top + (int) Math.ceil(13 * GRID_LINE * g) + 2);
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

		grid(graphics, menu.original(), attributes);
	}

	private static final int GAP = 10;
	private static final int GRID_LINE = 11;
	private static final int COLUMN_GAP = 8;
	/** Column 2 (FUN_1400cc620 with struct B): R weapon 1 (row 0), L weapon 1 (row 4), strike / slash / thrust, poise. */
	private static final int[] COLUMN2_LABELS = {909043, 0, 0, 0, 909040, 0, 0, 0, 0, 909046, 909047, 909048, 909049};

	private int labelWidth() {
		int w = 0;
		for (int id : allLabels()) {
			w = Math.max(w, font.width(Component.translatable("gui.soulsmode.levelup." + id)));
		}
		return w;
	}

	private List<Integer> allLabels() {
		List<Integer> ids = new ArrayList<>();
		for (var c : dev.soulsmode.ds2.MenuStats.COLUMN0) {
			ids.add(c.label());
		}
		for (var c : dev.soulsmode.ds2.MenuStats.COLUMN1) {
			if (c.label() != 0) {
				ids.add(c.label());
			}
		}
		for (int id : COLUMN2_LABELS) {
			if (id != 0) {
				ids.add(id);
			}
		}
		return ids;
	}

	private int columnWidth() {
		return labelWidth() + 6 + font.width("9999.9 " + Component.translatable("gui.soulsmode.levelup.arrow").getString()
				+ " 9999.9");
	}

	private int gridWidth() {
		return 3 * columnWidth() + 2 * COLUMN_GAP;
	}

	private int gridX() {
		return left + 170 + GAP;
	}

	/** The grid shrinks to fit narrow windows. */
	private float gridScale() {
		return Math.min(1f, (width - gridX() - 12) / (float) gridWidth());
	}

	/** DS2's 39 cells: current ⇒ preview, the preview highlighted when it changes. */
	private void grid(GuiGraphics graphics, Attributes before, Attributes after) {
		var player = minecraft.player;
		if (player == null) {
			return;
		}
		var physical = Ds2Data.physicalStats();
		float g = gridScale();
		graphics.pose().pushPose();
		graphics.pose().translate(gridX(), top, 0);
		graphics.pose().scale(g, g, 1);
		int colW = columnWidth() + COLUMN_GAP;
		var c0 = dev.soulsmode.ds2.MenuStats.COLUMN0;
		var c1 = dev.soulsmode.ds2.MenuStats.COLUMN1;
		float statPoiseBefore = dev.soulsmode.ds2.Poise.statPoise(before) + dev.soulsmode.ds2.Poise.base();
		float statPoiseAfter = dev.soulsmode.ds2.Poise.statPoise(after) + dev.soulsmode.ds2.Poise.base();
		for (int row = 0; row < 13; row++) {
			int y = row * GRID_LINE;
			cell(graphics, 0, y, c0[row].label(), dev.soulsmode.ds2.MenuStats.column0(row, before, physical),
					dev.soulsmode.ds2.MenuStats.column0(row, after, physical), c0[row].decimal());
			if (c1[row].label() != 0) {
				cell(graphics, colW, y, c1[row].label(), dev.soulsmode.ds2.MenuStats.column1(row, before, statPoiseBefore),
						dev.soulsmode.ds2.MenuStats.column1(row, after, statPoiseAfter), c1[row].decimal());
			}
			if (COLUMN2_LABELS[row] != 0) {
				cell(graphics, 2 * colW, y, COLUMN2_LABELS[row], column2(row, player, before),
						column2(row, player, after), row == 12);
			}
		}
		graphics.pose().popPose();
	}

	/**
	 * Column 2 with the worn equipment: a weapon's attack (sum of physical, magic, lightning, fire and dark, struct B via
	 * FUN_1402019f0), the physical defence per type with the armour (FUN_140201780: Σ ArmorReinforceParam subtype 2 / 0 /
	 * 1 + Σ ArmorParam +0x1c x the stats' Phys DEF) and the poise with the armour.
	 */
	private float column2(int row, net.minecraft.client.player.LocalPlayer player, Attributes a) {
		switch (row) {
			case 0, 4 -> {
				boolean leftHand = row == 4;
				var stack = leftHand ? player.getOffhandItem() : player.getMainHandItem();
				String key = dev.soulsmode.SoulsAttack.damageKey(dev.soulsmode.api.SoulsWeapons.movesetFor(stack, leftHand));
				if (dev.soulsmode.ds2.Ds2Damage.weapon(key) == null) {
					return 0;
				}
				boolean twoHanded = dev.soulsmode.client.anim.GripClient.grip(player).twoHanded();
				float[][] parts = dev.soulsmode.ds2.Ds2Damage.attackParts(key, dev.soulsmode.SoulsAttack.upgradeLevel(stack),
						dev.soulsmode.SoulsUpgrade.infusion(stack), a, twoHanded, leftHand, -1, 0, null, null);
				float sum = 0;
				for (int i = 0; i < 5; i++) {
					sum += (int) Math.max(0, parts[0][i] + parts[1][i]);
				}
				return sum * dev.soulsmode.SoulsAttack.itemFactor(stack);
			}
			case 9, 10, 11 -> {
				int subtype = row == 9 ? 2 : row == 10 ? 0 : 1;
				float factor = 0;
				float armour = 0;
				for (var piece : dev.soulsmode.SoulsAttack.armorPieces(player)) {
					if (piece != null) {
						factor += piece.factor();
						armour += piece.defence()[subtype];
					}
				}
				return armour + factor * dev.soulsmode.ds2.MenuStats.physicalDefence(a);
			}
			case 12 -> {
				return dev.soulsmode.SoulsPoise.max(player, a);
			}
			default -> {
				return Float.NaN;
			}
		}
	}

	private void cell(GuiGraphics graphics, int x, int y, int label, float before, float after, boolean decimal) {
		graphics.drawString(font, Component.translatable("gui.soulsmode.levelup." + label), x, y, LABEL);
		String b = decimal ? String.format("%.1f", before) : String.valueOf((int) before);
		String a = decimal ? String.format("%.1f", after) : String.valueOf((int) after);
		String arrow = " " + Component.translatable("gui.soulsmode.levelup.arrow").getString() + " ";
		int right = x + columnWidth();
		int ax = right - font.width(a);
		graphics.drawString(font, a, ax, y, a.equals(b) ? TEXT : CHANGED);
		int arx = ax - font.width(arrow);
		graphics.drawString(font, arrow, arx, y, LABEL);
		graphics.drawString(font, b, arx - font.width(b), y, TEXT);
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
