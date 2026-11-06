package dev.soulsmode.ds2;

/**
 * DS2 roll and backstep timings for the player (see docs/research/animations.md and equipload.md).
 * Choice (FUN_14038e900 / FUN_14038e9a0): dodge button with a movement direction rolls, without one it steps back.
 * The equip load picks the variant (FUN_14038e770): normal up to 70 %, Tired up to 120 %, Failed above. The Tired
 * animations are the Short ones (a00_12_0030 / a00_11_0051); their DodgeSpeed / StepSpeed playback change (0.9-0.95
 * near full load) is not ported.
 */
public enum Dodge {
	/**
	 * a00_12_0020 DodgeRollingShort / TAE 120020: 110500 window 0.1..0.667, length 46 frames at 30 fps
	 * (.nsa +0x28), re-roll lock from the CancelControl_Avoid window 0..0.5217 in c0001.nmb,
	 * CancelControl_Attack1L 0..0.3261 and Attack1H 0..0.587.
	 */
	ROLL(0.100f, 0.667f, 1.5333f, 0.5217391f, 0.3260870f, 0.5869565f),
	/**
	 * a00_11_0011 StepBackRightShort / TAE 110011: 110500 window 0.167..0.333, 35 frames,
	 * CancelControl_Avoid 0..0.2286 (8 frames), CancelControl_AttackAll 0..0.2857 (10 frames).
	 */
	BACKSTEP(0.167f, 0.333f, 1.1667f, 0.2285714f, 0.2857143f, 0.2857143f),
	/** a00_12_0030 DodgeRollingShortTired: 110500 0.1..0.667, 1.8 s, Avoid 0..1.0, Attack1L 0..0.867, 1H 0..1.133. */
	ROLL_TIRED(0.100f, 0.667f, 1.8f, 1.0f, 0.867f, 1.133f, true),
	/** a00_12_0040 DodgeRollingFailed: no 110500, 1.5 s, Avoid 0..0.975, attacks 0..1.167. */
	ROLL_FAILED(0, 0, 1.5f, 0.975f, 1.167f, 1.167f, true),
	/** a00_11_0051 StepBackRightShortTired: 110500 0.333..0.5, 1.5 s, Avoid 0..0.567, AttackAll 0..0.667. */
	BACKSTEP_TIRED(0.333f, 0.5f, 1.5f, 0.567f, 0.667f, 0.667f, true),
	/** a00_11_0081 StepBackRightFailed: no 110500, 1.6667 s, Avoid 0..1.083, AttackAll 0..1.3. */
	BACKSTEP_FAILED(0, 0, 1.6667f, 1.083f, 1.3f, 1.3f, true);

	/**
	 * TAE 110500 (chr.b8+0x5e8): not invulnerability. Only FUN_140139380 reads it, and while it is up a hit that lands
	 * always gets the normal reaction, even with poise (agility.md s.2.1). The i-frames are {@link #isInvincible}.
	 */
	public final float invincibleStart;
	public final float invincibleEnd;
	public final float duration;
	/** While CancelControl_Avoid is active FUN_14038e850 refuses a new dodge. */
	public final float rerollLock;
	/** CancelControl_Attack1L / 1H: no light / heavy attack before these times. */
	public final float lightAttackLock;
	public final float heavyAttackLock;

	Dodge(float invincibleStart, float invincibleEnd, float duration, float avoidWindowFraction,
			float lightAttackFraction, float heavyAttackFraction) {
		this.invincibleStart = invincibleStart;
		this.invincibleEnd = invincibleEnd;
		this.duration = duration;
		this.rerollLock = avoidWindowFraction * duration;
		this.lightAttackLock = lightAttackFraction * duration;
		this.heavyAttackLock = heavyAttackFraction * duration;
	}

