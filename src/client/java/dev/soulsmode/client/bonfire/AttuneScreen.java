package dev.soulsmode.client.bonfire;

import dev.soulsmode.client.SpellClient;
import dev.soulsmode.content.SoulsContent;
import dev.soulsmode.network.BonfirePayloads;
import dev.soulsmode.network.MagicPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Attune spells at a bonfire (SoulsMagic): the attuned list on the left (click to take one out), the spells in the
 * inventory on the right (click to attune). The layout is a design choice; the rules are the server's.
 */
public final class AttuneScreen extends Screen {
	private static final int COLUMN = 150;
	private final BonfirePayloads.Open data;
	private MagicPayloads.State shown;

	public AttuneScreen(BonfirePayloads.Open data) {
		super(Component.translatable("gui.soulsmode.attune.title"));
		this.data = data;
	}

	@Override
	protected void init() {
		shown = SpellClient.state();
		shownInventory = inventorySpells();
		int left = width / 2 - COLUMN - 8;
		int right = width / 2 + 8;
		int y = 48;
		List<MagicPayloads.Entry> attuned = shown.attuned();
		for (int i = 0; i < attuned.size(); i++) {
			MagicPayloads.Entry e = attuned.get(i);
			int index = i;
			addRenderableWidget(Button.builder(Component.empty().append(SpellClient.name(e.spell()))
							.append(" " + e.uses() + "/" + e.max()),
					b -> ClientPlayNetworking.send(new MagicPayloads.Unattune(index)))
					.bounds(left, y + i * 22, COLUMN, 20).build());
		}
		int row = 0;
		var inventory = minecraft.player.getInventory();
		int used = attuned.stream().mapToInt(MagicPayloads.Entry::slots).sum();
		for (int slot = 0; slot < inventory.getContainerSize() && row < 14; slot++) {
			ItemStack stack = inventory.getItem(slot);
			ResourceLocation id = SoulsContent.spellId(stack);
			if (id == null) {
				continue;
			}
			int s = SoulsContent.spell(id).slots();
			int inventorySlot = slot;
			Button b = addRenderableWidget(Button.builder(Component.empty().append(SpellClient.name(id)).append(" [" + s + "]"),
					button -> ClientPlayNetworking.send(new MagicPayloads.Attune(inventorySlot)))
					.bounds(right, y + row * 22, COLUMN, 20).build());
			b.active = used + s <= shown.capacity() && attuned.size() < dev.soulsmode.ds2.Spells.MAX_ATTUNED;
			row++;
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.soulsmode.level_up.back"),
				b -> minecraft.setScreen(new BonfireScreen(data))).bounds(width / 2 - 50, height - 28, 100, 20).build());
	}

	/** Spells in the inventory by slot, to rebuild when the server's inventory update arrives after the state. */
	private java.util.List<ResourceLocation> inventorySpells() {
		java.util.List<ResourceLocation> out = new java.util.ArrayList<>();
		var inventory = minecraft.player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			out.add(SoulsContent.spellId(inventory.getItem(slot)));
		}
		return out;
	}

	private java.util.List<ResourceLocation> shownInventory;

	@Override
	public void tick() {
		if (SpellClient.state() != shown || !inventorySpells().equals(shownInventory)) {
			rebuildWidgets();
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int used = shown.attuned().stream().mapToInt(MagicPayloads.Entry::slots).sum();
		graphics.drawCenteredString(font, title, width / 2, 12, 0xFFE0C080);
		graphics.drawCenteredString(font, Component.translatable("gui.soulsmode.attune.slots", used, shown.capacity()),
				width / 2, 26, 0xFFFFFFFF);
		graphics.drawString(font, Component.translatable("gui.soulsmode.attune.attuned"), width / 2 - COLUMN - 8, 38, 0xFFC0C0C0);
		graphics.drawString(font, Component.translatable("gui.soulsmode.attune.inventory"), width / 2 + 8, 38, 0xFFC0C0C0);
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
