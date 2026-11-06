package dev.soulsmode.entity;

import dev.soulsmode.SoulsIllagers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** The pillager's extra goals against Souls Mode players (SoulsIllagers). */
public final class IllagerGoals {
	private IllagerGoals() {
	}

	/** Backs away from a Souls Mode target inside the crossbow range, so it can shoot again. */
	public static final class KeepDistance extends Goal {
		private final Pillager pillager;

		public KeepDistance(Pillager pillager) {
			this.pillager = pillager;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return SoulsIllagers.shouldBackAway(pillager);
		}

		@Override
		public void tick() {
			LivingEntity target = pillager.getTarget();
			if (target == null) {
				return;
			}
			pillager.getLookControl().setLookAt(target, 30, 30);
			if (pillager.getNavigation().isDone()) {
				Vec3 away = DefaultRandomPos.getPosAway(pillager, 12, 4, target.position());
				if (away != null) {
					pillager.getNavigation().moveTo(away.x, away.y, away.z, 1.0);
				}
			}
		}

		@Override
		public void stop() {
			pillager.getNavigation().stop();
		}
	}

	/** Melee with the sword it took (SoulsIllagers swaps it in when the target is within 4 blocks). */
	public static final class Sword extends MeleeAttackGoal {
		private final Pillager pillager;

		public Sword(Pillager pillager) {
			super(pillager, 1.0, true);
			this.pillager = pillager;
		}

		@Override
		public boolean canUse() {
			return SoulsIllagers.holdsSword(pillager) && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return SoulsIllagers.holdsSword(pillager) && super.canContinueToUse();
		}
	}
}