	/** Times in seconds ({@code seconds} only tells this constructor apart). */
	Dodge(float invincibleStart, float invincibleEnd, float duration, float rerollLock, float lightAttackLock,
			float heavyAttackLock, boolean seconds) {
		this.invincibleStart = invincibleStart;
		this.invincibleEnd = invincibleEnd;
		this.duration = duration;
		this.rerollLock = rerollLock;
		this.lightAttackLock = lightAttackLock;
		this.heavyAttackLock = heavyAttackLock;
	}

	/** Provisional, not from DS2 (RollClient): how long the push lasts for a roll / a backstep. */
	public static final float ROLL_PUSH_SECONDS = 0.6f;
	public static final float STEP_PUSH_SECONDS = 0.3f;

	/**
	 * An NPC's distance (npc_invaders.md s.5): its dodge vector is 5221's (length 1.0 roll, 0.3 backstep) instead of
	 * the load-based base, through the same Short / Long blend (clamped like every Blend2, FUN_140636cc0).
	 */
	public float npcDistance(float cp) {
		return switch (this) {
			case ROLL -> blendAt(cp, 3.2f, 6.0f, 3.2f, 6.0f);
			case ROLL_TIRED -> blendAt(cp, 4.0f, 5.0f, 3.397f, 5.397f);
			case BACKSTEP -> blendAt(cp, 0.5f, 3.0f, 0.5f, 2.304f);
			case BACKSTEP_TIRED -> blendAt(cp, 0.5f, 3.0f, 0.5f, 3.0f);
			case ROLL_FAILED -> 0.503f;
			case BACKSTEP_FAILED -> 0.5f;
		};
	}

	private static float blendAt(float cp, float p0, float p1, float shortDistance, float longDistance) {
		float w = Math.max(0, Math.min(1, (cp - p0) / (p1 - p0)));
		return shortDistance + (longDistance - shortDistance) * w;
	}

	/**
	 * When the dodge gives movement back (seconds on the animation clock): SM_Dodge 0x17f2 / SM_Step 0x1ec9 leave to
	 * BT_Move 0x17d7 only by their exit transitions (c0001.nmb; the dodge clips have no CancelControl_Move), so the
	 * stick does not move the character before. With the stick held: roll 0x20a4 / 0x20b3 on StateControll (track 80,
	 * event 90) — Short 0.6667, Tired 0.9333; Failed 0x20d7 on event 80. Still: 0x20cc on StateControll_1 (event 80) —
	 * roll 1.2, Tired 1.3909, Failed 1.1667, backstep 0.8333 / 1.1667 / 1.2963. The backstep's stick-held exit
	 * (0x20bf / 0x20be, condition type 0x25b at 0.4) is not decoded: it uses event 80 too.
	 */
	public float movementBack(boolean stickHeld) {
		return switch (this) {
			case ROLL -> stickHeld ? 0.6667f : 1.2f;
			case ROLL_TIRED -> stickHeld ? 0.9333f : 1.3909f;
			case ROLL_FAILED -> 1.1667f;
			case BACKSTEP -> 0.8333f;
			case BACKSTEP_TIRED -> 1.1667f;
			case BACKSTEP_FAILED -> 1.2963f;
		};
	}

	/** A roll (any load variant), not a backstep. */
	public boolean isRoll() {
		return this == ROLL || this == ROLL_TIRED || this == ROLL_FAILED;
	}

	/** Name of the animation (assets/soulsmode/player_animations) and of its regen block in weapons.json. */
	public String animation() {
		return switch (this) {
			case ROLL -> "roll";
			case BACKSTEP -> "backstep";
			case ROLL_TIRED -> "roll_tired";
			case ROLL_FAILED -> "roll_failed";
			case BACKSTEP_TIRED -> "backstep_tired";
			case BACKSTEP_FAILED -> "backstep_failed";
		};
	}

	/** FUN_14038e770: the variant for an equip load ratio (weight / max load). */
	public static Dodge of(boolean roll, float loadRatio) {
		float percent = loadRatio * 100f;
		if (percent <= 70f) {
			return roll ? ROLL : BACKSTEP;
		}
		if (percent <= 120f) {
			return roll ? ROLL_TIRED : BACKSTEP_TIRED;
		}
		return roll ? ROLL_FAILED : BACKSTEP_FAILED;
	}

