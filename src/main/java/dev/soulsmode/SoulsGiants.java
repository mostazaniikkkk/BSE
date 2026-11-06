package dev.soulsmode;

import dev.soulsmode.ds2.C3096Attacks;
import dev.soulsmode.network.ZombieAttackPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The penalty for breaking an active bonfire (the user's design, 2026-11-04): a Minecraft Giant with DS2's The Last
 * Giant (c3096, docs/research/last_giant.md) appears there, announced like an invasion, stays until someone kills it,
 * and drops a Soul of a Giant.
 * <p>
 * Its fight is ai309600 / ai309610's: in phase 1 (weapon mode 0) the table S2147483644 is checked every tick while the
 * approach S2147483645 runs; single attacks launch where it stands, combos follow their steps at each HitDuration end
 * with their position and random cuts; after an attack, r < 10 within 4.5 m makes it leap back (action 1). Below 50 %
 * HP it plays action 0 (65001050) and goes to phase 2 (mode 1, table S2147483643), where it holds a log (the user's
 * choice instead of its own torn arm). HP 2530, defence 380, absorption 30 / 30 / 30 / 10 % (EnemyParam 309600); its
 * hits are its EnemyDamageParam rows through the player victim formula; it never flinches (column 7).
 * <p>
 * Authorised by the user (2026-11-04), not confirmed in DS2: f7300 counts as non-zero while fighting (the table comes
 * back), and f7003 (side) as "the target is on its right". Simplifications (backlog): the hit volume is the triggering
 * row's distance band and angle, not the EDP capsules at their dummies; the shockwaves use the same reach; the mode-1
 * locomotion uses the mode-0 walk / jog clips (mode 1's are not traced).
 */
public final class SoulsGiants {
	/** Entity tag of the penalty giant (it persists with the entity). */
	public static final String TAG = "soulsmode_last_giant";
	/** Entity tag once it is in phase 2 (mode 1). */
	public static final String MODE_TAG = "soulsmode_last_giant_mode1";
	/** Its ZombieAnimClient state for the clients: "mode:1" shows the log. */
	public static final String MODE_MESSAGE = "mode:";
	private static final int IDLE = 7001;
	private static final int IDLE_MODE1 = 7002;
	private static final int WALK = 37001;
	private static final int JOG = 47001;
	/** Back leap reach when it is not called by a table row (afterAttack maxDistance). */
	private static final float LEAP_REACH = 4.5f;

	private static final Map<Giant, GiantGoal> ACTIVE = new WeakHashMap<>();

	private SoulsGiants() {
	}

	public static boolean isLastGiant(Entity entity) {
		return entity instanceof Giant && entity.getTags().contains(TAG);
	}

	/** A player broke an active bonfire: the giant comes out next to it. */
	public static void onBonfireBroken(ServerPlayer player, BlockPos bonfire) {
		ServerLevel level = player.serverLevel();
		Giant giant = EntityType.GIANT.create(level);
		if (giant == null) {
			return;
		}
		giant.addTag(TAG);
		giant.setPersistenceRequired();
		giant.moveTo(bonfire.getX() + 0.5, bonfire.getY(), bonfire.getZ() + 0.5, player.getYRot() + 180, 0);
		// EnemyParam 309600 +0x28: 2530 HP.
		var health = giant.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(C3096Attacks.HP / SoulsStats.DS2_HP_PER_HEALTH);
		}
		giant.setHealth(giant.getMaxHealth());
		// Design choice: it notices players from farther than a mob's default 16 blocks.
		var follow = giant.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(48);
		}
		giant.setCustomName(Component.translatable("entity.soulsmode.last_giant"));
		level.addFreshEntity(giant);
		giant.setTarget(player);
		for (ServerPlayer p : level.players()) {
			if (p.distanceTo(giant) < 64) {
				p.sendSystemMessage(Component.translatable("message.soulsmode.invaded", giant.getDisplayName()));
			}
		}
	}

	/** The giant died: its soul, and the invasion's end text. */
	public static void onDeath(Giant giant, Entity killer) {
		giant.spawnAtLocation(new net.minecraft.world.item.ItemStack(dev.soulsmode.item.SoulsItems.SOUL_OF_A_GIANT));
		if (giant.level() instanceof ServerLevel level) {
			for (ServerPlayer p : level.players()) {
				if (p.distanceTo(giant) < 64) {
					p.sendSystemMessage(Component.translatable("message.soulsmode.invader_defeated", giant.getDisplayName()));
				}
			}
		}
	}

	/** A player's hit on it: DS2's enemy defence (damage.md "Defensa del enemigo") with EnemyParam 309600's values. */
	public static float damageFromPlayer(float[] attack, float rate, int floor, float weaponFactor) {
		return dev.soulsmode.ds2.Enemies.damageToEnemy(attack, rate, floor, weaponFactor, C3096Attacks.DEFENCE,
				C3096Attacks.ABSORPTION) / (float) SoulsStats.DS2_HP_PER_HEALTH;
	}

	/** Players starting to see it get its weapon mode (the log). */
	public static void onStartTracking(Entity entity, ServerPlayer viewer) {
		if (isLastGiant(entity) && entity.getTags().contains(MODE_TAG)) {
			ServerPlayNetworking.send(viewer, new ZombieAttackPayload(entity.getId(), MODE_MESSAGE + "1"));
		}
	}

	private enum State { LOOP, APPROACH, ATTACK }

	private enum Move { STAND, TURN, WALK, RUN }

	/** The fight loop (MOVE + LOOK) against its target. */
	public static final class GiantGoal extends Goal {
		private final Giant giant;
		private State state = State.LOOP;
		private Move move = Move.STAND;
		/** S2147483645's bytes 3 (how it approaches) and 4 (the run's "speed" read by the long rows). */
		private int byte3;
		private int byte4;
		private long approachStart;
		private long moveStart;
		private C3096Attacks.Action action;
		private C3096Attacks.Row row;
		private int r;
		private int step;
		private long stepStart;
		private float clipTime;
		private float yaw;
		private float[] lastRoot = {0, 0, 0};
		private boolean cut;
		private final Set<Long> hitDone = new HashSet<>();
		private String loco = "";

		public GiantGoal(Giant giant) {
			this.giant = giant;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		private LivingEntity target() {
			LivingEntity t = giant.getTarget();
			return isLastGiant(giant) && t != null && t.isAlive() && !(t instanceof ServerPlayer p && (p.isCreative()
					|| p.isSpectator())) ? t : null;
		}

		@Override
		public boolean canUse() {
			return giant.level() instanceof ServerLevel && target() != null;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse() || state == State.ATTACK;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			ACTIVE.put(giant, this);
			loop();
		}

		@Override
		public void stop() {
			ACTIVE.remove(giant);
			giant.getNavigation().stop();
			state = State.LOOP;
			loco = "";
			loco(idle());
		}

		private long now() {
			return giant.level().getGameTime();
		}

		private int mode() {
			return giant.getTags().contains(MODE_TAG) ? 1 : 0;
		}

		private int idle() {
			return mode() == 1 ? IDLE_MODE1 : IDLE;
		}

		/** Map 20 / 30 state 2: the phase change check, then the approach and the table. */
		private void loop() {
			state = State.LOOP;
			LivingEntity target = target();
			if (mode() == 0 && giant.getHealth() / giant.getMaxHealth() * 100 < C3096Attacks.phaseChangePercent()) {
				// 5300(f5200() < 50 ...) -> map 30: action 0 (65001050), then mode 1.
				launch(C3096Attacks.action(0, C3096Attacks.phaseChangeAction()), null);
				return;
			}
			if (target == null) {
				return;
			}
			startApproach(giant.distanceTo(target));
		}

		private C3096Attacks.Phase phase() {
			return C3096Attacks.phase(mode() + 1);
		}

		private void startApproach(double d) {
			state = State.APPROACH;
			approachStart = now();
			C3096Attacks.Phase p = phase();
			// On entry: byte 3 = 0 if d < e3, 38 if d < e3 + e0, else 37; byte 4 = 0.
			byte3 = d < p.stopDistance() ? 0 : d < p.stopDistance() + p.e0() ? 38 : 37;
			byte4 = 0;
			setMove(Move.STAND);
		}

		private void setMove(Move m) {
			if (move != m) {
				move = m;
				moveStart = now();
			}
		}

		@Override
		public void tick() {
			LivingEntity target = target();
			switch (state) {
				case LOOP -> loop();
				case APPROACH -> {
					if (target != null) {
						approach(target);
					}
				}
				case ATTACK -> attack(target);
			}
		}

		private void approach(LivingEntity target) {
			C3096Attacks.Phase p = phase();
			double d = giant.distanceTo(target);
			double a = angle(target);
			r = 1 + giant.getRandom().nextInt(100);
			// The table is checked every tick while the approach runs; the first matching row wins.
			for (C3096Attacks.Row candidate : p.rows()) {
				if (matches(candidate, d, a, target)) {
					launch(C3096Attacks.action(mode(), candidate.action()), candidate);
					return;
				}
			}
			float inApproach = (now() - approachStart) / 20f;
			if (inApproach >= p.giveUp()) {
				end(d);
				return;
			}
			if (d < p.stopDistance()) {
				// 5508: stop and turn towards it.
				giant.getNavigation().stop();
				turn(target);
				return;
			}
			if (byte3 == 37 && a < p.runAngle()) {
				run(target, true);
			} else if (byte3 == 38 && a < p.walkAngle()) {
				if (move == Move.WALK && (now() - moveStart) / 20f >= p.walkGiveUp()) {
					end(d);
					return;
				}
				run(target, false);
			} else {
				setMove(Move.TURN);
				giant.getNavigation().stop();
				turn(target);
			}
		}

		/** The approach gave up: within onApproachEnd's distance it leaps back, else it starts over. */
		private void end(double d) {
			giant.getNavigation().stop();
			C3096Attacks.Phase p = phase();
			if (d < p.approachEndMaxDistance()) {
				launch(C3096Attacks.action(mode(), p.approachEndAction()), null);
			} else {
				loop();
			}
		}

		private boolean matches(C3096Attacks.Row row, double d, double a, LivingEntity target) {
			if (a < row.angleMin() || a >= row.angleMax() || d < row.distMin() || d > row.distMax()) {
				return false;
			}
			if (row.speed() != null) {
				boolean slow = byte4 != 37 && byte4 != 38;
				if (row.speed().equals("slow") ? !slow : byte4 != Integer.parseInt(row.speed())) {
					return false;
				}
			}
			if (row.rand() > 0 && r >= row.rand()) {
				return false;
			}
			return row.side() == null || row.side() == onRight(target);
		}

		/** 5535 (run) / 5505 (walk) towards the target at the loop's root speed; byte 4 follows the run's time. */
		private void run(LivingEntity target, boolean run) {
			setMove(run ? Move.RUN : Move.WALK);
			float t = (now() - moveStart) / 20f;
			byte4 = t >= 1.3f ? 37 : t >= 1.1f ? 38 : t >= 0.4f && t < 0.7f ? 39 : byte4;
			C3096Attacks.Loop clip = C3096Attacks.loop(run ? JOG : WALK);
			double f = factor(clip.speed());
			if (giant.getNavigation().isDone() || (now() - moveStart) % 10 == 0) {
				giant.getNavigation().moveTo(target, f);
			}
			giant.getNavigation().setSpeedModifier(f);
			loco(run ? JOG : WALK);
		}

		/** The navigation factor for a root speed in m/s (ground speed ~ 43.17 (attribute x factor)^2, SoulsGolems). */
		private double factor(float speed) {
			double attribute = giant.getAttributeValue(Attributes.MOVEMENT_SPEED);
			return attribute > 0 ? Math.sqrt(speed / 43.17) / attribute : 1;
		}

		/** 5903: turn in place at EnemyMoveParam's 90 deg/s. */
		private void turn(LivingEntity target) {
			loco(idle());
			float want = (float) Math.toDegrees(Math.atan2(-(target.getX() - giant.getX()), target.getZ() - giant.getZ()));
			float delta = Mth.wrapDegrees(want - giant.getYRot());
			float max = C3096Attacks.TURN_DEGREES_PER_SECOND / 20;
			face(giant.getYRot() + Mth.clamp(delta, -max, max));
		}

		private void face(float yRot) {
			giant.setYRot(yRot);
			giant.setYBodyRot(yRot);
			giant.setYHeadRot(yRot);
		}

		private void launch(C3096Attacks.Action a, C3096Attacks.Row from) {
			if (a == null) {
				state = State.LOOP;
				return;
			}
			giant.getNavigation().stop();
			action = a;
			row = from;
			state = State.ATTACK;
			yaw = giant.getYRot();
			startStep(0);
		}

		private void startStep(int i) {
			step = i;
			stepStart = now();
			clipTime = 0;
			lastRoot = new float[] {0, 0, 0};
			cut = false;
			hitDone.clear();
			loco = "";
			send(action.steps().get(i).animation());
		}

		private void attack(LivingEntity target) {
			C3096Attacks.Step s = action.steps().get(step);
			float t = (now() - stepStart) / 20f;
			giant.getNavigation().stop();
			rootMotion(s, Math.min(t, s.duration()));
			if (action.mode() == 0 && action.action() == C3096Attacks.phaseChangeAction()) {
				// The weapon mode changes inside 65001050 (TAE 2500 at 4.533 s, its effect not confirmed): the log shows.
				for (float[] e : s.otherEvents()) {
					if (e[0] == 2500 && t >= e[1] && !giant.getTags().contains(MODE_TAG)) {
						giant.addTag(MODE_TAG);
						send(MODE_MESSAGE + "1");
					}
				}
			}
			if (target != null) {
				for (int i = 0; i < s.hits().size(); i++) {
					C3096Attacks.Hit h = s.hits().get(i);
					if (h.rate() > 0 && t >= h.start() && t < h.end() && hitDone.add(((long) i << 32) | target.getId())) {
						hit(target, h);
					}
				}
			}
			double d = target == null ? Double.MAX_VALUE : giant.distanceTo(target);
			double a = target == null ? 360 : angle(target);
			if (action.combo() && step < action.steps().size() - 1) {
				// S2147483641: the random cut (0 < r < c_k: no next step) and the position cut (f7001 >= reach,
				// f7002 >= angle) from 0.5 s; the next step starts at this one's HitDuration end.
				int[] cuts = row == null ? new int[] {0, 0} : row.cutRand();
				int c = step < cuts.length ? cuts[step] : 0;
				if (!cut && t >= 0.5f && (r < c || row != null && (d >= row.continueDistance() || a >= row.continueAngle()))) {
					cut = true;
				}
				if (!cut && t >= s.hitDurationEnd()) {
					startStep(step + 1);
					return;
				}
			}
			if (t >= s.duration()) {
				afterAttack(d);
			}
		}

		/** After an attack: r < 10 within 4.5 m -> the back leap (action 1); otherwise the loop. */
		private void afterAttack(double d) {
			C3096Attacks.Phase p = phase();
			boolean leap = action.action() == p.afterAction();
			int roll = 1 + giant.getRandom().nextInt(100);
			if (!leap && roll < p.afterRand() && d < p.afterMaxDistance()) {
				launch(C3096Attacks.action(mode(), p.afterAction()), null);
				return;
			}
			loop();
		}

		/** The step's trajectory delta in the facing it launched with, and its root turn. */
		private void rootMotion(C3096Attacks.Step s, float t) {
			float[] p1 = s.trajectory().at(t);
			float dl = p1[0] - lastRoot[0];
			float db = p1[1] - lastRoot[1];
			float dyaw = p1[2] - lastRoot[2];
			lastRoot = p1;
			clipTime = t;
			// The positions are in the launch frame; + yaw is counterclockwise from above (Minecraft's yaw is clockwise).
			double rad = Math.toRadians(yaw);
			if (dl != 0 || db != 0) {
				// Left of a facing (-sin, cos) is (cos, sin); back is (sin, -cos).
				giant.move(MoverType.SELF, new Vec3(dl * Math.cos(rad) + db * Math.sin(rad), 0,
						dl * Math.sin(rad) - db * Math.cos(rad)));
			}
			if (dyaw != 0) {
				face(giant.getYRot() - dyaw);
			}
		}

		/**
		 * A hit event: within the triggering row's distance band and angle (the back leap: 4.5 m all round); DS2's
		 * player victim formula with the EnemyDamageParam row, then its poise.
		 */
		private void hit(LivingEntity target, C3096Attacks.Hit h) {
			float reach = row == null ? LEAP_REACH : row.distMax();
			float maxAngle = row == null ? 360 : Math.max(row.angleMax(), 90);
			if (giant.distanceTo(target) - target.getBbWidth() / 2 > reach || angle(target) > maxAngle) {
				return;
			}
			C3096Attacks.DamageRow dmg = C3096Attacks.damageRow(h.enemyDamageParam());
			if (dmg == null) {
				return;
			}
			var source = giant.damageSources().mobAttack(giant);
			dev.soulsmode.ds2.Ds2Damage.Defence defence = null;
			if (target instanceof ServerPlayer player && SoulsGameMode.isActive(player)
					&& SoulsPlayer.data(player).isPresent()) {
				defence = SoulsAttack.defenceOf(player);
				source = SoulsAttack.soulsDamage(giant);
			}
			float amount = dev.soulsmode.ds2.Enemies.damageToPlayer(new dev.soulsmode.ds2.Enemies.Damage(dmg.values(),
					dmg.floor(), dmg.rate() * h.rate(), dmg.staminaDamage()), 1, defence) / (float) SoulsStats.DS2_HP_PER_HEALTH;
			if (amount > 0 && target.hurt(source, amount) && target instanceof ServerPlayer player) {
				SoulsPoise.onHit(player, dmg.poise(), giant);
			}
		}

		/** f7002: absolute angle in degrees between its front and the target. */
		private double angle(Entity target) {
			double toYaw = Math.toDegrees(Math.atan2(-(target.getX() - giant.getX()), target.getZ() - giant.getZ()));
			return Math.abs(Mth.wrapDegrees(toYaw - giant.getYRot()));
		}

		/** f7003(0, 179) taken as "the target is on its right" (authorised by the user, not confirmed). */
		private boolean onRight(Entity target) {
			double toYaw = Math.toDegrees(Math.atan2(-(target.getX() - giant.getX()), target.getZ() - giant.getZ()));
			// Minecraft's yaw grows clockwise from above: a target on the right is at a larger yaw.
			return Mth.wrapDegrees(toYaw - giant.getYRot()) > 0;
		}

		private void loco(int clip) {
			C3096Attacks.Loop l = C3096Attacks.loop(clip);
			if (l == null || l.animation().equals(loco)) {
				return;
			}
			loco = l.animation();
			send("loop:" + l.animation());
		}

		private void send(String animation) {
			ZombieAttackPayload payload = new ZombieAttackPayload(giant.getId(), animation);
			for (ServerPlayer player : PlayerLookup.tracking(giant)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}
}
