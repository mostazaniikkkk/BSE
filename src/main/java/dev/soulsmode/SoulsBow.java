package dev.soulsmode;

import dev.soulsmode.api.SoulsWeapons;
import dev.soulsmode.ds2.Attack;
import dev.soulsmode.ds2.Bow;
import dev.soulsmode.entity.SoulsBullet;
import dev.soulsmode.network.AttackPayloads;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * DS2's bow and crossbow on the server (docs/research/bow.md, crossbow.md; data in {@link Bow}):
 * <ul>
 * <li>The bow fires only two-handed (its one-handed slots are motion type 15; its kick and jump still attack). The
 * crossbow also fires one-handed from either hand (type 10). With either one two-handed, the weapon hand's buttons
 * fire (the heavy one only picks the second ammo slot) and the other hand's only aim (the client's camera).</li>
 * <li>A press starts the grip's Prepare; ShootStateControl charges the stamina. With the button still held when the
 * Prepare ends, the CF follows; released, the weapon stays drawn until the next press (the reading of conditions
 * 0x262 / 0x270, not confirmed in game).</li>
 * <li>The CF's TAE 2300 takes the ammo (arrows for the bow, bolts for the crossbow; ArrowParam +0x56) and spawns the
 * grip's bullet from the weapon's dummy 200 towards the aim (the camera ray, 100 m) or the facing, with the weapon's
 * attack rating; homing on the lock-on target.</li>
 * <li>No ammo at the press: the grip's NoBullet clip (when DS2 checks the ammo is not confirmed).</li>
 * </ul>
 * How the weapon's and the ammo's attack values combine is not traced: the shot carries the weapon's attack rating and
 * the bullet's damage row (with its flat damage).
 */
public final class SoulsBow {
	private enum Phase { PREPARE, HOLD, CF }

	private static final class Shot {
		final Bow weapon;
		final Bow.Shot data;
		final boolean weaponLeft;
		final boolean twoHanded;
		final boolean aiming;
		Phase phase = Phase.PREPARE;
		long start;
		boolean released;
		boolean charged;
		boolean fired;

		Shot(Bow weapon, Bow.Shot data, boolean weaponLeft, boolean twoHanded, boolean aiming, long start) {
			this.weapon = weapon;
			this.data = data;
			this.weaponLeft = weaponLeft;
			this.twoHanded = twoHanded;
			this.aiming = aiming;
			this.start = start;
		}
	}

	private static final Map<UUID, Shot> SHOTS = new HashMap<>();
	private static final Set<UUID> AIMING = new HashSet<>();

	private SoulsBow() {
	}

	/** The ranged weapon in a hand, or null. */
	private static Bow ranged(ServerPlayer player, boolean left) {
		return Bow.of(SoulsAttack.damageKey(SoulsWeapons.movesetFor(left ? player.getOffhandItem()
				: player.getMainHandItem())));
	}

	/** The id of a ranged clip in an AttackPayloads.Start (SoulsAttack.itemUse resolves it). */
	public static ResourceLocation clipId(Attack clip) {
		return SoulsMode.id("ranged_" + clip.taeId());
	}

	/** A ranged clip by its Start id, or null. */
	public static Attack clip(ResourceLocation id) {
		if (!id.getPath().startsWith("ranged_")) {
			return null;
		}
		int tae = Integer.parseInt(id.getPath().substring(7));
		Attack a = Bow.BOW.clip(tae);
		return a != null ? a : Bow.CROSSBOW.clip(tae);
	}

	/**
	 * An attack request with a ranged weapon in hand: true when the weapon handles it (fires, or does nothing). The
	 * other hand's buttons with the weapon two-handed only aim, which is the client's camera: also handled.
	 */
	public static boolean handles(ServerPlayer player, AttackPayloads.Request request) {
		var grip = SoulsAttack.grip(player);
		if (grip.twoHanded()) {
			Bow held = ranged(player, grip.leftWeapon());
			if (held == null) {
				return false;
			}
			if (request.leftHand() == grip.leftWeapon()) {
				press(player, held, held.shot(Bow.TWO_HAND), grip.leftWeapon(), true);
			}
			return true;
		}
		Bow held = ranged(player, request.leftHand());
		if (held == null) {
			return false;
		}
		if (held.oneHanded) {
			press(player, held, held.shot(request.leftHand() ? Bow.ONE_HAND_LEFT : Bow.ONE_HAND_RIGHT), request.leftHand(),
					false);
			return true;
		}
		// The bow one-handed (type 15): nothing, except its kick and jump (the fist's rows), through the normal path.
		var moveset = SoulsWeapons.moveset(SoulsWeapons.movesetFor(request.leftHand() ? player.getOffhandItem()
				: player.getMainHandItem())).orElse(null);
		var movement = moveset == null ? null : SoulsAttack.movementType(player, moveset, request.leftHand(), false, request);
		return movement != dev.soulsmode.ds2.Moveset.Type.KICK && movement != dev.soulsmode.ds2.Moveset.Type.JUMP;
	}

	/** The client's aim mode (BowAimPayload): the shot follows the camera ray while it lasts. */
	public static void setAiming(ServerPlayer player, boolean aiming) {
		if (aiming) {
			AIMING.add(player.getUUID());
		} else {
			AIMING.remove(player.getUUID());
		}
	}