	/**
	 * Distance in metres (= blocks) for an equip load ratio (docs/research/equipload.md, "Rodar y paso atrás"):
	 * DodgeDistance / StepDistance = base x (1 - load row x 0.01) (FUN_14031fed0, min 0.1), blending the Short / Long
	 * animations' root motion between the blend node's two points; Failed has one animation (0.5 m). The linear blend is
	 * Morpheme's, not verified in DS2's code.
	 */
	public float distance(float loadRatio, EquipLoadRows rows) {
		return switch (this) {
			case ROLL -> blend(5.0f, rows.rollDistance(), loadRatio, rows, 3.2f, 6.0f, 3.2f, 6.0f);
			case ROLL_TIRED -> blend(4.2f, rows.rollTiredDistance(), loadRatio, rows, 4.0f, 5.0f, 3.397f, 5.397f);
			case BACKSTEP -> blend(2.5f, rows.stepDistance(), loadRatio, rows, 0.5f, 3.0f, 0.5f, 2.304f);
			case BACKSTEP_TIRED -> blend(2.5f, rows.stepTiredDistance(), loadRatio, rows, 0.5f, 3.0f, 0.5f, 3.0f);
			case ROLL_FAILED -> 0.503f;
			case BACKSTEP_FAILED -> 0.5f;
		};
	}

	private static float blend(float base, float[] row, float loadRatio, EquipLoadRows rows, float p0, float p1,
			float shortDistance, float longDistance) {
		float cp = Math.max(0.1f, base * (1 - EquipLoadCurve.value(loadRatio, rows.thresholds(), row) * 0.01f));
		float w = Math.max(0, Math.min(1, (cp - p0) / (p1 - p0)));
		return shortDistance + (longDistance - shortDistance) * w;
	}

	/**
	 * Playback speed (CP DodgeSpeed / StepSpeed, FUN_14035f750 / FUN_14035f980): 1.0, except the Tired variants, which
	 * read ChrEquipLoadParam row 0x1a (roll) / 0x25 (backstep) raw: 0.9 at 100 % load, 0.95 at 120 %. The TAE and cancel
	 * windows follow the animation clock (Morpheme's, not verified in DS2's code), so they stretch by 1 / speed.
	 */
	public float speed(float loadRatio, EquipLoadRows rows) {
		return switch (this) {
			case ROLL_TIRED -> EquipLoadCurve.value(loadRatio, rows.thresholds(), rows.rollTiredSpeed());
			case BACKSTEP_TIRED -> EquipLoadCurve.value(loadRatio, rows.thresholds(), rows.stepTiredSpeed());
			default -> 1f;
		};
	}

	public boolean blocksAttack(boolean heavy, float secondsSinceStart) {
		return secondsSinceStart < (heavy ? heavyAttackLock : lightAttackLock);
	}

	/** The 110500 window (see {@link #invincibleStart}). */
	public boolean inNoPoiseWindow(float secondsSinceStart) {
		return secondsSinceStart >= invincibleStart && secondsSinceStart < invincibleEnd;
	}

	/** The clip's TAE id (c000100_pl.tae, agility.json "tae"). */
	public int taeId() {
		return switch (this) {
			case ROLL -> 120020;
			case ROLL_TIRED -> 120030;
			case ROLL_FAILED -> 120040;
			case BACKSTEP -> 110011;
			case BACKSTEP_TIRED -> 110051;
			case BACKSTEP_FAILED -> 110081;
		};
	}

	/**
	 * Damage only lands with chr.b8+0x608 at 0 (FUN_140139b60): raised by TAE 110100 while it lasts and by the first
	 * {@code fraction} (chr.b8+0xa4, {@link Agility#iframeFraction}) of TAE 110300 (agility.md s.2).
	 */
	public boolean isInvincible(float secondsSinceStart, float fraction) {
		return Agility.invulnerable(taeId(), secondsSinceStart, fraction);
	}
}
