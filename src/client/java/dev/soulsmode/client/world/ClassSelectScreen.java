package dev.soulsmode.client.world;

import dev.soulsmode.client.bonfire.LevelUpScreen;
import dev.soulsmode.ds2.Attributes;
import dev.soulsmode.ds2.DerivedStats;
import dev.soulsmode.ds2.Ds2Data;
import dev.soulsmode.ds2.StartingClasses.StartingClass;
import dev.soulsmode.network.ClassPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Starting class selection (the user's request): DS2's eight classes (PlayerStatusParam rows, starting_classes.json) in
 * DS2's order, with their names from common.fmg (es: NeutralSpanish) on the left, the selected one's level, attributes and derived stats on the right. It cannot be closed without picking
 * one. The layout is a design choice.
 */
public final class ClassSelectScreen extends Screen {
	/** Attribute rows in DS2's menu order (same as the level-up screen). */
	private static final int[] ROW_SLOTS = {0, 1, 2, 3, 4, 5, 8, 6, 7};
	private static final int LINE = 12;
	private static final int LIST_WIDTH = 110;
	private static final int PANEL_WIDTH = 170;
	private static final int LABEL = 0xFFB0A080;
	private static final int TEXT = 0xFFFFFFFF;

	/** On-screen order of DS2's class list (FUN_1400e06c0, docs/research/classes.md), by class index. */
	private static final int[] ORDER = {1, 2, 9, 4, 6, 7, 8, 10};

	private final List<StartingClass> classes = java.util.Arrays.stream(ORDER)
			.mapToObj(i -> Ds2Data.startingClasses().byIndex(i).orElseThrow()).toList();
	private int selected;
	private int left;
	private int top;

	public ClassSelectScreen() {
		super(Component.translatable("gui.soulsmode.class.title"));
	}

	public static Component name(StartingClass c) {
		return Component.translatableWithFallback("gui.soulsmode.class." + c.id(), c.name());
	}

	@Override
	protected void init() {
		left = (width - LIST_WIDTH - 10 - PANEL_WIDTH) / 2;
		top = (height - Math.max(classes.size() * 22, panelBottom(0) + 24)) / 2 + 10;
		for (int i = 0; i < classes.size(); i++) {
			int index = i;
			addRenderableWidget(Button.builder(name(classes.get(i)), b -> selected = index)
					.bounds(left, top + i * 22, LIST_WIDTH, 20).build());
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.class.confirm"), b -> confirm())
				.bounds(left + LIST_WIDTH + 10, panelBottom(top) + 4, PANEL_WIDTH, 20).build());
	}

	/** Bottom of the stats panel: the class name, level, 9 attributes, a gap and 3 derived stats. */
	private static int panelBottom(int top) {
		return top + 2 + LINE + 2 + 10 * LINE + 2 + 3 * LINE + 2;
	}

	private void confirm() {
		ClientPlayNetworking.send(new ClassPayloads.Choose(classes.get(selected).index()));
		onClose();
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics, mouseX, mouseY, partialTick);
		int x = left + LIST_WIDTH + 10;
		LevelUpScreen.panel(graphics, x, top - 4, x + PANEL_WIDTH, panelBottom(top));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, top - 22, 0xFFE0C080);
		StartingClass c = classes.get(selected);
		Attributes a = c.attributes();
		DerivedStats d = DerivedStats.compute(a, Ds2Data.physicalStats());
		int x = left + LIST_WIDTH + 18;
		int right = left + LIST_WIDTH + 10 + PANEL_WIDTH - 8;
		int y = top + 2;
		graphics.drawString(font, name(c), x, y, 0xFFE0C080);
		y += LINE + 2;
		y = line(graphics, "gui.soulsmode.level_up.level", String.valueOf(a.soulLevel()), x, right, y);
		for (int slot : ROW_SLOTS) {
			y = line(graphics, "gui.soulsmode.attribute." + Attributes.NAMES.get(slot), String.valueOf(a.get(slot)), x, right, y);
		}
		y = line(graphics, "gui.soulsmode.level_up.hp", String.valueOf(d.maxHp()), x, right, y + 2);
		y = line(graphics, "gui.soulsmode.level_up.stamina", String.valueOf((int) d.maxStamina()), x, right, y);
		line(graphics, "gui.soulsmode.level_up.equip_load", String.format("%.1f", d.maxEquipLoad()), x, right, y);
	}

	private int line(GuiGraphics graphics, String key, String value, int x, int right, int y) {
		graphics.drawString(font, Component.translatable(key), x, y, LABEL);
		graphics.drawString(font, value, right - font.width(value), y, TEXT);
		return y + LINE;
	}

	/** A class must be picked: no closing with Escape. */
	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
