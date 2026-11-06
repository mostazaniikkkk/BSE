package dev.soulsmode.entity;

import dev.soulsmode.SoulsThrown;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * A thrown DS2 item. The flight is Minecraft's thrown item (snowball physics): the effects are adapted to Minecraft by
 * the user, so DS2's bullet is not used. On landing, {@link SoulsThrown#land} does what the item does.
 */
public class ThrownSoulsItem extends ThrowableItemProjectile {
	public ThrownSoulsItem(EntityType<? extends ThrownSoulsItem> type, Level level) {
		super(type, level);
	}

	public ThrownSoulsItem(Level level, LivingEntity owner) {
		super(SoulsEntities.THROWN_ITEM, owner, level);
	}

	@Override
	protected Item getDefaultItem() {
		return dev.soulsmode.item.SoulsItems.LLOYDS_TALISMAN;
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!level().isClientSide()) {
			SoulsThrown.land(this, result);
			discard();
		}
	}
}
