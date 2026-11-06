package dev.soulsmode.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * "Llama de la adversidad" (DS2 Bonfire Ascetic, ItemParam 60527000): burned from the bonfire menu
 * (dev.soulsmode.ascetic.SoulsAscetic). Using it in the hand does nothing, as in DS2, where it is only burned.
 */
public class BonfireAsceticItem extends Item {
	public BonfireAsceticItem(Properties properties) {
		super(properties);
	}

	/** The DS2 short description (SimpleExplanation 60527000, es: "Quémala en la hoguera y fortalece a los enemigos."). */
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.soulsmode.bonfire_ascetic.desc").withStyle(ChatFormatting.GRAY));
	}
}
