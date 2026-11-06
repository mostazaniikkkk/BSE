package dev.soulsmode.item;

import dev.soulsmode.SoulsResin;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** A DS2 pine resin (ItemParam 60250000..60280000): in Souls Mode, using it buffs the right weapon (SoulsResin). */
public class ResinItem extends Item {
	public ResinItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResultHolder.pass(stack);
		}
		return player instanceof ServerPlayer serverPlayer
				&& SoulsResin.use(serverPlayer, hand, SoulsResin.elementOf(stack.getItem()))
				? InteractionResultHolder.consume(stack) : InteractionResultHolder.pass(stack);
	}

	/** The DS2 short description (menu text). */
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable(BuiltInRegistries.ITEM.getKey(this).toLanguageKey("item") + ".desc")
				.withStyle(ChatFormatting.GRAY));
	}
}
