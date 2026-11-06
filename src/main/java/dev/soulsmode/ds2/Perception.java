package dev.soulsmode.ds2;

/**
 * DS2 enemy perception, sight and hearing (docs/research/detection.md): the LogicComParam rows, the candidate check
 * timers of SearchEzStateCtrl, the sight test FUN_14043bef0 / FUN_14043b790, the sound manager (AiManager+0x1260) and
 * the hearing test f7320, and the player's sounds (TAE events 300 / 301 of c000100_pl.tae with ChrCommonParam 100
 * +0x22c). Pure logic: the server side is SoulsPerception.
 */
public final class Perception {
	private Perception() {
	}

	/** The LogicComParam fields the perception reads (detection.md s.1). Angles in degrees, times in seconds. */
	public record Logic(float litRange, float darkRange, float alwaysSeen, float halfHorizontal, float halfVertical,
			float hearingScale, int minLevel, float memory) {
	}

	/** LogicComParam 102010 (Hollow Soldier): 20 / 13 m, 1.0 m, 1.0472 / 0.7854 rad, scale 1.0, level 1, 10 s. */
	public static final Logic HOLLOW = new Logic(20, 13, 1.0f, 60, 45, 1.0f, 1, 10);
	/** LogicComParam 300030 (Ogre): as 102010 but always seen within 1.8 m. */
	public static final Logic OGRE = new Logic(20, 13, 1.8f, 60, 45, 1.0f, 1, 10);

	/** DAT_1410edfb0: an unlit enemy sees a target carrying light this far; also the torch's light radius. */
	public static final float LIGHT_RANGE = 27;
	/** FUN_140428fa0: a line of sight longer than this counts as blocked. */
	public static final float MAX_RAY = 55;
	/** FUN_140428f50: the line of sight is cast again every second. */
	public static final float RAY_PERIOD = 1;
	/** FUN_140439c40: the memory countdown runs every second. */
	public static final float MEMORY_PERIOD = 1;
	/** SearchEzStateCtrl +0xf8: at most this many candidates, the list rebuilt every 2-3 s. */
	public static final int CANDIDATES = 5;
	public static final float REBUILD_MIN = 2;
	public static final float REBUILD_MAX = 3;

	/** Sounds live 0.1 s; the level is capped at 10 (AiManager+0x1260). */
	public static final float SOUND_LIFE = 0.1f;
	public static final int MAX_LEVEL = 10;
	/** AreaParam +0x1c, the factor of event 300 in most maps. */
	public static final float AREA_FACTOR = 20;
	/** ChrCommonParam 100 +0x22c: the level of each event 300 index. */
	private static final int[] LEVELS = {0, 0, 2, 4, 10};

	/** Hollow Soldier ai102010: investigation timings (detection.md s.4). */
	public static final float INVESTIGATE_DELAY_MIN = 1.15f;
	public static final float INVESTIGATE_DELAY_MAX = 2.25f;
	public static final float INVESTIGATE_REACH = 3;
	public static final float INVESTIGATE_WALK_MIN = 5;
	public static final float INVESTIGATE_WALK_MAX = 10;
	public static final float INVESTIGATE_WAIT_MIN = 2.2f;
	public static final float INVESTIGATE_WAIT_MAX = 3.3f;
	public static final float INVESTIGATE_RETURN = 10;
	/** ai102010: someone seen or heard this close alarms the Hollow. */
	public static final float HOLLOW_NEAR = 3;
	/** ai300030 ("reacción auditiva"): heard with the player this close, seen this close, or this long near. */
	public static final float OGRE_HEAR = 10;
	public static final float OGRE_SEE = 4;
	public static final float OGRE_NEAR = 10;
	public static final float OGRE_NEAR_TIME = 25;

	/** The level of an event 300 index (ChrCommonParam 100 +0x22c), plus the ground material (taken as 0). */
	public static int level(int index) {
		return LEVELS[index];
	}

	/** A sound's radius: L x factor / 10 with L capped at 10. */
	public static float radius(int level, float factor) {
		return Math.min(level, MAX_LEVEL) * factor / 10;
	}

	/**
	 * f7320 for one sound: d'^2 = d^2 / scale; inside the radius the perceived level is (r^2 - d'^2) / r^2 x L. The
	 * rounding of FUN_140c356d8 is not established: the comparison is continuous. -1 when outside the radius.
	 */
	public static float perceived(int level, float factor, double distanceSqr, float scale) {
		float r = radius(level, factor);
		double d2 = distanceSqr / scale;
		double r2 = (double) r * r;
		if (!(d2 < r2)) {
			return -1;
		}
		return (float) ((r2 - d2) / r2 * Math.min(level, MAX_LEVEL));
	}

