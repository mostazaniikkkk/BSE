package dev.soulsmode;

import dev.soulsmode.ds2.C3000Attacks;
import dev.soulsmode.network.ZombieAttackPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Iron golems fight Souls Mode players like DS2's Ogre (c3000; the user's request, 2026-11-02;
 * docs/research/c3000_ogre.md): ai300000's fight loop S2147483610, attack table S2147483609 and attack subroutines
 * S2147483639 / S2147483632 / S2147483628 / S2147483633, playing the c3000 clips (attacks, idle / walk / jog). The hit
 * at each TAE 2200 window is the golem's own vanilla hit (IronGolem.doHurtTarget: its damage and effects), when the
 * target is within the action's reach and angle. Against anyone else the golem stays vanilla.
 * <p>
 * ESD functions whose meaning is not identified count as 0 (as SoulsZombies does); see c3000_ogre.md s.4 for each.
 */
public final class SoulsGolems {
	/** EnemyMoveParam 300000: +0x2c / +0x30 turn rate (rad/s) at speed 0 / at +0x20 (m/s); the turn outside attacks. */
	private static final float TURN_SLOW = 4.537856f;
	private static final float TURN_FAST = 4.886922f;
	private static final float TURN_SPEED = 3.2f;
	/** Combo 52 (S2147483633) has no approach: the hit counts with the table's condition, f7001 < 4.5, f7002 >= 110. */
	private static final float REAR_REACH = 4.5f;
	private static final float REAR_ANGLE = 110;
	private static final String IDLE = "c3000_7001";
	private static final String WALK = "c3000_37001";
	private static final String JOG = "c3000_47001";

	private static final Map<IronGolem, OgreGoal> ACTIVE = new WeakHashMap<>();
	/** The golem whose window hit is running, so the mixin lets it through. */
	private static IronGolem hitting;

	private SoulsGolems() {
	}

	/** Only against a Souls Mode player. */
	public static boolean applies(Entity target) {
		return target instanceof ServerPlayer player && player.isAlive() && SoulsGameMode.isActive(player);
	}

	/** IronGolem.doHurtTarget goes through only for a window's hit. */
	public static boolean allowHit(IronGolem golem) {
		return golem == hitting;
	}

	/** Whether the Ogre AI drives the golem now. */
	public static boolean active(Mob mob) {
		return mob instanceof IronGolem golem && ACTIVE.containsKey(golem);
	}

	/** A damage reaction cut the golem's attack (SoulsHitReaction): the fight loop starts over. */
	public static void interrupt(Mob mob) {
		if (mob instanceof IronGolem golem) {
			OgreGoal goal = ACTIVE.get(golem);
			if (goal != null) {
				goal.interrupt();
			}
		}
	}

	/**
	 * EnemyMoveParam 300000 outside an attack (FUN_1403a28b0, enemy_ai.md s.8.5): lerp(+0x2c, +0x30, clamp(v / +0x20))
	 * rad/s, in degrees per tick.
	 */
	private static float turnPerTick(Mob mob) {
		Vec3 v = mob.getDeltaMovement();
		float speed = (float) Math.sqrt(v.x * v.x + v.z * v.z) * 20;
		float f = Mth.clamp(speed / TURN_SPEED, 0, 1);
		return (float) Math.toDegrees(TURN_SLOW + (TURN_FAST - TURN_SLOW) * f) / 20;
	}

	/** The vanilla move control, turning at most at the Ogre's rate while its AI drives the golem. */
	public static final class OgreMoveControl extends MoveControl {
		public OgreMoveControl(Mob mob) {
			super(mob);
		}

		@Override
		protected float rotlerp(float from, float to, float max) {
			return super.rotlerp(from, to, active(mob) ? Math.min(max, turnPerTick(mob)) : max);
		}
	}

	private enum Phase { LOOP, APPROACH, ATTACK }

	/** Approach states of S2147483639 / S2147483632. */
	private enum Move { TURN_IN_REACH, TURN, WALK, RUN }

	/** Preempts the vanilla melee / move-towards goals (same flags, higher priority) while it applies. */
	public static final class OgreGoal extends Goal {
		private final IronGolem golem;
		private Phase phase = Phase.LOOP;
		private long phaseStart;
		private int r3;
		private C3000Attacks.Action action;
		private Move move;
		private long moveStart;
		private int step;
		private long stepStart;
		private float clipTime;
		private float yaw;
		private boolean cut;
		private boolean[] fired;
		private String loco = "";

