package dev.soulsmode;

import dev.soulsmode.item.SoulsItems;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Incandescent minerals (the user's design, 2026-11-16; not DS2. Tried first in DarkTwo as the incandescent steel, whose
 * rules it copies at the user's request). Souls Mode only:
 * <ul>
 * <li>A Souls Mode player taking iron / gold / copper ingots or netherite scrap out of a furnace's result slot (any
 * furnace, by hand: click, shift-click, number key, drop) gets the incandescent version instead, and no experience.
 * A hopper takes the normal item (the user's choice).</li>
 * <li>Carried in the inventory (main inventory, hotbar or off hand) it burns its Souls Mode holder: 1 damage per second
 * (damage type {@code soulsmode:incandescent}), unless an armour piece has Fire Protection. On the cursor it does
 * not burn.</li>
 * <li>More than 5 seconds out of the inventory (on the cursor, or lying on the ground) it cools back into the raw
 * mineral (raw iron / gold / copper, ancient debris), same count.</li>
 * <li>Thrown into a cauldron with water it becomes the finished item (same count) with the extinguish sound and
 * steam, drops the experience of its smelting recipe (raw mineral -> finished) and has a 25 % chance of taking one
 * level of water.</li>
 * </ul>
 * Behind the gamerule {@code soulsIncandescentMinerals} (on by default; its game feel was tried in DarkTwo).
 */
public final class SoulsIncandescent {
	/** Incandescent item -> {finished item, raw item}. */
	private static final Map<Item, Item[]> FORMS = new HashMap<>();
	/** Finished item -> incandescent item. */
	private static final Map<Item, Item> HOT = new HashMap<>();
	public static final int COOL_TICKS = 100;
	public static final float DRAIN_CHANCE = 0.25f;
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
			SoulsMode.id("incandescent"));
	public static final GameRules.Key<GameRules.BooleanValue> RULE = GameRuleRegistry.register(
			"soulsIncandescentMinerals", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));
	private static final Map<UUID, Integer> CURSOR = new HashMap<>();
	private static int tick;

	private SoulsIncandescent() {
	}

	private static void form(Item hot, Item finished, Item raw) {
		FORMS.put(hot, new Item[] {finished, raw});
		HOT.put(finished, hot);
	}

	public static void init() {
		form(SoulsItems.INCANDESCENT_IRON_INGOT, Items.IRON_INGOT, Items.RAW_IRON);
		form(SoulsItems.INCANDESCENT_GOLD_INGOT, Items.GOLD_INGOT, Items.RAW_GOLD);
		form(SoulsItems.INCANDESCENT_COPPER_INGOT, Items.COPPER_INGOT, Items.RAW_COPPER);
		form(SoulsItems.INCANDESCENT_NETHERITE_SCRAP, Items.NETHERITE_SCRAP, Items.ANCIENT_DEBRIS);
	}

	public static boolean isHot(ItemStack stack) {
		return !stack.isEmpty() && FORMS.containsKey(stack.getItem());
	}

	/** The finished item an incandescent one turns into (null for anything else). */
	public static Item finished(Item hot) {
		Item[] f = FORMS.get(hot);
		return f == null ? null : f[0];
	}

	private static ItemStack transmute(ItemStack stack, Item to) {
		return new ItemStack(to, stack.getCount());
	}

	private static boolean enabled(ServerPlayer player) {
		return SoulsGameMode.isActive(player) && player.serverLevel().getGameRules().getBoolean(RULE);
	}

	// ---- Furnace -------------------------------------------------------------------------------------------------

	/**
	 * Before a click on a furnace result slot by a Souls Mode player: its ingots / scrap become incandescent, so every
	 * way of taking them gives the incandescent item. Returns the slot to restore after the click, or null.
	 */
	public static Slot beforeClick(ServerPlayer player, Slot slot) {
		if (!(slot instanceof FurnaceResultSlot) || !enabled(player)) {
			return null;
		}
		Item hot = HOT.get(slot.getItem().getItem());
		if (hot == null) {
			return null;
		}
		slot.set(transmute(slot.getItem(), hot));
		return slot;
	}

	/** After the click: what was not taken goes back to the normal item (the furnace keeps stacking it). */
	public static void afterClick(Slot slot) {
		if (slot != null && isHot(slot.getItem())) {
			slot.set(transmute(slot.getItem(), finished(slot.getItem().getItem())));
		}
	}

	// ---- Inventory: burn and cursor ------------------------------------------------------------------------------

	public static void tick(MinecraftServer server) {
		boolean second = ++tick % 20 == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ItemStack carried = player.containerMenu.getCarried();
			if (isHot(carried)) {
				int t = CURSOR.merge(player.getUUID(), 1, Integer::sum);
				if (t > COOL_TICKS) {
					player.containerMenu.setCarried(transmute(carried, FORMS.get(carried.getItem())[1]));
					CURSOR.remove(player.getUUID());
				}
			} else {
				CURSOR.remove(player.getUUID());
			}
			if (second && SoulsGameMode.isActive(player) && !player.isCreative() && !player.isSpectator()
					&& carries(player) && !fireProtected(player)) {
				Holder<DamageType> type = player.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
						.getHolderOrThrow(DAMAGE);
				player.hurt(new DamageSource(type), 1);
			}
		}
	}

	private static boolean carries(ServerPlayer player) {
		for (ItemStack s : player.getInventory().items) {
			if (isHot(s)) {
				return true;
			}
		}
		return isHot(player.getOffhandItem());
	}

	private static boolean fireProtected(ServerPlayer player) {
		Holder<Enchantment> fire = player.serverLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
				.getHolderOrThrow(Enchantments.FIRE_PROTECTION);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
				EquipmentSlot.FEET}) {
			if (player.getItemBySlot(slot).getEnchantments().getLevel(fire) > 0) {
				return true;
			}
		}
		return false;
	}

	public static void forget(ServerPlayer player) {
		CURSOR.remove(player.getUUID());
	}

	// ---- On the ground -------------------------------------------------------------------------------------------

	/** ItemEntity tick: quench in a water cauldron, else cool after 5 s on the ground. */
	public static void itemTick(ItemEntity item) {
		if (!(item.level() instanceof ServerLevel level) || item.isRemoved() || !isHot(item.getItem())) {
			return;
		}
		ItemStack stack = item.getItem();
		BlockPos pos = item.blockPosition();
		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.WATER_CAULDRON)) {
			Item[] f = FORMS.get(stack.getItem());
			item.setItem(transmute(stack, f[0]));
			level.playSound(null, item.getX(), item.getY(), item.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1,
					1);
			level.sendParticles(ParticleTypes.CLOUD, item.getX(), item.getY() + 0.4, item.getZ(), 12, 0.2, 0.1, 0.2, 0.02);
			experience(level, item.position(), f[1], stack.getCount());
			if (level.random.nextFloat() < DRAIN_CHANCE) {
				LayeredCauldronBlock.lowerFillLevel(state, level, pos);
			}
			return;
		}
		if (item.getAge() > COOL_TICKS) {
			item.setItem(transmute(stack, FORMS.get(stack.getItem())[1]));
		}
	}

	/** The smelting recipe's experience (raw -> finished) for {@code count} items, rounded as the furnace does. */
	private static void experience(ServerLevel level, Vec3 at, Item raw, int count) {
		float per = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(raw)),
				level).map(h -> h.value().getExperience()).orElse(0f);
		float total = per * count;
		int whole = Mth.floor(total);
		float frac = Mth.frac(total);
		if (frac != 0 && level.random.nextFloat() < frac) {
			whole++;
		}
		if (whole > 0) {
			ExperienceOrb.award(level, at, whole);
		}
	}

	/** The furnace recipes ({@code recipesUsed}) whose result is a finished mineral: their experience is not popped. */
	public static boolean finishedMineral(ItemStack result) {
		return HOT.containsKey(result.getItem());
	}
}
