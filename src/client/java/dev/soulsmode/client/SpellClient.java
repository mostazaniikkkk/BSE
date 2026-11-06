package dev.soulsmode.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.soulsmode.network.MagicPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** The local player's attuned spells (SoulsMagic): the active spell on the HUD and the key to switch spells. */
public final class SpellClient {
	/** DS2 switches spells with a d-pad button (input 9, not identified); the key is a design choice. */
	private static final KeyMapping CYCLE_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.soulsmode.cycle_spell", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.soulsmode"));

	private static MagicPayloads.State state = new MagicPayloads.State(List.of(), -1, 0);

	private SpellClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(MagicPayloads.State.TYPE, (payload, context) -> state = payload);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> state = new MagicPayloads.State(List.of(), -1, 0));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (CYCLE_KEY.consumeClick()) {
				if (client.player != null && StaminaHud.soulsMode()) {
					ClientPlayNetworking.send(new MagicPayloads.Cycle());
				}
			}
		});
		HudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
	}

	public static MagicPayloads.State state() {
		return state;
	}

	/** A spell's name: {@code spell.<namespace>.<path>} from the resource pack's lang, else its id. */
	public static Component name(ResourceLocation spell) {
		return Component.translatableWithFallback("spell." + spell.getNamespace() + "." + spell.getPath(), spell.toString());
	}

	/** The active spell and its uses, above the hotbar's left end (layout: design choice). */
	private static void render(GuiGraphics graphics) {
		Minecraft mc = Minecraft.getInstance();
		if (!StaminaHud.visible() || mc.options.hideGui || state.active() < 0 || state.active() >= state.attuned().size()) {
			return;
		}
		MagicPayloads.Entry e = state.attuned().get(state.active());
		int uses = 0;
		for (int i = state.active(); i < state.attuned().size() && state.attuned().get(i).spell().equals(e.spell()); i++) {
			uses += state.attuned().get(i).uses();
		}
		// Right-aligned left of the off-hand slot (29 px left of the hotbar): the uses, the name above them.
		int right = graphics.guiWidth() / 2 - 91 - 29 - 6;
		int y = graphics.guiHeight() - 19;
		int color = uses > 0 ? 0xFFE0E0FF : 0xFF808080;
		String count = String.valueOf(uses);
		graphics.drawString(mc.font, count, right - mc.font.width(count), y, color);
		Component name = name(e.spell());
		graphics.drawString(mc.font, name, Math.max(2, right - mc.font.width(name)), y - 10, color);
	}
}
