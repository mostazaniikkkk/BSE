package dev.soulsmode;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The user's rule (2026-10-31; durability lowered 2026-11-03): in Souls Mode a stick does not stack and breaks after a
 * quarter of a wooden sword's durability, one point per hit. It takes Fire Aspect, Unbreaking and Mending from books
 * at an anvil (tags enchantable/fire_aspect and enchantable/durability). Sticks in a Souls Mode player's inventory are made single and damageable (the extra
 * ones go to free slots, or drop at the player's feet); outside Souls Mode they turn back into plain sticks.
 */
public final class SoulsStick {
	/** A quarter of the wooden sword's durability (59 / 4). */
	public static final int DURABILITY = Items.WOODEN_SWORD.getDefaultInstance().getMaxDamage() / 4;

	private SoulsStick() {
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 5 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean souls = SoulsGameMode.isActive(player);
			Inventory inventory = player.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (!stack.is(Items.STICK)) {
					continue;
				}
				boolean souled = stack.has(DataComponents.MAX_DAMAGE);
				if (souls && !souled) {
					int extra = stack.getCount() - 1;
					stack.setCount(1);
					stack.set(DataComponents.MAX_STACK_SIZE, 1);
					stack.set(DataComponents.MAX_DAMAGE, DURABILITY);
					stack.set(DataComponents.DAMAGE, 0);
					for (int n = 0; n < extra; n++) {
						ItemStack single = stack.copy();
						if (!inventory.add(single)) {
							player.drop(single, false);
						}
					}
				} else if (souls && stack.getMaxDamage() != DURABILITY) {
					// A stick made under the old durability: the new one, keeping the damage within it.
					stack.set(DataComponents.MAX_DAMAGE, DURABILITY);
					stack.set(DataComponents.DAMAGE, Math.min(stack.getDamageValue(), DURABILITY - 1));
				} else if (!souls && souled) {
					stack.remove(DataComponents.MAX_STACK_SIZE);
					stack.remove(DataComponents.MAX_DAMAGE);
					stack.remove(DataComponents.DAMAGE);
				}
			}
		}
	}

	/** After a Souls Mode hit with a stick: one point of durability. */
	public static void onHit(ServerPlayer player, ItemStack weapon, boolean leftHand) {
		if (weapon.is(Items.STICK) && weapon.has(DataComponents.MAX_DAMAGE)) {
			weapon.hurtAndBreak(1, player, leftHand ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
		}
	}
}
