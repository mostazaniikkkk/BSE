package dev.soulsmode;

import dev.soulsmode.mixin.AxeItemAccessor;
import dev.soulsmode.mixin.HoeItemAccessor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The user's rule (2026-11-09): a tool with a DS2 moveset keeps its Minecraft job. Aimed at a block its use changes
 * (an axe stripping a log, scraping or unwaxing copper; a hoe tilling dirt), the use key is Minecraft's use instead of
 * the heavy attack; the attack key harvests crops with a hoe (AttackClient.mines).
 */
public final class SoulsTools {
	private SoulsTools() {
	}

	/**
	 * Whether the attack key with this tool breaks the block instead of attacking, on top of the right-tool rule: a
	 * hoe harvests what breaks at once (crops, flowers, grass, saplings).
	 */
	public static boolean harvests(ItemStack held, BlockState state, net.minecraft.world.level.BlockGetter level,
			net.minecraft.core.BlockPos pos) {
		return held.getItem() instanceof HoeItem && state.getDestroySpeed(level, pos) == 0;
	}

	/** Whether the main-hand tool's vanilla use would change the aimed block. */
	public static boolean usesOn(Player player, BlockHitResult hit, BlockState state) {
		ItemStack held = player.getMainHandItem();
		if (held.getItem() instanceof AxeItem) {
			return AxeItemAccessor.soulsmode$strippables().containsKey(state.getBlock())
					|| WeatheringCopper.getPrevious(state).isPresent()
					|| HoneycombItem.WAX_OFF_BY_BLOCK.get().containsKey(state.getBlock());
		}
		if (held.getItem() instanceof HoeItem) {
			var till = HoeItemAccessor.soulsmode$tillables().get(state.getBlock());
			return till != null && till.getFirst().test(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
		}
		return false;
	}
}
