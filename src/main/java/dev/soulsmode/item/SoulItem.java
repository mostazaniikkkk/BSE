package dev.soulsmode.item;

import dev.soulsmode.SoulsSoulItems;
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
 * A DS2 soul item (docs/research/soul_items.md, ItemParam 60630000..60660000): in Souls Mode, using it plays
 * a00_18_0090 and grants its souls (SpEffect 930), which are this mod's experience points.
 */
public class SoulItem extends Item {
	private final int souls;

	public SoulItem(int souls, Properties properties) {
		super(properties);
		this.souls = souls;
	}

	public int souls() {
		return souls;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResultHolder.pass(stack);
		}
		return player instanceof ServerPlayer serverPlayer && SoulsSoulItems.use(serverPlayer, hand)
				? InteractionResultHolder.consume(stack) : InteractionResultHolder.pass(stack);
	}

	/** The DS2 short description (menu text). */
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable(BuiltInRegistries.ITEM.getKey(this).toLanguageKey("item") + ".desc")
				.withStyle(ChatFormatting.GRAY));
	}
}
