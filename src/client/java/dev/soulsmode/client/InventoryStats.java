package dev.soulsmode.client;

import dev.soulsmode.ExperienceSouls;
import dev.soulsmode.SoulsStats;
import dev.soulsmode.ds2.Attributes;
import dev.soulsmode.ds2.DerivedStats;
import dev.soulsmode.ds2.Ds2Data;
import dev.soulsmode.network.StatsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * Souls Mode stats panel to the right of the survival inventory (the user's request; layout is a design choice): level,
 * souls, attributes, HP, stamina, equip load used / max and the human / hollow state. The recipe book button is
 * removed in Souls Mode.
 */
public final class InventoryStats {
	/** Inventory background size (AbstractContainerScreen defaults used by InventoryScreen). */
	private static final int INVENTORY_WIDTH = 176;
	private static final int INVENTORY_HEIGHT = 166;
	public static final int WIDTH = 138;
	public static final int GAP = 4;
	private static final int LINE = 10;
	/** Attribute rows in DS2's menu order (same as the level-up screen). */
	private static final int[] ROW_SLOTS = {0, 1, 2, 3, 4, 5, 8, 6, 7};
	private static final int LABEL = 0xFFB0A080;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int WARN = 0xFFE05050;

	private static StatsPayload stats;
	/**
	 * The item whose weapon stats the panel shows instead of the character, picked with the middle mouse button (the
	 * user's request); empty = the character page.
	 */
	private static net.minecraft.world.item.ItemStack inspected = net.minecraft.world.item.ItemStack.EMPTY;
	/** Damage type names for the weapon page, in Ds2Damage's order. */
	private static final String[] TYPES = {"physical", "magic", "lightning", "fire", "dark", "poison"};
	/** Requirement order of WeaponParam (Ds2Damage.WeaponData.requirements): Str, Dex, Int, Fth. */
	private static final int[] REQUIREMENT_SLOTS = {Attributes.STRENGTH, Attributes.DEXTERITY, Attributes.INTELLIGENCE,
			Attributes.FAITH};
	private static final String[] REQUIREMENT_KEYS = {"strength", "dexterity", "intelligence", "faith"};
	private static final int PANEL_HEIGHT = (3 + 9 + 5) * LINE + 2 * LINE + 6;
	private static final dev.soulsmode.ds2.EquipLoadRows ROWS = dev.soulsmode.ds2.EquipLoadRows.load();

	/** The local player's equip load ratio (weight / max load) from the last stats sync; 0 before one. */
	public static float loadRatio() {
		if (stats == null || stats.attributes().size() < Attributes.SLOT_COUNT) {
			return 0;
		}
		short[] slots = new short[Attributes.SLOT_COUNT];
		for (int i = 0; i < slots.length; i++) {
			slots[i] = stats.attributes().get(i).shortValue();
		}
		float max = DerivedStats.compute(new Attributes(slots), Ds2Data.physicalStats()).maxEquipLoad()
				* (Minecraft.getInstance().player == null ? 1 : dev.soulsmode.SoulsEquipLoad.strengthFactor(Minecraft.getInstance().player));
		return max > 0 ? stats.weight() / max : 0;
	}

	/** The equip load's turn rate multiplier (EquipLoadRows.turn) for the local player. */
	public static float turnFactor(boolean dashing, boolean locked) {
		return ROWS.turn(loadRatio(), dashing, locked);
	}

	private InventoryStats() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(StatsPayload.TYPE, (payload, context) -> stats = payload);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stats = null);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if ((screen instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen
					|| screen instanceof net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen<?>)
					&& StaminaHud.soulsMode()) {
				// No recipe book in Souls Mode at the crafting table and furnaces: its button goes too.
				Screens.getButtons(screen).removeIf(button -> button instanceof ImageButton);
				return;
			}
			if (!(screen instanceof InventoryScreen) || !StaminaHud.soulsMode()) {
				return;
			}
			Screens.getButtons(screen).removeIf(button -> button instanceof ImageButton);
			// The inventory and the panel are centred together (the recipe book's room is free in Souls Mode).
			var container = (dev.soulsmode.client.mixin.AbstractContainerScreenAccessor) screen;
			container.soulsmode$setLeftPos(Math.max(0, (width - INVENTORY_WIDTH - GAP - WIDTH) / 2));
			int panelX = container.soulsmode$getLeftPos() + INVENTORY_WIDTH + GAP;
			inspected = net.minecraft.world.item.ItemStack.EMPTY;
			// Middle click on an item: its weapon stats; on the same item or an empty slot: back to the character.
			net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, button) -> {
				if (button != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE || !StaminaHud.soulsMode()) {
					return true;
				}
				net.minecraft.world.inventory.Slot slot = container.soulsmode$getHoveredSlot();
				net.minecraft.world.item.ItemStack stack = slot == null ? net.minecraft.world.item.ItemStack.EMPTY : slot.getItem();
				inspected = stack.isEmpty() || stack == inspected ? net.minecraft.world.item.ItemStack.EMPTY : stack;
				return false;
			});
			ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) -> {
				if (StaminaHud.soulsMode()) {
					if (!inspected.isEmpty()) {
						renderWeapons(graphics, client, panelX, height);
					} else {
						render(graphics, client, panelX, height);
					}
				}
			});
		});
	}

	private static void render(GuiGraphics graphics, Minecraft client, int x, int height) {
		if (stats == null || stats.attributes().size() < Attributes.SLOT_COUNT || client.player == null) {
			return;
		}
		short[] slots = new short[Attributes.SLOT_COUNT];
		for (int i = 0; i < slots.length; i++) {
			slots[i] = stats.attributes().get(i).shortValue();
		}
		Attributes attributes = new Attributes(slots);
		DerivedStats derived = DerivedStats.compute(attributes, Ds2Data.physicalStats());
		Font font = client.font;
		int panelHeight = PANEL_HEIGHT;
		// Centred on the screen vertically, like the inventory beside it.
		int top = (height - panelHeight) / 2;
		dev.soulsmode.client.bonfire.LevelUpScreen.panel(graphics, x, top, x + WIDTH, top + panelHeight);
		int left = x + 6;
		int right = x + WIDTH - 6;
		int y = top + 6;
		y = line(graphics, font, "gui.soulsmode.level_up.level", String.valueOf(attributes.soulLevel()), TEXT, left, right, y);
		y = line(graphics, font, "gui.soulsmode.level_up.souls", String.valueOf(ExperienceSouls.get(client.player)), TEXT,
				left, right, y);
		y = line(graphics, font, "gui.soulsmode.level_up.soul_memory", String.valueOf(stats.soulMemory()), TEXT, left, right,
				y);
		y += LINE / 2;
		for (int slot : ROW_SLOTS) {
			y = line(graphics, font, "gui.soulsmode.attribute." + Attributes.NAMES.get(slot),
					String.valueOf(attributes.get(slot)), TEXT, left, right, y);
		}
		y += LINE / 2;
		int hp = (int) Math.round(client.player.getHealth() * SoulsStats.DS2_HP_PER_HEALTH);
		int maxHp = (int) Math.round(client.player.getMaxHealth() * SoulsStats.DS2_HP_PER_HEALTH);
		y = line(graphics, font, "gui.soulsmode.stats.hp", hp + " / " + maxHp, TEXT, left, right, y);
		y = line(graphics, font, "gui.soulsmode.level_up.stamina", String.valueOf((int) derived.maxStamina()), TEXT, left,
				right, y);
		y = line(graphics, font, "gui.soulsmode.level_up.agility", String.valueOf(dev.soulsmode.ds2.Agility.shown(attributes)),
				TEXT, left, right, y);
		float load = derived.maxEquipLoad() * dev.soulsmode.SoulsEquipLoad.strengthFactor(client.player);
		String weight = String.format("%.1f / %.1f", stats.weight(), load);
		y = line(graphics, font, "gui.soulsmode.stats.weight", weight, stats.weight() > load ? WARN : TEXT, left, right, y);
		String percent = String.format("%.1f %%", load > 0 ? stats.weight() / load * 100 : 0);
		y = line(graphics, font, "gui.soulsmode.stats.load", percent, stats.weight() > load ? WARN : TEXT, left, right, y);
		Component state = stats.hollowing() > 0
				? Component.translatable("gui.soulsmode.stats.hollow", stats.hollowing())
				: Component.translatable("gui.soulsmode.stats.human");
		graphics.drawString(font, Component.translatable("gui.soulsmode.stats.state"), left, y, LABEL);
		graphics.drawString(font, state, right - font.width(state), y, stats.hollowing() > 0 ? WARN : TEXT);
	}

	/**
	 * The inspected item as DS2's weapon menu: the DS2 weapon it attacks as, its reinforcement, the
	 * attack per type as base + stat bonus (Ds2Damage.attackParts, with the grip and the lack penalty; resins and
	 * buffs are not included) and the Str / Dex / Int / Fth requirements, red when not met.
	 */
	private static void renderWeapons(GuiGraphics graphics, Minecraft client, int x, int height) {
		if (stats == null || stats.attributes().size() < Attributes.SLOT_COUNT || client.player == null) {
			return;
		}
		short[] slots = new short[Attributes.SLOT_COUNT];
		for (int i = 0; i < slots.length; i++) {
			slots[i] = stats.attributes().get(i).shortValue();
		}
		Attributes attributes = new Attributes(slots);
		Font font = client.font;
		int top = (height - PANEL_HEIGHT) / 2;
		dev.soulsmode.client.bonfire.LevelUpScreen.panel(graphics, x, top, x + WIDTH, top + PANEL_HEIGHT);
		int left = x + 6;
		int right = x + WIDTH - 6;
		int y = top + 6;
		// In the left hand it is the item in the off-hand slot; held in both hands, the current grip.
		boolean leftHand = inspected == client.player.getOffhandItem();
		boolean held = leftHand || inspected == client.player.getMainHandItem();
		boolean twoHanded = held
				&& dev.soulsmode.client.anim.GripClient.grip(client.player).twoHanded();
		weapon(graphics, font, leftHand ? "gui.soulsmode.stats.left_hand" : "gui.soulsmode.stats.right_hand", inspected,
				leftHand, twoHanded, attributes, left, right, y);
	}

	private static int weapon(GuiGraphics graphics, Font font, String hand, net.minecraft.world.item.ItemStack stack,
			boolean leftHand, boolean twoHanded, Attributes attributes, int left, int right, int y) {
		graphics.drawString(font, Component.translatable(hand), left, y, LABEL);
		y += LINE;
		net.minecraft.resources.ResourceLocation moveset = dev.soulsmode.api.SoulsWeapons.movesetFor(stack);
		String key = dev.soulsmode.SoulsAttack.damageKey(moveset);
		dev.soulsmode.ds2.Ds2Damage.WeaponData data = dev.soulsmode.ds2.Ds2Damage.weapon(key);
		float level = dev.soulsmode.SoulsAttack.upgradeLevel(stack);
		int infusion = dev.soulsmode.SoulsUpgrade.infusion(stack);
		Component name = stack.isEmpty() ? Component.translatable("gui.soulsmode.stats.no_weapon") : stack.getHoverName();
		// The titanite level and its tier's maximum (SoulsUpgrade), "+N / +max".
		int max = dev.soulsmode.SoulsUpgrade.maxLevel(stack);
		String plus = data == null || max <= 0 ? "" : " +" + dev.soulsmode.SoulsUpgrade.level(stack) + "/+" + max;
		String title = font.plainSubstrByWidth(name.getString(), right - left - font.width(plus)) + plus;
		graphics.drawString(font, title, left, y, TEXT);
		y += LINE;
		if (data == null) {
			return y;
		}
		float[][] parts = dev.soulsmode.ds2.Ds2Damage.attackParts(key, level, infusion, attributes, twoHanded, leftHand, -1, 0,
				null, null);
		for (int i = 0; i < TYPES.length; i++) {
			// The stick's own factor (SoulsAttack.itemFactor), as its hits deal.
			float factor = dev.soulsmode.SoulsAttack.itemFactor(stack);
			int base = Math.round(parts[0][i] * factor);
			int bonus = Math.round(parts[1][i] * factor);
			if (i > 0 && base <= 0 && bonus <= 0) {
				continue;
			}
			y = line(graphics, font, "gui.soulsmode.stats.attack." + TYPES[i], base + " + " + bonus, TEXT, left, right, y);
		}
		// Scaling letters (Str, Dex, Magic, Lightning, Fire, Dark), the ones that are not "-".
		String[] letters = dev.soulsmode.ds2.Ds2Damage.scalingLetters(key, Math.round(level), infusion);
		String[] scalingKeys = {"strength", "dexterity", "magic", "lightning", "fire", "dark"};
		graphics.drawString(font, Component.translatable("gui.soulsmode.stats.scaling"), left, y, LABEL);
		y += LINE;
		int sx = left;
		for (int i = 0; i < letters.length; i++) {
			if (letters[i].equals("-")) {
				continue;
			}
			String label = Component.translatable("gui.soulsmode.stats.scale." + scalingKeys[i]).getString() + " ";
			if (sx + font.width(label + letters[i]) > right) {
				sx = left;
				y += LINE;
			}
			graphics.drawString(font, label, sx, y, LABEL);
			sx += font.width(label);
			graphics.drawString(font, letters[i], sx, y, TEXT);
			sx += font.width(letters[i]) + 6;
		}
		if (sx == left) {
			graphics.drawString(font, "-", sx, y, TEXT);
		}
		y += LINE;
		// Requirements: the Strength one is halved two-handed (Ds2Damage.requirementLack).
		int cx = left;
		for (int i = 0; i < REQUIREMENT_SLOTS.length; i++) {
			int need = data.requirements()[i];
			if (i == 0 && twoHanded) {
				need /= 2;
			}
			String label = Component.translatable("gui.soulsmode.stats.req." + REQUIREMENT_KEYS[i]).getString() + " ";
			String value = String.valueOf(need);
			graphics.drawString(font, label, cx, y, LABEL);
			cx += font.width(label);
			graphics.drawString(font, value, cx, y, attributes.get(REQUIREMENT_SLOTS[i]) < need ? WARN : TEXT);
			cx += font.width(value) + 6;
		}
		return y + LINE;
	}

	private static int line(GuiGraphics graphics, Font font, String key, String value, int color, int left, int right,
			int y) {
		graphics.drawString(font, Component.translatable(key), left, y, LABEL);
		graphics.drawString(font, value, right - font.width(value), y, color);
		return y + LINE;
	}
}
