package dev.soulsmode.item;

import dev.soulsmode.SoulsMultiplayItems;
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

/**
 * The Cracked Red Eye Orb and the Black Separation Crystal (SoulsMultiplayItems). They ask first (SoulsUseCheck): the
 * use starts from the client's YES, never from the vanilla use.
 */
public class MultiplayItem extends Item {
	public MultiplayItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResultHolder.pass(stack);
		}
		return player instanceof ServerPlayer serverPlayer && dev.soulsmode.SoulsUseCheck.message(this) == 0
				&& SoulsMultiplayItems.use(serverPlayer, hand)
				? InteractionResultHolder.consume(stack) : InteractionResultHolder.pass(stack);
	}

	/** The DS2 short description (menu text). */
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable(BuiltInRegistries.ITEM.getKey(this).toLanguageKey("item") + ".desc")
				.withStyle(ChatFormatting.GRAY));
	}
}
