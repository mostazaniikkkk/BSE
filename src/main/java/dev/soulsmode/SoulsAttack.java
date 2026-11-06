package dev.soulsmode;

import dev.soulsmode.api.SoulsWeapons;
import dev.soulsmode.ds2.Attack;
import dev.soulsmode.ds2.Moveset;
import dev.soulsmode.network.AttackPayloads;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of the committed DS2 attack, with the held weapon's moveset ({@link SoulsWeapons}): the click starts an animation of fixed length, damage is only dealt
 * inside the TAE 2200 window, and the next attack, dodge and movement wait for the Morpheme cancel windows
 * ({@link Attack}). Replaces the vanilla click-to-hit in Souls Mode.
 */
public final class SoulsAttack {
	/** Game-feel switch. */
	public static final boolean ENABLED = true;
	/**
	 * Not from DS2 (the hitbox lives in the attack slot data, not decoded): entities within the player's vanilla
	 * entity interaction range and this half-angle of the facing are hit.
	 */
	private static final float HALF_ARC_DEGREES = 60f;

	private static final class Active {
		final ResourceLocation movesetId;
		final Moveset moveset;
		/** The hand whose buttons started it (combo and cancel windows are per hand). */
		final boolean leftHand;
		/** The hand holding the weapon used (differs from leftHand for the other buttons of a two-handed weapon). */
		final boolean weaponLeft;
		final Moveset.Type type;
		final Attack attack;
		final long startTick;
		final float yaw;
		/** (hit window << 32 | entity id) already hit. */
		final Set<Long> hit = new HashSet<>();
		/** Animation time up to which the stamina charges (hit event starts) were taken. */
		float chargedUntil = -1;
		/** FUN_140376510: (cost - stamina) / cost of the last charge, 0 when there was enough stamina. */
		float staminaLack;
		/** A spell being cast (CAST_PREPARE / CAST): what its bullets carry; null otherwise. */
		SoulsMagic.Cast cast;
		/** CAST: bullet events already fired. */
		int bulletsFired;
		/** Jump attack: the Y it left the ground at (ChrJumpAttackCtrl state 0), NaN before the leap. */
		double takeoffY = Double.NaN;
		/** A guard attack (0x12 / 0x13): the guard kept up meanwhile (b8+0x14); null once its button is released. */
		Active guardHeld;
		/** HitBack: animation time up to which the weapon's capsule was swept against the blocks. */
		float hitBackSwept = -1;
		/** SoulsSounds: animation time up to which the TAE sound events were played (-1: none yet). */
		float soundsUntil = -1;

		Active(ResourceLocation movesetId, Moveset moveset, boolean leftHand, boolean weaponLeft, Moveset.Type type,
				Attack attack, long startTick, float yaw) {
			this.movesetId = movesetId;
			this.moveset = moveset;
			this.leftHand = leftHand;
			this.weaponLeft = weaponLeft;
			this.type = type;
			this.attack = attack;
			this.startTick = startTick;
			this.yaw = yaw;
		}

		boolean over(float t) {
			return !attack.guard() && t >= attack.duration();
		}
	}

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();

	private SoulsAttack() {
	}

	/** The dodge's left-hand windows (Attack2L / 2H) equal its right-hand ones, so one roll check covers both hands. */
	public static void onRequest(ServerPlayer player, AttackPayloads.Request request) {
		if (isVictim(player)) {
			return;
		}
		if (ENABLED && SoulsGameMode.isActive(player) && player.isAlive() && !player.onGround()) {
			tryFall(player, request);
			return;
		}
		if (!ENABLED || !SoulsGameMode.isActive(player) || !player.isAlive() || player.isUsingItem()
				|| !player.onGround() || SoulsRoll.blocksAttack(player, request.heavy())) {
			return;
		}
		// The damage reaction's CancelControl_AttackAll / _Guard (SoulsPoise).
		if (SoulsPoise.blocksAttack(player)) {
			return;
		}
		// FUN_140367b60 / FUN_140367a80: no attack request is accepted with stamina <= 0.
		if (!SoulsStamina.canAct(player)) {
			return;
		}
		if (SoulsBow.handles(player, request)) {
			return;
		}
		float yaw = player.getYRot();
		float length = (float) Math.sqrt(request.dx() * request.dx() + request.dz() * request.dz());
		if (length > 1e-3f) {
			// Attack towards the held direction (same convention as the dodge request).
			yaw = (float) Math.toDegrees(Math.atan2(-request.dx(), request.dz()));
		}
		Active active = ACTIVE.get(player.getUUID());
		if ((active == null || active.over(seconds(player, active)))
				&& (tryBackstab(player, request) || tryGuardBreakStab(player, request) || tryRiposte(player, request))) {
			return;
		}
		if (active == null || active.over(seconds(player, active))) {
			start(player, null, request, yaw);
		} else if (!tryGuardAttack(player, active, request, yaw)) {
			// A press during the lock is dropped: DS2's input buffering (InputControl) is not decoded yet.
			tryFollow(player, active, request, yaw);
		}
	}

	/** ChrMoveParam +0x1f0 / ChrCommonParam +0x27c: the plunge needs a fall of at least 0.5 m (FUN_1401f9ea0). */
	private static final float FALL_MIN_DISTANCE = 0.5f;

	/**
	 * Type 8, the plunging attack (docs/research/weapons.md, "Tipo 8"): either button while airborne, descending and
	 * fallen at least 0.5 m (b8+0x4c8 bit 12, FUN_140372620). The airborne part runs until the player lands, then the
	 * landing part (switched in tick). No extra damage for the height: DS2 has none (PlayerDamageParam rate 1.512).
	 */
	private static void tryFall(ServerPlayer player, AttackPayloads.Request request) {
		if (player.isUsingItem() || player.isInWater() || player.isFallFlying() || player.getAbilities().flying
				|| player.getDeltaMovement().y > 0 || player.fallDistance < FALL_MIN_DISTANCE
				|| !SoulsStamina.canAct(player)) {
			return;
		}
		Active current = ACTIVE.get(player.getUUID());
		if (current != null && !current.over(seconds(player, current))) {
			return;
		}
		Moveset.Grip grip = grip(player);
		boolean twoHanded = grip.twoHanded();
		boolean weaponLeft = twoHanded ? grip.leftWeapon() : request.leftHand();
		ResourceLocation movesetId = movesetFor(player, weaponLeft);
		Moveset moveset = movesetId == null ? null : SoulsWeapons.moveset(movesetId).orElse(null);
		Attack air = moveset == null ? null : moveset.attack(weaponLeft, twoHanded, Moveset.Type.FALL);
		if (air == null) {
			return;
		}
		player.setSprinting(false);
		ACTIVE.put(player.getUUID(), new Active(movesetId, moveset, request.leftHand(), weaponLeft, Moveset.Type.FALL, air,
				player.serverLevel().getGameTime(), player.getYRot()));
		broadcast(player, new AttackPayloads.Start(player.getId(), movesetId, weaponLeft, twoHanded,
				Moveset.Type.FALL.ordinal(), player.getYRot()));
	}

	/** AttackStateControl_01 of the aXX_01_0240 jump attacks: the leap starts (ChrJumpAttackCtrl state 0). */
	private static final float LEAP_AT = 0.5333f;
	/** DAT_1410ac694: dropping this far below the takeoff turns the jump attack into the fall attack. */
	private static final double JUMP_TO_FALL_DROP = 0.5;

	/**
	 * ChrJumpAttackCtrl state 3 (FUN_140141230): while the jump attack is airborne, more than 0.5 m below its takeoff
	 * sets b8+0x4c0 bit 8 and FUN_1401fcd50 requests the fall attack (type 8) with the same hand. Returns whether it did.
	 */
	private static boolean jumpToFall(ServerPlayer player, Active active, float t) {
		if (t < LEAP_AT) {
			return false;
		}
		if (Double.isNaN(active.takeoffY)) {
			active.takeoffY = player.getY();
			return false;
		}
		if (player.onGround() || player.getY() >= active.takeoffY - JUMP_TO_FALL_DROP) {
			return false;
		}
		boolean twoHanded = grip(player).twoHanded();
		Attack air = active.moveset.attack(active.weaponLeft, twoHanded, Moveset.Type.FALL);
		if (air == null) {
			return false;
		}
		ACTIVE.put(player.getUUID(), new Active(active.movesetId, active.moveset, active.leftHand, active.weaponLeft,
				Moveset.Type.FALL, air, player.serverLevel().getGameTime(), player.getYRot()));
		broadcast(player, new AttackPayloads.Start(player.getId(), active.movesetId, active.weaponLeft, twoHanded,
				Moveset.Type.FALL.ordinal(), player.getYRot()));
		return true;
	}

	/** The plunge's airborne part ends on landing (SM 0x6c7 condition 0x260): the landing part starts. */
	private static void tickFall(ServerPlayer player, Active active) {
		boolean twoHanded = grip(player).twoHanded();
		Attack landing = active.moveset.attack(active.weaponLeft, twoHanded, Moveset.Type.FALL_LANDING);
		ACTIVE.put(player.getUUID(), new Active(active.movesetId, active.moveset, active.leftHand, active.weaponLeft,
				Moveset.Type.FALL_LANDING, landing, player.serverLevel().getGameTime(), player.getYRot()));
		broadcast(player, new AttackPayloads.Start(player.getId(), active.movesetId, active.weaponLeft, twoHanded,
				Moveset.Type.FALL_LANDING.ordinal(), player.getYRot()));
	}

	/** Mobs held by a backstab: victim id -> (attacker, position, end tick). The victim cannot move or hurt anyone. */
	/**
	 * A backstabbed mob: frozen in place until {@code freezeUntil} and harmless until {@code harmlessUntil} (game ticks).
	 * The times are those of DS2's player victim (the a79_02 catch, then the get-up a00_16_0730): Minecraft mobs have no
	 * DS2 victim animation, so they use the player's, the user's choice (2026-10-31).
	 */
	private record Held(UUID attacker, net.minecraft.world.phys.Vec3 pos, float yaw, long start, long freezeUntil,
			long harmlessUntil, java.util.List<float[]> invulnerable, long riposteFrom, long riposteUntil) {
		Held(UUID attacker, net.minecraft.world.phys.Vec3 pos, float yaw, long start, long freezeUntil, long harmlessUntil,
				java.util.List<float[]> invulnerable) {
			this(attacker, pos, yaw, start, freezeUntil, harmlessUntil, invulnerable, 0, 0);
		}

		boolean riposteOpen(long now) {
			return now >= riposteFrom && now < riposteUntil;
		}
	}

	private static final Map<Integer, Held> HELD = new HashMap<>();
	/** b8+0x618: no new critical on a victim for 3 s after it gets up (entity id -> game tick). */
	private static final Map<Integer, Long> NO_CRITICAL = new HashMap<>();
	private static final Map<ResourceLocation, dev.soulsmode.ds2.Weapons.BackstabVictim> VICTIMS = new HashMap<>();

	private static final Map<ResourceLocation, Attack> RIPOSTES = new HashMap<>();
	private static final Map<ResourceLocation, Attack> GUARD_BREAK_STABS = new HashMap<>();
	private static final Map<ResourceLocation, dev.soulsmode.ds2.Weapons.BackstabVictim> GUARD_BREAK_VICTIMS = new HashMap<>();
	private static final Map<ResourceLocation, dev.soulsmode.ds2.Weapons.BackstabVictim> RIPOSTE_VICTIMS = new HashMap<>();
	private static final dev.soulsmode.ds2.Weapons.Parried PARRIED = dev.soulsmode.ds2.Weapons.parried();

	static {
		dev.soulsmode.ds2.Weapons.backstabVictims().forEach((name, v) -> VICTIMS.put(SoulsWeapons.id(name), v));
		dev.soulsmode.ds2.Weapons.criticals("riposte").forEach((name, a) -> RIPOSTES.put(SoulsWeapons.id(name), a));
		dev.soulsmode.ds2.Weapons.criticalVictims("riposte").forEach((name, v) -> RIPOSTE_VICTIMS.put(SoulsWeapons.id(name), v));
		dev.soulsmode.ds2.Weapons.criticals("guard_break").forEach((name, a) -> GUARD_BREAK_STABS.put(SoulsWeapons.id(name), a));
		dev.soulsmode.ds2.Weapons.criticalVictims("guard_break")
				.forEach((name, v) -> GUARD_BREAK_VICTIMS.put(SoulsWeapons.id(name), v));
	}

