package dev.soulsmode.entity;

import dev.soulsmode.SoulsAttack;
import dev.soulsmode.ds2.Bullets;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A DS2 spell bullet (docs/research/casting.md sections 3 and 7), simulated on the server only and never saved:
 * {@link Bullets.Motion} moves it; each step its swept volume hits characters once per bullet (the per-attack hit
 * history) and blocks; contact byte +0xb0 (characters) / +0xb1 (blocks) = 0 destroys it; child bullets are created on
 * hit (+0xec) and on expiry (+0xed) at its position, with its owner, target and attack values. Who it can hit:
 * PlayerDamageParam +0x40 (the owner, allies, hostiles); the hit deals the spell's damage and applies the damage row's
 * SpEffect (SoulsSpEffects).
 * <p>
 * Not ported: the capsule length (its axis is not confirmed, the swept sphere of the radius is used), per-material
 * contact bytes +0xb1..+0xb3 (the block byte is +0xb1 for every block), the distance falloff and bouncing.
 */
public class SoulsBullet extends Entity {
	private ServerPlayer owner;
	private Bullets.Def def;
	private Map<String, Bullets.Def> bullets;
	private Bullets.Motion motion;
	private float[] attack;
	private String catalystKey;
	private Entity target;
	private net.minecraft.resources.ResourceLocation spell;
	private ParticleOptions particle;
	/** BulletAttachMotion (+0x05 = 2): its offset from the owner, kept while the owner moves. */
	private Vec3 attached;
	private final Set<Integer> hit = new HashSet<>();

