package dev.soulsmode.entity;

import dev.soulsmode.SoulsThrown;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * A thrown DS2 item. Lloyd's Talisman and the firebomb fly as Minecraft's thrown item (snowball physics): their effects
 * are adapted to Minecraft by the user, so DS2's bullet is not used. The Alluring Skull flies as its DS2 bullet
 * (BulletParam 60530000, docs/research/thrown_items.md "Calavera seductora"): no drag, gravity
 * {@link #SKULL_GRAVITY}, gone after {@link #SKULL_LIFE} ticks without touching anything. On landing,
 * {@link SoulsThrown#land} does what the item does.
 */
public class ThrownSoulsItem extends ThrowableItemProjectile {
	/** BulletParam 60530000 +0x5c x +0x64 = 3.0 x 2.0 = 6 m/s2, in blocks per tick squared. */
	public static final double SKULL_GRAVITY = 6.0 / 400;
	/** BulletParam 60530000 +0x48: 5.0 s. */
	public static final int SKULL_LIFE = 100;

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
	public void tick() {
		if (!getItem().is(dev.soulsmode.item.SoulsItems.ALLURING_SKULL)) {
			super.tick();
			return;
		}
		// DS2's bullet has no drag: the move uses this velocity, then only its gravity changes it.
		net.minecraft.world.phys.Vec3 before = getDeltaMovement();
		super.tick();
		if (!isRemoved()) {
			setDeltaMovement(before.subtract(0, SKULL_GRAVITY, 0));
			if (!level().isClientSide() && tickCount >= SKULL_LIFE) {
				// BulletParam +0xed = 0: no child when its life ends, so no lure.
				discard();
			}
		}
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
