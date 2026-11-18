package dev.soulsmode.entity;

import dev.soulsmode.SoulsAttack;
import dev.soulsmode.SoulsGameMode;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.SoulsStats;
import dev.soulsmode.content.SoulsContent;
import dev.soulsmode.ds2.Enemies;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A datapack enemy (docs/datapacks.md "Enemigos", docs/research/enemies.md): the DS2 enemy engine — its HP, defence
 * and absorption against the player's hits, its attacks (damage rows on the animation's hit windows) through the
 * player victim formula, poise (no reaction while it is above 0), souls and drops on death — with the pack's model,
 * animations, attack timings and declarative AI (attack table: range, angle, weight, cooldown).
 * <p>
 * Not ported (not traced in DS2): the poise damage of each hit (so poise only matters as 0 / above 0), the second
 * gauge, enemy guarding and stamina, and the hit reaction animation chosen by category (the pack gives one).
 */
public class SoulsEnemy extends PathfinderMob implements GeoEntity {
	private static final EntityDataAccessor<String> ENEMY = SynchedEntityData.defineId(SoulsEnemy.class,
			EntityDataSerializers.STRING);
	/** The action animation playing ("" none) and a counter so the same one can restart. */
	private static final EntityDataAccessor<String> ACTION = SynchedEntityData.defineId(SoulsEnemy.class,
			EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> ACTION_SERIAL = SynchedEntityData.defineId(SoulsEnemy.class,
			EntityDataSerializers.INT);
	/** DS2's DmgCategory has no counterpart; the pack's "hurt" plays for every reaction. */
	private static final String HURT = "hurt";

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private Enemies.Def def;
	private ResourceLocation defId;
	private float poise;
	private Enemies.Attack attack;
	private String actionKind = "";
	private int actionTick;
	private int actionLength;
	private final Set<Integer> hitThisWindow = new HashSet<>();
	private int window = -1;
	/** The attack being approached (the attack subroutine), its ticks and the ticks spent turning. */
	private Enemies.Attack plan;
	private int planTicks;
	private int turnTicks;

	public SoulsEnemy(EntityType<? extends SoulsEnemy> type, Level level) {
		super(type, level);
		moveControl = new net.minecraft.world.entity.ai.control.MoveControl(this) {
			@Override
			protected float rotlerp(float from, float to, float max) {
				// Walking or running also turns at the enemy's DS2 rate.
				Enemies.Def d = def();
				return super.rotlerp(from, to, d == null ? max : Math.min(max, d.turnDegreesPerTick(horizontalSpeed())));
			}
		};
	}

	/** Blocks per second over the last tick. */
	private double horizontalSpeed() {
		return Math.hypot(getX() - xo, getZ() - zo) * 20;
	}

	public static AttributeSupplier.Builder attributes() {
		return createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.1).add(Attributes.FOLLOW_RANGE, 16);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ENEMY, "");
		builder.define(ACTION, "");
		builder.define(ACTION_SERIAL, 0);
	}

	public ResourceLocation enemyId() {
		String s = entityData.get(ENEMY);
		return s.isEmpty() ? null : ResourceLocation.tryParse(s);
	}

	/** The definition; looked up again after a datapack reload. */
	public Enemies.Def def() {
		ResourceLocation id = enemyId();
		if (id == null) {
			return null;
		}
		if (!id.equals(defId) || def != SoulsContent.enemy(id)) {
			defId = id;
			def = SoulsContent.enemy(id);
			if (def != null) {
				refreshDimensions();
			}
		}
		return def;
	}

	/** Makes this entity the given datapack enemy, full HP. */
	public void setEnemy(ResourceLocation id) {
		entityData.set(ENEMY, id.toString());
		Enemies.Def d = def();
		if (d != null) {
			getAttribute(Attributes.MAX_HEALTH).setBaseValue(d.hp() / SoulsStats.DS2_HP_PER_HEALTH);
			// Minecraft's ground physics: a mob's MoveControl feeds the speed both as input and as factor, so blocks / s
			// = attribute^2 x 43.17 (a zombie's 0.23 walks 2.28 blocks / s; a player's 0.1 with input 1 walks 4.317).
			getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Math.sqrt(d.runSpeed() / 43.17));
			getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(d.followRange());
			setHealth(getMaxHealth());
			poise = d.poise();
		}
	}

	/** The name: {@code enemy.<namespace>.<path>} from the pack's resource pack, else the generic "Enemy". */
	@Override
	protected net.minecraft.network.chat.Component getTypeName() {
		ResourceLocation id = enemyId();
		return id == null ? super.getTypeName() : net.minecraft.network.chat.Component.translatableWithFallback(
				"enemy." + id.getNamespace() + "." + id.getPath(), super.getTypeName().getString());
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		Enemies.Def d = def();
		return d == null ? super.getDefaultDimensions(pose) : EntityDimensions.scalable(d.width(), d.height());
	}

	// Server: AI, attacks, reactions (docs/research/enemy_ai.md).

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		Enemies.Def d = def();
		if (d == null || isDeadOrDying()) {
			return;
		}
		// EP +0x11c: poise regen per s outside a reaction; refilled when a reaction ends with it at 0 (enemies.md s.7.1).
		if (!actionKind.equals(HURT)) {
			poise = Math.min(d.poise(), poise + d.poiseRegen() / 20f);
		}
		if (SoulsAttack.isHeld(this)) {
			// Grabbed by a critical or parried: the engine holds it; no action.
			endAction();
			plan = null;
			return;
		}
		if (!actionKind.isEmpty()) {
			tickAction(d);
			return;
		}
		LivingEntity target = target(d);
		setTarget(target);
		if (target == null) {
			getNavigation().stop();
			plan = null;
			return;
		}
		if (plan == null) {
			// The upper logic: the pack's Lua on_think (in the ESD's place), else the declarative table (s.9.2).
			if (!dev.soulsmode.SoulsScripts.onThink(this, d.script())) {
				plan = choose(d);
				planTicks = 0;
				turnTicks = 0;
			}
		}
		if (plan != null) {
			approach(plan, target);
		} else {
			getNavigation().stop();
		}
	}

	/**
	 * Keeps the current target while it is valid and within the follow range; else the nearest valid one in sight
	 * (players and, with {@code targets.entities}, those mobs).
	 */
	private LivingEntity target(Enemies.Def d) {
		LivingEntity current = getTarget();
		double range = d.followRange();
		if (current != null && current.level() == level() && validTarget(d, current) && distanceTo(current) <= range) {
			return current;
		}
		LivingEntity best = null;
		double bestSq = range * range;
		for (Player p : level().players()) {
			double sq = distanceToSqr(p);
			if (sq <= bestSq && validTarget(d, p) && hasLineOfSight(p)) {
				best = p;
				bestSq = sq;
			}
		}
		if (!d.targets().entities().isEmpty()) {
			for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range),
					e -> !(e instanceof Player))) {
				double sq = distanceToSqr(e);
				if (sq <= bestSq && validTarget(d, e) && hasLineOfSight(e)) {
					best = e;
					bestSq = sq;
				}
			}
		}
		return best;
	}

	/** Whether {@code e} passes the pack's target filter ({@code targets}, docs/datapacks.md). */
	private boolean validTarget(Enemies.Def d, LivingEntity e) {
		if (e == this || !e.isAlive() || e instanceof SoulsEnemy) {
			return false;
		}
		if (e instanceof Player p) {
			return !p.isCreative() && !p.isSpectator() && playerTarget(d.targets(), p);
		}
		for (String s : d.targets().entities()) {
			if (s.startsWith("#") ? e.getType().is(net.minecraft.tags.TagKey.create(
					net.minecraft.core.registries.Registries.ENTITY_TYPE, ResourceLocation.parse(s.substring(1))))
					: BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(ResourceLocation.parse(s))) {
				return true;
			}
		}
		return false;
	}

	/** {@code targets.players}: off, all, or those passing the datapack predicate (as {@code execute if predicate}). */
	private boolean playerTarget(Enemies.Targets t, Player p) {
		if (!t.players()) {
			return false;
		}
		if (t.playerPredicate() == null || !(level() instanceof ServerLevel level)) {
			return true;
		}
		var condition = level.getServer().reloadableRegistries().get()
				.registry(net.minecraft.core.registries.Registries.PREDICATE)
				.flatMap(r -> r.getOptional(t.playerPredicate())).orElse(null);
		if (condition == null) {
			if (WARNED.add(t.playerPredicate())) {
				SoulsMode.LOGGER.warn("[BSE] enemy {}: predicate {} not found; no player is a target", enemyId(),
						t.playerPredicate());
			}
			return false;
		}
		var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
				.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, p.position())
				.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, p)
				.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.COMMAND);
		var context = new net.minecraft.world.level.storage.loot.LootContext.Builder(params)
				.create(java.util.Optional.empty());
		context.pushVisitedElement(net.minecraft.world.level.storage.loot.LootContext.createVisitedEntry(condition));
		return condition.test(context);
	}

	private static final Set<ResourceLocation> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** f7001: distance to the target, 9999 without one. */
	public double targetDistance() {
		LivingEntity t = getTarget();
		return t == null ? 9999 : distanceTo(t);
	}

	/** f7002: absolute angle in degrees between this enemy's front and the direction to the target. */
	public double targetAngle() {
		LivingEntity t = getTarget();
		if (t == null) {
			return 180;
		}
		return Math.abs(net.minecraft.util.Mth.wrapDegrees(yawTo(t) - yBodyRot));
	}

	/** f7007: the target's height minus this enemy's. */
	public double targetHeight() {
		LivingEntity t = getTarget();
		return t == null ? 0 : t.getY() - getY();
	}

	private float yawTo(Entity t) {
		return (float) Math.toDegrees(Math.atan2(-(t.getX() - getX()), t.getZ() - getZ()));
	}

	/**
	 * The declarative attack table, as ai102000's S2147483585 (enemy_ai.md s.9.2): the attacks whose distance rule
	 * (f7018(min, max)) holds, then one at random with the width of its interval in the RandInt cascade ("weight").
	 */
	private Enemies.Attack choose(Enemies.Def d) {
		double dist = targetDistance();
		List<Enemies.Attack> ok = new ArrayList<>();
		int total = 0;
		for (Enemies.Attack a : d.attacks()) {
			if (dist >= a.minRange() && dist <= a.maxRange() && a.weight() > 0) {
				ok.add(a);
				total += a.weight();
			}
		}
		if (ok.isEmpty()) {
			return null;
		}
		int r = getRandom().nextInt(total);
		for (Enemies.Attack a : ok) {
			r -= a.weight();
			if (r < 0) {
				return a;
			}
		}
		return null;
	}

	/** cmd 5204 by name, through the attack subroutine (approach, turn, give up); false if it cannot start now. */
	public boolean requestAttack(String name) {
		Enemies.Def d = def();
		Enemies.Attack a = d == null ? null : d.attack(name);
		if (a == null || !actionKind.isEmpty()) {
			return false;
		}
		plan = a;
		planTicks = 0;
		turnTicks = 0;
		return true;
	}

	/** Whether an attack is being approached or played (the ESD waits on f5001 for this). */
	public boolean busy() {
		return plan != null || !actionKind.isEmpty();
	}

	/**
	 * S2147483595(action, reach, angle, ...) of ai102000 (enemy_ai.md s.9.2): within reach and angle the action starts;
	 * within reach but out of angle it turns, up to 1.5 s; out of reach it walks when closer than
	 * reach + 1.5, else runs, and gives up after 3 s walking or 10 s running, or when there is no path.
	 */
	private void approach(Enemies.Attack a, LivingEntity target) {
		planTicks++;
		double dist = targetDistance();
		double angle = targetAngle();
		if (dist < a.reach() && angle < a.angle()) {
			getNavigation().stop();
			plan = null;
			startAction("attack", a.animation(), Math.round(a.duration() * 20));
			attack = a;
			return;
		}
		if (dist < a.reach()) {
			getNavigation().stop();
			// The enemy's turn rate (FUN_1403a28b0, enemies.md s.8); standing still, the +0x2c end.
			float rate = def().turnDegreesPerTick(horizontalSpeed());
			getLookControl().setLookAt(target, rate, 30);
			setYRot(net.minecraft.util.Mth.approachDegrees(getYRot(), yawTo(target), rate));
			yBodyRot = getYRot();
			if (++turnTicks >= 30) {
				plan = null;
			}
			return;
		}
		turnTicks = 0;
		Enemies.Def d = def();
		boolean walk = dist < a.reach() + 1.5;
		// The attribute is the run speed (setEnemy); speed goes as (factor x attribute)^2.
		double factor = walk ? Math.sqrt(d.walkSpeed() / d.runSpeed()) : 1;
		if (planTicks >= (walk ? 60 : 200) || !getNavigation().moveTo(target, factor)) {
			getNavigation().stop();
			plan = null;
		}
	}

	// The script's logic variables (5900 / 5300): 8 per enemy.
	private final int[] vars = new int[8];

	public int var(int i) {
		return i >= 0 && i < vars.length ? vars[i] : -1;
	}

	public void setVar(int i, int v) {
		if (i >= 0 && i < vars.length) {
			vars[i] = v;
		}
	}

	private void startAction(String kind, String animation, int ticks) {
		actionKind = kind;
		actionTick = 0;
		actionLength = Math.max(1, ticks);
		window = -1;
		hitThisWindow.clear();
		entityData.set(ACTION, animation);
		entityData.set(ACTION_SERIAL, entityData.get(ACTION_SERIAL) + 1);
	}

	private void endAction() {
		if (!actionKind.isEmpty()) {
			actionKind = "";
			attack = null;
			entityData.set(ACTION, "");
		}
	}

	private void tickAction(Enemies.Def d) {
		actionTick++;
		if (actionKind.equals("attack") && attack != null) {
			float t = actionTick / 20f;
			int w = -1;
			for (int i = 0; i < attack.hits().size(); i++) {
				float[] h = attack.hits().get(i);
				if (t >= h[0] && t < h[1]) {
					w = i;
				}
			}
			if (w != window) {
				window = w;
				hitThisWindow.clear();
			}
			if (w >= 0) {
				hit(d, attack, w);
			}
		}
		if (actionTick >= actionLength) {
			if (actionKind.equals(HURT) && poise <= 0) {
				poise = d.poise();
			}
			endAction();
		}
	}

	/** Everything in front within reach and the attack's half angle, once per hit window. */
	private void hit(Enemies.Def d, Enemies.Attack a, int w) {
		ServerLevel level = (ServerLevel) level();
		double yaw = Math.toRadians(getYRot());
		Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		AABB area = getBoundingBox().inflate(a.reach(), 1, a.reach());
		for (Entity e : level.getEntities(this, area)) {
			if (!(e instanceof LivingEntity victim) || !victim.isAlive() || hitThisWindow.contains(e.getId())
					|| e instanceof SoulsEnemy || e instanceof Player p && (p.isCreative() || p.isSpectator()
					// A player outside the filter is not an enemy of it: its swings pass through them.
					|| !playerTarget(d.targets(), p))) {
				continue;
			}
			Vec3 to = new Vec3(e.getX() - getX(), 0, e.getZ() - getZ());
			double dist = to.length() - e.getBbWidth() / 2;
			if (dist > a.reach() || dist > 1e-3 && Math.toDegrees(Math.acos(Math.max(-1, Math.min(1,
					to.normalize().dot(fwd))))) > a.angle()) {
				continue;
			}
			hitThisWindow.add(e.getId());
			dealDamage(d, a.damage(w), victim);
		}
	}

	private void dealDamage(Enemies.Def d, Enemies.Damage row, LivingEntity victim) {
		DamageSource source = damageSources().mobAttack(this);
		dev.soulsmode.ds2.Ds2Damage.Defence defence = null;
		if (victim instanceof ServerPlayer player && SoulsGameMode.isActive(player)
				&& dev.soulsmode.SoulsPlayer.data(player).isPresent()) {
			// DS2's player victim with its own stats and armour; the armour is in that defence, so MC armour is skipped.
			defence = SoulsAttack.defenceOf(player);
			source = SoulsAttack.soulsDamage(this);
		}
		float amount = Enemies.damageToPlayer(row, d.attackMultiplier(), defence)
				/ (float) SoulsStats.DS2_HP_PER_HEALTH;
		boolean landed = amount > 0 && victim.hurt(source, amount);
		if (victim instanceof ServerPlayer player && (landed || amount <= 0)) {
			// The row's status pairs x EP +0xd8 (FUN_140391e60): petrification and curse (dev.soulsmode.SoulsStatus).
			dev.soulsmode.SoulsStatus.buildUp(player, dev.soulsmode.SoulsStatus.Kind.PETRIFY,
					row.petrify() * d.attackMultiplier());
			dev.soulsmode.SoulsStatus.buildUp(player, dev.soulsmode.SoulsStatus.Kind.CURSE,
					row.curse() * d.attackMultiplier());
		}
	}

	/** The guard stamina damage (EDP u16 +0x24) of the attack hitting now, for a guarding player. */
	public float guardStaminaDamage() {
		return attack != null && window >= 0 ? attack.damage(window).staminaDamage() : 0;
	}

	/**
	 * After damage: with poise above 0 there is no reaction (FUN_140136570, enemy: reaction 0); at 0 the pack's hurt
	 * animation plays and cuts the attack.
	 */
	@Override
	public boolean hurt(DamageSource source, float amount) {
		boolean hurt = super.hurt(source, amount);
		Enemies.Def d = def();
		if (hurt && d != null && !level().isClientSide() && isAlive() && poise <= 0) {
			startAction(HURT, d.animations().getOrDefault(HURT, HURT), Math.round(d.durations().get(HURT) * 20));
			getNavigation().stop();
		}
		return hurt;
	}

	public Enemies.Def enemyDef() {
		return def();
	}

	// Death: souls (EP +0xd0) to the killer as experience (the mod's souls), the drop roll, the death animation.

	@Override
	public void die(DamageSource source) {
		Enemies.Def d = def();
		if (!level().isClientSide() && d != null) {
			if (source.getEntity() instanceof ServerPlayer killer) {
				killer.giveExperiencePoints(d.souls());
			}
			dropRoll(d);
			startAction("death", d.animations().getOrDefault("death", "death"), Math.round(d.durations().get("death") * 20));
		}
		super.die(source);
	}

	/**
	 * ItemLotParam2_Chr mode 0 (enemies.md s.6): per roll, slot i drops with p_i % (r over max(W, 10000) + 1); a slot
	 * drops once per death.
	 */
	private void dropRoll(Enemies.Def d) {
		Set<Integer> dropped = new HashSet<>();
		for (int roll = 0; roll < d.rolls(); roll++) {
			int total = 0;
			int[] w = new int[d.drops().size()];
			for (int i = 0; i < w.length; i++) {
				w[i] = dropped.contains(i) ? 0 : (int) (d.drops().get(i).chance() * 100);
				total += w[i];
			}
			if (total == 0) {
				return;
			}
			int r = getRandom().nextInt(Math.max(total, 10000) + 1);
			if (r > total) {
				continue;
			}
			int acc = 0;
			for (int i = 0; i < w.length; i++) {
				acc += w[i];
				if (w[i] > 0 && r <= acc) {
					dropped.add(i);
					Enemies.Drop drop = d.drops().get(i);
					BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(drop.item()))
							.ifPresent(item -> spawnAtLocation(new ItemStack(item, drop.count())));
					break;
				}
			}
		}
	}

	@Override
	protected void dropExperience(Entity killer) {
		// Souls go straight to the killer (die).
	}

	@Override
	protected void tickDeath() {
		Enemies.Def d = def();
		int length = d == null ? 20 : Math.round(d.durations().get("death") * 20);
		++deathTime;
		if (deathTime >= Math.max(20, length) && !level().isClientSide() && !isRemoved()) {
			level().broadcastEntityEvent(this, (byte) 60);
			remove(RemovalReason.KILLED);
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putString("BseEnemy", entityData.get(ENEMY));
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("BseEnemy")) {
			ResourceLocation id = ResourceLocation.tryParse(tag.getString("BseEnemy"));
			if (id != null) {
				float health = getHealth();
				boolean fresh = !tag.contains("Health");
				setEnemy(id);
				if (!fresh) {
					setHealth(health);
				}
			}
		}
	}

	/** A boss's health bar ("boss": true), shown to the players within its follow range. */
	private net.minecraft.server.level.ServerBossEvent bossBar;

	private void tickBossBar(Enemies.Def d) {
		if (!d.boss()) {
			return;
		}
		if (bossBar == null) {
			bossBar = new net.minecraft.server.level.ServerBossEvent(getDisplayName(),
					net.minecraft.world.BossEvent.BossBarColor.RED, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
		}
		bossBar.setProgress(getHealth() / getMaxHealth());
		for (ServerPlayer p : List.copyOf(bossBar.getPlayers())) {
			if (!p.isAlive() || p.level() != level() || distanceTo(p) > d.followRange()) {
				bossBar.removePlayer(p);
			}
		}
		if (isAlive()) {
			for (Player p : level().players()) {
				if (p instanceof ServerPlayer sp && distanceTo(p) <= d.followRange()) {
					bossBar.addPlayer(sp);
				}
			}
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (bossBar != null) {
			bossBar.removeAllPlayers();
			bossBar = null;
		}
		super.remove(reason);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && def() != null) {
			tickBossBar(def());
		}
		if (!level().isClientSide() && enemyId() != null && def() == null && tickCount > 40) {
			// Its datapack is gone.
			SoulsMode.LOGGER.warn("[BSE] enemy {} has no definition; removed", enemyId());
			discard();
		}
	}

	// Client: GeckoLib.

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		int[] lastSerial = {-1};
		controllers.add(new AnimationController<>(this, "main", 3, state -> {
			Enemies.Def d = def();
			if (d == null) {
				return PlayState.STOP;
			}
			String action = entityData.get(ACTION);
			int serial = entityData.get(ACTION_SERIAL);
			if (!action.isEmpty()) {
				if (serial != lastSerial[0]) {
					lastSerial[0] = serial;
					state.getController().forceAnimationReset();
				}
				return state.setAndContinue(RawAnimation.begin().thenPlay(action));
			}
			lastSerial[0] = serial;
			// Run above the middle of the pack's walk and run speeds (blocks / s from the last tick's movement).
			double moved = Math.hypot(getX() - xo, getZ() - zo) * 20;
			String loop = state.isMoving() ? d.animations().getOrDefault(moved > (d.walkSpeed() + d.runSpeed()) / 2
					? "run" : "walk", d.animations().get("walk"))
					: d.animations().get("idle");
			return loop == null ? PlayState.STOP : state.setAndContinue(RawAnimation.begin().thenLoop(loop));
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