	public SoulsBullet(EntityType<? extends SoulsBullet> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	/** Where an emitter shoots from: a point and its forward; the frame is built with world up. */
	public record Frame(Vec3 pos, Vec3 forward) {
	}

	/** An emitter with shots left (FUN_14043f3b0): the next one fires when {@code wait} reaches 0. */
	private record Emitter(ServerLevel level, ServerPlayer owner, Bullets.Def def, Map<String, Bullets.Def> bullets,
			java.util.function.Supplier<Frame> frame, float[] attack, String catalystKey, Entity target,
			float homingRange, net.minecraft.resources.ResourceLocation spell, int[] state) {
	}

	private static final java.util.List<Emitter> EMITTERS = new java.util.ArrayList<>();
	private static final java.util.Random RANDOM = new java.util.Random();

	/**
	 * Emits a bullet row (BulletEmitterCtrl, casting.md section 8): max(1, +0x2f) shots of +0x2e bullets, the first at
	 * once and then every +0x38 s (the timer reloads without carrying the leftover, so shots snap to ticks). The
	 * emitter follows its source: {@code frame} is asked again for every shot.
	 */
	public static void emit(ServerLevel level, ServerPlayer owner, Bullets.Def def, Map<String, Bullets.Def> bullets,
			java.util.function.Supplier<Frame> frame, float[] attack, String catalystKey, Entity target, float homingRange,
			net.minecraft.resources.ResourceLocation spell) {
		Emitter e = new Emitter(level, owner, def, bullets, frame, attack, catalystKey, target, homingRange, spell,
				new int[] {0, 0});
		shoot(e);
		if (def.shotCount() > 1) {
			EMITTERS.add(e);
		}
	}

	public static void tickEmitters(net.minecraft.server.MinecraftServer server) {
		EMITTERS.removeIf(e -> {
			if (e.owner().isRemoved()) {
				return true;
			}
			if (--e.state()[1] > 0) {
				return false;
			}
			shoot(e);
			return e.state()[0] >= e.def().shotCount();
		});
	}

	/** Shot n: bullet i goes along the fan direction, turned towards the target and spread (FUN_1404404a0). */
	private static void shoot(Emitter e) {
		int n = e.state()[0]++;
		e.state()[1] = Math.max(1, Math.round(e.def().interval() * 20));
		Frame f = e.frame().get();
		if (f == null) {
			return;
		}
		Vec3 z = f.forward().normalize();
		Vec3 x = new Vec3(0, 1, 0).cross(z);
		x = x.lengthSqr() < 1e-9 ? new Vec3(1, 0, 0) : x.normalize();
		Vec3 y = z.cross(x);
		Bullets.Def d = e.def();
		// Origin offset in the emitter frame: +0x0c along +X, +0x08 up, +0x10 forward.
		Vec3 pos = f.pos().add(x.scale(d.sideOffset())).add(y.scale(d.upOffset())).add(z.scale(d.forwardOffset()));
		for (int i = 0; i < d.perShot(); i++) {
			double[] l = d.fanDirection(i, n);
			Vec3 dir = x.scale(l[0]).add(y.scale(l[1])).add(z.scale(l[2]));
			if (e.target() != null && e.target().isAlive()) {
				dir = aim(dir, pos, e.target(), d);
			}
			dir = turn(dir, (RANDOM.nextDouble() * 2 - 1) * d.spread()[1], (RANDOM.nextDouble() * 2 - 1) * d.spread()[0]);
			spawn(e.level(), e.owner(), d, e.bullets(), pos, dir, e.attack(), e.catalystKey(), e.target(),
					e.homingRange(), e.spell());
		}
	}

	/** casting.md section 7.2: the target's velocity used to lead it is capped at 8. */
	private static final double LEAD_SPEED_CAP = 8;

	/**
	 * FUN_14043fc40: the bullet turned towards where the target will be (distance / speed ahead; +0x2c height, +0x2d
	 * horizontal), at most +0x1c sideways and +0x20 in pitch.
	 */
	private static Vec3 aim(Vec3 dir, Vec3 pos, Entity target, Bullets.Def d) {
		if (d.aimYaw() <= 0 && d.aimPitch() <= 0) {
			return dir;
		}
		Vec3 point = new Vec3(target.getX(), target.getY(0.5), target.getZ());
		Vec3 v = target.getDeltaMovement().scale(20);
		if (v.length() > LEAD_SPEED_CAP) {
			v = v.normalize().scale(LEAD_SPEED_CAP);
		}
		double ahead = d.speed() > 0 ? point.distanceTo(pos) / d.speed() : 0;
		point = new Vec3(d.leadHorizontal() ? point.x + v.x * ahead : point.x, d.leadHeight() ? point.y + v.y * ahead : point.y,
				d.leadHorizontal() ? point.z + v.z * ahead : point.z);
		Vec3 to = point.subtract(pos);
		double yaw = Math.atan2(dir.x, dir.z);
		double pitch = Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z));
		double dyaw = Math.atan2(Math.sin(Math.atan2(to.x, to.z) - yaw), Math.cos(Math.atan2(to.x, to.z) - yaw));
		double dpitch = Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) - pitch;
		return turn(dir, Math.max(-d.aimYaw(), Math.min(d.aimYaw(), dyaw)), Math.max(-d.aimPitch(), Math.min(d.aimPitch(), dpitch)));
	}

	/** Turns a direction by {@code dyaw} about world up and {@code dpitch} up / down. */
	private static Vec3 turn(Vec3 dir, double dyaw, double dpitch) {
		double yaw = Math.atan2(dir.x, dir.z) + dyaw;
		double pitch = Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) + dpitch;
		pitch = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, pitch));
		return new Vec3(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
	}

	/** A bullet of a spell cast by {@code owner}; {@code homingRange} < 0 = no limit. */
	public static SoulsBullet spawn(ServerLevel level, ServerPlayer owner, Bullets.Def def, Map<String, Bullets.Def> bullets,
			Vec3 pos, Vec3 dir, float[] attack, String catalystKey, Entity target, float homingRange,
			net.minecraft.resources.ResourceLocation spell) {
		SoulsBullet b = new SoulsBullet(SoulsEntities.BULLET, level);
		b.owner = owner;
		b.def = def;
		b.bullets = bullets;
		b.attack = attack;
		b.catalystKey = catalystKey;
		b.target = target;
		b.spell = spell;
		b.particle = dev.soulsmode.content.SoulsContent.spellParticle(spell);
		b.entityData.set(ARROW, dev.soulsmode.ds2.Bow.of(catalystKey) != null);
		if (def.directionMode() == 2) {
			b.attached = pos.subtract(owner.position());
		}
		b.motion = new Bullets.Motion(def, pos.x, pos.y, pos.z, dir.x, dir.y, dir.z, homingRange);
		b.setPos(pos);
		level.addFreshEntity(b);
		return b;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			return;
		}
		if (motion == null || owner == null || owner.isRemoved()) {
			discard();
			return;
		}
		Vec3 from = position();
		double[] aim = target != null && target.isAlive() ? new double[] {target.getX(), target.getY(0.5), target.getZ()}
				: null;
		motion.step(0.05f, aim);
		if (attached != null) {
			// Attached to the caster: it moves with it (BulletAttachMotion, casting.md section 3).
			Vec3 at = owner.position().add(attached);
			motion.x = at.x;
			motion.y = at.y;
			motion.z = at.z;
		}
		Vec3 to = new Vec3(motion.x, motion.y, motion.z);
		ServerLevel level = (ServerLevel) level();
		float radius = def.damage() == null ? 0 : def.damage().radiusAt(motion.t);
		if (def.damage() != null && hitCharacters(level, from, to, radius)) {
			return;
		}
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (block.getType() == HitResult.Type.BLOCK && def.contact()[1] == 0) {
			setPos(block.getLocation());
			end(level, def.childrenOnHit());
			return;
		}
		setPos(to);
		if (particle != null) {
			level.sendParticles(particle, to.x, to.y, to.z, 2, 0.05, 0.05, 0.05, 0);
		}
		if (def.life() >= 0 && motion.t >= def.life()) {
			end(level, def.childrenOnExpiry());
		}
	}

	/** True when the bullet was destroyed. */
	private boolean hitCharacters(ServerLevel level, Vec3 from, Vec3 to, float radius) {
		AABB area = new AABB(from, to).inflate(radius + 1);
		for (Entity e : level.getEntities(this, area)) {
			if (!(e instanceof LivingEntity victim) || !victim.isAlive() || hit.contains(e.getId())
					|| !def.damage().hits(e == owner, e != owner && ally(e))) {
				continue;
			}
			AABB box = e.getBoundingBox().inflate(radius);
			if (!box.contains(from) && box.clip(from, to).isEmpty()) {
				continue;
			}
			hit.add(e.getId());
			// +0xb0: 0 hits and is destroyed, 1 hits and goes on, 2 goes on without damage (FUN_14013a6a0 rejects it).
			if (def.contact()[0] != 2) {
				if (e != owner && e.isAttackable()) {
					SoulsAttack.spellHit(owner, victim, catalystKey, attack, def.damage().rate(), def.damage().floor(),
							def.damage().knockBackSpeed());
				}
				dev.soulsmode.SoulsScripts.onHit(owner, spell, attack, catalystKey, e, def.id());
				// PDP +0x48 applied to whoever is hit when +0x4c bit 0 (FUN_14014be20).
				if (def.damage().spEffectOnHit() && def.damage().spEffect() > 0) {
					dev.soulsmode.SoulsSpEffects.apply(owner, victim, spell, def.damage().spEffect(), bullets, attack,
							catalystKey, def.damage().effectSlot(), def.damage().effectMode());
				}
			}
			if (def.contact()[0] == 0) {
				setPos(box.clip(from, to).orElse(from));
				end(level, def.childrenOnHit());
				return true;
			}
		}
		return false;
	}

	/**
	 * Destroys the bullet, first creating its children (FUN_140445ec0) when asked: each one an emitter at this
	 * bullet's position (origin mode 2, or 5 = the contact point, where it already is) facing along it (direction modes
	 * 0 / 3: the parent's forward, the frame rebuilt with world up) or along the owner's facing (6); with the same
	 * owner, target and attack values; homing range no limit.
	 */
	private void end(ServerLevel level, boolean children) {
		dev.soulsmode.SoulsScripts.onExpire(owner, spell, attack, catalystKey, def.id(), position());
		if (children) {
			Vec3 v = new Vec3(motion.vx, motion.vy, motion.vz);
			Vec3 forward = v.lengthSqr() > 1e-9 ? v : getLookAngle();
			Vec3 pos = position();
			for (String id : def.children()) {
				Bullets.Def child = bullets.get(id);
				Vec3 f = child.directionMode() == 6 || child.directionMode() == 1 ? ownerFacing() : forward;
				emit(level, owner, child, bullets, () -> new Frame(pos, f), attack, catalystKey, target, -1, spell);
			}
		}
		discard();
	}

	/**
	 * DS2's ally (PDP +0x40 bit 8) in Minecraft: another player the owner may not harm (no PvP or the same team), or
	 * a pet the owner tamed; everything else is hostile.
	 */
	private boolean ally(Entity e) {
		if (e instanceof Player p) {
			return !owner.canHarmPlayer(p);
		}
		return e instanceof net.minecraft.world.entity.OwnableEntity pet && owner.getUUID().equals(pet.getOwnerUUID());
	}

	private Vec3 ownerFacing() {
		double yaw = Math.toRadians(owner.getYRot());
		return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
	}

	/** An arrow from the bow (SoulsBow): clients draw it (spell bullets show as particles only). */
	private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> ARROW =
			SynchedEntityData.defineId(SoulsBullet.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

	public boolean isArrow() {
		return entityData.get(ARROW);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(ARROW, false);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	public Optional<ServerPlayer> owner() {
		return Optional.ofNullable(owner);
	}
}
