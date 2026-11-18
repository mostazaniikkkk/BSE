package dev.soulsmode.item;


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
 * DS2 Homeward Bone (ItemParam 60350000): in Souls Mode, DS2's prayer animation and then back to the last bonfire
 * rested at (SoulsHomeward). Stack of 99 (ItemParam +0x4a).
 */
public class HomewardBoneItem extends Item {
	public HomewardBoneItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResultHolder.pass(stack);
		}
		// DS2 asks first (SoulsUseCheck): the use starts from the client's YES, never from the vanilla use.
		return player instanceof ServerPlayer serverPlayer && dev.soulsmode.SoulsUseCheck.message(this) == 0
				&& dev.soulsmode.SoulsHomeward.use(serverPlayer, hand)
				? InteractionResultHolder.consume(stack) : InteractionResultHolder.pass(stack);
	}

	/** The DS2 short description (menu text). */
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.soulsmode.homeward_bone.desc").withStyle(ChatFormatting.GRAY));
	}
}
