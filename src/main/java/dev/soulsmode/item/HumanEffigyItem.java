package dev.soulsmode.item;

import dev.soulsmode.SoulsHollow;
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

/** DS2 Human Effigy (ItemParam 60151000): in Souls Mode, using it restores human form (SoulsHollow). */
public class HumanEffigyItem extends Item {
	public HumanEffigyItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResultHolder.pass(stack);
		}
		return player instanceof ServerPlayer serverPlayer && SoulsHollow.use(serverPlayer, hand)
				? InteractionResultHolder.consume(stack) : InteractionResultHolder.pass(stack);
	}

	/** The DS2 short description (menu text, es: "Anula estado de Hueco."). */
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.soulsmode.human_effigy.desc").withStyle(ChatFormatting.GRAY));
	}
}
