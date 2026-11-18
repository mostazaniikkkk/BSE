package dev.soulsmode.entity;

import dev.soulsmode.SoulsIllagers;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Pillager;

import java.util.EnumSet;

/** The pillager's extra goals against Souls Mode players (SoulsIllagers). */
public final class IllagerGoals {
	private IllagerGoals() {
	}

	/**
	 * Backs away from a Souls Mode target inside the crossbow range while it keeps shooting. It strafes backwards like
	 * vanilla's skeleton (RangedBowAttackGoal: strafe -0.5 when too close) and takes no goal flag, so vanilla's
	 * RangedCrossbowAttackGoal keeps running and reloads: it only charges within its 8 blocks, and a goal holding MOVE
	 * there stopped it after the first shot (bug 2026-11-17: "solo un disparo, no recargan").
	 */
	public static final class KeepDistance extends Goal {
		private static final float BACK = -0.5f;
		private final Pillager pillager;

		public KeepDistance(Pillager pillager) {
			this.pillager = pillager;
			setFlags(EnumSet.noneOf(Flag.class));
		}

		@Override
		public boolean canUse() {
			return SoulsIllagers.shouldBackAway(pillager);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (pillager.getTarget() != null) {
				// The strafe is relative to the body's yaw: face the target first, as the skeleton's goal does.
				pillager.getMoveControl().strafe(BACK, 0);
				pillager.lookAt(pillager.getTarget(), 30, 30);
			}
		}

		@Override
		public void stop() {
			pillager.getMoveControl().strafe(0, 0);
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
