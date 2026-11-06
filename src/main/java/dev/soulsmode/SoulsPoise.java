package dev.soulsmode;

import dev.soulsmode.ds2.Poise;
import dev.soulsmode.network.PoisePayloads;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The player's poise and own hit reaction (docs/research/poise.md):
 * <ul>
 * <li>Maximum (FUN_140145a40): the armour's DS2 rows (ArmorParam +0x40, lack-of-stats penalty; the mod's material ->
 * DS2 set of equipload.md) + PSPL +0x90 at min(Endurance, Adaptability) + ChrParam 100 +0x118. Rings / SpEffects that
 * add to it (b8+0x305 / +0x3a0) do not exist in Minecraft.</li>
 * <li>Regeneration (FUN_140145ba0): ChrParam +0x11c per s outside the reaction's DamageDuration; a reaction that ends
 * with poise at 0 refills it.</li>
 * <li>A hit (FUN_140137aa0 / FUN_140136570): poise -= DamageCtrlParam +0xa0 (x WeaponParam +0xb8 of the victim's own
 * swing inside its TAE 111900); then TAE 110600, or 110500 unless the rule is 2, gives no reaction; a kick gives
 * reaction 2; poise above 0 (rule 0, or rule 1 inside 111900) gives reaction 1; otherwise the hit's reaction.</li>
 * </ul>
 * Reaction 1 (s.6): the upper-body overlay on the clients and x0.7 movement for 1.1 s; it cuts nothing. Reactions 2 / 3
 * (s.7): BT_Damage DmgKnockBack, which cuts the current action and locks attack / guard, dodge and walking by its
 * clip's tracks at the hit's speed.
 */
public final class SoulsPoise {
	/** Game-feel switch (CLAUDE.md rule 2): on so it can be playtested. */
	public static final boolean ENABLED = true;

	private static final net.minecraft.resources.ResourceLocation SLOW_ID = SoulsMode.id("poise_hold_slow");
	/** DmgKnockBack Light / Heavy (hit_reaction.md s.2; poise.json staggerClips). */
	private static final int LIGHT_CLIP = 160010;
	private static final int HEAVY_CLIP = 160050;
	/** TAE 101100 of the DmgKnockBack clips: no stamina regeneration (hit_reaction.md s.2). */
	private static final float NO_REGEN_UNTIL = 0.2667f;
	/** The Blend2 on DmgDistance of BT_Damage 0x1923: Light at 0, Heavy at 5. */
	private static final float HEAVY_AT = 5;
	/** DamageCtrlParam +0x10 of a kick (FUN_140136570 turns it into reaction 2 against a target not guarding). */
	private static final int KICK = 10;