	/** A weapon's riposte (slot 61), for the client's Start payload; null if it has none. */
	public static Attack riposte(ResourceLocation moveset) {
		var pack = dev.soulsmode.content.SoulsContent.weapon(moveset);
		return pack != null ? pack.riposte() : RIPOSTES.get(moveset);
	}

	/** A critical's victim times for a weapon: its datapack definition, else the mod's own table. */
	private static dev.soulsmode.ds2.Weapons.BackstabVictim victim(ResourceLocation moveset,
			Map<ResourceLocation, dev.soulsmode.ds2.Weapons.BackstabVictim> builtIn,
			java.util.function.Function<dev.soulsmode.ds2.Weapons.Def, dev.soulsmode.ds2.Weapons.BackstabVictim> of) {
		var pack = dev.soulsmode.content.SoulsContent.weapon(moveset);
		return pack != null ? of.apply(pack) : builtIn.get(moveset);
	}

	/** A weapon's guard-break stab (slot 62); null if it has none. */
	public static Attack guardBreakStab(ResourceLocation moveset) {
		var pack = dev.soulsmode.content.SoulsContent.weapon(moveset);
		return pack != null ? pack.guardBreakStab() : GUARD_BREAK_STABS.get(moveset);
	}

	/**
	 * FUN_140391370 / FUN_140390a50: a right-hand light attack while not attacking becomes a backstab if a living
	 * entity is within the victim's backstab limits: horizontal distance, height difference and slope, the attacker
	 * facing it (within pi/4) and the victim facing away (|angle - pi| within its facing limit + WeaponTypeParam
	 * +0x4c). Victim states DS2 also checks (stunned, already grabbed...) have no Minecraft equivalent and are skipped.
	 */
	/**
	 * The user's looser backstab against mobs (2026-11-01; DS2: victim facing pi/6, attacker pi/4, 2.0 m, height 0.5 m):
	 * victim facing away within 45 deg, attacker aiming within 60 deg, 2.5 blocks, 1 block of height difference.
	 */
	private static final float MOB_BACKSTAB_FACING = (float) Math.toRadians(45);
	private static final double MOB_BACKSTAB_AIM = Math.toRadians(60);
	private static final float MOB_BACKSTAB_DISTANCE = 2.5f;
	private static final float MOB_BACKSTAB_HEIGHT = 1.0f;