	/** Whether a sound of {@code level} is heard: the level and the perceived level both reach +0x24. */
	public static boolean hears(Logic logic, int level, float factor, double distanceSqr) {
		return level >= logic.minLevel() && perceived(level, factor, distanceSqr, logic.hearingScale()) >= logic.minLevel();
	}

	/**
	 * FUN_14043bef0's range: +0x04 lit, +0x08 unlit (+0x32 = 0); below 27 m, an unlit enemy sees a target that carries
	 * light (b8+0x6d4) at 27 m.
	 */
	public static float sightRange(Logic logic, boolean lit, boolean targetLight) {
		float range = lit ? logic.litRange() : logic.darkRange();
		if (range < LIGHT_RANGE && !lit && targetLight) {
			range = LIGHT_RANGE;
		}
		return range;
	}

	/**
	 * FUN_14043b790: the target (dx, dy, dz from the enemy, Minecraft axes) within the horizontal half angle of the
	 * enemy's facing ({@code yawDegrees}, Minecraft yaw) and within the vertical half angle of the horizontal plane.
	 */
	public static boolean inCone(Logic logic, double dx, double dy, double dz, float yawDegrees) {
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal > 1e-6) {
			double toYaw = Math.toDegrees(Math.atan2(-dx, dz));
			double diff = Math.abs(wrapDegrees(toYaw - yawDegrees));
			if (diff > logic.halfHorizontal()) {
				return false;
			}
		}
		double elevation = Math.toDegrees(Math.atan2(Math.abs(dy), horizontal));
		return elevation <= logic.halfVertical();
	}

	/** FUN_14043bef0: always seen within +0x0c; otherwise within the range, inside the cone and with a clear ray. */
	public static boolean sees(Logic logic, double distance, float range, boolean inCone, boolean clearRay) {
		if (distance <= logic.alwaysSeen()) {
			return true;
		}
		return distance <= range && inCone && clearRay;
	}

	/**
	 * SearchEzStateCtrl (14043ea20): seconds until a candidate is checked again. Under 20 m 0.15 s; 20-50 m
	 * 0.5 + (d - 20) / 30; beyond 1.5 s. With the candidate seen (flags 4 and 8; flag 8 is not identified) a random
	 * time instead: 0.05-0.15, between 0.45 f + 0.05 and f + 0.5, 0.5-1.5. {@code u} is uniform in [0, 1).
	 */
	public static float checkInterval(double distance, boolean seen, float u) {
		if (distance < 20) {
			return seen ? 0.05f + 0.1f * u : 0.15f;
		}
		if (distance < 50) {
			float f = (float) (distance - 20) / 30;
			float hi = f + 0.5f;
			if (!seen) {
				return hi;
			}
			float lo = f * 0.45f + 0.05f;
			return (hi - lo) * u + lo;
		}
		return seen ? 0.5f + u : 1.5f;
	}

	private static double wrapDegrees(double degrees) {
		double d = degrees % 360;
		if (d >= 180) {
			d -= 360;
		}
		if (d < -180) {
			d += 360;
		}
		return d;
	}

	/** A sound the player makes: level and factor (radius = L x factor / 10). */
	public record Sound(int level, float factor) {
		public float radius() {
			return Perception.radius(level, factor);
		}
	}

	/** Event 300 index 2 / 3 (L 2 / 4) with the area factor; event 301 of the hit-back clips: L 8, factor 15. */
	public static final Sound JOG = new Sound(level(2), AREA_FACTOR);
	public static final Sound DASH = new Sound(level(3), AREA_FACTOR);
	public static final Sound HIT_BACK = new Sound(8, 15);

	/**
	 * A locomotion loop with its event-300 times (c000100_pl.tae) and cycle (.nsa +0x28 / the Footsteps track of
	 * c0001.nmb). Walk (30010, index 1) and side walk (6001x, index 0) are silent and have no entry.
	 */
	public enum Gait {
		/** a00_04_0010 Jog: index 2 at 0.367 and 0.733 s, cycle 0.7667 s. */
		JOG(0.7667, Perception.JOG, 0.367, 0.733),
		/** a00_05_0010 Dash: index 3 at 0.267 and 0.467 s, cycle 0.6 s. */
		DASH(0.6, Perception.DASH, 0.267, 0.467),
		/** a00_07_0011 SideWalkJog (forward): index 2 at 0.4 s, cycle 0.7667 s. */
		SIDE_JOG_FORWARD(0.7667, Perception.JOG, 0.4),
		/** a00_07_0012 SideWalkJogBack: index 2 at 0.267 s, cycle 0.7333 s. */
		SIDE_JOG_BACK(0.7333, Perception.JOG, 0.267),
		/** a00_07_0013 SideWalkJogLeft: index 2 at 0.367 s, cycle 0.7333 s. */
		SIDE_JOG_LEFT(0.7333, Perception.JOG, 0.367),
		/** a00_07_0014 SideWalkJogRight: index 2 at 0.333 s, cycle 0.7333 s. */
		SIDE_JOG_RIGHT(0.7333, Perception.JOG, 0.333);

		public final double cycle;
		public final Sound sound;
		private final double[] events;

		Gait(double cycle, Sound sound, double... events) {
			this.cycle = cycle;
			this.sound = sound;
			this.events = events;
		}

		/** Whether one of the loop's events falls in (t0, t1], seconds since the loop started. */
		public boolean fires(double t0, double t1) {
			for (double e : events) {
				if (Math.floor((t1 - e) / cycle) > Math.floor((t0 - e) / cycle)) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * The event-300 time of each dodge clip (animation clock): roll a00_12_0020 / 0021 0.233 s, tired 0030 and failed
	 * 0040 0.333 s (index 3, not suppressible: L 4); backstep a00_11_0011 / 0031 0.333 s, tired 0051 0.667 s, failed
	 * 0081 0.5 s (index 2: L 2).
	 */
	public static float dodgeTime(Dodge dodge) {
		return switch (dodge) {
			case ROLL -> 0.233f;
			case ROLL_TIRED, ROLL_FAILED -> 0.333f;
			case BACKSTEP -> 0.333f;
			case BACKSTEP_TIRED -> 0.667f;
			case BACKSTEP_FAILED -> 0.5f;
		};
	}

	public static Sound dodgeSound(Dodge dodge) {
		return dodge.isRoll() ? DASH : JOG;
	}

	/** a00_13_0020 JumpTakeoff: index 3 at 0.0 s (L 4). */
	public static final float JUMP_TIME = 0;
	public static final Sound JUMP = DASH;
	/** a00_14_0020 RollLanding: index 3 at 0.233 s (L 4). */
	public static final float ROLL_LANDING_TIME = 0.233f;
	public static final Sound ROLL_LANDING = DASH;

	/**
	 * SM_Jump / SM_Fall of c0001.nmb as the client's AirClient plays them (docs/research/movement.md), run on the server
	 * to know when JumpTakeoff and RollLanding start: a jump leaves the ground rising; landing from 0.4333 s of
	 * JumpTakeoff on, or out of FallLoop (falling at least 0.5 m below the apex), is a RollLanding with a horizontal
	 * speed of at least 4.0 m/s after a jump (any drop), or 4.5 m/s with a drop of at least 5.0 m otherwise
	 * (ChrMoveParam 100 +0x160 / +0x154 / +0x158 / +0x1f0). JumpTakeoff ends in FallLoop at 1.6667 s.
	 */
	public static final class Air {
		public enum Event { NONE, TAKEOFF, ROLL_LANDING }

		private enum Phase { NONE, TAKEOFF, FALL }

		private static final double TAKEOFF_LAND_FROM = 0.4333;
		private static final double TAKEOFF_LENGTH = 1.6667;
		private static final double FALL_DROP = 0.5;

		private Phase phase = Phase.NONE;
		private double start;
		private boolean afterJump;
		private double apexY;
		private double lastY = Double.NaN;
		private boolean wasOnGround = true;

		/** Leaves the air (swimming, flying, riding, out of Souls Mode). */
		public void reset(boolean ground, double y) {
			phase = Phase.NONE;
			wasOnGround = ground;
			lastY = y;
			apexY = y;
		}

		/** One step: on the ground or not, height, horizontal speed (m/s), time (s). */
		public Event update(boolean ground, double y, double speed, double now) {
			double vy = Double.isNaN(lastY) ? 0 : y - lastY;
			lastY = y;
			double drop = apexY - y;
			if (ground || vy > 0) {
				apexY = y;
			}
			Event event = Event.NONE;
			switch (phase) {
				case NONE -> {
					if (wasOnGround && !ground && vy > 0) {
						phase = Phase.TAKEOFF;
						start = now;
						afterJump = true;
						event = Event.TAKEOFF;
					} else if (!ground && vy < 0 && drop >= FALL_DROP) {
						phase = Phase.FALL;
						start = now;
						afterJump = false;
					}
				}
				case TAKEOFF -> {
					if (ground && now - start >= TAKEOFF_LAND_FROM) {
						event = land(true, 0, speed);
					} else if (!ground && now - start >= TAKEOFF_LENGTH) {
						phase = Phase.FALL;
						start = now;
					}
				}
				case FALL -> {
					if (ground) {
						event = land(afterJump, drop, speed);
					}
				}
			}
			wasOnGround = ground;
			return event;
		}

		private Event land(boolean afterJump, double drop, double speed) {
			phase = Phase.NONE;
			double height = afterJump ? 0 : 5.0;
			double minSpeed = afterJump ? 4.0 : 4.5;
			return drop >= height && speed >= minSpeed ? Event.ROLL_LANDING : Event.NONE;
		}
	}
}