	private static final class State {
		float poise = Float.NaN;
		boolean wasReacting;
		/** Tick the DamageDuration (b8+0x7e6) of the reaction ends. */
		long reactingUntil;
		/** Reaction 1: tick the x0.7 movement ends (CCP+0x144 for b8+0x724 = 1.1 s). */
		long slowUntil;
		/** BT_Damage: ticks the attack / guard, dodge and walking locks end, and the stamina regen block. */
		long attackUntil;
		long avoidUntil;
		long moveUntil;
		long noRegenUntil;
		/** DmgDistance base of the reaction playing (+0x540) and what gives its root motion (+0x580). */
		float base;
		dev.soulsmode.ds2.ReactionBlend.Lower lower;
		long start;
		float speed;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private SoulsPoise() {
	}

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), u -> new State());
	}

	/** The mod's armour material -> poise.json's set name (equipload.md: netherite counts as diamond). */
	private static String material(Holder<ArmorMaterial> material) {
		if (material.equals(ArmorMaterials.LEATHER)) {
			return "leather";
		}
		if (material.equals(ArmorMaterials.CHAIN)) {
			return "chainmail";
		}
		if (material.equals(ArmorMaterials.IRON)) {
			return "iron";
		}
		if (material.equals(ArmorMaterials.GOLD)) {
			return "gold";
		}
		if (material.equals(ArmorMaterials.DIAMOND)) {
			return "diamond";
		}
		if (material.equals(ArmorMaterials.NETHERITE)) {
			return "netherite";
		}
		if (material.equals(ArmorMaterials.TURTLE)) {
			return "turtle";
		}
		return null;
	}

	/** FUN_140145a40's maximum for this player. */
	public static float max(ServerPlayer player) {
		var stats = SoulsPlayer.data(player).map(SoulsData::toAttributes).orElse(null);
		if (stats == null) {
			return Poise.base();
		}
		float armour = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
				EquipmentSlot.FEET}) {
			ItemStack stack = player.getItemBySlot(slot);
			if (!(stack.getItem() instanceof ArmorItem item)) {
				continue;
			}
			String set = material(item.getMaterial());
			String piece = switch (item.getType()) {
				case HELMET -> "helmet";
				case CHESTPLATE -> "chestplate";
				case LEGGINGS -> "leggings";
				case BOOTS -> "boots";
				default -> null;
			};
			Poise.Piece row = set == null || piece == null ? null : Poise.piece(set, piece);
			if (row != null) {
				armour += Poise.piecePoise(row, stats);
			}
		}
		return armour + Poise.statPoise(stats) + Poise.base();
	}

	/** The current poise (for /souls status and tests); the maximum before the first tick. */
	public static float current(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		return s == null || Float.isNaN(s.poise) ? max(player) : s.poise;
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!ENABLED || !SoulsGameMode.isActive(player) || !player.isAlive()) {
				STATES.remove(player.getUUID());
				continue;
			}
			State s = state(player);
			long now = player.serverLevel().getGameTime();
			float max = max(player);
			if (Float.isNaN(s.poise)) {
				s.poise = max;
			}
			// A lower maximum cuts the current poise (it never raises it).
			s.poise = Math.min(s.poise, max);
			boolean reacting = now < s.reactingUntil;
			if (!reacting) {
				if (s.wasReacting && s.poise <= 0) {
					s.poise = max;
				} else {
					s.poise = Math.min(max, s.poise + Poise.regenPerSecond() / 20f);
				}
			}
			s.wasReacting = reacting;
			if (s.slowUntil != 0 && now >= s.slowUntil) {
				s.slowUntil = 0;
				var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
				if (speed != null) {
					speed.removeModifier(SLOW_ID);
				}
			}
		}
	}

	/**
	 * A hit that took the player's health (category 1, not guarded): {@code hit} is its damage row with its
	 * DamageCtrlParam (poise.json), {@code attacker} gives DmgDirection and the DmgDistance falloff.
	 */
	public static void onHit(ServerPlayer player, Poise.Hit hit, Entity attacker) {
		// A backstab / riposte victim is held by its own critical sequence.
		if (!ENABLED || hit == null || attacker == null || !SoulsGameMode.isActive(player) || !player.isAlive()
				|| SoulsAttack.isVictim(player)) {
			return;
		}
		State s = state(player);
		if (Float.isNaN(s.poise)) {
			s.poise = max(player);
		}
		// The clip the player is in (an attack, a cast, a dodge) for the TAE windows.
		SoulsAttack.Clip clip = SoulsAttack.currentClip(player);
		if (clip == null) {
			clip = SoulsRoll.currentClip(player);
		}
		// b8+0x7d0: WeaponParam +0xb8 of the swinging weapon while its TAE 111900 runs (FUN_14034e6f0).
		float factor = clip != null && clip.weapon() != null && Poise.poiseFactorWindow(clip.taeId(), clip.t())
				? Poise.attackPoiseFactor(clip.weapon()) : 0;
		float damage = hit.poiseDamage() * (factor > 0 ? factor : 1);
		s.poise = Math.max(0, Math.min(max(player), s.poise - damage));
		boolean withPoise = s.poise > 0;
		if (hit.rule() == 2 || hit.rule() == 1 && factor <= 0) {
			withPoise = false;
		}
		boolean superArmor = clip != null && (Poise.noReaction(clip.taeId(), clip.t())
				|| Poise.superArmor(clip.taeId(), clip.t()) && hit.rule() != 2);
		int reaction = superArmor ? 0 : hit.reaction() == KICK ? 2 : withPoise ? 1 : hit.remapped();
		if (Boolean.getBoolean("soulsmode.qa")) {
			SoulsMode.LOGGER.info("[BSE QA] poise hit {} -> {} / {} (rule {}, factor {}, clip {}) reaction {}", damage,
					s.poise, max(player), hit.rule(), factor, clip == null ? "-" : clip.taeId() + "@" + clip.t(), reaction);
		}
		if (reaction == 1) {
			held(player, s, hit, attacker);
		} else if (reaction == 2 || reaction == 3) {
			knockBack(player, s, reaction, hit, attacker);
		}
		// Reactions 4, 8, 23 and others: not ported yet (poise.md s.7; backlog).
	}

	/** Reaction 1 (FUN_14036c310(..., 4, 0)): x0.7 movement for b8+0x724 = 1.1 s and the overlay on the clients. */
	private static void held(ServerPlayer player, State s, Poise.Hit hit, Entity attacker) {
		long now = player.serverLevel().getGameTime();
		var r1 = Poise.reaction1();
		float lasting = r1.get("rampIn").getAsFloat() + r1.get("rampOut").getAsFloat();
		var factors = r1.getAsJsonArray("moveSpeedFactors");
		float factor = factors.get(factors.size() - 1).getAsFloat();
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SLOW_ID);
			speed.addTransientModifier(new AttributeModifier(SLOW_ID, factor - 1,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		s.slowUntil = now + Math.round(lasting * 20);
		// The overlay's clip lasts DmgDuration = 1 / b8+0x724 s (ScaleToDuration 0x5e5); DmgDistance is 0 (b8+0x720).
		float clip = r1.getAsJsonObject("overlay").get("clipDuration").getAsFloat();
		broadcast(player, new PoisePayloads.Reaction(player.getId(), PoisePayloads.HELD, 30, hit.thrust() == 1,
				hit.downUp() != 0, direction(player, attacker), 0, clip / r1.get("dmgDuration").getAsFloat(),
				player.getYRot(), 0));
	}

	/**
	 * Reactions 2 / 3: BT_Damage's DmgKnockBack (DmgCategory 30 / 40) at the hit's speed. It cuts the current action
	 * (message 16 from any state); the locks are its clip's CancelControl tracks / v, walking until the exit with the
	 * stick held (StateControl_1) or the Heavy branch's CancelControl_Move.
	 */
	private static void knockBack(ServerPlayer player, State s, int reaction, Poise.Hit hit, Entity attacker) {
		long now = player.serverLevel().getGameTime();
		// v = row +0x2c (FUN_1403807c0; the first hit of a reaction adds nothing).
		float v = hit.speed() > 0 ? hit.speed() : 1;
		// DmgDistance = row +0x28 x (1 + 0.01 DCP +0xbc t), t = 0..1 of the distance between DCP +0xa4 and +0xa8.
		double d = attacker.position().distanceTo(player.position());
		float t = d <= hit.near() ? 0 : d >= hit.far() || hit.far() <= hit.near() ? 1
				: (float) ((d - hit.near()) / (hit.far() - hit.near()));
		float falloff = Float.isNaN(hit.falloff()) ? 0 : hit.falloff();
		// FUN_14013b4d0: hit again inside the reaction, + clamp(previous base - |root motion so far|, 0, 9999.9).
		float distance = (Float.isNaN(hit.distance()) ? 0 : hit.distance()) * (1 + 0.01f * falloff * t);
		if (now < s.reactingUntil && s.lower != null) {
			float clipTime = Math.min(dev.soulsmode.ds2.ReactionBlend.C0001_LENGTH, (now - s.start) / 20f * s.speed);
			float[] moved = dev.soulsmode.ds2.ReactionBlend.delta(s.lower, 0, clipTime);
			distance += Math.max(0, Math.min(9999.9f, s.base - (float) Math.hypot(moved[0], moved[1])));
		}
		float heavy = Math.max(0, Math.min(1, distance / HEAVY_AT));
		Poise.StaggerClip light = Poise.staggerClip(LIGHT_CLIP);
		Poise.StaggerClip heavyClip = Poise.staggerClip(HEAVY_CLIP);
		float attack = Math.max(light.end("CancelControl_AttackAll"), light.end("CancelControl_Guard"));
		float avoid = light.end("CancelControl_Avoid");
		float[] exitMoving = light.tracks().get("StateControl_1");
		float move = Math.max(exitMoving == null ? 0 : exitMoving[0],
				heavy > 0 ? heavyClip.end("CancelControl_Move") : 0);
		s.attackUntil = now + Math.round(attack / v * 20);
		s.avoidUntil = now + Math.round(avoid / v * 20);
		s.moveUntil = now + Math.round(move / v * 20);
		s.noRegenUntil = now + Math.round(NO_REGEN_UNTIL / v * 20);
		s.reactingUntil = now + Math.round(light.end("DamageDuration") / v * 20);
		s.base = distance;
		s.start = now;
		s.speed = v;
		// The reaction cuts what the player was doing.
		SoulsAttack.reset(player);
		SoulsRoll.forget(player);
		float angle = direction(player, attacker);
		// DmgDownOrUp mode (row +0x43): 0 down, 1 up, 2 by the hit height, taken as up like the mobs' (hit_reaction.md).
		boolean up = hit.downUp() != 0;
		boolean thrust = hit.thrust() == 1;
		int x = 1 + (thrust ? 2 : 0) + (up ? 1 : 0);
		s.lower = dev.soulsmode.ds2.ReactionBlend.c0001Lower(x, angle, heavy);
		broadcast(player, new PoisePayloads.Reaction(player.getId(), PoisePayloads.KNOCKBACK, reaction == 3 ? 40 : 30,
				thrust, up, angle, heavy, v, player.getYRot(), move / v));
	}

	/**
	 * DmgDirection (FUN_14031f8d0; a player keeps the full signed angle, FUN_140203c10): degrees from the victim's
	 * facing to the attacker, positive = attacker on its right.
	 */
	private static float direction(ServerPlayer player, Entity attacker) {
		double yaw = Math.toRadians(player.getYRot());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double vx = attacker.getX() - player.getX();
		double vz = attacker.getZ() - player.getZ();
		// Minecraft's right of a facing (-sin, cos) is (-cos, -sin).
		double side = -Math.cos(yaw) * vx - Math.sin(yaw) * vz;
		double cross = fx * vz - fz * vx;
		return (float) (Math.toDegrees(Math.atan2(Math.abs(cross), fx * vx + fz * vz)) * (side < 0 ? -1 : 1));
	}

	/** CancelControl_AttackAll / _Guard of the reaction playing. */
	public static boolean blocksAttack(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		return s != null && player.serverLevel().getGameTime() < s.attackUntil;
	}

	/** CancelControl_Avoid of the reaction playing. */
	public static boolean blocksDodge(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		return s != null && player.serverLevel().getGameTime() < s.avoidUntil;
	}

	/** TAE 101100 of the reaction clip. */
	public static boolean blocksRegen(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		return s != null && player.serverLevel().getGameTime() < s.noRegenUntil;
	}

	/** Death, respawn, leaving: nothing carries over. */
	public static void forget(ServerPlayer player) {
		STATES.remove(player.getUUID());
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SLOW_ID);
		}
	}

	private static void broadcast(ServerPlayer player, PoisePayloads.Reaction payload) {
		ServerPlayNetworking.send(player, payload);
		for (ServerPlayer other : PlayerLookup.tracking(player)) {
			if (other != player) {
				ServerPlayNetworking.send(other, payload);
			}
		}
	}
}
