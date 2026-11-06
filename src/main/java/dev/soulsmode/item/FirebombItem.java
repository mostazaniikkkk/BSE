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
 * DS2 Firebomb (ItemParam 60570000): in Souls Mode it is thrown with DS2's animation and explodes where it
 * lands (SoulsThrown, the user's design). Stacks of 16 (the user's choice).
 */
public class FirebombItem extends Item {
	public FirebombItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResultHolder.pass(stack);
		}
		return player instanceof ServerPlayer serverPlayer && SoulsThrown.useFirebomb(serverPlayer, hand)
				? InteractionResultHolder.consume(stack) : InteractionResultHolder.pass(stack);
	}

	/** The DS2 short description (menu text). */
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.soulsmode.firebomb.desc").withStyle(ChatFormatting.GRAY));
	}
}