		public OgreGoal(IronGolem golem) {
			this.golem = golem;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		private LivingEntity target() {
			LivingEntity t = golem.getTarget();
			return t != null && applies(t) ? t : null;
		}

		@Override
		public boolean canUse() {
			return golem.level() instanceof ServerLevel && target() != null;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			ACTIVE.put(golem, this);
			loop();
		}

		@Override
		public void stop() {
			ACTIVE.remove(golem);
			golem.getNavigation().stop();
			phase = Phase.LOOP;
			loco = "";
			send("");
		}

		void interrupt() {
			loop();
			loco = "";
		}

		private long now() {
			return golem.level().getGameTime();
		}

		/** S2147483610 state 1: r3 = RandInt(0, 10). */
		private void loop() {
			phase = Phase.LOOP;
			phaseStart = now();
			r3 = golem.getRandom().nextInt(11);
		}

		@Override
		public void tick() {
			LivingEntity target = target();
			if (target == null) {
				return;
			}
			double d = golem.distanceTo(target);
			double a = angle(target);
			switch (phase) {
				case LOOP -> {
					// S2147483610 state 3 over S2147483620(0.8, 1, 4, 50, 1.4, 1): to the table when the height
					// difference f7007 is in [-1, 2], 0 < r3 < 5 and d < 8 (the f5050 / f7080 / f5061 checks hold).
					// S2147483620 itself, with f7300 = 0, stays in its state 0 until f3() >= 5 and returns.
					golem.getNavigation().stop();
					loco(IDLE);
					double dy = target.getY() - golem.getY();
					if ((dy >= -1 && dy <= 2 && r3 > 0 && r3 < 5 && d < 8) || now() - phaseStart >= 5 * 20) {
						table(target, d, a);
					}
				}
				case APPROACH -> approach(target, d, a);
				case ATTACK -> attack(target, d, a);
			}
		}

		/** S2147483609: r0 = RandInt(0, 20); the action's subroutine. */
		private void table(LivingEntity target, double d, double a) {
			action = C3000Attacks.action(C3000Attacks.table(d, a, golem.getRandom().nextInt(21)));
			if (action == null) {
				loop();
				return;
			}
			if (Float.isNaN(action.reach())) {
				// S2147483633(52, 0, ...): requested where it stands.
				launch();
				return;
			}
			phase = Phase.APPROACH;
			moveStart = now();
			// State 1 of S2147483639 / S2147483632 (f7018(0, x) = 0 <= d <= x).
			if (d <= action.reach() && a < action.angle()) {
				launch();
			} else if (a >= action.angle() && d <= action.reach()) {
				enter(Move.TURN_IN_REACH);
			} else if (a >= 90) {
				enter(Move.TURN);
			} else {
				enter(d <= action.reach() + 1.5 ? Move.WALK : Move.RUN);
			}
		}

		private long stateStart;

		private void enter(Move m) {
			move = m;
			stateStart = now();
		}

		private float inState() {
			return (now() - stateStart) / 20f;
		}

		private void approach(LivingEntity target, double d, double a) {
			float reach = action.reach();
			switch (move) {
				case TURN_IN_REACH -> {
					// State 2 (5903): launch when facing (f7002 < angle) or after 1.5 s.
					turn(target);
					if (angle(target) < action.angle() || inState() >= 1.5f) {
						launch();
					}
				}
				case TURN -> {
					// State 3 (5903) until f7002 < 45 or 1.5 s, then walk within reach + 1.5, else run.
					turn(target);
					if (angle(target) < 45 || inState() >= 1.5f) {
						enter(d <= reach + 1.5 ? Move.WALK : Move.RUN);
					}
				}
				case WALK, RUN -> {
					// States 4 / 5 (5505 walk / 5535 run): into reach -> launch or turn; give up at f2(0) >= 3 / 10 s.
					boolean walk = move == Move.WALK;
					if (d <= reach) {
						golem.getNavigation().stop();
						if (a < action.angle()) {
							launch();
						} else {
							enter(Move.TURN_IN_REACH);
						}
						return;
					}
					if ((now() - moveStart) / 20f >= (walk ? 3 : 10)) {
						golem.getNavigation().stop();
						loop();
						return;
					}
					C3000Attacks.Loop clip = C3000Attacks.locomotion(walk ? 37001 : 47001);
					double f = factor(clip.speed());
					// A new path every half second (as the vanilla melee goal re-paths), the speed every tick.
					if (golem.getNavigation().isDone() || (now() - stateStart) % 10 == 0) {
						golem.getNavigation().moveTo(target, f);
					}
					golem.getNavigation().setSpeedModifier(f);
					loco(walk ? WALK : JOG);
				}
			}
		}

		/**
		 * The navigation factor that gives the loop's root motion speed (m/s): a mob's ground speed is about
		 * 43.17 (attribute x factor)^2 m/s (SoulsInvader).
		 */
		private double factor(float speed) {
			double attribute = golem.getAttributeValue(Attributes.MOVEMENT_SPEED);
			return attribute > 0 ? Math.sqrt(speed / 43.17) / attribute : 1;
		}

		/** 5903: turn towards the target at EnemyMoveParam's rate, in place. */
		private void turn(LivingEntity target) {
			golem.getNavigation().stop();
			loco(IDLE);
			float want = (float) Math.toDegrees(Math.atan2(-(target.getX() - golem.getX()), target.getZ() - golem.getZ()));
			float delta = Mth.wrapDegrees(want - golem.getYRot());
			float max = turnPerTick(golem);
			float yRot = golem.getYRot() + Mth.clamp(delta, -max, max);
			face(yRot);
		}

		private void face(float yRot) {
			golem.setYRot(yRot);
			golem.setYBodyRot(yRot);
			golem.setYHeadRot(yRot);
		}

		private void launch() {
			golem.getNavigation().stop();
			phase = Phase.ATTACK;
			yaw = golem.getYRot();
			startStep(0);
		}

		private void startStep(int i) {
			step = i;
			stepStart = now();
			clipTime = 0;
			cut = false;
			C3000Attacks.Step s = action.steps().get(i);
			fired = new boolean[s.hits().size()];
			loco = "";
			send(s.animation());
		}

		private void attack(LivingEntity target, double d, double a) {
			C3000Attacks.Step s = action.steps().get(step);
			float t = (now() - stepStart) / 20f;
			golem.getNavigation().stop();
			// No turn during the attack: EnemyAttackMoveParam 7 only turns with chr.b8+0x4ec / +0x4f0 set, and who sets
			// them is not established (enemy_ai.md s.8.5).
			face(yaw);
			rootMotion(s, Math.min(t, s.duration()));
			for (int i = 0; i < s.hits().size(); i++) {
				C3000Attacks.Hit h = s.hits().get(i);
				if (!fired[i] && t >= h.start()) {
					fired[i] = true;
					if (h.rate() > 0 && t < h.end()) {
						hit(target, h.enemyDamageParam());
					}
				}
			}
			if (action.combo() && step < action.steps().size() - 1) {
				// S2147483628: cut the combo when f7001 >= 6 or f7002 >= 90 (step 0 from 0.5 s on; combo 52's zero
				// limits always cut it). The next step starts at this step's HitDuration end.
				if (!cut && t >= (step == 0 ? 0.5f : 0) && (d >= action.continueDistance() || a >= action.continueAngle())) {
					cut = true;
				}
				if (!cut && t >= s.hitDurationEnd()) {
					startStep(step + 1);
					return;
				}
			}
			if (t >= s.duration()) {
				// S2147483636(2, 4, 5, 0, 0, 4, 0.8) ends at once with the unidentified checks at 0: next loop.
				loop();
			}
		}

		/** The step's trajectory delta, turned by the facing it launched with (the trajectory has no turn). */
		private void rootMotion(C3000Attacks.Step s, float t) {
			float[] p0 = s.trajectory().at(clipTime);
			float[] p1 = s.trajectory().at(t);
			clipTime = t;
			float dl = p1[0] - p0[0];
			float db = p1[1] - p0[1];
			if (dl == 0 && db == 0) {
				return;
			}
			double r = Math.toRadians(yaw);
			// Left of a facing (-sin, cos) is (cos, sin); back is (sin, -cos).
			golem.move(MoverType.SELF, new Vec3(dl * Math.cos(r) + db * Math.sin(r), 0,
					dl * Math.sin(r) - db * Math.cos(r)));
		}

		private void hit(LivingEntity target, int enemyDamageParam) {
			boolean rear = Float.isNaN(action.reach());
			float reach = rear ? REAR_REACH : action.reach();
			double a = angle(target);
			if (target.isAlive() && golem.distanceTo(target) - target.getBbWidth() / 2 < reach
					&& (rear ? a >= REAR_ANGLE : a < action.angle())) {
				hitting = golem;
				try {
					if (golem.doHurtTarget(target) && target instanceof net.minecraft.server.level.ServerPlayer player) {
						// The Ogre's EnemyDamageParam row (poise.md s.4.2).
						SoulsPoise.onHit(player, dev.soulsmode.ds2.Poise.enemyHit("c3000", enemyDamageParam), golem);
					}
				} finally {
					hitting = null;
				}
			}
		}

		/** f7002: absolute angle in degrees between the golem's front and the target. */
		private double angle(Entity target) {
			double toYaw = Math.toDegrees(Math.atan2(-(target.getX() - golem.getX()), target.getZ() - golem.getZ()));
			return Math.abs(Mth.wrapDegrees(toYaw - golem.getYRot()));
		}

		/** A locomotion loop; the idle one waits for a damage clip to end. */
		private void loco(String clip) {
			if (clip.equals(loco) || (clip.equals(IDLE) && SoulsHitReaction.clipPlaying(golem))) {
				return;
			}
			loco = clip;
			send("loop:" + clip);
		}

		private void send(String animation) {
			ZombieAttackPayload payload = new ZombieAttackPayload(golem.getId(), animation);
			for (ServerPlayer player : PlayerLookup.tracking(golem)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}
}
