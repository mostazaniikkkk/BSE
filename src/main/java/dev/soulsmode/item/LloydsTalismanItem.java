package dev.soulsmode.item;

import dev.soulsmode.SoulsThrown;
import net.minecraft.ChatFormatting;
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
 * DS2 Lloyd's Talisman (ItemParam 60531000): in Souls Mode it is thrown with DS2's animation; where it lands nothing
 * heals for 30 s (SoulsThrown, the user's design). One per stack (the user's choice).
 */
public class LloydsTalismanItem extends Item {
	public LloydsTalismanItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResultHolder.pass(stack);
		}
		return player instanceof ServerPlayer serverPlayer && SoulsThrown.useLloyd(serverPlayer, hand)
				? InteractionResultHolder.consume(stack) : InteractionResultHolder.pass(stack);
	}

	/** The DS2 short description (menu text). */
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.soulsmode.lloyds_talisman.desc").withStyle(ChatFormatting.GRAY));
	}
}
