package dev.soulsmode;

import dev.soulsmode.ds2.Dodge;
import dev.soulsmode.ds2.Perception;
import dev.soulsmode.mixin.MobAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * DS2 enemy perception against Souls Mode players (docs/research/detection.md; the user's decisions, 2026-11-02):
 * <ul>
 * <li>Ground hostile mobs ({@link Monster}s but the flying, water-only and own-sense ones, see {@link #kind}) see and
 * hear with LogicComParam 102010 (Hollow Soldier); the iron golem with 300030 and the Ogre's alarm rules.</li>
 * <li>A mob only takes a Souls Mode player as a target once it perceives it: the vanilla targeting checks
 * (TargetingConditions, TargetGoal, DefendVillageTargetGoal mixins) ask {@link #acquired}. What makes it hostile stays
 * vanilla; being hurt by the player, and any target set outside the targeting checks (vanilla alerts), count as
 * perceiving it.</li>
 * <li>Sight: candidates rebuilt every 2-3 s, each checked on SearchEzStateCtrl's timers with FUN_14043bef0. Once
 * acquired the player is kept while it is within the always-seen distance or a clear ray of 55 m (no cone), and
 * forgotten 10 s after that stops; forgetting drops the target.</li>
 * <li>Hearing: the player's actions make sounds at their DS2 clips' TAE event times ({@link #tick}); a mob hears the
 * nearest one that f7320 lets through (walls do not block). A Hollow investigates it (ai102010) or, with the player
 * within 3 m, is alarmed; the golem is alarmed with the player within 10 m.</li>
 * </ul>
 * Lighting (the user's decision): the mob is lit with a light level of at least 8 at its feet, or within 27 m of a
 * player holding the torch with a clear ray. No group alarm (placement groups do not exist in Minecraft).
 */
public final class SoulsPerception {
	/** Game-feel feature switch (CLAUDE.md rule 2): on so it can be playtested. */
	public static final boolean ENABLED = true;
	/** The user's threshold for a lit mob: Minecraft light level (block, or sky with the time of day). */
	private static final int LIT_LEVEL = 8;
	/** AiManager+0x1260 holds 64 sounds. */
	private static final int MAX_SOUNDS = 64;

	private enum Kind { HOLLOW, OGRE }

	private SoulsPerception() {
	}

	/**
	 * Which mobs perceive like DS2: the iron golem (Ogre) and every {@link Monster} except the endermen (their stare
	 * rule stays), the warden (its own senses), and the flying or water-only blaze, vex, guardians and wither.
	 */
	private static Kind kind(Mob mob) {
		if (mob instanceof IronGolem) {
			return Kind.OGRE;
		}
		if (mob instanceof Monster && !(mob instanceof EnderMan || mob instanceof Warden || mob instanceof Blaze
				|| mob instanceof Vex || mob instanceof Guardian || mob instanceof WitherBoss)) {
			return Kind.HOLLOW;
		}
		return null;
	}

	private static Perception.Logic logic(Kind kind) {
		return kind == Kind.OGRE ? Perception.OGRE : Perception.HOLLOW;
	}

	/** A Souls Mode player a mob can perceive. */
	private static boolean isTarget(LivingEntity entity) {
		return entity instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()
				&& SoulsGameMode.isActive(player);
	}

	/** Whether the perception decides if {@code attacker} may target {@code target}. */
	public static boolean gates(LivingEntity attacker, LivingEntity target) {
		return ENABLED && attacker instanceof Mob mob && !mob.level().isClientSide() && kind(mob) != null
				&& isTarget(target);
	}

	/** Whether the mob has perceived the player and not forgotten it. */
	public static boolean acquired(Mob mob, ServerPlayer player) {
		State s = STATES.get(mob);
		return s != null && s.acquired.containsKey(player.getUUID());
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sounds

	private record Noise(ServerLevel level, Vec3 pos, Perception.Sound sound, ServerPlayer emitter, long tick) {
	}

	private static final List<Noise> SOUNDS = new ArrayList<>();

	/** A Souls Mode player makes a sound where it stands (lives 0.1 s). */
	public static void emit(ServerPlayer player, Perception.Sound sound) {
		if (!ENABLED || !isTarget(player) || sound.level() <= 0) {
			return;
		}
		if (SOUNDS.size() >= MAX_SOUNDS) {
			SOUNDS.remove(0);
		}
		SOUNDS.add(new Noise(player.serverLevel(), player.position(), sound, player, player.serverLevel().getGameTime()));
	}

	private static boolean alive(Noise noise, long now) {
		return now - noise.tick() <= Math.round(Perception.SOUND_LIFE * 20);
	}

	/** What the server tracks of each player to know its clips. */
	private static final class Body {
		final Perception.Air air = new Perception.Air();
		/** Positions this tick and the two before. */
		final Vec3[] trail = new Vec3[3];
		Perception.Gait gait;
		long gaitStart;
		/** Locomotion is under RollLanding until this tick. */
		long landingUntil = -1;
		long landingSound = -1;
		Dodge dodge;
		long dodgeSound = -1;
	}

	private static final Map<UUID, Body> BODIES = new HashMap<>();
	/** Status bit 10 of b8+0x4b8 (Hush, Slumbering Dragoncrest Ring): suppressible sounds are not made. */
	private static final int SILENT_BIT = 10;

	/** The dodge just started: its clip's event 300 at its time on the animation clock. */
	public static void onDodge(ServerPlayer player, Dodge kind, float speed) {
		if (!ENABLED) {
			return;
		}
		Body body = BODIES.computeIfAbsent(player.getUUID(), k -> new Body());
		body.dodge = kind;
		body.dodgeSound = player.serverLevel().getGameTime() + Math.round(Perception.dodgeTime(kind) / speed * 20);
	}

	/** RollLanding goes back to locomotion at 0.5667 s when moving (movement.md, 0x20e6). */
	private static final float ROLL_LANDING_EXIT = 0.5667f;

	/** The players' sounds, then expired sounds and dead mobs. */
	public static void tick(MinecraftServer server) {
		if (!ENABLED) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!isTarget(player)) {
				BODIES.remove(player.getUUID());
				continue;
			}
			tickBody(player, BODIES.computeIfAbsent(player.getUUID(), k -> new Body()));
		}
		for (Iterator<Noise> it = SOUNDS.iterator(); it.hasNext(); ) {
			Noise noise = it.next();
			if (!alive(noise, noise.level().getGameTime()) || noise.emitter().isRemoved()) {
				it.remove();
			}
		}
	}

	private static void tickBody(ServerPlayer player, Body body) {
		long now = player.serverLevel().getGameTime();
		Vec3 pos = player.position();
		body.trail[2] = body.trail[1];
		body.trail[1] = body.trail[0];
		body.trail[0] = pos;
		Vec3 back = body.trail[2] != null ? body.trail[2] : body.trail[1] != null ? body.trail[1] : pos;
		int span = body.trail[2] != null ? 2 : 1;
		// Moved over the last two ticks (the server sees the client's moves as packets arrive, not one every tick).
		double mx = pos.x - back.x;
		double mz = pos.z - back.z;
		double moved = Math.sqrt(mx * mx + mz * mz);
		double speed = moved / span * 20;
		boolean free = !player.isSwimming() && !player.isFallFlying() && !player.isPassenger();
		boolean ground = player.onGround();
		if (!free) {
			body.air.reset(ground, pos.y);
		} else {
			switch (body.air.update(ground, pos.y, speed, now / 20.0)) {
				case TAKEOFF -> {
					// a00_13_0020 JumpTakeoff, event 300 at 0 s (a jump attack plays its own clip instead).
					if (!SoulsAttack.isActing(player)) {
						if (!SoulsSpEffects.statusFlag(player, SILENT_BIT)) {
					emit(player, Perception.JUMP);
				}
					}
				}
				case ROLL_LANDING -> {
					body.landingSound = now + Math.round(Perception.ROLL_LANDING_TIME * 20);
					body.landingUntil = now + Math.round(ROLL_LANDING_EXIT * 20);
				}
				case NONE -> {
				}
			}
		}
		if (body.landingSound >= 0 && now >= body.landingSound) {
			body.landingSound = -1;
			emit(player, Perception.ROLL_LANDING);
		}
		if (body.dodgeSound >= 0 && now >= body.dodgeSound) {
			// Only if that dodge still plays (a new dodge or its end cancels it).
			if (SoulsRoll.dodging(player) == body.dodge) {
				if (body.dodge.isRoll() || !SoulsSpEffects.statusFlag(player, SILENT_BIT)) {
					emit(player, Perception.dodgeSound(body.dodge));
				}
			}
			body.dodgeSound = -1;
		}
		Perception.Gait gait = null;
		if (free && ground && moved > 1e-4 && now >= body.landingUntil && !SoulsRoll.isDodging(player)
				&& !SoulsAttack.isActing(player)) {
			gait = gait(player, mx, mz);
		}
		if (gait != body.gait) {
			body.gait = gait;
			body.gaitStart = now;
		}
		if (gait != null) {
			double t = (now - body.gaitStart) / 20.0;
			if (gait.fires(t - 0.05, t) && !SoulsSpEffects.statusFlag(player, SILENT_BIT)) {
				emit(player, gait.sound);
			}
		}
	}

	/**
	 * The locomotion loop the client plays (Locomotion: sprint = Dash, sneak = Walk, else Jog; locked on, the side
	 * clip of the dominant direction), or null for the silent ones (Walk and side walk have no level).
	 */
	private static Perception.Gait gait(ServerPlayer player, double mx, double mz) {
		if (player.isSprinting()) {
			return Perception.Gait.DASH;
		}
		if (player.isShiftKeyDown()) {
			return null;
		}
		if (SoulsStamina.isLocked(player)) {
			double yaw = Math.toRadians(player.getYRot());
			double forward = mx * -Math.sin(yaw) + mz * Math.cos(yaw);
			double left = mx * Math.cos(yaw) + mz * Math.sin(yaw);
			if (Math.abs(forward) >= Math.abs(left)) {
				return forward >= 0 ? Perception.Gait.SIDE_JOG_FORWARD : Perception.Gait.SIDE_JOG_BACK;
			}
			return left >= 0 ? Perception.Gait.SIDE_JOG_LEFT : Perception.Gait.SIDE_JOG_RIGHT;
		}
		return Perception.Gait.JOG;
	}

	public static void forget(ServerPlayer player) {
		BODIES.remove(player.getUUID());
		SOUNDS.removeIf(noise -> noise.emitter() == player);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Mobs

	private static final class Candidate {
		final ServerPlayer player;
		long nextCheck;
		boolean seen;

		Candidate(ServerPlayer player) {
			this.player = player;
		}
	}

	private record Ray(long tick, boolean clear) {
	}

	private static final class State {
		/** Acquired players and the tick each was last seen. */
		final Map<UUID, Long> acquired = new HashMap<>();
		long memoryCheck;
		final List<Candidate> candidates = new ArrayList<>();
		long rebuildAt;
		final Map<UUID, Ray> rays = new HashMap<>();
		/** Ogre: ticks each player has spent within 10 m. */
		final Map<UUID, Integer> near = new HashMap<>();
		/** ChrAi+0x3b0: where the last sound heard came from. */
		Vec3 heard;
		long investigateAt = -1;
		boolean investigating;
	}

	private static final Map<Mob, State> STATES = new WeakHashMap<>();

	private static float random(Mob mob, float min, float max) {
		return min + (max - min) * mob.getRandom().nextFloat();
	}

	private static long ticks(float seconds) {
		return Math.max(1, Math.round(seconds * 20));
	}

	/** Mob.serverAiStep: the mob's sight, memory and hearing. */
	public static void tickMob(Mob mob) {
		if (!ENABLED || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		Kind kind = kind(mob);
		if (kind == null) {
			return;
		}
		State s = STATES.get(mob);
		if (s == null) {
			if (level.players().stream().noneMatch(SoulsPerception::isTarget)) {
				return;
			}
			s = new State();
			STATES.put(mob, s);
		}
		long now = level.getGameTime();
		Perception.Logic logic = logic(kind);
		// A target set outside the targeting checks (being hurt, vanilla alerts) is perceived.
		if (mob.getTarget() instanceof ServerPlayer target && isTarget(target)
				&& !s.acquired.containsKey(target.getUUID())) {
			acquire(mob, s, target, now);
		}
		remember(mob, s, level, logic, now);
		look(mob, s, level, kind, logic, now);
		listen(mob, s, level, kind, logic, now);
	}

	/** FUN_140439c40, every second: seen keeps the memory full, else it runs out after +0x50 seconds. */
	private static void remember(Mob mob, State s, ServerLevel level, Perception.Logic logic, long now) {
		if (s.acquired.isEmpty() || now < s.memoryCheck) {
			return;
		}
		s.memoryCheck = now + ticks(Perception.MEMORY_PERIOD);
		for (Iterator<Map.Entry<UUID, Long>> it = s.acquired.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Long> e = it.next();
			if (!(level.getPlayerByUUID(e.getKey()) instanceof ServerPlayer player) || !isTarget(player)) {
				it.remove();
				continue;
			}
			double d = mob.position().distanceTo(player.position());
			// Followed without the cone while the ray is clear within 55 m.
			if (d <= logic.alwaysSeen() || ray(mob, s, player, now)) {
				e.setValue(now);
			} else if (now - e.getValue() >= ticks(logic.memory())) {
				it.remove();
				drop(mob, player);
			}
		}
	}

	/** Forgotten: the mob lets go of the player (goal target and brain memories). */
	private static void drop(Mob mob, ServerPlayer player) {
		if (mob.getTarget() == player) {
			mob.setTarget(null);
		}
		Brain<?> brain = mob.getBrain();
		if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
				&& brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == player) {
			brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
		}
		if (brain.hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER)
				&& brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER).orElse(null) == player) {
			brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER);
		}
	}

	private static void acquire(Mob mob, State s, ServerPlayer player, long now) {
		s.acquired.put(player.getUUID(), now);
		s.near.remove(player.getUUID());
		s.investigateAt = -1;
	}

	/** Being hurt by a Souls Mode player makes the mob perceive it (vanilla's hurt-by targeting stays). */
	public static void onHurt(LivingEntity entity, DamageSource source) {
		if (entity instanceof Mob mob && source.getEntity() instanceof ServerPlayer player && gates(mob, player)) {
			State s = STATES.computeIfAbsent(mob, k -> new State());
			acquire(mob, s, player, mob.level().getGameTime());
		}
	}

	/** The candidates' sight checks, and the Ogre's time near. */
	private static void look(Mob mob, State s, ServerLevel level, Kind kind, Perception.Logic logic, long now) {
		if (now >= s.rebuildAt) {
			s.rebuildAt = now + ticks(random(mob, Perception.REBUILD_MIN, Perception.REBUILD_MAX));
			rebuild(mob, s, level);
		}
		for (Candidate c : s.candidates) {
			ServerPlayer player = c.player;
			if (!isTarget(player) || player.level() != level || s.acquired.containsKey(player.getUUID())
					|| now < c.nextCheck) {
				continue;
			}
			double d = mob.position().distanceTo(player.position());
			c.seen = sees(mob, s, level, logic, player, d, now);
			c.nextCheck = now + ticks(Perception.checkInterval(d, c.seen, mob.getRandom().nextFloat()));
			// ai300030 is alarmed by sight only within 4 m.
			if (c.seen && (kind == Kind.HOLLOW || d <= Perception.OGRE_SEE)) {
				acquire(mob, s, player, now);
			}
		}
		if (kind == Kind.OGRE) {
			for (Player p : level.players()) {
				if (p instanceof ServerPlayer player && isTarget(player) && !s.acquired.containsKey(player.getUUID())
						&& mob.position().distanceTo(player.position()) <= Perception.OGRE_NEAR) {
					int t = s.near.merge(player.getUUID(), 1, Integer::sum);
					if (t >= ticks(Perception.OGRE_NEAR_TIME)) {
						acquire(mob, s, player, now);
					}
				}
			}
		}
	}

	/**
	 * The candidate list: the 5 nearest Souls Mode players within 55 m (the search radius is not established; nothing
	 * farther can be seen). Kept candidates keep their timers.
	 */
	private static void rebuild(Mob mob, State s, ServerLevel level) {
		Map<UUID, Candidate> old = new HashMap<>();
		for (Candidate c : s.candidates) {
			old.put(c.player.getUUID(), c);
		}
		s.candidates.clear();
		List<ServerPlayer> players = new ArrayList<>();
		for (Player p : level.players()) {
			if (p instanceof ServerPlayer player && isTarget(player)
					&& mob.position().distanceTo(player.position()) <= Perception.MAX_RAY) {
				players.add(player);
			}
		}
		players.sort(Comparator.comparingDouble(p -> mob.position().distanceToSqr(p.position())));
		for (int i = 0; i < Math.min(Perception.CANDIDATES, players.size()); i++) {
			ServerPlayer player = players.get(i);
			Candidate c = old.get(player.getUUID());
			s.candidates.add(c != null && c.player == player ? c : new Candidate(player));
		}
		s.rays.keySet().removeIf(id -> s.candidates.stream().noneMatch(c -> c.player.getUUID().equals(id))
				&& !s.acquired.containsKey(id));
	}

	/** FUN_14043bef0 with the ray: always within +0x0c; else the range for the light, the cone and a clear ray. */
	private static boolean sees(Mob mob, State s, ServerLevel level, Perception.Logic logic, ServerPlayer player,
			double d, long now) {
		if (!player.canBeSeenAsEnemy()) {
			return false;
		}
		if (d <= logic.alwaysSeen()) {
			return true;
		}
		float range = Perception.sightRange(logic, lit(mob, s, level, now), SoulsTorch.isHeld(player));
		if (player.isInvisible()) {
			// The user's rule (2026-11-03): the invisibility potion keeps working. Vanilla's factor
			// (LivingEntity.getVisibilityPercent): 0.7 x armour cover, at least 0.1 cover. Sneaking and mob heads do not
			// apply (DS2's sight decides).
			range *= 0.7f * Math.max(0.1f, player.getArmorCoverPercentage());
		}
		if (d > range) {
			return false;
		}
		Vec3 to = player.position().subtract(mob.position());
		if (!Perception.inCone(logic, to.x, to.y, to.z, mob.yBodyRot)) {
			return false;
		}
		return ray(mob, s, player, now);
	}

	/**
	 * chr+0x380: the user's light level >= 8 at the mob, or within the torch's 27 m of a player holding it with a
	 * clear ray.
	 */
	private static boolean lit(Mob mob, State s, ServerLevel level, long now) {
		if (level.getMaxLocalRawBrightness(mob.blockPosition()) >= LIT_LEVEL) {
			return true;
		}
		for (Player p : level.players()) {
			if (p instanceof ServerPlayer player && isTarget(player) && SoulsTorch.isHeld(player)
					&& mob.position().distanceTo(player.position()) <= Perception.LIGHT_RANGE && ray(mob, s, player, now)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * FUN_140428f50 / FUN_140428fa0: a ray between the eyes through the blocks (entities do not block), cast again
	 * every second; longer than 55 m it counts as blocked.
	 */
	private static boolean ray(Mob mob, State s, ServerPlayer player, long now) {
		Ray cached = s.rays.get(player.getUUID());
		if (cached != null && now - cached.tick() < ticks(Perception.RAY_PERIOD)) {
			return cached.clear();
		}
		Vec3 from = mob.getEyePosition();
		Vec3 to = player.getEyePosition();
		boolean clear = from.distanceTo(to) <= Perception.MAX_RAY && mob.level().clip(new ClipContext(from, to,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
		s.rays.put(player.getUUID(), new Ray(now, clear));
		return clear;
	}

	/** f7320: the nearest sound heard; the Hollow's and the Ogre's reactions. */
	private static void listen(Mob mob, State s, ServerLevel level, Kind kind, Perception.Logic logic, long now) {
		Noise best = null;
		double bestD2 = Double.MAX_VALUE;
		for (Noise noise : SOUNDS) {
			if (noise.level() != level || !alive(noise, now)) {
				continue;
			}
			double d2 = mob.position().distanceToSqr(noise.pos());
			if (d2 < bestD2 && Perception.hears(logic, noise.sound().level(), noise.sound().factor(), d2)) {
				best = noise;
				bestD2 = d2;
			}
		}
		if (best == null) {
			return;
		}
		ServerPlayer emitter = best.emitter();
		double d = mob.position().distanceTo(emitter.position());
		if (kind == Kind.OGRE) {
			// ai300030: heard with the player within 10 m.
			if (d <= Perception.OGRE_HEAR && isTarget(emitter)) {
				acquire(mob, s, emitter, now);
			}
			return;
		}
		// ai102010: someone heard within 3 m alarms it; otherwise it investigates after 1.15-2.25 s.
		if (d <= Perception.HOLLOW_NEAR && isTarget(emitter)) {
			acquire(mob, s, emitter, now);
			return;
		}
		s.heard = best.pos();
		if (idle(mob, s) && !s.investigating && s.investigateAt < 0) {
			s.investigateAt = now + ticks(random(mob, Perception.INVESTIGATE_DELAY_MIN, Perception.INVESTIGATE_DELAY_MAX));
		}
	}

	/** Not in alarm: no target and nobody acquired. */
	private static boolean idle(Mob mob, State s) {
		return mob.getTarget() == null && s.acquired.isEmpty();
	}

	/** Mob construction: the investigation goal for the Hollow mobs that run goals (not the brain-driven ones). */
	public static void addGoals(Mob mob) {
		if (mob.level() == null || mob.level().isClientSide() || kind(mob) != Kind.HOLLOW
				|| !(mob instanceof PathfinderMob pathfinder)
				|| mob instanceof AbstractPiglin || mob instanceof Zoglin || mob instanceof Breeze) {
			return;
		}
		((MobAccessor) mob).soulsmode$goalSelector().addGoal(1, new Investigate(pathfinder));
	}

	/**
	 * ai102010's investigation: turn to the sound and walk to it until within 3 m or for 5-10 s, wait 2.2-3.3 s, walk
	 * back for up to 10 s. The turn is the navigation's; the walk is the mob's vanilla walking speed (DS2's walk speed
	 * is not ported). Above the vanilla wander and attack goals; it stops when the mob is alarmed.
	 */
	private static final class Investigate extends Goal {
		private enum Step { WALK, WAIT, RETURN }

		private final PathfinderMob mob;
		private Step step;
		private long stepStart;
		private long limit;
		private Vec3 origin;
		private Vec3 spot;

		Investigate(PathfinderMob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		private State state() {
			return STATES.get(mob);
		}

		private long now() {
			return mob.level().getGameTime();
		}

		@Override
		public boolean canUse() {
			State s = state();
			return ENABLED && s != null && s.investigateAt >= 0 && now() >= s.investigateAt && s.heard != null
					&& idle(mob, s);
		}

		@Override
		public boolean canContinueToUse() {
			State s = state();
			return step != null && s != null && idle(mob, s);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			State s = state();
			s.investigateAt = -1;
			s.investigating = true;
			origin = mob.position();
			spot = s.heard;
			enter(Step.WALK, ticks(random(mob, Perception.INVESTIGATE_WALK_MIN, Perception.INVESTIGATE_WALK_MAX)));
			mob.getLookControl().setLookAt(spot);
			mob.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
		}

		private void enter(Step next, long ticks) {
			step = next;
			stepStart = now();
			limit = ticks;
		}

		@Override
		public void tick() {
			// The RETURN step ends by clearing step; with requiresUpdateEveryTick the goal is ticked again before the
			// selector asks canContinueToUse (crash 2026-11-08 on QA Server SAM): nothing left to do.
			if (step == null) {
				return;
			}
			long t = now() - stepStart;
			switch (step) {
				case WALK -> {
					if (mob.position().distanceTo(spot) <= Perception.INVESTIGATE_REACH || t >= limit) {
						mob.getNavigation().stop();
						enter(Step.WAIT, ticks(random(mob, Perception.INVESTIGATE_WAIT_MIN, Perception.INVESTIGATE_WAIT_MAX)));
					} else if (mob.getNavigation().isDone() && t % 20 == 0) {
						mob.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
					}
				}
				case WAIT -> {
					mob.getNavigation().stop();
					if (t >= limit) {
						enter(Step.RETURN, ticks(Perception.INVESTIGATE_RETURN));
						mob.getNavigation().moveTo(origin.x, origin.y, origin.z, 1.0);
					}
				}
				case RETURN -> {
					if (t >= limit || t > 0 && mob.getNavigation().isDone()) {
						step = null;
					}
				}
			}
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
			step = null;
			State s = state();
			if (s != null) {
				s.investigating = false;
			}
		}
	}
}