	private static void press(ServerPlayer player, Bow weapon, Bow.Shot data, boolean weaponLeft, boolean twoHanded) {
		if (data == null) {
			return;
		}
		long now = player.serverLevel().getGameTime();
		Shot shot = SHOTS.get(player.getUUID());
		if (shot != null && shot.phase == Phase.HOLD) {
			startCf(player, shot, now);
			return;
		}
		if (shot != null) {
			return;
		}
		if (!player.getAbilities().instabuild && findAmmo(player, weapon).isEmpty()) {
			SoulsAttack.startBowAction(player, clipId(data.noBullet()), data.noBullet(), weaponLeft, twoHanded);
			return;
		}
		if (!SoulsAttack.startBowAction(player, clipId(data.prepare()), data.prepare(), weaponLeft, twoHanded)) {
			return;
		}
		SHOTS.put(player.getUUID(), new Shot(weapon, data, weaponLeft, twoHanded,
				twoHanded && AIMING.contains(player.getUUID()), now));
	}

	private static void startCf(ServerPlayer player, Shot shot, long now) {
		if (!SoulsAttack.startBowAction(player, clipId(shot.data.cf()), shot.data.cf(), shot.weaponLeft, shot.twoHanded)) {
			return;
		}
		shot.phase = Phase.CF;
		shot.start = now;
	}

	/** The fire button was released. */
	public static void onRelease(ServerPlayer player) {
		Shot shot = SHOTS.get(player.getUUID());
		if (shot != null) {
			shot.released = true;
		}
	}

	public static void tick(MinecraftServer server) {
		SHOTS.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Shot shot = entry.getValue();
			if (player == null || !player.isAlive() || !SoulsGameMode.isActive(player)
					|| ranged(player, shot.weaponLeft) != shot.weapon) {
				return true;
			}
			long now = player.serverLevel().getGameTime();
			float t = (now - shot.start) / 20f;
			switch (shot.phase) {
				case PREPARE -> {
					if (t >= shot.data.prepare().duration()) {
						if (shot.released) {
							shot.phase = Phase.HOLD;
						} else {
							startCf(player, shot, now);
						}
						return false;
					}
					if (!SoulsAttack.isDoing(player, shot.data.prepare())) {
						// Cut by a dodge or anything else before its end.
						return true;
					}
					if (!shot.charged && t >= shot.data.shootAt()) {
						shot.charged = true;
						SoulsStamina.spend(player, shot.data.staminaCost());
					}
				}
				case HOLD -> {
					// Drawn: the next press fires (press()); a dodge or another action ends it.
					if (SoulsAttack.isActing(player)) {
						return true;
					}
				}
				case CF -> {
					if (!shot.fired && t >= shot.data.bulletAt()) {
						shot.fired = true;
						fire(player, shot);
					}
					if (t >= shot.data.cf().duration() || !SoulsAttack.isDoing(player, shot.data.cf())) {
						return true;
					}
				}
			}
			return false;
		});
	}

	/** Arrows for the bow, bolts (vanilla's arrows too, as Minecraft's crossbow uses them) for the crossbow. */
	private static ItemStack findAmmo(ServerPlayer player, Bow weapon) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(ItemTags.ARROWS) && (weapon == Bow.BOW || !s.is(Items.FIREWORK_ROCKET))) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	private static void fire(ServerPlayer player, Shot shot) {
		ItemStack ammo = findAmmo(player, shot.weapon);
		if (ammo.isEmpty() && !player.getAbilities().instabuild) {
			return;
		}
		if (!player.getAbilities().instabuild) {
			ammo.shrink(1);
		}
		ItemStack held = shot.weaponLeft ? player.getOffhandItem() : player.getMainHandItem();
		var stats = SoulsPlayer.data(player).map(SoulsData::toAttributes).orElse(null);
		float[] attack = stats == null ? new float[6] : dev.soulsmode.ds2.Ds2Damage.attackRating(shot.weapon.weapon,
				SoulsAttack.upgradeLevel(held), SoulsUpgrade.infusion(held), stats, shot.twoHanded, shot.weaponLeft, -1, 0,
				null, null);
		// Model space (left, up, forward) to the world along the body's facing.
		float[] o = shot.data.origin(shot.weaponLeft);
		double yaw = Math.toRadians(player.getYRot());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double lx = Math.cos(yaw);
		double lz = Math.sin(yaw);
		Vec3 pos = player.position().add(o[0] * lx + o[2] * fx, o[1], o[0] * lz + o[2] * fz);
		Vec3 look = player.getLookAngle();
		Vec3 dir = look;
		if (shot.aiming) {
			// While aiming the shot goes to what the camera ray (eye 1.5 m up, 0.4 m forward) hits within 100 m.
			Bow w = shot.weapon;
			Vec3 eye = player.position().add(fx * w.aimEyeForward(), w.aimEyeUp(), fz * w.aimEyeForward());
			Vec3 end = eye.add(look.scale(w.aimRayLength()));
			HitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
					player));
			Vec3 aim = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
			dir = aim.subtract(pos).normalize();
		}
		Vec3 from = pos;
		Vec3 forward = dir;
		SoulsBullet.emit(player.serverLevel(), player, shot.weapon.bullets().get(shot.data.bullet()), shot.weapon.bullets(),
				() -> new SoulsBullet.Frame(from, forward), attack, shot.weapon.weapon, SoulsMagic.lockTarget(player), -1,
				null);
	}

	public static void forget(ServerPlayer player) {
		SHOTS.remove(player.getUUID());
		AIMING.remove(player.getUUID());
	}
}