	private static boolean tryBackstab(ServerPlayer player, AttackPayloads.Request request) {
		if (request.heavy() || request.leftHand() || grip(player) != Moveset.Grip.ONE_HANDED) {
			return false;
		}
		ResourceLocation movesetId = movesetFor(player, false);
		Moveset moveset = movesetId == null ? null : SoulsWeapons.moveset(movesetId).orElse(null);
		if (moveset == null || moveset.backstab() == null) {
			return false;
		}
		double yaw = Math.toRadians(player.getYRot());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		for (Entity e : player.serverLevel().getEntities(player, player.getBoundingBox().inflate(3.0, 1.0, 3.0))) {
			if (!(e instanceof LivingEntity victim) || !victim.isAlive() || !e.isAttackable() || HELD.containsKey(e.getId())
					|| NO_CRITICAL.getOrDefault(e.getId(), 0L) > player.serverLevel().getGameTime()
					|| e instanceof Player other && !player.canHarmPlayer(other)) {
				continue;
			}
			dev.soulsmode.ds2.Weapons.CriticalRange range = dev.soulsmode.ds2.Weapons.backstabRange(e instanceof Player);
			if (range.victimClass() < 1 || range.victimClass() > 3) {
				continue;
			}
			boolean mob = !(e instanceof Player);
			if (mob) {
				// The user's exception (2026-11-01): against mobs DS2's limits are too strict in Minecraft; players keep
				// DS2's for PvP.
				range = new dev.soulsmode.ds2.Weapons.CriticalRange(range.victimClass(), MOB_BACKSTAB_FACING, range.slope(),
						MOB_BACKSTAB_HEIGHT, MOB_BACKSTAB_DISTANCE);
			}
			double dx = e.getX() - player.getX();
			double dz = e.getZ() - player.getZ();
			double dy = Math.abs(e.getY() - player.getY());
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist > range.distance() || dy > range.height() || dist < 1e-3
					|| dy / Math.sqrt(dist * dist + dy * dy) > Math.abs(Math.sin(range.slope()))) {
				continue;
			}
			// Attacker facing the victim.
			double angle1 = Math.acos(Math.max(-1, Math.min(1, (dx * fx + dz * fz) / dist)));
			// Victim facing away from the attacker: its facing vs the direction to the attacker is about pi.
			double vyaw = Math.toRadians(victim.getYRot());
			double angle2 = Math.acos(Math.max(-1, Math.min(1, (-dx * -Math.sin(vyaw) + -dz * Math.cos(vyaw)) / dist)));
			if (angle1 > (mob ? MOB_BACKSTAB_AIM : Math.PI / 4)
					|| Math.abs(angle2 - Math.PI) > range.facing() + moveset.backstabAngleBonus()) {
				continue;
			}
			float faceYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			startBackstab(player, movesetId, moveset, victim, faceYaw);
			return true;
		}
		return false;
	}

	/**
	 * FUN_1403908a0: a right-hand light attack, one-handed, becomes a riposte on a parried victim inside its window
	 * (StabControl 261) within the victim's riposte limits (CommonParam +0x110..+0x11c; mobs use the most common
	 * EnemyCommonParam row): distance, height, slope, the attacker facing it within pi/4 and the victim facing the
	 * attacker within its facing limit + WeaponTypeParam +0x44.
	 */
	private static boolean tryRiposte(ServerPlayer player, AttackPayloads.Request request) {
		if (request.heavy() || request.leftHand() || grip(player) != Moveset.Grip.ONE_HANDED) {
			return false;
		}
		ResourceLocation movesetId = movesetFor(player, false);
		Attack riposte = movesetId == null ? null : riposte(movesetId);
		if (riposte == null) {
			return false;
		}
		long now = player.serverLevel().getGameTime();
		double yaw = Math.toRadians(player.getYRot());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		for (Map.Entry<Integer, Held> entry : HELD.entrySet()) {
			Held held = entry.getValue();
			if (!held.riposteOpen(now) || !(player.serverLevel().getEntity(entry.getKey()) instanceof LivingEntity victim)
					|| !victim.isAlive()) {
				continue;
			}
			dev.soulsmode.ds2.Weapons.CriticalRange range = dev.soulsmode.ds2.Weapons.criticalRange("riposte",
					victim instanceof Player);
			double dx = victim.getX() - player.getX();
			double dz = victim.getZ() - player.getZ();
			double dy = Math.abs(victim.getY() - player.getY());
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist > range.distance() || dy > range.height() || dist < 1e-3
					|| dy / Math.sqrt(dist * dist + dy * dy) > Math.abs(Math.sin(range.slope()))) {
				continue;
			}
			double angle1 = Math.acos(Math.max(-1, Math.min(1, (dx * fx + dz * fz) / dist)));
			// The victim faces the attacker (no -pi, unlike the backstab).
			double vyaw = Math.toRadians(held.yaw());
			double angle2 = Math.acos(Math.max(-1, Math.min(1, (-dx * -Math.sin(vyaw) + -dz * Math.cos(vyaw)) / dist)));
			if (angle1 > Math.PI / 4 || angle2 > range.facing()) {
				continue;
			}
			Moveset moveset = SoulsWeapons.moveset(movesetId).orElseThrow();
			float faceYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			startCritical(player, movesetId, moveset, victim, faceYaw, riposte, Moveset.Type.RIPOSTE,
					victim(movesetId, RIPOSTE_VICTIMS, dev.soulsmode.ds2.Weapons.Def::riposteVictim));
			return true;
		}
		return false;
	}

	/**
	 * FUN_140390700: the guard-break stab (slot 62) on a guard-broken player inside StabControl 262, within the
	 * victim's guard-break limits (CommonParam +0x120..+0x12c; player victim row) and facing the attacker within its
	 * facing limit. Same input as the backstab; tried after it (FUN_140391370).
	 */
	private static boolean tryGuardBreakStab(ServerPlayer player, AttackPayloads.Request request) {
		if (request.heavy() || request.leftHand() || grip(player) != Moveset.Grip.ONE_HANDED || GUARD_BROKEN.isEmpty()) {
			return false;
		}
		ResourceLocation movesetId = movesetFor(player, false);
		Attack stab = movesetId == null ? null : guardBreakStab(movesetId);
		if (stab == null) {
			return false;
		}
		long now = player.serverLevel().getGameTime();
		double yaw = Math.toRadians(player.getYRot());
		for (Map.Entry<Integer, Long> entry : GUARD_BROKEN.entrySet()) {
			if (entry.getValue() <= now || !(player.serverLevel().getEntity(entry.getKey()) instanceof ServerPlayer victim)
					|| victim == player || !victim.isAlive() || !player.canHarmPlayer(victim) || HELD.containsKey(victim.getId())) {
				continue;
			}
			dev.soulsmode.ds2.Weapons.CriticalRange range = dev.soulsmode.ds2.Weapons.criticalRange("guard_break", true);
			double dx = victim.getX() - player.getX();
			double dz = victim.getZ() - player.getZ();
			double dy = Math.abs(victim.getY() - player.getY());
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist > range.distance() || dy > range.height() || dist < 1e-3
					|| dy / Math.sqrt(dist * dist + dy * dy) > Math.abs(Math.sin(range.slope()))) {
				continue;
			}
			double angle1 = Math.acos(Math.max(-1, Math.min(1, (dx * -Math.sin(yaw) + dz * Math.cos(yaw)) / dist)));
			double vyaw = Math.toRadians(victim.getYRot());
			double angle2 = Math.acos(Math.max(-1, Math.min(1, (-dx * -Math.sin(vyaw) + -dz * Math.cos(vyaw)) / dist)));
			if (angle1 > Math.PI / 4 || angle2 > range.facing()) {
				continue;
			}
			GUARD_BROKEN.remove(victim.getId());
			ACTIVE.remove(victim.getUUID());
			startCritical(player, movesetId, SoulsWeapons.moveset(movesetId).orElseThrow(), victim,
					(float) Math.toDegrees(Math.atan2(-dx, dz)), stab, Moveset.Type.GUARD_BREAK_STAB,
					victim(movesetId, GUARD_BREAK_VICTIMS, dev.soulsmode.ds2.Weapons.Def::guardBreakVictim));
			return true;
		}
		return false;
	}

	/** A player held as a critical's or a parry's victim cannot attack, dodge or use items until it can act again. */
	public static boolean isVictim(ServerPlayer player) {
		return HELD.containsKey(player.getId());
	}

	/**
	 * A successful parry against a mob (the user's choice: the c0001 parried sequence, DmgParryBreak Start, 2.0 s
	 * loop, End): it is held in place and harmless until it can move / attack again, and open to a riposte in its
	 * window.
	 */
	public static void onParried(ServerPlayer player, Entity attacker) {
		if (!(attacker instanceof LivingEntity victim) || !victim.isAlive() || HELD.containsKey(victim.getId())) {
			return;
		}
		if (victim instanceof ServerPlayer parried) {
			// A player: DmgParryBreak Start, Loop (2.0 s, the user's choice), End (docs/research/weapons.md "Riposte").
			ACTIVE.remove(parried.getUUID());
			SoulsRoll.forget(parried);
			broadcast(parried, new dev.soulsmode.network.VictimPayload(parried.getId(), "soulsmode:ds2_guard_break",
					"soulsmode:ds2_parried_loop", PARRY_BREAK_START, true, "soulsmode:ds2_parried_end", PARRIED.riposteUntil(),
					PARRIED.moveAt(), PARRIED.attackAt()));
		}
		long now = player.serverLevel().getGameTime();
		HELD.put(victim.getId(), new Held(player.getUUID(), victim.position(), victim.getYRot(), now,
				now + Math.round(PARRIED.moveAt() * 20), now + Math.round(PARRIED.attackAt() * 20),
				PARRIED.invulnerable(), now + Math.round(PARRIED.riposteFrom() * 20),
				now + Math.round(PARRIED.riposteUntil() * 20)));
		// The user's rule (2026-11-01): humanoid mobs (tag soulsmode:parry_animation) play the parried player's DS2
		// clips (DmgParryBreak Start / Loop / End) retargeted to their model; the others are only stunned (held, the
		// stun particles), like a backstab's victim.
		if (!(victim instanceof ServerPlayer) && victim.getType().is(PARRY_ANIMATION)) {
			var payload = new dev.soulsmode.network.ZombieAttackPayload(victim.getId(), "parried");
			for (ServerPlayer viewer : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(victim)) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(viewer, payload);
			}
		}
	}

	/** Mobs that play the DS2 parried animation when parried (the user's list); the rest are stunned. */
	public static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> PARRY_ANIMATION =
			net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
					SoulsMode.id("parry_animation"));

	private static void startBackstab(ServerPlayer player, ResourceLocation movesetId, Moveset moveset,
			LivingEntity victim, float yaw) {
		Attack attack = moveset.backstab();
		SoulsRoll.forget(player);
		player.setSprinting(false);
		player.setYRot(yaw);
		player.setYBodyRot(yaw);
		player.setYHeadRot(yaw);
		startCritical(player, movesetId, moveset, victim, yaw, attack, Moveset.Type.BACKSTAB,
				victim(movesetId, VICTIMS, dev.soulsmode.ds2.Weapons.Def::backstabVictim));
	}

	private static void startCritical(ServerPlayer player, ResourceLocation movesetId, Moveset moveset,
			LivingEntity victim, float yaw, Attack attack, Moveset.Type type, dev.soulsmode.ds2.Weapons.BackstabVictim times) {
		SoulsRoll.forget(player);
		player.setSprinting(false);
		player.setYRot(yaw);
		player.setYBodyRot(yaw);
		player.setYHeadRot(yaw);
		long now = player.serverLevel().getGameTime();
		ACTIVE.put(player.getUUID(), new Active(movesetId, moveset, false, false, type, attack, now, yaw));
		float moveAt = times == null ? attack.duration() : times.moveAt();
		float attackAt = times == null ? attack.duration() : times.attackAt();
		HELD.put(victim.getId(), new Held(player.getUUID(), victim.position(), victim.getYRot(), now,
				now + Math.round(moveAt * 20), now + Math.round(attackAt * 20),
				times == null ? java.util.List.of() : times.invulnerable()));
		if (times != null) {
			NO_CRITICAL.put(victim.getId(), now + Math.round(times.noCriticalUntil() * 20));
			if (victim instanceof ServerPlayer victimPlayer) {
				ACTIVE.remove(victimPlayer.getUUID());
				SoulsRoll.forget(victimPlayer);
				broadcast(victimPlayer, new dev.soulsmode.network.VictimPayload(victimPlayer.getId(),
						"soulsmode:ds2_victim_" + times.catchTae(), "soulsmode:ds2_get_up", times.getUpAt(), false, "", 0,
						times.moveAt(), times.attackAt()));
			}
		}
		broadcast(player, new AttackPayloads.Start(player.getId(), movesetId, false, false, type.ordinal(), yaw));
	}

	/**
	 * TAE 110100 of the victim's side (the catch from 2.0-2.667 s on, then the get-up's 0-1.833 s): every hit on it
	 * is rejected (FUN_140139b60), whoever deals it.
	 */
	public static boolean isVictimInvulnerable(Entity entity) {
		Held held = entity == null ? null : HELD.get(entity.getId());
		if (held == null) {
			return false;
		}
		float t = (entity.level().getGameTime() - held.start()) / 20f;
		for (float[] w : held.invulnerable()) {
			if (t >= w[0] && t < w[1]) {
				return true;
			}
		}
		return false;
	}

	/** Damage from a mob held by a backstab is cancelled: the victim is in the attacker's grab. */
	public static boolean isHeld(Entity entity) {
		return entity != null && HELD.containsKey(entity.getId());
	}

	/** TAE 110100 of the current attack (backstab end): hits are rejected (FUN_140139b60). */
	public static boolean isInvulnerable(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		return active != null && active.attack.isInvulnerable(seconds(player, active));
	}

	/** Particles circling a stunned victim's head (the user's visual cue; the look is a design choice). */
	private static void stunParticles(ServerLevel level, Entity victim, long now) {
		if (now % 2 != 0) {
			return;
		}
		double y = victim.getY() + victim.getBbHeight() + 0.25;
		double radius = Math.max(0.35, victim.getBbWidth() * 0.6);
		for (int i = 0; i < 3; i++) {
			double angle = now * 0.35 + i * Math.PI * 2 / 3;
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, victim.getX() + Math.cos(angle) * radius, y,
					victim.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
		}
	}

	private static void tickHeld(MinecraftServer server) {
		long tick = server.overworld().getGameTime();
		NO_CRITICAL.values().removeIf(until -> until <= tick);
		Iterator<Map.Entry<Integer, Held>> it = HELD.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Held> e = it.next();
			Held held = e.getValue();
			Entity attacker = attacker(server, held.attacker());
			ServerLevel attackerLevel = attacker == null ? null : (ServerLevel) attacker.level();
			Entity victim = attackerLevel == null ? null : attackerLevel.getEntity(e.getKey());
			long now = attackerLevel == null ? 0 : attackerLevel.getGameTime();
			if (victim == null || !victim.isAlive() || now >= held.harmlessUntil()) {
				it.remove();
				continue;
			}
			stunParticles(attackerLevel, victim, now);
			if (now >= held.freezeUntil()) {
				continue;
			}
			victim.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
			victim.teleportTo(held.pos().x, held.pos().y, held.pos().z);
			// The grab also keeps it facing away: it does not turn towards the attacker while held.
			victim.setYRot(held.yaw());
			victim.setYHeadRot(held.yaw());
			if (victim instanceof LivingEntity living) {
				living.setYBodyRot(held.yaw());
			}
			if (victim instanceof net.minecraft.world.entity.Mob mob) {
				mob.getNavigation().stop();
			}
		}
	}

	/** A grab's attacker: a player, or an NPC invader (npcBackstab). */
	private static Entity attacker(MinecraftServer server, UUID id) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player != null) {
			return player;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Entity e = level.getEntity(id);
			if (e != null) {
				return e;
			}
		}
		return null;
	}

	/**
	 * An NPC invader's light press behind a player (npc_invaders.md s.5): the engine turns it into a backstab with the
	 * same test as a player's (FUN_140393a30 / FUN_140390a50; no check on the attacker's type was found): the
	 * victim's ChrCommonParam 100 limits (backstabRange(true)) plus the weapon's WeaponTypeParam +0x4c bonus, the
	 * attacker facing it within pi/4. The victim is caught like by a player (startCritical). False when it does not
	 * apply (the press stays a light attack).
	 */
	public static boolean npcBackstab(dev.soulsmode.entity.SoulsInvader attacker, ServerPlayer victim, Moveset moveset,
			ResourceLocation movesetId) {
		if (moveset == null || moveset.backstab() == null || !victim.isAlive() || HELD.containsKey(victim.getId())
				|| NO_CRITICAL.getOrDefault(victim.getId(), 0L) > victim.serverLevel().getGameTime()) {
			return false;
		}
		dev.soulsmode.ds2.Weapons.CriticalRange range = dev.soulsmode.ds2.Weapons.backstabRange(true);
		if (range.victimClass() < 1 || range.victimClass() > 3) {
			return false;
		}
		double dx = victim.getX() - attacker.getX();
		double dz = victim.getZ() - attacker.getZ();
		double dy = Math.abs(victim.getY() - attacker.getY());
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist > range.distance() || dy > range.height() || dist < 1e-3
				|| dy / Math.sqrt(dist * dist + dy * dy) > Math.abs(Math.sin(range.slope()))) {
			return false;
		}
		double yaw = Math.toRadians(attacker.getYRot());
		double angle1 = Math.acos(Math.max(-1, Math.min(1, (dx * -Math.sin(yaw) + dz * Math.cos(yaw)) / dist)));
		double vyaw = Math.toRadians(victim.getYRot());
		double angle2 = Math.acos(Math.max(-1, Math.min(1, (-dx * -Math.sin(vyaw) + -dz * Math.cos(vyaw)) / dist)));
		if (angle1 > Math.PI / 4 || Math.abs(angle2 - Math.PI) > range.facing() + moveset.backstabAngleBonus()) {
			return false;
		}
		dev.soulsmode.ds2.Weapons.BackstabVictim times = victim(movesetId, VICTIMS,
				dev.soulsmode.ds2.Weapons.Def::backstabVictim);
		long now = victim.serverLevel().getGameTime();
		Attack attack = moveset.backstab();
		float moveAt = times == null ? attack.duration() : times.moveAt();
		float attackAt = times == null ? attack.duration() : times.attackAt();
		HELD.put(victim.getId(), new Held(attacker.getUUID(), victim.position(), victim.getYRot(), now,
				now + Math.round(moveAt * 20), now + Math.round(attackAt * 20),
				times == null ? java.util.List.of() : times.invulnerable()));
		if (times != null) {
			NO_CRITICAL.put(victim.getId(), now + Math.round(times.noCriticalUntil() * 20));
			ACTIVE.remove(victim.getUUID());
			SoulsRoll.forget(victim);
			broadcast(victim, new dev.soulsmode.network.VictimPayload(victim.getId(),
					"soulsmode:ds2_victim_" + times.catchTae(), "soulsmode:ds2_get_up", times.getUpAt(), false, "", 0,
					times.moveAt(), times.attackAt()));
		}
		return true;
	}

	/** Whether {@code victim} is held by {@code attacker}'s grab (its backstab's hits go to it only). */
	public static boolean heldBy(Entity victim, Entity attacker) {
		Held held = victim == null ? null : HELD.get(victim.getId());
		return held != null && held.attacker().equals(attacker.getUUID());
	}

	/**
	 * Starts an item-use animation (lighting the torch) when nothing locks attacks: no attack or dodge inside its
	 * attack lock. Which DS2 lock gates item use is not decoded; the attack locks are used.
	 */
	public static boolean startItemUse(ServerPlayer player, ResourceLocation id, Attack attack) {
		if (!ENABLED || !SoulsGameMode.isActive(player) || !player.isAlive() || player.isUsingItem()
				|| !player.onGround() || SoulsRoll.blocksAttack(player, false) || isVictim(player)) {
			return false;
		}
		Active active = ACTIVE.get(player.getUUID());
		if (active != null && !active.over(seconds(player, active))
				&& (active.attack.guard() || !active.attack.allows(false, true, seconds(player, active)))) {
			return false;
		}
		SoulsRoll.forget(player);
		player.setSprinting(false);
		ACTIVE.put(player.getUUID(), new Active(id, null, false, false, Moveset.Type.ITEM_USE, attack,
				player.serverLevel().getGameTime(), player.getYRot()));
		broadcast(player, new AttackPayloads.Start(player.getId(), id, false, false, Moveset.Type.ITEM_USE.ordinal(),
				player.getYRot()));
		return true;
	}

	/**
	 * A bow action (SoulsBow): Prepare, CF or NoBullet, with the bow's hand, when nothing locks it (an attack or a dodge
	 * inside its lock; the bow's own Prepare once it has ended).
	 */
	public static boolean startBowAction(ServerPlayer player, ResourceLocation id, Attack attack, boolean bowLeft,
			boolean twoHanded) {
		if (!ENABLED || !SoulsGameMode.isActive(player) || !player.isAlive() || isVictim(player)
				|| SoulsRoll.blocksAttack(player, false) || SoulsPoise.blocksAttack(player)) {
			return false;
		}
		Active active = ACTIVE.get(player.getUUID());
		if (active != null && !active.over(seconds(player, active))
				&& (active.attack.guard() || !active.attack.allows(false, bowLeft, seconds(player, active)))) {
			return false;
		}
		SoulsRoll.forget(player);
		player.setSprinting(false);
		ACTIVE.put(player.getUUID(), new Active(id, null, bowLeft, bowLeft, Moveset.Type.ITEM_USE, attack,
				player.serverLevel().getGameTime(), player.getYRot()));
		broadcast(player, new AttackPayloads.Start(player.getId(), id, bowLeft, twoHanded, Moveset.Type.ITEM_USE.ordinal(),
				player.getYRot()));
		return true;
	}

	/** Whether the player's current action is this one (an item use not cut short by a dodge or attack). */
	public static boolean isDoing(ServerPlayer player, Attack attack) {
		Active active = ACTIVE.get(player.getUUID());
		return active != null && active.attack == attack && !active.over(seconds(player, active));
	}

	/** Ends a held guard (the button was released). */
	public static void onRelease(ServerPlayer player) {
		SoulsBow.onRelease(player);
		Active active = ACTIVE.get(player.getUUID());
		if (active != null && active.guardHeld != null) {
			// The guard drops; the guard attack goes on.
			active.guardHeld = null;
			broadcast(player, new AttackPayloads.Stop(-player.getId() - 1));
		}
		if (active != null && active.attack.guard()) {
			ACTIVE.remove(player.getUUID());
			broadcast(player, new AttackPayloads.Stop(player.getId()));
		}
	}

	public static Moveset.Grip grip(ServerPlayer player) {
		return SoulsGrip.grip(player);
	}

	/** Whether an attack, guard or item use is running. */
	public static boolean isActing(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		return active != null && !active.over(seconds(player, active));
	}

	/**
	 * FUN_1401f9f00 / FUN_1401f9d50 (weapon_arts.md s.2, s.8): both weapons one-handed, the other hand guarding, a light
	 * press gives 0x12 (slot 57), the same hand's next light inside its combo window 0x13 (58), then 0x12 again. The
	 * guard stays up during it (FUN_14038fd30 accepts the guard during motion types 4 / 5). False when it does not apply.
	 */
	private static boolean tryGuardAttack(ServerPlayer player, Active active, AttackPayloads.Request request, float yaw) {
		Active guard = active.attack.guard() ? active : active.guardHeld;
		if (request.heavy() || guard == null || guard.leftHand == request.leftHand() || grip(player) != Moveset.Grip.ONE_HANDED) {
			return false;
		}
		ResourceLocation movesetId = movesetFor(player, request.leftHand());
		var data = movesetId == null ? null : dev.soulsmode.ds2.GuardAttacks.data(damageKey(movesetId));
		if (data == null || !SoulsStamina.canAct(player)) {
			return false;
		}
		float t = seconds(player, active);
		Moveset.Type type = Moveset.Type.GUARD_ATTACK;
		if (active.type == Moveset.Type.GUARD_ATTACK && active.leftHand == request.leftHand()) {
			if (!active.attack.allows(false, request.leftHand(), t)) {
				return true;
			}
			if (active.attack.inCombo(t) && data.lightCombo() != null) {
				type = Moveset.Type.GUARD_ATTACK_COMBO;
			}
		} else if (!active.attack.guard() && !active.attack.allows(false, request.leftHand(), t)) {
			return true;
		}
		Attack attack = data.attack(type);
		SoulsRoll.forget(player);
		player.setSprinting(false);
		player.setYRot(yaw);
		player.setYBodyRot(yaw);
		player.setYHeadRot(yaw);
		Active next = new Active(movesetId, SoulsWeapons.moveset(movesetId).orElse(null), request.leftHand(),
				request.leftHand(), type, attack, player.serverLevel().getGameTime(), yaw);
		next.guardHeld = guard;
		ACTIVE.put(player.getUUID(), next);
		broadcast(player, new AttackPayloads.Start(player.getId(), movesetId, request.leftHand(), false, type.ordinal(),
				player.getYRot()));
		return true;
	}

	/**
	 * The guard's values for SM 0x210e (weapon_arts.md s.8.5), sent before its Start: the exit blend by
	 * GuardCancelSpeedCategory (WeaponTypeParam u8 +0x51 of the guarding weapon, one step up 10 -> 20 -> 30 -> 40 with a
	 * Str / Dex lack, FUN_14038ffb0; 0..10 -> 0.3 s, 20 -> 0.5, 30 -> 0.8, 40..100 -> 1.1, transitions 0x2329 / 0x232c /
	 * 0x232d / 0x2330) and GuardSpeed (FUN_14038f870). A guard without a weapons.json slot (a datapack weapon) gets 0
	 * (no exit blend) and 1.0 (FUN_14038f870 without a WAMP row).
	 */
	private static void sendGuard(ServerPlayer player, ResourceLocation movesetId, boolean weaponLeft, boolean twoHanded,
			Moveset.Type type) {
		String key = damageKey(movesetId);
		com.google.gson.JsonObject slot = dev.soulsmode.ds2.Weapons.guardSlot(key, weaponLeft, twoHanded, type);
		var stats = SoulsPlayer.data(player).map(SoulsData::toAttributes).orElse(null);
		float exit = 0;
		float speed = 1;
		if (slot != null && stats != null) {
			float lack = dev.soulsmode.ds2.Ds2Damage.strDexLack(key, stats, twoHanded);
			exit = dev.soulsmode.ds2.Weapons.guardExitBlend(dev.soulsmode.ds2.Weapons.guardCancelCategory(slot, lack > 0));
			speed = dev.soulsmode.ds2.Weapons.guardSpeed(slot, stats.get(dev.soulsmode.ds2.Attributes.ATTUNEMENT),
					stats.get(dev.soulsmode.ds2.Attributes.ADAPTABILITY), lack);
		}
		broadcast(player, new AttackPayloads.GuardLayer(player.getId(), exit, speed));
	}

	/** Starts the requested attack if the current one's cancel window allows it; false while still locked. */
	private static boolean tryFollow(ServerPlayer player, Active active, AttackPayloads.Request request, float yaw) {
		float t = seconds(player, active);
		// CancelControl_Attack1L/1H/2L/2H of the current attack (a held guard has none).
		if (!active.attack.guard() && !active.attack.allows(request.heavy(), request.leftHand(), t)) {
			return false;
		}
		start(player, active, request, yaw);
		return true;
	}

	/**
	 * Resolves the request like FUN_140396680 / FUN_140395b40: with a two-handed weapon, its own hand's buttons use the
	 * two-handed slots and the other hand's buttons use slots 50 / 51 of that weapon; otherwise each hand uses its
	 * own weapon.
	 */
	private static void start(ServerPlayer player, Active current, AttackPayloads.Request request, float yaw) {
		if (!SoulsStamina.canAct(player)) {
			return;
		}
		Moveset.Grip grip = grip(player);
		if (grip.powerStance() && request.leftHand() && startPowerStance(player, current, request, yaw, grip)) {
			return;
		}
		boolean twoHanded = grip.twoHanded();
		boolean weaponLeft = twoHanded ? grip.leftWeapon() : request.leftHand();
		ResourceLocation movesetId = movesetFor(player, weaponLeft);
		if (movesetId == null) {
			return;
		}
		Moveset moveset = SoulsWeapons.moveset(movesetId).orElseThrow();
		Moveset.Type type;
		if (twoHanded && request.leftHand() != weaponLeft) {
			type = Moveset.other(request.heavy());
		} else {
			float t = current == null ? 0 : seconds(player, current);
			Moveset.Type movement = movementType(player, moveset, weaponLeft, twoHanded, request);
			type = movement != null ? movement
					: current == null ? Moveset.next(null, false, false, request.heavy(), request.leftHand())
					: Moveset.next(current.type, current.leftHand, current.attack.inCombo(t), request.heavy(),
							request.leftHand());
		}
		Attack attack = moveset.attack(weaponLeft, twoHanded, type);
		if (attack != null && attack.isCast()) {
			// FUN_140395930: the checks pass -> Prepare (request +0x54 = 1); they fail -> NoBullet, nothing spent.
			ResourceLocation spell = SoulsMagic.castable(player, movesetId, weaponLeft);
			SoulsMagic.Cast cast = spell == null ? null
					: SoulsMagic.prepare(player, spell, movesetId, attack.staminaCost(), weaponLeft, twoHanded, upgradeLevel(
							weaponLeft ? player.getOffhandItem() : player.getMainHandItem()));
			Attack prepare = cast == null ? SoulsMagic.failedCast() : cast.prepare();
			SoulsRoll.forget(player);
			player.setSprinting(false);
			player.setYRot(yaw);
			player.setYBodyRot(yaw);
			player.setYHeadRot(yaw);
			Active active = new Active(cast == null ? SoulsMagic.FAILED_CAST : spell, null, request.leftHand(), weaponLeft,
					Moveset.Type.CAST_PREPARE, prepare, player.serverLevel().getGameTime(), yaw);
			active.cast = cast;
			ACTIVE.put(player.getUUID(), active);
			broadcast(player, new AttackPayloads.Start(player.getId(), active.movesetId, weaponLeft, twoHanded,
					Moveset.Type.CAST_PREPARE.ordinal(), player.getYRot()));
			return;
		}
		SoulsRoll.forget(player);
		player.setSprinting(false);
		if (!attack.guard()) {
			player.setYRot(yaw);
			player.setYBodyRot(yaw);
			player.setYHeadRot(yaw);
		}
		ACTIVE.put(player.getUUID(), new Active(movesetId, moveset, request.leftHand(), weaponLeft, type, attack,
				player.serverLevel().getGameTime(), yaw));
		if (attack.guard()) {
			sendGuard(player, movesetId, weaponLeft, twoHanded, type);
		}
		broadcast(player, new AttackPayloads.Start(player.getId(), movesetId, weaponLeft, twoHanded, type.ordinal(),
				player.getYRot()));
	}

	/**
	 * Power stance, a left button (powerstance.md s.3): the movement attacks keep their order and their one-handed left
	 * slots; otherwise light = 0x0d (slot 52), light inside the combo window after a 0x0d = 0x0e (53), heavy = 0x0f
	 * (54), from the weapon of the state (6: right, 5: left). False when it is a movement attack (the normal path runs).
	 */
	private static boolean startPowerStance(ServerPlayer player, Active current, AttackPayloads.Request request, float yaw,
			Moveset.Grip grip) {
		ResourceLocation leftId = movesetFor(player, true);
		Moveset left = leftId == null ? null : SoulsWeapons.moveset(leftId).orElse(null);
		if (left == null) {
			return false;
		}
		// Movement attacks of the left buttons (powerstance.md s.3): the left weapon's grip-5 slots 34 / 35 / 36 / 38 /
		// 39, which for every weapon are the rows of its slots 4..9 (the one-handed right block), in the left hand.
		Moveset.Type movement = movementType(player, left, false, false, request);
		if (movement != null) {
			Attack attack = left.attack(false, false, movement);
			SoulsRoll.forget(player);
			player.setSprinting(false);
			player.setYRot(yaw);
			player.setYBodyRot(yaw);
			player.setYHeadRot(yaw);
			ACTIVE.put(player.getUUID(), new Active(leftId, left, true, true, movement, attack,
					player.serverLevel().getGameTime(), yaw));
			broadcast(player, new AttackPayloads.Start(player.getId(), leftId, true, false, movement.ordinal(),
					player.getYRot()));
			return true;
		}
		boolean weaponLeft = grip == Moveset.Grip.POWER_STANCE_LEFT;
		ResourceLocation movesetId = movesetFor(player, weaponLeft);
		dev.soulsmode.ds2.PowerStance.Data data = movesetId == null ? null
				: dev.soulsmode.ds2.PowerStance.data(damageKey(movesetId));
		if (data == null) {
			return true;
		}
		float t = current == null ? 0 : seconds(player, current);
		Moveset.Type type = request.heavy() ? Moveset.Type.POWER_HEAVY
				: current != null && current.type == Moveset.Type.POWER_LIGHT && current.attack.inCombo(t)
						? Moveset.Type.POWER_LIGHT_COMBO : Moveset.Type.POWER_LIGHT;
		Attack attack = data.attack(type);
		if (attack == null) {
			return true;
		}
		SoulsRoll.forget(player);
		player.setSprinting(false);
		player.setYRot(yaw);
		player.setYBodyRot(yaw);
		player.setYHeadRot(yaw);
		ACTIVE.put(player.getUUID(), new Active(movesetId, SoulsWeapons.moveset(movesetId).orElse(null), true, weaponLeft,
				type, attack, player.serverLevel().getGameTime(), yaw));
		broadcast(player, new AttackPayloads.Start(player.getId(), movesetId, weaponLeft, false, type.ordinal(),
				player.getYRot()));
		return true;
	}

	/**
	 * Power stance hits (powerstance.md s.3): every TAE 2200 event is its own hit, the right weapon's (slots 0..4) or the
	 * left one's (5..9), with that weapon's PlayerDamageParam row for the slot and its hand factor.
	 */
	private static void powerStanceHit(ServerPlayer player, Active active, float t) {
		java.util.List<dev.soulsmode.ds2.PowerStance.Event> events = dev.soulsmode.ds2.PowerStance.events(active.attack);
		ServerLevel level = player.serverLevel();
		double range = player.entityInteractionRange();
		double yaw = Math.toRadians(active.yaw);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double minCos = Math.cos(Math.toRadians(HALF_ARC_DEGREES));
		for (int i = 0; i < events.size(); i++) {
			dev.soulsmode.ds2.PowerStance.Event event = events.get(i);
			if (t < event.start() || t >= event.end()) {
				continue;
			}
			boolean leftWeapon = event.leftWeapon();
			String weapon = damageKey(movesetFor(player, leftWeapon));
			float[] rate = dev.soulsmode.ds2.PowerStance.rate(weapon, active.type, event.slot() % 5);
			if (rate == null) {
				continue;
			}
			int window = 100 + i;
			AABB area = player.getBoundingBox().inflate(range, 0.5, range);
			for (Entity target : level.getEntities(player, area)) {
				if (!(target instanceof LivingEntity living) || !living.isAlive() || !target.isAttackable()
						|| target.skipAttackInteraction(player) || active.hit.contains(key(window, target))
						|| target instanceof Player other && !player.canHarmPlayer(other)) {
					continue;
				}
				double dx = target.getX() - player.getX();
				double dz = target.getZ() - player.getZ();
				double distance = Math.sqrt(dx * dx + dz * dz);
				double reach = distance - target.getBbWidth() / 2;
				if (reach > range || distance > 1e-3 && (dx * fx + dz * fz) / distance < minCos) {
					continue;
				}
				active.hit.add(key(window, target));
				damage(player, level, living, leftWeapon, rate[0], (int) rate[1], rate[2],
						rate.length > 3 && !Float.isNaN(rate[3]) ? java.util.Arrays.copyOfRange(rate, 3, rate.length) : null);
			}
		}
	}

	/**
	 * The movement attacks of FUN_140396680, tried in its order, each only if the weapon has an attack in that slot
	 * (thunk_FUN_141ca0bab returns 1 for an empty slot):
	 * <ul>
	 * <li>light: rolling (6, SM_Dodge active: the roll still playing), dash (5, dash mode: sprinting), backstep (10,
	 * SM_Step active), kick (0x10 / 0x11, the forward push of the stick);</li>
	 * <li>heavy: jump (9, the forward push).</li>
	 * </ul>
	 * Not ported: the fall attack (8; its landing state machine is not decoded) and type 7 (unreachable with the
	 * regulation's CommonParam +0xfc = 0). The dash also needs DS2's speed check (4.5 m/s x b8+0x11c, value not read):
	 * sprinting stands for dash mode alone.
	 */
	static Moveset.Type movementType(ServerPlayer player, Moveset moveset, boolean weaponLeft, boolean twoHanded,
			AttackPayloads.Request request) {
		java.util.function.Predicate<Moveset.Type> has = t -> moveset.attack(weaponLeft, twoHanded, t) != null;
		if (request.heavy()) {
			return request.flick() && has.test(Moveset.Type.JUMP) ? Moveset.Type.JUMP : null;
		}
		dev.soulsmode.ds2.Dodge dodge = SoulsRoll.dodging(player);
		if (dodge != null && dodge.isRoll() && has.test(Moveset.Type.ROLLING)) {
			return Moveset.Type.ROLLING;
		}
		if (player.isSprinting() && has.test(Moveset.Type.DASH)) {
			return Moveset.Type.DASH;
		}
		if (dodge != null && !dodge.isRoll() && has.test(Moveset.Type.BACKSTEP)) {
			return Moveset.Type.BACKSTEP;
		}
		return request.flick() && has.test(Moveset.Type.KICK) ? Moveset.Type.KICK : null;
	}

	private static void broadcast(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload packet) {
		ServerPlayNetworking.send(player, packet);
		for (ServerPlayer other : PlayerLookup.tracking(player)) {
			if (other != player) {
				ServerPlayNetworking.send(other, packet);
			}
		}
	}

	/**
	 * The hand's moveset, or null if that hand cannot attack: an off-hand item without a moveset does nothing; an
	 * empty off hand punches. A held DS2 torch replaces the left weapon (WeaponParam 6000000 override, FUN_14034f300).
	 */
	static ResourceLocation movesetFor(ServerPlayer player, boolean leftHand) {
		if (leftHand && SoulsTorch.isHeld(player)) {
			return SoulsTorch.MOVESET;
		}
		ItemStack stack = leftHand ? player.getOffhandItem() : player.getMainHandItem();
		ResourceLocation id = SoulsWeapons.movesetFor(stack);
		return leftHand && !stack.isEmpty() && id.equals(SoulsWeapons.DEFAULT) ? null : id;
	}

	public static void tick(MinecraftServer server) {
		tickHeld(server);
		Iterator<Map.Entry<UUID, Active>> it = ACTIVE.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Active> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Active active = entry.getValue();
			if (player == null || !player.isAlive() || !SoulsGameMode.isActive(player)) {
				it.remove();
				continue;
			}
			if (active.type == Moveset.Type.FALL && (player.isInWater() || player.isFallFlying())) {
				it.remove();
				broadcast(player, new AttackPayloads.Stop(player.getId()));
				continue;
			}
			if (active.type == Moveset.Type.FALL && player.onGround()) {
				tickFall(player, active);
				active = ACTIVE.get(player.getUUID());
			}
			float t = seconds(player, active);
			if (active.type == Moveset.Type.CAST_PREPARE && active.cast != null && t >= active.attack.duration()) {
				float pending = active.attack.staminaDue(active.chargedUntil, t) * lackCostFactor(player, active.movesetId);
				if (pending > 0) {
					SoulsStamina.spend(player, pending);
				}
				// FUN_1403972d0: the wait ran out with the spell still going: the cast animation (state 0x2f).
				Active cast = new Active(active.movesetId, null, active.leftHand, active.weaponLeft, Moveset.Type.CAST,
						active.cast.castAttack(), player.serverLevel().getGameTime(), player.getYRot());
				cast.cast = active.cast;
				entry.setValue(cast);
				broadcast(player, new AttackPayloads.Start(player.getId(), active.movesetId, active.weaponLeft,
						grip(player).twoHanded(), Moveset.Type.CAST.ordinal(), player.getYRot()));
				continue;
			}
			if (active.type == Moveset.Type.CAST && active.cast != null) {
				active.bulletsFired = SoulsMagic.fire(player, active.cast, active.bulletsFired, t);
			}
			attackSounds(player, active, t);
			if (active.over(t)) {
				if (active.guardHeld != null) {
					// The guard button is still held: back to the guard (it was requested every frame meanwhile).
					Active guard = active.guardHeld;
					entry.setValue(new Active(guard.movesetId, guard.moveset, guard.leftHand, guard.weaponLeft, guard.type,
							guard.attack, player.serverLevel().getGameTime(), guard.yaw));
					broadcast(player, new AttackPayloads.Start(player.getId(), guard.movesetId, guard.weaponLeft, false,
							guard.type.ordinal(), player.getYRot()));
					continue;
				}
				it.remove();
				continue;
			}
			// FUN_1403269e0 / FUN_14015c2d0: each hit event with a filled slot charges the attack's cost when it starts.
			float due = (active.type.powerStance()
					? dev.soulsmode.ds2.PowerStance.staminaDue(active.attack, active.chargedUntil, t)
					: active.attack.staminaDue(active.chargedUntil, t)) * lackCostFactor(player, active.movesetId);
			active.chargedUntil = t;
			if (due > 0) {
				var stamina = SoulsStamina.get(player);
				float before = stamina == null ? due : stamina.current();
				active.staminaLack = before < due ? (due - Math.max(0, before)) / due : 0;
				SoulsStamina.spend(player, due);
			}
			if (active.type == Moveset.Type.JUMP && jumpToFall(player, active, t)) {
				continue;
			}
			if (active.type.powerStance()) {
				powerStanceHit(player, active, t);
				continue;
			}
			int window = active.attack.hitWindow(t);
			if (window >= 0 && hitBack(player, active, t)) {
				continue;
			}
			if (window >= 0) {
				hit(player, active, window);
			}
		}
	}

	/**
	 * The DS2 sound events (c000100_snd.tae 201 / 202) of the running animation up to {@code t} (SoulsSounds). Hand 0
	 * swings the right weapon, hand 1 the left one (FUN_14037e210: chr +0x7b / +0x7a); a two-handed grip swings the held
	 * weapon with both (its grip modes 2 / 3).
	 */
	private static void attackSounds(ServerPlayer player, Active active, float t) {
		if (!SoulsSounds.ENABLED || active.attack == null || active.attack.taeId() == 0 || t <= active.soundsUntil) {
			return;
		}
		Moveset.Grip grip = grip(player);
		ResourceLocation right = grip.twoHanded() ? movesetFor(player, grip.leftWeapon()) : movesetFor(player, false);
		ResourceLocation left = grip.twoHanded() ? right : movesetFor(player, true);
		float limit = active.attack.guard() ? t : Math.min(t, active.attack.duration());
		SoulsSounds.attack(player, active.attack.taeId(), active.soundsUntil, limit,
				right == null ? null : damageKey(right), left == null ? null : damageKey(left), armorSets(player));
		active.soundsUntil = limit;
	}

	/**
	 * HitBack (docs/research/wall_bounce.md): during a hit window, the attack's capsule (PDP +0x31 dummy, +0x34 radius,
	 * +0x38 length along the dummy's forward; R_Weapon from the animation's exported samples) against the blocks'
	 * collision boxes, at every exported 1/30 s pose since the last tick. A contact whose normal has |n.y| <= cos 45 deg
	 * (FUN_140170d60: a wall) or that is more than 1 m above the attacker (PlayerCommonParam +0x134) bounces unless
	 * PDP +0x65 & 0x08; any other contact unless & 0x04. The bounce cuts the attack and plays SM_HitBack's animation
	 * for the grip and side at the level's DmgHitBackDuration speed.
	 */
	private static boolean hitBack(ServerPlayer player, Active active, float t) {
		dev.soulsmode.ds2.HitBack.Slot slot = dev.soulsmode.ds2.HitBack.slot(active.attack);
		if (slot == null || !slot.map() || slot.noFloor() && slot.noWall() || !"weapon".equals(slot.source())) {
			return false;
		}
		dev.soulsmode.ds2.HitBack.Dummy dummy = dev.soulsmode.ds2.HitBack.dummy(damageKey(active.movesetId), slot.dummy());
		if (dummy == null) {
			return false;
		}
		// From the last sweep (one tick back on the first), not before this hit window opened.
		float windowStart = active.attack.hits().get(active.attack.hitWindow(t))[0];
		float from = Math.max(windowStart - 1e-4f, active.hitBackSwept < 0 ? t - 0.05f : active.hitBackSwept);
		active.hitBackSwept = t;
		// Model space (left, up, forward) to the world, along the attack's facing; mirrored for the other hand.
		double yaw = Math.toRadians(active.yaw);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double side = active.attack.mirroredFor(active.weaponLeft) ? -1 : 1;
		double lx = Math.cos(yaw) * side;
		double lz = Math.sin(yaw) * side;
		net.minecraft.world.phys.Vec3 origin = player.position();
		Contact contact = null;
		for (dev.soulsmode.ds2.HitBack.Bone bone : dev.soulsmode.ds2.HitBack.bones(active.attack.taeId(), from, t)) {
			float[] base = new float[3];
			float[] axis = new float[3];
			for (int i = 0; i < 3; i++) {
				float[] r = bone.rot()[i];
				base[i] = bone.pos()[i] + r[0] * dummy.pos()[0] + r[1] * dummy.pos()[1] + r[2] * dummy.pos()[2];
				axis[i] = r[0] * dummy.fwd()[0] + r[1] * dummy.fwd()[1] + r[2] * dummy.fwd()[2];
			}
			net.minecraft.world.phys.Vec3 a = origin.add(base[0] * lx + base[2] * fx, base[1], base[0] * lz + base[2] * fz);
			net.minecraft.world.phys.Vec3 dir = new net.minecraft.world.phys.Vec3(axis[0] * lx + axis[2] * fx, axis[1],
					axis[0] * lz + axis[2] * fz).normalize();
			contact = capsuleContact(player.level(), player, a, a.add(dir.scale(slot.length())), slot.radius());
			if (contact != null) {
				break;
			}
		}
		if (contact == null) {
			return false;
		}
		boolean wall = Math.abs(contact.normal().y) <= Math.cos(Math.PI / 4)
				|| contact.point().y - player.getY() > dev.soulsmode.ds2.HitBack.HIGH_CONTACT;
		if (wall ? slot.noWall() : slot.noFloor()) {
			return false;
		}
		boolean twoHanded = grip(player).twoHanded();
		boolean upperL = dev.soulsmode.ds2.HitBack.event(active.attack.taeId()) == 281;
		Attack bounce = dev.soulsmode.ds2.HitBack.bounce(twoHanded, upperL, slot.speed(), slot.distance());
		if (bounce == null) {
			return false;
		}
		ResourceLocation id = hitBackId(twoHanded, upperL, slot.speed(), slot.distance());
		ACTIVE.put(player.getUUID(), new Active(id, null, false, active.weaponLeft, Moveset.Type.ITEM_USE, bounce,
				player.serverLevel().getGameTime(), active.yaw));
		// The bounce clips' TAE event 301 at 0 s: a sound of level 8, factor 15 (detection.md s.3).
		SoulsPerception.emit(player, dev.soulsmode.ds2.Perception.HIT_BACK);
		broadcast(player, new AttackPayloads.Start(player.getId(), id, active.weaponLeft, twoHanded,
				Moveset.Type.ITEM_USE.ordinal(), active.yaw));
		return true;
	}

	/** A capsule touching a collision box: the nearest point on the box and the normal from it towards the capsule. */
	private record Contact(net.minecraft.world.phys.Vec3 point, net.minecraft.world.phys.Vec3 normal) {
	}

	/** The capsule a..b of radius r against the blocks' collision boxes: the deepest contact, or null. */
	private static Contact capsuleContact(net.minecraft.world.level.Level level, Entity owner,
			net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b, double r) {
		AABB bounds = new AABB(a, b).inflate(r);
		Contact best = null;
		double bestDistance = r;
		for (net.minecraft.world.phys.shapes.VoxelShape shape : level.getBlockCollisions(owner, bounds)) {
			for (AABB box : shape.toAabbs()) {
				// The distance to a convex box from a point moving along a segment is convex: ternary search.
				double lo = 0;
				double hi = 1;
				for (int i = 0; i < 40; i++) {
					double m1 = lo + (hi - lo) / 3;
					double m2 = hi - (hi - lo) / 3;
					if (boxDistance(box, lerp(a, b, m1)) <= boxDistance(box, lerp(a, b, m2))) {
						hi = m2;
					} else {
						lo = m1;
					}
				}
				net.minecraft.world.phys.Vec3 p = lerp(a, b, (lo + hi) / 2);
				net.minecraft.world.phys.Vec3 q = closest(box, p);
				double d = p.distanceTo(q);
				if (d > bestDistance) {
					continue;
				}
				net.minecraft.world.phys.Vec3 n = p.subtract(q);
				if (n.lengthSqr() < 1e-12) {
					// The axis is inside the box: the normal of the face it is nearest to.
					double[] gaps = {p.x - box.minX, box.maxX - p.x, p.y - box.minY, box.maxY - p.y, p.z - box.minZ,
							box.maxZ - p.z};
					int face = 0;
					for (int i = 1; i < 6; i++) {
						if (gaps[i] < gaps[face]) {
							face = i;
						}
					}
					double sign = face % 2 == 0 ? -1 : 1;
					n = new net.minecraft.world.phys.Vec3(face / 2 == 0 ? sign : 0, face / 2 == 1 ? sign : 0,
							face / 2 == 2 ? sign : 0);
				}
				bestDistance = d;
				best = new Contact(q, n.normalize());
			}
		}
		return best;
	}

	private static net.minecraft.world.phys.Vec3 lerp(net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b,
			double s) {
		return a.add(b.subtract(a).scale(s));
	}

	private static net.minecraft.world.phys.Vec3 closest(AABB box, net.minecraft.world.phys.Vec3 p) {
		return new net.minecraft.world.phys.Vec3(net.minecraft.util.Mth.clamp(p.x, box.minX, box.maxX),
				net.minecraft.util.Mth.clamp(p.y, box.minY, box.maxY), net.minecraft.util.Mth.clamp(p.z, box.minZ, box.maxZ));
	}

	private static double boxDistance(AABB box, net.minecraft.world.phys.Vec3 p) {
		return p.distanceTo(closest(box, p));
	}

	/** The bounce's Start id: grip, side, DmgHitBackDuration x1000 and DmgHitBackDistance x100. */
	private static ResourceLocation hitBackId(boolean twoHanded, boolean upperL, float speed, float distance) {
		return SoulsMode.id("hit_back_" + (twoHanded ? "2h" : "1h") + (upperL ? "_l" : "_r") + "_"
				+ Math.round(speed * 1000) + "_" + Math.round(distance * 100));
	}

	/** DmgHitBackDuration of a bounce's Start id (its playback speed); 1 for any other id. */
	public static float hitBackSpeed(ResourceLocation id) {
		String[] parts = id.getPath().split("_");
		return id.getPath().startsWith("hit_back_") && parts.length >= 5 ? Integer.parseInt(parts[4]) / 1000f : 1;
	}

	private static void hit(ServerPlayer player, Active active, int window) {
		ServerLevel level = player.serverLevel();
		if (active.type == Moveset.Type.BACKSTAB || active.type == Moveset.Type.RIPOSTE
				|| active.type == Moveset.Type.GUARD_BREAK_STAB) {
			// The backstab's damage goes to the held victim only (the grab), once per TAE 2201 event.
			for (Map.Entry<Integer, Held> e : HELD.entrySet()) {
				if (e.getValue().attacker().equals(player.getUUID()) && level.getEntity(e.getKey()) instanceof LivingEntity victim
						&& active.hit.add(key(window, victim))) {
					// The grabbed victim plays the throw, not a damage reaction.
					damage(player, level, victim, false, active.attack.damageRate(window), active.attack.damageFloor(window),
							Float.NaN, null);
				}
			}
			return;
		}
		double range = player.entityInteractionRange();
		double yaw = Math.toRadians(active.yaw);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double minCos = Math.cos(Math.toRadians(HALF_ARC_DEGREES));
		AABB area = player.getBoundingBox().inflate(range, 0.5, range);
		for (Entity target : level.getEntities(player, area)) {
			if (!(target instanceof LivingEntity living) || !living.isAlive() || !target.isAttackable()
					|| target.skipAttackInteraction(player) || active.hit.contains(key(window, target))
					|| target instanceof Player other && !player.canHarmPlayer(other)) {
				continue;
			}
			double dx = target.getX() - player.getX();
			double dz = target.getZ() - player.getZ();
			double distance = Math.sqrt(dx * dx + dz * dz);
			// Measure to the near side of the target's box so large mobs can be reached.
			double reach = distance - target.getBbWidth() / 2;
			if (reach > range || distance > 1e-3 && (dx * fx + dz * fz) / distance < minCos) {
				continue;
			}
			active.hit.add(key(window, target));
			damage(player, level, living, active.weaponLeft, active.attack.damageRate(window),
					active.attack.damageFloor(window), active.attack.reactionSpeed(window), active.attack.reactionMotion(window));
		}
	}

	/**
	 * Weapon damage the vanilla way (attribute + enchantments + durability), times the hit's DS2 damage rate
	 * (PlayerDamageParam +0x1c, FUN_140138d50: 1.0 for the one-handed light attack, 1.4 / 1.54 for backstab / riposte,
	 * 0 for parries). DS2 applies it after defense; here it goes before armor, which in Minecraft mostly scales the
	 * same way. The rest of the DS2 damage formula (attack ratings, defenses) is not ported.
	 */
	/**
	 * DS2 damage (docs/research/damage.md): the weapon's attack rating from the player's stats and the weapon's upgrade
	 * level, dealt to the target as an enemy with zero DS2 defence (Minecraft armour still applies), in DS2 HP turned
	 * into Minecraft health at {@link SoulsStats#DS2_HP_PER_HEALTH} (the user's choice: the same scale as max HP).
	 * The Minecraft material is the upgrade level (the user's choice): wood +0, stone +1, iron and gold +3, diamond
	 * +6, netherite +10; items without a material +0. Enchantments are added on top as in vanilla.
	 */
	private static void damage(ServerPlayer player, ServerLevel level, LivingEntity target, boolean leftHand, float rate,
			int floor, float reactionSpeed, float[] reactionMotion) {
		ItemStack weapon = leftHand ? SoulsTorch.isHeld(player) ? SoulsTorch.STACK : player.getOffhandItem()
				: player.getMainHandItem();
		DamageSource source = player.damageSources().playerAttack(player);
		ResourceLocation moveset = leftHand && SoulsTorch.isHeld(player) ? SoulsTorch.MOVESET : SoulsWeapons.movesetFor(weapon);
		var stats = SoulsPlayer.data(player).map(SoulsData::toAttributes).orElse(null);
		float amount;
		if (stats != null) {
			int resinType = leftHand ? -1 : SoulsResin.damageType(player);
			Active current = ACTIVE.get(player.getUUID());
			float[][] buffs = SoulsSpEffects.weaponBuffs(player, leftHand);
			float[] attack = dev.soulsmode.ds2.Ds2Damage.attackRating(damageKey(moveset), upgradeLevel(weapon), stats,
					grip(player).twoHanded(), leftHand, resinType,
					current == null ? 0 : current.staminaLack, buffs[0], buffs[1]);
			if (resinType == 3 && target.fireImmune()) {
				attack[3] = 0;
			}
			SoulsResin.weakness(resinType, target, attack);
			if (target instanceof ServerPlayer victim && SoulsGameMode.isActive(victim)
					&& SoulsPlayer.data(victim).isPresent()) {
				// A Souls Mode player: DS2's player victim formula with its own stats and armour (the armour is in DS2's
				// defence, so the hit bypasses Minecraft's armour).
				var defence = defence(victim);
				amount = dev.soulsmode.ds2.Ds2Damage.damageToPlayer(damageKey(moveset), attack, rate, floor, defence)
						/ (float) SoulsStats.DS2_HP_PER_HEALTH;
				source = new DamageSource(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
						.getHolderOrThrow(SOULS_ATTACK), player);
			} else if (target instanceof dev.soulsmode.entity.SoulsInvader invader) {
				// An NPC invader is a player-type character: DS2's player victim formula with its stats and armour.
				amount = dev.soulsmode.ds2.Ds2Damage.damageToPlayer(damageKey(moveset), attack, rate, floor,
						defence(invader, invader.stats())) / (float) SoulsStats.DS2_HP_PER_HEALTH;
			} else if (SoulsGiants.isLastGiant(target)) {
				// The penalty giant: EnemyParam 309600's defence and absorption (SoulsGiants).
				amount = SoulsGiants.damageFromPlayer(attack, rate, floor,
						dev.soulsmode.ds2.Ds2Damage.weaponFactor(damageKey(moveset)));
			} else if (target instanceof dev.soulsmode.entity.SoulsEnemy enemy && enemy.enemyDef() != null) {
				// A datapack enemy: its DS2 defence and absorption (docs/research/enemies.md).
				amount = dev.soulsmode.ds2.Enemies.damageToEnemy(attack, rate, floor,
						dev.soulsmode.ds2.Ds2Damage.weaponFactor(damageKey(moveset)), enemy.enemyDef())
						/ (float) SoulsStats.DS2_HP_PER_HEALTH;
			} else {
				amount = dev.soulsmode.ds2.Ds2Damage.damageToEnemy(damageKey(moveset), attack, rate, floor)
						/ (float) SoulsStats.DS2_HP_PER_HEALTH;
			}
		} else {
			amount = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * rate;
		}
		float base = amount;
		amount = EnchantmentHelper.modifyDamage(level, weapon, target, source, amount);
		if (stats == null && leftHand) {
			amount += (float) (weaponDamage(weapon) - weaponDamage(player.getMainHandItem()));
		}
		if (base <= 0 && amount <= 0) {
			return;
		}
		if (!target.hurt(source, amount)) {
			return;
		}
		if (!Float.isNaN(reactionSpeed)) {
			SoulsHitReaction.onHit(target, reactionSpeed, reactionMotion, player);
		}
		if (target instanceof ServerPlayer victim) {
			// A Souls Mode player hit by another: the DCP row of the swing's PDP slot (poise.md s.4.3).
			Active current = ACTIVE.get(player.getUUID());
			if (current != null && current.attack != null) {
				int window = current.attack.hitWindow(seconds(player, current));
				int slot = window < 0 ? -1 : current.attack.hitSlot(window);
				SoulsPoise.onHit(victim, dev.soulsmode.ds2.Poise.weaponHit(current.attack.taeId(), slot), player);
			}
		}
		if (!leftHand) {
			SoulsResin.onHit(player, target);
		}
		player.setLastHurtMob(target);
		EnchantmentHelper.doPostAttackEffects(level, target, source);
		if (weapon.hurtEnemy(target, player)) {
			weapon.postHurtEnemy(target, player);
		}
		SoulsStick.onHit(player, weapon, leftHand);
		player.causeFoodExhaustion(0.1f);
	}

	/** Player-vs-player hits: DS2 defence already counts the armour, so this type bypasses Minecraft's. */
	private static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> SOULS_ATTACK =
			net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
					SoulsMode.id("souls_attack"));

	/**
	 * The DS2 armour pieces a player wears: each Minecraft armour piece is its material's DS2 set piece (the weight
	 * mapping, docs/research/equipload.md: helmet head, chestplate chest, leggings legs, boots arms); netherite uses
	 * the diamond set (DS2 has no data for the coating); empty or other slots are naked.
	 */
	/** A Souls Mode player's DS2 defence, for hits from other attackers (datapack enemies). */
	public static dev.soulsmode.ds2.Ds2Damage.Defence defenceOf(ServerPlayer victim) {
		return defence(victim);
	}

	/** The damage source of DS2 hits on a Souls Mode player (it bypasses Minecraft's armour). */
	public static DamageSource soulsDamage(Entity attacker) {
		return new DamageSource(attacker.level().registryAccess()
				.registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(SOULS_ATTACK), attacker);
	}

	/** A Souls Mode player's DS2 defence with its armour and its defence buffs (SoulsSpEffects). */
	private static dev.soulsmode.ds2.Ds2Damage.Defence defence(ServerPlayer victim) {
		float[] b = SoulsSpEffects.defenceBuffs(victim);
		return dev.soulsmode.ds2.Ds2Damage.playerDefence(SoulsPlayer.data(victim).get().toAttributes(), armorPieces(victim),
				b[0], new float[] {b[1], b[2], b[3], b[4]});
	}

	/** A player-type character's DS2 defence from its stats and worn armour (no buffs): NPC invaders. */
	private static dev.soulsmode.ds2.Ds2Damage.Defence defence(LivingEntity victim, dev.soulsmode.ds2.Attributes stats) {
		return dev.soulsmode.ds2.Ds2Damage.playerDefence(stats, armorPieces(victim), 0, new float[4]);
	}

	/**
	 * An NPC invader's hit (SoulsInvader): DS2's player attack rating with its stats, weapon and grip, then the
	 * victim's formula (a Souls Mode player: DS2's player victim with its defence; anything else: the mob formula).
	 */
	public static boolean npcHit(dev.soulsmode.entity.SoulsInvader attacker, LivingEntity target, float rate, int floor) {
		ItemStack weapon = attacker.getMainHandItem();
		String key = damageKey(SoulsWeapons.movesetFor(weapon));
		float[] attack = dev.soulsmode.ds2.Ds2Damage.attackRating(key, upgradeLevel(weapon), attacker.stats(),
				attacker.twoHanded(), false, -1, attacker.staminaLack());
		float amount;
		DamageSource source;
		if (target instanceof ServerPlayer victim && SoulsGameMode.isActive(victim) && SoulsPlayer.data(victim).isPresent()) {
			amount = dev.soulsmode.ds2.Ds2Damage.damageToPlayer(key, attack, rate, floor, defence(victim))
					/ (float) SoulsStats.DS2_HP_PER_HEALTH;
			source = soulsDamage(attacker);
		} else {
			amount = dev.soulsmode.ds2.Ds2Damage.damageToEnemy(key, attack, rate, floor) / (float) SoulsStats.DS2_HP_PER_HEALTH;
			source = attacker.damageSources().mobAttack(attacker);
		}
		return amount > 0 && target.hurt(source, amount);
	}

	/** The mod's DS2 armour set worn per piece (head, chest, arms = boots, legs): "" empty, null a datapack piece. */
	private static String[] armorSets(LivingEntity player) {
		net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD,
				net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.FEET,
				net.minecraft.world.entity.EquipmentSlot.LEGS};
		String[] out = new String[4];
		for (int i = 0; i < 4; i++) {
			ItemStack stack = player.getItemBySlot(slots[i]);
			if (stack.isEmpty()) {
				out[i] = "";
			} else if (dev.soulsmode.content.SoulsContent.armor(stack) == null
					&& stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor) {
				String set = armorSet(armor.getMaterial());
				out[i] = set == null ? null : set;
			}
		}
		return out;
	}

	private static String armorSet(net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> m) {
		return m.equals(net.minecraft.world.item.ArmorMaterials.LEATHER) ? "leather"
				: m.equals(net.minecraft.world.item.ArmorMaterials.CHAIN) ? "chainmail"
				: m.equals(net.minecraft.world.item.ArmorMaterials.IRON) ? "iron"
				: m.equals(net.minecraft.world.item.ArmorMaterials.GOLD) ? "gold"
				: m.equals(net.minecraft.world.item.ArmorMaterials.DIAMOND)
						|| m.equals(net.minecraft.world.item.ArmorMaterials.NETHERITE) ? "diamond"
				: m.equals(net.minecraft.world.item.ArmorMaterials.TURTLE) ? "turtle" : null;
	}

	private static dev.soulsmode.ds2.Ds2Damage.Piece[] armorPieces(LivingEntity player) {
		net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD,
				net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.FEET,
				net.minecraft.world.entity.EquipmentSlot.LEGS};
		dev.soulsmode.ds2.Ds2Damage.Piece[] out = new dev.soulsmode.ds2.Ds2Damage.Piece[4];
		for (int i = 0; i < 4; i++) {
			ItemStack stack = player.getItemBySlot(slots[i]);
			String set = null;
			if (stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor) {
				var m = armor.getMaterial();
				set = m.equals(net.minecraft.world.item.ArmorMaterials.LEATHER) ? "leather"
						: m.equals(net.minecraft.world.item.ArmorMaterials.CHAIN) ? "chainmail"
						: m.equals(net.minecraft.world.item.ArmorMaterials.IRON) ? "iron"
						: m.equals(net.minecraft.world.item.ArmorMaterials.GOLD) ? "gold"
						: m.equals(net.minecraft.world.item.ArmorMaterials.DIAMOND)
								|| m.equals(net.minecraft.world.item.ArmorMaterials.NETHERITE) ? "diamond"
						: m.equals(net.minecraft.world.item.ArmorMaterials.TURTLE) ? "turtle" : null;
			}
			// A datapack armour piece counts in the slot it is worn in.
			var pack = dev.soulsmode.content.SoulsContent.armor(stack);
			out[i] = pack != null ? pack.piece() : dev.soulsmode.ds2.Ds2Damage.piece(set, i);
		}
		return out;
	}

	/**
	 * A spell bullet's hit (docs/research/catalysts.md section 3): the bullet's attack values (the catalyst's attack
	 * rating plus the spell's flat damage) through the same victim formulas as a weapon hit, with the bullet's own
	 * damage row floor and rate. The school factor is 1.0 (no SpEffects change it in the mod). A bullet's damage row is
	 * a PDP like a melee hit's, so a hit that goes in makes the mob react at its {@code reactionSpeed} (NaN: none).
	 */
	public static void spellHit(ServerPlayer player, LivingEntity target, String catalystKey, float[] attack, float rate,
			int floor, float reactionSpeed) {
		ServerLevel level = player.serverLevel();
		float amount;
		DamageSource source = player.damageSources().playerAttack(player);
		if (target instanceof ServerPlayer victim && SoulsGameMode.isActive(victim) && SoulsPlayer.data(victim).isPresent()) {
			var defence = defence(victim);
			amount = dev.soulsmode.ds2.Ds2Damage.damageToPlayer(catalystKey, attack, rate, floor, defence)
					/ (float) SoulsStats.DS2_HP_PER_HEALTH;
			source = new DamageSource(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
					.getHolderOrThrow(SOULS_ATTACK), player);
		} else if (target instanceof dev.soulsmode.entity.SoulsEnemy enemy && enemy.enemyDef() != null) {
			amount = dev.soulsmode.ds2.Enemies.damageToEnemy(attack, rate, floor,
					dev.soulsmode.ds2.Ds2Damage.weaponFactor(catalystKey), enemy.enemyDef())
					/ (float) SoulsStats.DS2_HP_PER_HEALTH;
		} else {
			amount = dev.soulsmode.ds2.Ds2Damage.damageToEnemy(catalystKey, attack, rate, floor)
					/ (float) SoulsStats.DS2_HP_PER_HEALTH;
		}
		if (amount > 0 && target.hurt(source, amount)) {
			player.setLastHurtMob(target);
			SoulsHitReaction.onHit(target, reactionSpeed);
		}
	}

	/** Ds2Damage's key for a weapon: a built-in weapon's name, or a datapack weapon's full id. */
	public static String damageKey(ResourceLocation moveset) {
		return dev.soulsmode.content.SoulsContent.weapon(moveset) != null ? moveset.toString() : moveset.getPath();
	}

	/** The user's material -> DS2 upgrade level table. */
	public static int upgradeLevel(ItemStack stack) {
		if (stack.getItem() instanceof net.minecraft.world.item.TieredItem tiered) {
			var tier = tiered.getTier();
			if (tier == net.minecraft.world.item.Tiers.STONE) {
				return 1;
			}
			if (tier == net.minecraft.world.item.Tiers.IRON || tier == net.minecraft.world.item.Tiers.GOLD) {
				return 3;
			}
			if (tier == net.minecraft.world.item.Tiers.DIAMOND) {
				return 6;
			}
			if (tier == net.minecraft.world.item.Tiers.NETHERITE) {
				return 10;
			}
		}
		return 0;
	}

	/** Flat attack damage the item adds when held in the main hand (its tooltip value minus the base 1). */
	private static double weaponDamage(ItemStack stack) {
		double[] sum = {0};
		stack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attribute, modifier) -> {
			if (attribute.value() == Attributes.ATTACK_DAMAGE.value() && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
				sum[0] += modifier.amount();
			}
		});
		return sum[0];
	}

	/**
	 * The current action's clip for the poise checks (SoulsPoise): its TAE id, its time, and the weapons.json key of the
	 * weapon swinging it (WeaponParam +0xb8 under TAE 111900); null without one.
	 */
	public record Clip(int taeId, float t, String weapon) {
	}

	public static Clip currentClip(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		if (active == null || active.attack == null) {
			return null;
		}
		float t = seconds(player, active);
		if (active.over(t)) {
			return null;
		}
		ResourceLocation weapon = movesetFor(player, active.weaponLeft);
		return new Clip(active.attack.taeId(), t, weapon == null ? null : damageKey(weapon));
	}

	/** TAE 101100 of the current action: stamina does not regenerate. */
	public static boolean blocksRegen(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		if (active == null) {
			return false;
		}
		float t = seconds(player, active);
		return !active.over(t) && active.attack.blocksRegen(t);
	}

	/**
	 * Inside the current attack's AttackDuration track (chr.c0+0x54), which spans every ported attack animation
	 * whole: a dodge costs ChrCommonParam+0x3c times more.
	 */
	public static boolean inAttackDuration(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		return active != null && !active.attack.guard() && active.type != Moveset.Type.ITEM_USE
				&& !active.over(seconds(player, active));
	}

	/** CancelControl_Avoid of the current attack. */
	public static boolean blocksDodge(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		if (active == null) {
			return false;
		}
		float t = seconds(player, active);
		return !active.over(t) && !active.attack.guard() && active.attack.blocksDodge(t);
	}

	/**
	 * Damage after the guard of a player who is holding one (DS2 guard rates of the guarding weapon, see
	 * {@link Moveset#guardFactor}). Only physical hits from in front count as blocked: index 0 is the only guard rate
	 * whose damage type is confirmed, and the shield's hit volume is not decoded, so "in front" (the attacker within
	 * 90 degrees of the facing) is a design choice.
	 */
	public static float guarded(ServerPlayer player, DamageSource source, float amount) {
		Active current = ACTIVE.get(player.getUUID());
		// A guard attack keeps the guard of the other hand (weapon_arts.md s.8).
		Active active = current == null ? null : current.attack.guard() ? current : current.guardHeld;
		Entity attacker = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
		if (active == null || attacker == null
				|| source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
				|| source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
				|| source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)
				|| source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)
				|| source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC)) {
			return amount;
		}
		double yaw = Math.toRadians(player.getYRot());
		double dx = attacker.getX() - player.getX();
		double dz = attacker.getZ() - player.getZ();
		if (dx * -Math.sin(yaw) + dz * Math.cos(yaw) <= 0) {
			return amount;
		}
		// FUN_1401345c0: vt+0xf0 (FUN_140136220) applies the blocked damage and charges the stamina damage, then vt+0x80
		// (FUN_140136570) turns a hit on guard with stamina <= 0 into a guard break (reaction 10). The hit that empties
		// the bar still deals blocked damage.
		SoulsStamina.spend(player, guardStaminaDamage(player, active, source));
		if (!SoulsStamina.canAct(player)) {
			guardBreak(player);
		}
		return amount * active.moveset.guardFactor(0);
	}

	public static final ResourceLocation GUARD_BREAK = SoulsMode.id("guard_break");
	private static final Attack GUARD_BREAK_ATTACK = dev.soulsmode.ds2.Weapons.guardBreak();
	private static final Map<ResourceLocation, dev.soulsmode.ds2.Weapons.GuardData> GUARDS = new HashMap<>();

	private static dev.soulsmode.ds2.Weapons.GuardData guard(ResourceLocation moveset) {
		var pack = dev.soulsmode.content.SoulsContent.weapon(moveset);
		return pack != null ? pack.guard() : GUARDS.get(moveset);
	}
	private static final Map<String, Float> MOB_GUARD_STAMINA = mobGuardStamina();

	static {
		dev.soulsmode.ds2.Weapons.guards().forEach((name, data) -> GUARDS.put(SoulsWeapons.id(name), data));
	}

	/** The item-use animation for an id sent in an {@link AttackPayloads.Start}: the torch, or the guard break. */
	public static Attack itemUse(ResourceLocation id) {
		if (SoulsHollow.EFFIGY_USE.equals(id)) {
			return SoulsHollow.EFFIGY.use();
		}
		if (SoulsResin.RESIN_USE.equals(id)) {
			return SoulsResin.USE.use();
		}
		if (SoulsSoulItems.USE_ID.equals(id)) {
			return SoulsSoulItems.USE.use();
		}
		if (SoulsThrown.LLOYD_USE.equals(id)) {
			return SoulsThrown.LLOYD.use();
		}
		if (SoulsThrown.FIREBOMB_USE.equals(id)) {
			return SoulsThrown.FIREBOMB.use();
		}
		Attack ranged = SoulsBow.clip(id);
		if (ranged != null) {
			return ranged;
		}
		if (id.getPath().startsWith("hit_back_")) {
			// hit_back_<1h|2h>_<r|l>_<speed x1000>_<distance x100>
			String[] parts = id.getPath().split("_");
			return dev.soulsmode.ds2.HitBack.bounce(parts[2].equals("2h"), parts[3].equals("l"), hitBackSpeed(id),
					parts.length >= 6 ? Integer.parseInt(parts[5]) / 100f : Float.NaN);
		}
		var consumable = dev.soulsmode.content.SoulsContent.consumable(id);
		if (consumable != null) {
			return consumable.use().attack();
		}
		return GUARD_BREAK.equals(id) ? GUARD_BREAK_ATTACK : SoulsTorch.itemUse(id);
	}

	/**
	 * Stamina damage of a hit on guard (FUN_140137aa0 / FUN_140139210, docs/research/stamina.md "Guardia"):
	 * base x (1 - stability / 100) x the attacker's rate. The base is the hit's damage row u16 +0x24: for a Souls Mode
	 * player, the PlayerDamageParam of the current hit window, times WeaponParam +0x84 x WeaponTypeParam +0x64 / +0x58;
	 * Minecraft mobs have no DS2 row, so they use data/soulsmode/mob_guard_stamina.json (the user's choice). Stability is
	 * the guarding weapon's, one- or two-handed. Not ported: the distance falloff of DamageCtrlParam +0xa4..+0xb0 (player
	 * attackers only), stat lack, effects and multiplayer rows (all neutral offline without effects).
	 */
	private static float guardStaminaDamage(ServerPlayer player, Active guard, DamageSource source) {
		float base = 0;
		if (source.getEntity() instanceof ServerPlayer attacker && SoulsGameMode.isActive(attacker)) {
			Active hit = ACTIVE.get(attacker.getUUID());
			int window = hit == null ? -1 : hit.attack.hitWindow(seconds(attacker, hit));
			if (window >= 0 && !Float.isNaN(hit.attack.staminaDamage(window))) {
				var data = guard(hit.movesetId);
				base = hit.attack.staminaDamage(window) * (data == null ? 1 : data.staminaRates()[hit.weaponLeft ? 1 : 0]);
			}
		} else if (source.getEntity() instanceof dev.soulsmode.entity.SoulsEnemy enemy) {
			// EnemyDamageParam u16 +0x24 of the hit (enemies.md s.4.1).
			base = enemy.guardStaminaDamage();
		} else if (source.getEntity() != null) {
			base = MOB_GUARD_STAMINA.getOrDefault(
					net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(source.getEntity().getType()).toString(), 0f);
		}
		var data = guard(guard.movesetId);
		float stability = data == null ? 0 : data.stability()[grip(player) == Moveset.Grip.ONE_HANDED ? 0 : 1];
		// FUN_14034cc80(4, f): x (1 - 0.01 (30 + 50 f)) when the guarding weapon's requirements are not met (stamina.md).
		float f = requirementLack(player, guard.movesetId);
		if (f > 0) {
			stability *= 1 - Math.max(0, Math.min(1, 0.01f * (30 + 50 * f)));
		}
		return base * (1 - Math.max(0, Math.min(100, stability)) / 100f);
	}

	/** The weapon's stat lack f (Ds2Damage.requirementLack) for this player, with its current grip; 0 outside Souls Mode. */
	private static float requirementLack(ServerPlayer player, ResourceLocation movesetId) {
		var stats = SoulsPlayer.data(player).map(SoulsData::toAttributes).orElse(null);
		return stats == null || movesetId == null ? 0 : dev.soulsmode.ds2.Ds2Damage.requirementLack(damageKey(movesetId),
				stats, grip(player).twoHanded());
	}

	/**
	 * FUN_14038fb10: an attack's stamina cost x (1 + lack), lack = PlayerLackOfStatsParam row 11 (30, 50): 0.01 (30 +
	 * 20 f) when the weapon's requirements are not met, clamped to [0, 1] (stamina.md "Coste de un ataque normal").
	 */
	private static float lackCostFactor(ServerPlayer player, ResourceLocation movesetId) {
		float f = requirementLack(player, movesetId);
		return f > 0 ? 1 + Math.max(0, Math.min(1, 0.01f * (30 + 20 * f))) : 1;
	}

	private static Map<String, Float> mobGuardStamina() {
		try (var in = SoulsAttack.class.getResourceAsStream("/data/soulsmode/mob_guard_stamina.json")) {
			Map<String, Float> out = new HashMap<>();
			for (var e : com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,
					java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet()) {
				if (!e.getKey().startsWith("_")) {
					out.put(e.getKey(), e.getValue().getAsFloat());
				}
			}
			return out;
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	/** Reaction 10: the guard drops and the guard break animation runs with its locks (see Weapons.guardBreak). */
	/** Length of a00_16_1010 DmgParryBreak_Start; the loop follows it. */
	private static final float PARRY_BREAK_START = 1.6667f;
	/** End of StabControl 262 in a00_16_1010 DmgParryBreak_Start (the guard-break critical window). */
	private static final float GUARD_BREAK_STAB_UNTIL = 0.6667f;
	/** Guard-broken players: entity id -> game tick their guard-break stab window closes. */
	private static final Map<Integer, Long> GUARD_BROKEN = new HashMap<>();

	private static void guardBreak(ServerPlayer player) {
		GUARD_BROKEN.put(player.getId(), player.serverLevel().getGameTime() + Math.round(GUARD_BREAK_STAB_UNTIL * 20));
		ACTIVE.put(player.getUUID(), new Active(GUARD_BREAK, null, false, false, Moveset.Type.ITEM_USE, GUARD_BREAK_ATTACK,
				player.serverLevel().getGameTime(), player.getYRot()));
		broadcast(player, new AttackPayloads.Start(player.getId(), GUARD_BREAK, false, false,
				Moveset.Type.ITEM_USE.ordinal(), player.getYRot()));
	}

	private static final float[] PARRY_ANGLES = dev.soulsmode.ds2.Weapons.parryAngles();

	/**
	 * A hit parried (FUN_140138510 category 5): no damage and no reaction for the parrier (FUN_140139b40). It needs the
	 * parry flag of the current attack ({@link Attack#isParrying}) and the attacker within the parrier's angle limits
	 * (FUN_140144b00, ChrCommonParam +0x9c horizontal / +0x98 vertical). Only melee hits count: DS2 also filters by
	 * the weapon's parry type and the attack's DamageCtrlParam +0x03, which Minecraft attacks do not have; type 2 (most
	 * weapons) appears to exclude projectiles, which is not confirmed. What the parry does to the attacker is not found
	 * yet and is not applied.
	 */
	public static boolean parries(ServerPlayer player, DamageSource source) {
		Active active = ACTIVE.get(player.getUUID());
		Entity attacker = source.getEntity();
		Entity direct = source.getDirectEntity();
		if (active == null || active.moveset == null || direct == null
				|| !active.attack.isParrying(seconds(player, active), active.moveset.parryRate())) {
			return false;
		}
		// The user's rules (2026-11-01): a parry blocks projectiles too; the ravager's hits go through it.
		boolean projectile = direct instanceof net.minecraft.world.entity.projectile.Projectile;
		if (!projectile && (attacker != direct || attacker instanceof net.minecraft.world.entity.monster.Ravager)) {
			return false;
		}
		double yaw = Math.toRadians(player.getYRot());
		double dx = direct.getX() - player.getX();
		double dz = direct.getZ() - player.getZ();
		double dy = (projectile ? direct.getY() : direct.getEyeY()) - player.getEyeY();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		boolean parried = horizontal < 1e-6;
		if (!parried) {
			double cos = (dx * -Math.sin(yaw) + dz * Math.cos(yaw)) / horizontal;
			double angle = Math.acos(Math.max(-1, Math.min(1, cos)));
			parried = angle <= PARRY_ANGLES[0] && Math.abs(Math.atan2(dy, horizontal)) <= PARRY_ANGLES[1];
		}
		if (!parried) {
			return false;
		}
		if (projectile) {
			// Blocked: the projectile is gone and the shooter is not affected.
			direct.discard();
			return true;
		}
		onParried(player, attacker);
		return true;
	}

	/** A dodge cut the attack (or guard) short. */
	public static void cancel(ServerPlayer player) {
		ACTIVE.remove(player.getUUID());
	}

	public static void forget(ServerPlayer player) {
		ACTIVE.remove(player.getUUID());
	}

	/**
	 * Death and respawn: the attack in progress ends (a plunge waiting to land would otherwise carry over to the new
	 * player entity and keep it locked) and the clients drop it too.
	 */
	public static void reset(ServerPlayer player) {
		ACTIVE.remove(player.getUUID());
		broadcast(player, new AttackPayloads.Stop(player.getId()));
	}

	private static long key(int window, Entity target) {
		return (long) window << 32 | (target.getId() & 0xffffffffL);
	}

	private static float seconds(ServerPlayer player, Active active) {
		return (player.serverLevel().getGameTime() - active.startTick) / 20f;
	}
}
