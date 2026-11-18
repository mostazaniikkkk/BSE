package dev.soulsmode.entity;

import dev.soulsmode.SoulsAttack;
import dev.soulsmode.SoulsEquipLoad;
import dev.soulsmode.SoulsGameMode;
import dev.soulsmode.SoulsMode;
import dev.soulsmode.SoulsPlayer;
import dev.soulsmode.SoulsStats;
import dev.soulsmode.api.SoulsWeapons;
import dev.soulsmode.ds2.Attack;
import dev.soulsmode.ds2.DerivedStats;
import dev.soulsmode.ds2.Ds2Damage;
import dev.soulsmode.ds2.Ds2Data;
import dev.soulsmode.ds2.Moveset;
import dev.soulsmode.network.ZombieAttackPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An NPC invader (the user's design, 2026-11-01; docs/research/npc_invaders.md): a player-type character with
 * Steve's look that attacks with the player's DS2 movesets, driven by ai820004's map-20 table (right-hand melee).
 * <ul>
 * <li>Build (the user's rules): a random DS2 starting class (Warrior, Knight, Swordsman: swords; Bandit: axes; Cleric:
 * mace; Deprived: club; Sorcerer and Explorer are left out, their dagger and spells are not ported) levelled up to a
 * random level up to the player's, points first to the best-material weapon of its family whose DS2 requirements they
 * can meet, the rest spread evenly; the heaviest Minecraft armour set that keeps it under 70 % equip load. Level 1 is
 * the special one: Deprived, naked, with a club.</li>
 * <li>HP from DS2's player formula (DerivedStats); its hits use the DS2 player damage with its stats and weapon, and
 * hits on it use the player-victim formula with its armour.</li>
 * <li>AI (ai820004, enemy_ai / npc_invaders.md s.2): approach (run beyond 2 m), start a row when closer than 2.5 m or,
 * 48 % of the time, closer than 5 m; RandInt(0, 20) over the table: behind the target and closer than 2.5 m: light
 * x2; farther than 4.5 m: jump attack, light; then light-heavy-light / heavy-heavy-light / light x3 / approach and
 * light x2 by r0's interval. Each press needs its range and angle; follow-ups wait for the combo window and 0.5 s.
 * Every 20 s, 60 % chance to two-hand the weapon, or back.</li>
 * </ul>
 * Behind the target, the row's first light press becomes a backstab when the engine's test passes (SoulsAttack.npcBackstab);
 * otherwise it stays light x2.
 * Dodges (command 5221, the player's roll / backstep): row 7 rolls (0 / 60 / 300 deg from the target) before
 * approaching; 24 % backstep after rows 4..6 closer than 3 m; 14 % roll (0 / 30 / 330) once free after being hit.
 * The dodge on the target's attack never fires in ai820004 (f1(3) is never drawn) and is not ported. Stamina: the
 * player's from its Endurance (the user's choice); like DS2's AI it never waits for stamina, but its hits lose attack
 * rating without it. Not done: the kick row (f5050(1) not identified) and the reposition rolls (f5212 / f5213).
 */
public class SoulsInvader extends PathfinderMob {
	/** One AI row: presses (LIGHT / HEAVY / JUMP), range to start, distance and angle to keep pressing. */
	private record Row(List<Moveset.Type> presses, float range, float maxDistance, float angle) {
	}

	private static final Row BEHIND = new Row(List.of(Moveset.Type.LIGHT, Moveset.Type.LIGHT), 3, 4, 90);
	private static final Row JUMP = new Row(List.of(Moveset.Type.JUMP, Moveset.Type.LIGHT), 4.5f, 5.5f, 60);
	private static final Row LIGHT_HEAVY_LIGHT = new Row(
			List.of(Moveset.Type.LIGHT, Moveset.Type.HEAVY, Moveset.Type.LIGHT), 2.5f, 3.5f, 60);
	private static final Row HEAVY_HEAVY_LIGHT = new Row(
			List.of(Moveset.Type.HEAVY, Moveset.Type.HEAVY, Moveset.Type.LIGHT), 2.5f, 3.5f, 60);
	private static final Row LIGHT_X3 = new Row(
			List.of(Moveset.Type.LIGHT, Moveset.Type.LIGHT, Moveset.Type.LIGHT), 2.5f, 3.5f, 60);
	private static final Row APPROACH_LIGHT_X2 = new Row(List.of(Moveset.Type.LIGHT, Moveset.Type.LIGHT), 2.5f, 3.5f, 60);
	/** Follow-up presses wait at least this long into the attack (the ESD's f3() >= 0.5). */
	private static final float FOLLOW_UP = 0.5f;
	private static final float GIVE_UP = 5;
	private static final int GRIP_SWITCH_TICKS = 20 * 20;
	/** DS2 dash speed range (Movement.DASH_SPEED_LOW, m/s) for running; Minecraft's player walk for walking. */
	private static final double RUN = dev.soulsmode.ds2.Movement.DASH_SPEED_LOW;
	private static final double WALK = 4.317;
	private static final float HALF_ARC = 60;

	private UUID host;
	private int classIndex;
	private short[] stats = new short[dev.soulsmode.ds2.Attributes.SLOT_COUNT];
	private String weaponKey = "club";
	private boolean twoHanded;
	private Row row;
	private int press;
	private long rowStart;
	private Attack attack;
	private Moveset.Type attackType;
	private long attackStart;
	private int window = -1;
	private final java.util.Set<Integer> hitWindows = new java.util.HashSet<>();
	private String loco = "";
	/** The player's stamina from its Endurance (the user's choice, 2026-11-01; DS2 uses the NPC's EnemyParam row). */
	private dev.soulsmode.ds2.Stamina stamina;
	private float chargedUntil = -1;
	/** FUN_140376510 of the last charge: (cost - stamina) / cost, 0 with enough stamina. */
	private float staminaLack;
	/** The dodge playing: kind, start tick, world direction (unit), distance. */
	private dev.soulsmode.ds2.Dodge dodge;
	private long dodgeStart;
	private double dodgeX;
	private double dodgeZ;
	private float dodgeDistance;
	/** After the dodge: the row to start (row 7 rolls, then approaches and presses light x2), or null. */
	private Row afterDodge;
	/** f5202 then RandInt(1, 100) < 15: roll (0, 30, 330) once free, for up to 5 s (tick of the hit; 0 = none). */
	private long rollWhenFree;

	public SoulsInvader(EntityType<? extends SoulsInvader> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder attributes() {
		return createMobAttributes().add(Attributes.MOVEMENT_SPEED, Math.sqrt(RUN / 43.17)).add(Attributes.FOLLOW_RANGE, 48)
				.add(Attributes.ATTACK_DAMAGE, 1);
	}

	public dev.soulsmode.ds2.Attributes stats() {
		return new dev.soulsmode.ds2.Attributes(stats);
	}

	public UUID host() {
		return host;
	}

	public boolean twoHanded() {
		return twoHanded;
	}

	public String weaponKey() {
		return weaponKey;
	}

	// The build.

	private static final List<String> SWORDS = List.of("red_rust_sword", "drakekeepers_sword", "longsword", "shortsword",
			"puzzling_stone_sword", "broken_straight_sword");
	private static final List<String> AXES = List.of("gyrm_axe", "dragonslayers_crescent_axe", "bandit_axe", "battle_axe",
			"infantry_axe", "hand_axe");

	private static Item item(String weapon) {
		return switch (weapon) {
			case "red_rust_sword" -> Items.NETHERITE_SWORD;
			case "drakekeepers_sword" -> Items.DIAMOND_SWORD;
			case "longsword" -> Items.IRON_SWORD;
			case "shortsword" -> Items.GOLDEN_SWORD;
			case "puzzling_stone_sword" -> Items.STONE_SWORD;
			case "broken_straight_sword" -> Items.WOODEN_SWORD;
			case "gyrm_axe" -> Items.NETHERITE_AXE;
			case "dragonslayers_crescent_axe" -> Items.DIAMOND_AXE;
			case "bandit_axe" -> Items.GOLDEN_AXE;
			case "battle_axe" -> Items.IRON_AXE;
			case "infantry_axe" -> Items.STONE_AXE;
			case "hand_axe" -> Items.WOODEN_AXE;
			case "mace" -> Items.MACE;
			default -> Items.STICK;
		};
	}

	/** Builds the invader for a host of soul level {@code hostLevel}. Returns its display name's class key. */
	public void build(ServerPlayer hostPlayer, int hostLevel) {
		host = hostPlayer.getUUID();
		var random = getRandom();
		int level = 1 + random.nextInt(Math.max(1, hostLevel));
		var classes = Ds2Data.startingClasses().all().stream()
				.filter(c -> !c.name().equals("Sorcerer") && !c.name().equals("Explorer")).toList();
		var chosen = level == 1 ? classes.stream().filter(c -> c.name().equals("Deprived")).findFirst().orElse(classes.get(0))
				: classes.get(random.nextInt(classes.size()));
		classIndex = chosen.index();
		for (int i = 0; i < stats.length; i++) {
			stats[i] = (short) chosen.attributes().get(i);
		}
		List<String> family = switch (chosen.name()) {
			case "Warrior", "Knight", "Swordsman" -> SWORDS;
			case "Bandit" -> AXES;
			case "Cleric" -> List.of("mace");
			default -> List.of("club");
		};
		int points = Math.max(0, level - chosen.attributes().soulLevel());
		weaponKey = family.get(family.size() - 1);
		if (level > 1) {
			// The best weapon of the family whose Strength / Dexterity the points can reach.
			for (String w : family) {
				var data = Ds2Damage.weapon(w);
				if (data == null) {
					continue;
				}
				int needStr = Math.max(0, data.requirements()[0] - stats[dev.soulsmode.ds2.Attributes.STRENGTH]);
				int needDex = Math.max(0, data.requirements()[1] - stats[dev.soulsmode.ds2.Attributes.DEXTERITY]);
				if (needStr + needDex <= points) {
					stats[dev.soulsmode.ds2.Attributes.STRENGTH] += (short) needStr;
					stats[dev.soulsmode.ds2.Attributes.DEXTERITY] += (short) needDex;
					points -= needStr + needDex;
					weaponKey = w;
					break;
				}
			}
			// The rest evenly over the nine attributes.
			for (int i = 0; points > 0; i = (i + 1) % 9) {
				if (stats[i] < dev.soulsmode.ds2.Attributes.MAX_VALUE) {
					stats[i]++;
					points--;
				}
			}
		}
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item(weaponKey)));
		setDropChance(EquipmentSlot.MAINHAND, 0);
		if (level > 1) {
			armour();
		}
		DerivedStats derived = DerivedStats.compute(stats(), Ds2Data.physicalStats());
		stamina = new dev.soulsmode.ds2.Stamina(dev.soulsmode.SoulsStamina.params(), dev.soulsmode.SoulsStamina.curves(),
				derived.maxStamina());
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(derived.maxHp() / SoulsStats.DS2_HP_PER_HEALTH);
		setHealth(getMaxHealth());
		setCustomName(Component.translatable("entity.soulsmode.invader.name",
				Component.translatable("class.soulsmode." + chosen.name().toLowerCase(java.util.Locale.ROOT)), soulLevel()));
		setCustomNameVisible(true);
	}

	public int soulLevel() {
		return stats().soulLevel();
	}

	/** The heaviest full Minecraft armour set that, with the weapon, stays under 70 % of its max equip load. */
	private void armour() {
		float max = DerivedStats.compute(stats(), Ds2Data.physicalStats()).maxEquipLoad();
		float weapon = SoulsEquipLoad.weight(getMainHandItem());
		Item[][] sets = {
				{Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS},
				{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
				{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
				{Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS},
				{Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
				{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS}};
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		Item[] best = null;
		float bestWeight = -1;
		for (Item[] set : sets) {
			float w = weapon;
			for (Item i : set) {
				w += SoulsEquipLoad.weight(new ItemStack(i));
			}
			if (w <= 0.7f * max && w > bestWeight) {
				best = set;
				bestWeight = w;
			}
		}
		if (best != null) {
			for (int i = 0; i < 4; i++) {
				setItemSlot(slots[i], new ItemStack(best[i]));
				setDropChance(slots[i], 0);
			}
		}
	}

	// The AI.

	private ServerPlayer hostPlayer() {
		return host == null || !(level() instanceof ServerLevel level) ? null
				: level.getServer().getPlayerList().getPlayer(host);
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		ServerPlayer target = hostPlayer();
		if (target == null || !target.isAlive() || target.level() != level() || !SoulsGameMode.isActive(target)) {
			// The host is gone or dead: the invasion ends.
			dev.soulsmode.SoulsInvasions.end(this, false);
			return;
		}
		setTarget(target);
		long now = level().getGameTime();
		if (tickCount % GRIP_SWITCH_TICKS == 0 && attack == null) {
			// Every 20 s: one-handed, 60 % to two-hand (RandInt(1, 10) >= 5); two-handed, always back.
			twoHanded = !twoHanded && getRandom().nextInt(10) + 1 >= 5;
		}
		if (dodge != null) {
			tickDodge(now);
			return;
		}
		if (attack != null) {
			tickAttack(target, now);
			return;
		}
		if (rollWhenFree != 0) {
			// State 8: the roll once free, waiting up to 5 s.
			long hit = rollWhenFree;
			rollWhenFree = 0;
			if ((now - hit) / 20f <= 5 && roll(target, true, 4, now, 0, 30, 330)) {
				return;
			}
		}
		if (afterDodge != null) {
			row = afterDodge;
			afterDodge = null;
			press = 0;
			rowStart = now;
		}
		double d = distanceTo(target);
		double dy = Math.abs(target.getY() - getY());
		if (row == null) {
			int roll = getRandom().nextInt(100) + 1;
			if (tickCount % 10 == 0 && dy <= 1.5 && (d < 2.5 || d < 5 && roll > 1 && roll < 50)) {
				row = choose(target, d);
				press = 0;
				rowStart = now;
				if (row == APPROACH_LIGHT_X2) {
					// Row 7: rodada 2147483598(0, 60, 300, -1, 1, 4, 0.5); when it went (returns 0), approach and light
					// x2; when no direction is free (returns 1), the row ends.
					row = null;
					if (roll(target, true, 4, now, 0, 60, 300)) {
						afterDodge = APPROACH_LIGHT_X2;
					}
					return;
				}
			} else {
				move(target, d >= 2 ? RUN : WALK);
				return;
			}
		}
		// The row's next press: get within range and angle (walk inside range + 1.5 m, else run; give up after 5 s).
		if ((now - rowStart) / 20f > GIVE_UP) {
			row = null;
			return;
		}
		if (d < row.range() && angle(target) < row.angle()) {
			getNavigation().stop();
			startPress(target, now);
		} else {
			move(target, d < row.range() + 1.5 ? WALK : RUN);
		}
	}

	/** RandInt(0, 20) over the map-20 table (npc_invaders.md s.2). */
	private Row choose(ServerPlayer target, double d) {
		int r = getRandom().nextInt(21);
		if (behind(target) && d < 2.5 && r < 15) {
			return BEHIND;
		}
		if (d >= 4.5 && r < 10) {
			return JUMP;
		}
		if (r < 5) {
			return LIGHT_HEAVY_LIGHT;
		}
		if (r < 10) {
			return HEAVY_HEAVY_LIGHT;
		}
		if (r < 15) {
			return LIGHT_X3;
		}
		return APPROACH_LIGHT_X2;
	}

	private void move(LivingEntity target, double speed) {
		// The attribute is the run speed; a mob's speed goes as (factor x attribute)^2.
		getNavigation().moveTo(target, Math.sqrt(speed / RUN));
		loco(speed >= RUN ? "player:ds2_dash" : "player:ds2_walk");
	}

	private void startPress(ServerPlayer target, long now) {
		ResourceLocation movesetId = SoulsWeapons.movesetFor(getMainHandItem());
		Moveset moveset = SoulsWeapons.moveset(movesetId).orElse(null);
		Moveset.Type wanted = row.presses().get(press);
		if (row == BEHIND && press == 0 && !twoHanded && attack == null
				&& SoulsAttack.npcBackstab(this, target, moveset, movesetId)) {
			// Backstab group 2147483607: its light press caught the target; the group ends with the grab.
			float faceYaw = (float) Math.toDegrees(Math.atan2(-(target.getX() - getX()), target.getZ() - getZ()));
			setYRot(faceYaw);
			setYBodyRot(faceYaw);
			setYHeadRot(faceYaw);
			attack = moveset.backstab();
			attackType = Moveset.Type.BACKSTAB;
			attackStart = now;
			window = -1;
			hitWindows.clear();
			row = null;
			loco = "";
			send("player:" + attack.animation().substring(attack.animation().indexOf(':') + 1));
			return;
		}
		Moveset.Type type = wanted == Moveset.Type.JUMP ? Moveset.Type.JUMP
				: Moveset.next(attackType, false, attack != null && attack.inCombo((now - attackStart) / 20f),
						wanted.heavy(), false);
		Attack a = moveset == null ? null : moveset.attack(false, twoHanded, type);
		if (a == null || a.isCast()) {
			row = null;
			return;
		}
		lookAt(target, 360, 360);
		attack = a;
		attackType = type;
		attackStart = now;
		chargedUntil = -1;
		window = -1;
		hitWindows.clear();
		press++;
		loco = "";
		send("player:" + a.animation().substring(a.animation().indexOf(':') + 1));
	}

	private void tickAttack(ServerPlayer target, long now) {
		getNavigation().stop();
		float t = (now - attackStart) / 20f;
		int w = attack.hitWindow(t);
		if (w >= 0 && w != window) {
			window = w;
			boolean reaches = attackType == Moveset.Type.BACKSTAB ? SoulsAttack.heldBy(target, this)
					: distanceTo(target) - target.getBbWidth() / 2 <= 3 && angle(target) <= HALF_ARC;
			if (!hitWindows.contains(w) && reaches) {
				hitWindows.add(w);
				if (SoulsAttack.npcHit(this, target, attack.damageRate(w), attack.damageFloor(w))) {
					// The invader builds its hits like an enemy, from its weapon's rows (poise.md s.4.3).
					dev.soulsmode.SoulsPoise.onHit(target, dev.soulsmode.ds2.Poise.weaponHit(
							dev.soulsmode.SoulsAttack.damageKey(dev.soulsmode.api.SoulsWeapons.movesetFor(getMainHandItem())),
							attack, attack.hitSlot(w)), this);
				}
			}
		}
		boolean more = row != null && press < row.presses().size();
		if (more && t >= FOLLOW_UP && attack.allows(row.presses().get(press).heavy(), false, t)) {
			double d = distanceTo(target);
			if (d < row.maxDistance() && angle(target) < row.angle()) {
				Attack previous = attack;
				startPress(target, now);
				if (attack != previous) {
					return;
				}
			} else {
				row = null;
			}
		}
		if (t >= attack.duration()) {
			Row finished = row != null && press >= row.presses().size() ? row : null;
			attack = null;
			if (finished != null) {
				row = null;
				// After rows 2, 4, 5 and 6: RandInt(1, 100) < 25 and d < 3 -> backstep 2147483614(0.3).
				if ((finished == LIGHT_HEAVY_LIGHT || finished == HEAVY_HEAVY_LIGHT || finished == LIGHT_X3)
						&& getRandom().nextInt(100) + 1 < 25 && distanceTo(target) < 3) {
					roll(target, false, 3, now, 180);
				}
			}
		}
	}

	// Dodges (npc_invaders.md s.5): command 5221 sends the player's roll / backstep request.

	/**
	 * 5221(kind, d1..d4, 0, probe): directions in degrees around the direction to the target; one at random among those
	 * whose landing {@code probe} m away passes the navmesh test (FUN_14042dfa0 / FUN_14042ddc0; here: no block in the
	 * way and ground under the end), drawing again without the failed ones. A roll turns to its vector
	 * (FUN_1403851d0 -> FUN_14031c9b0) and travels it; a backstep goes back from the current facing. False when none
	 * is free (f5620).
	 */
	private boolean roll(LivingEntity target, boolean isRoll, double probe, long now, double... directions) {
		double bx = target.getX() - getX();
		double bz = target.getZ() - getZ();
		double len = Math.sqrt(bx * bx + bz * bz);
		if (len < 1e-3) {
			double yaw = Math.toRadians(getYRot());
			bx = -Math.sin(yaw);
			bz = Math.cos(yaw);
		} else {
			bx /= len;
			bz /= len;
		}
		List<Double> left = new ArrayList<>();
		for (double d : directions) {
			left.add(d);
		}
		while (!left.isEmpty()) {
			double d = left.remove(left.size() > 1 ? getRandom().nextInt(left.size()) : 0);
			double th = Math.toRadians(d);
			double x = bx * Math.cos(th) + bz * Math.sin(th);
			double z = -bx * Math.sin(th) + bz * Math.cos(th);
			if (free(x, z, probe)) {
				startDodge(isRoll, x, z, now);
				return true;
			}
		}
		return false;
	}

	/** The navmesh probe's stand-in: the box can move {@code probe} m along (x, z) and lands on ground. */
	private boolean free(double x, double z, double probe) {
		net.minecraft.world.phys.Vec3 move = new net.minecraft.world.phys.Vec3(x * probe, 0, z * probe);
		if (!level().noCollision(this, getBoundingBox().expandTowards(move))) {
			return false;
		}
		net.minecraft.world.phys.AABB end = getBoundingBox().move(move);
		return !level().noCollision(this, end.move(0, -1, 0).setMaxY(end.minY));
	}

	private void startDodge(boolean isRoll, double x, double z, long now) {
		float ratio = equipRatio();
		boolean chained = dodge != null;
		boolean inAttack = attack != null;
		dodge = dev.soulsmode.ds2.Dodge.of(isRoll, ratio);
		dodgeStart = now;
		attack = null;
		row = null;
		getNavigation().stop();
		if (isRoll) {
			float yaw = (float) Math.toDegrees(Math.atan2(-x, z));
			setYRot(yaw);
			setYBodyRot(yaw);
			setYHeadRot(yaw);
			dodgeX = x;
			dodgeZ = z;
		} else {
			double yaw = Math.toRadians(getYRot());
			dodgeX = Math.sin(yaw);
			dodgeZ = -Math.cos(yaw);
		}
		// The NPC's dodge vector is the 5221 one (x 1.0 roll, x 0.3 backstep), not a computed distance.
		dodgeDistance = dodge.npcDistance(isRoll ? 1.0f : 0.3f);
		if (stamina != null) {
			stamina.consume(dev.soulsmode.SoulsStamina.params().dodgeCost(isRoll, chained, inAttack));
		}
		loco = "";
		send("player:" + dodge.animation());
	}

	/** The dodge's push (the same split over time as the player's, RollClient) and its end. */
	private void tickDodge(long now) {
		getNavigation().stop();
		float t = (now - dodgeStart) / 20f;
		float push = dodge.isRoll() ? dev.soulsmode.ds2.Dodge.ROLL_PUSH_SECONDS : dev.soulsmode.ds2.Dodge.STEP_PUSH_SECONDS;
		if (t < push) {
			double step = dodgeDistance / (push * 20);
			setDeltaMovement(dodgeX * step, getDeltaMovement().y, dodgeZ * step);
			hurtMarked = true;
		}
		if (t >= dodge.duration) {
			dodge = null;
		}
	}

	/** TAE 110500 of the dodge playing: hits are rejected. */
	public boolean isDodgeInvincible() {
		// Unchanged until decided: NPC dodges get chr.b8+0xa4 from ChrDodgeCtrl / EnemyDodgeCtrl's own calculator
		// (FUN_140328b10 / FUN_140328c20), not traced; this is still the 110500 window, which is not DS2's i-frames
		// (agility.md s.2.1; backlog).
		return dodge != null && dodge.inNoPoiseWindow((level().getGameTime() - dodgeStart) / 20f);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (isDodgeInvincible()) {
			return false;
		}
		boolean hurt = super.hurt(source, amount);
		// f5202 (ChrAi+0x375, FUN_140426fc0): hit, not while rolling (5230(0, 6) in the roll group). State 5:
		// RandInt(1, 100) < 15 -> state 8.
		if (hurt && !level().isClientSide() && dodge == null && getRandom().nextInt(100) + 1 < 15) {
			rollWhenFree = level().getGameTime();
		}
		return hurt;
	}

	/** Stamina per tick: the attack's charges (FUN_1403269e0) and regen, with the player's rules. */
	private void tickStamina(long now) {
		if (stamina == null) {
			return;
		}
		stamina.setEquipLoadRatio(equipRatio());
		boolean blocks = false;
		if (attack != null) {
			float t = (now - attackStart) / 20f;
			// FUN_14038fb10: x (1 + lack), PlayerLackOfStatsParam row 11: 0.01 (30 + 20 f) when the requirements are not met.
			float f = dev.soulsmode.ds2.Ds2Damage.requirementLack(weaponKey, stats(), twoHanded);
			float due = attack.staminaDue(chargedUntil, t) * (f > 0 ? 1 + Math.max(0, Math.min(1, 0.01f * (30 + 20 * f))) : 1);
			chargedUntil = t;
			if (due > 0) {
				float before = stamina.current();
				staminaLack = before < due ? (due - Math.max(0, before)) / due : 0;
				stamina.consume(due);
			}
			blocks = attack.blocksRegen(t);
		}
		if (dodge != null) {
			float t = (now - dodgeStart) / 20f;
			for (float[] w : DODGE_REGEN_BLOCK.get(dodge.ordinal())) {
				blocks |= t >= w[0] && t < w[1];
			}
		}
		stamina.setActionBlocksRegen(blocks);
		stamina.tick(0.05f);
	}

	/** TAE 101100 of each dodge animation (the player's, SoulsRoll). */
	private static final List<List<float[]>> DODGE_REGEN_BLOCK = dev.soulsmode.ds2.Weapons.dodgeRegenBlock();

	/** Weapon and armour weight over its max equip load (FUN_14034b390 / DerivedStats). */
	private float equipRatio() {
		float max = DerivedStats.compute(stats(), Ds2Data.physicalStats()).maxEquipLoad();
		float w = SoulsEquipLoad.weight(getMainHandItem());
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
				EquipmentSlot.FEET}) {
			w += SoulsEquipLoad.weight(getItemBySlot(slot));
		}
		return max > 0 ? w / max : 0;
	}

	/** FUN_140376510 of its last attack charge (attack rating: up to 10 % less without stamina). */
	public float staminaLack() {
		return staminaLack;
	}

	/** A damage reaction cut its attack and its combo row (SoulsHitReaction). */
	public void interrupt() {
		afterDodge = null;
		if (attack != null) {
			attack = null;
			row = null;
			send("");
		}
		// Locomotion is sent again once it moves (the reaction clip replaces the loop meanwhile).
		loco = "";
	}

	private double angle(LivingEntity target) {
		double toYaw = Math.toDegrees(Math.atan2(-(target.getX() - getX()), target.getZ() - getZ()));
		return Math.abs(Mth.wrapDegrees(toYaw - getYRot()));
	}

	/** f7005 >= 110: the target faces away from it. */
	private boolean behind(LivingEntity target) {
		double toMe = Math.toDegrees(Math.atan2(-(getX() - target.getX()), getZ() - target.getZ()));
		return Math.abs(Mth.wrapDegrees(toMe - target.getYRot())) >= 110;
	}

	private void loco(String clip) {
		if (!clip.equals(loco)) {
			loco = clip;
			send("loop:" + clip);
		}
	}

	private void send(String animation) {
		ZombieAttackPayload payload = new ZombieAttackPayload(getId(), animation);
		for (ServerPlayer player : PlayerLookup.tracking(this)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			tickStamina(level().getGameTime());
		}
		if (!level().isClientSide() && attack == null && dodge == null && getNavigation().isDone()
				&& !"player:ds2_idle".equals(loco)) {
			loco("player:ds2_idle");
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!level().isClientSide()) {
			dev.soulsmode.SoulsInvasions.end(this, true);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (host != null) {
			tag.putUUID("BseHost", host);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		// An invasion does not survive a reload: no host link, so it ends on its first tick.
		host = null;
	}
}
