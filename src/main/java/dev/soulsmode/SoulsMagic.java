package dev.soulsmode;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.soulsmode.api.SoulsWeapons;
import dev.soulsmode.content.SoulsContent;
import dev.soulsmode.ds2.Attack;
import dev.soulsmode.ds2.Attributes;
import dev.soulsmode.ds2.Bullets;
import dev.soulsmode.ds2.DerivedStats;
import dev.soulsmode.ds2.Ds2Data;
import dev.soulsmode.ds2.Spells;
import dev.soulsmode.ds2.Weapons;
import dev.soulsmode.item.SoulsComponents;
import dev.soulsmode.item.SoulsItems;
import dev.soulsmode.network.MagicPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * DS2 spells, player side (docs/research/spells.md): attunement, uses, the active spell and the cast checks. Spells
 * are datapack content (docs/datapacks.md); each {@link SoulsItems#SPELL} stack is one owned copy.
 * <ul>
 * <li>Attuning (only while resting at a bonfire, as in DS2's menu) moves the copy from the inventory into the attuned
 * list, with its uses; taking it out gives it back with its uses.</li>
 * <li>Capacity: min(14, attunement slots) (FUN_1401b40f0; the slot bonus is not traced: none). After a stat change,
 * the entries past the capacity go back to the inventory and uses are trimmed to the new max (FUN_1401b2e80 (0, 0)).</li>
 * <li>Resting and respawning restore 100 % (SpEffect 110000010).</li>
 * </ul>
 */
public final class SoulsMagic {
	/** One attuned copy: the spell and its uses left (entry +0x14 / +0x20). */
	public record Attuned(ResourceLocation spell, int uses) {
		public static final Codec<Attuned> CODEC = RecordCodecBuilder.create(i -> i.group(
				ResourceLocation.CODEC.fieldOf("spell").forGetter(Attuned::spell),
				Codec.INT.fieldOf("uses").forGetter(Attuned::uses)).apply(i, Attuned::new));
	}

	/** Id sent in the Start payload for a failed cast (StickSpellNoBullet). */
	public static final ResourceLocation FAILED_CAST = SoulsMode.id("failed_cast");

	private SoulsMagic() {
	}

	/** The lock-on target of each player (entity id), sent by its client: spell bullets home on it. */
	private static final java.util.Map<java.util.UUID, Integer> LOCK_TARGETS = new java.util.HashMap<>();

	public static void setLockTarget(ServerPlayer player, int entityId) {
		if (entityId < 0) {
			LOCK_TARGETS.remove(player.getUUID());
		} else {
			LOCK_TARGETS.put(player.getUUID(), entityId);
		}
	}

	public static net.minecraft.world.entity.Entity lockTarget(ServerPlayer player) {
		Integer id = LOCK_TARGETS.get(player.getUUID());
		return id == null ? null : player.serverLevel().getEntity(id);
	}

	public static void forget(ServerPlayer player) {
		LOCK_TARGETS.remove(player.getUUID());
	}

	public static List<Attuned> attuned(ServerPlayer player) {
		return player.getAttachedOrElse(SoulsAttachments.ATTUNED, List.of());
	}

	private static int active(ServerPlayer player) {
		return player.getAttachedOrElse(SoulsAttachments.ACTIVE_SPELL, -1);
	}

	private static DerivedStats derived(ServerPlayer player) {
		return SoulsPlayer.data(player).map(d -> DerivedStats.compute(d.toAttributes(), Ds2Data.physicalStats()))
				.orElse(null);
	}

	public static int capacity(ServerPlayer player) {
		DerivedStats d = derived(player);
		return d == null ? 0 : Spells.capacity(d.attunementSlots());
	}

	/** Max uses of a spell for the player (FUN_1401ab750); 0 for a spell no datapack defines. */
	public static int maxUses(ServerPlayer player, ResourceLocation id) {
		Spells.Spell spell = SoulsContent.spell(id);
		DerivedStats d = derived(player);
		return spell == null || d == null ? 0 : Spells.maxUses(spell, d.spellUsesLevel());
	}

	private static int slots(ResourceLocation id) {
		Spells.Spell spell = SoulsContent.spell(id);
		return spell == null ? 0 : spell.slots();
	}

	// Attunement.

	public static void onAttune(ServerPlayer player, int slot) {
		if (!SoulsBonfire.isResting(player) || slot < 0 || slot >= player.getInventory().getContainerSize()) {
			return;
		}
		ItemStack stack = player.getInventory().getItem(slot);
		ResourceLocation id = SoulsContent.spellId(stack);
		List<Attuned> list = new ArrayList<>(attuned(player));
		if (id == null || list.size() >= Spells.MAX_ATTUNED) {
			return;
		}
		int used = list.stream().mapToInt(a -> slots(a.spell())).sum();
		if (used + slots(id) > capacity(player)) {
			return;
		}
		int max = maxUses(player, id);
		Integer uses = stack.get(SoulsComponents.SPELL_USES);
		list.add(new Attuned(id, uses == null ? max : Math.min(uses, max)));
		player.getInventory().setItem(slot, ItemStack.EMPTY);
		player.setAttached(SoulsAttachments.ATTUNED, List.copyOf(list));
		if (active(player) < 0) {
			player.setAttached(SoulsAttachments.ACTIVE_SPELL, 0);
		}
		sync(player);
	}

	public static void onUnattune(ServerPlayer player, int index) {
		List<Attuned> list = new ArrayList<>(attuned(player));
		if (!SoulsBonfire.isResting(player) || index < 0 || index >= list.size()) {
			return;
		}
		giveBack(player, list.remove(index));
		player.setAttached(SoulsAttachments.ATTUNED, List.copyOf(list));
		fixActive(player, list);
		sync(player);
	}

	private static void giveBack(ServerPlayer player, Attuned a) {
		ItemStack stack = new ItemStack(SoulsItems.SPELL);
		stack.set(SoulsComponents.SPELL, a.spell());
		stack.set(SoulsComponents.SPELL_USES, a.uses());
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	private static void fixActive(ServerPlayer player, List<Attuned> list) {
		int active = active(player);
		if (list.isEmpty()) {
			player.setAttached(SoulsAttachments.ACTIVE_SPELL, -1);
		} else if (active < 0 || active >= list.size()) {
			player.setAttached(SoulsAttachments.ACTIVE_SPELL, 0);
		}
	}

	/**
	 * After a stat change (FUN_1401b40f0, then FUN_1401b2e80 (0, 0)): entries past the capacity go back to the
	 * inventory and uses are trimmed to the new max.
	 */
	public static void refresh(ServerPlayer player) {
		List<Attuned> list = new ArrayList<>(attuned(player));
		int keep = Spells.keptWithin(list.stream().map(a -> slots(a.spell())).toList(), capacity(player));
		while (list.size() > keep) {
			giveBack(player, list.remove(list.size() - 1));
		}
		list.replaceAll(a -> new Attuned(a.spell(), Math.min(a.uses(), maxUses(player, a.spell()))));
		player.setAttached(SoulsAttachments.ATTUNED, List.copyOf(list));
		fixActive(player, list);
		sync(player);
	}

	/**
	 * FUN_1401b2e80(percent, round up): owned copies in the inventory get R(their max) each; attuned copies of one
	 * spell share R(sum of their max), filled from the last position back, each up to its max.
	 */
	public static void restore(ServerPlayer player, float percent) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			ResourceLocation id = SoulsContent.spellId(stack);
			Integer uses = stack.get(SoulsComponents.SPELL_USES);
			if (id != null && uses != null) {
				int max = maxUses(player, id);
				int now = Math.min(max, uses + Spells.restored(max, percent));
				if (now >= max) {
					stack.remove(SoulsComponents.SPELL_USES);
				} else {
					stack.set(SoulsComponents.SPELL_USES, now);
				}
			}
		}
		List<Attuned> list = new ArrayList<>(attuned(player));
		for (ResourceLocation id : list.stream().map(Attuned::spell).distinct().toList()) {
			int total = 0;
			for (Attuned a : list) {
				if (a.spell().equals(id)) {
					total += maxUses(player, id);
				}
			}
			int left = Spells.restored(total, percent);
			for (int i = list.size() - 1; i >= 0 && left > 0; i--) {
				Attuned a = list.get(i);
				if (a.spell().equals(id)) {
					int add = Math.min(left, maxUses(player, id) - a.uses());
					if (add > 0) {
						list.set(i, new Attuned(id, a.uses() + add));
						left -= add;
					}
				}
			}
		}
		player.setAttached(SoulsAttachments.ATTUNED, List.copyOf(list));
		sync(player);
	}

	/**
	 * FUN_1401b2870: the next attuned spell, wrapping around; copies of the active spell are skipped, it stops at the
	 * first spell with another id (staying put when there is none), -1 when nothing is attuned.
	 */
	public static void onCycle(ServerPlayer player) {
		List<Attuned> list = attuned(player);
		if (list.isEmpty()) {
			player.setAttached(SoulsAttachments.ACTIVE_SPELL, -1);
			sync(player);
			return;
		}
		int active = Math.max(0, active(player));
		ResourceLocation current = list.get(Math.min(active, list.size() - 1)).spell();
		for (int step = 1; step <= list.size(); step++) {
			int i = (active + step) % list.size();
			if (!list.get(i).spell().equals(current)) {
				player.setAttached(SoulsAttachments.ACTIVE_SPELL, i);
				break;
			}
		}
		sync(player);
	}

	/** FUN_1401b1a90: uses of the active group, the contiguous copies of the active spell from the active one on. */
	private static int groupUses(List<Attuned> list, int active) {
		int sum = 0;
		for (int i = active; i < list.size() && list.get(i).spell().equals(list.get(active).spell()); i++) {
			sum += list.get(i).uses();
		}
		return sum;
	}

	/** FUN_1401b3b10 + FUN_1401b4670: one use off the first copy of the active group that has any. */
	static void spendUse(ServerPlayer player) {
		List<Attuned> list = new ArrayList<>(attuned(player));
		int active = active(player);
		if (active < 0 || active >= list.size()) {
			return;
		}
		ResourceLocation id = list.get(active).spell();
		for (int i = active; i < list.size() && list.get(i).spell().equals(id); i++) {
			if (list.get(i).uses() > 0) {
				list.set(i, new Attuned(id, list.get(i).uses() - 1));
				break;
			}
		}
		player.setAttached(SoulsAttachments.ATTUNED, List.copyOf(list));
		sync(player);
	}

	public static void sync(ServerPlayer player) {
		List<MagicPayloads.Entry> entries = attuned(player).stream()
				.map(a -> new MagicPayloads.Entry(a.spell(), a.uses(), maxUses(player, a.spell()), slots(a.spell())))
				.toList();
		ServerPlayNetworking.send(player, new MagicPayloads.State(entries, active(player), capacity(player)));
	}

	// Casting.

	/**
	 * FUN_1403963b0, in order: uses of the active group (1 needed), the Int / Faith requirements against the effective
	 * attributes (FUN_1403978c0; the requirement reduction table is empty), the catalyst in that hand allowing the
	 * spell's school (FUN_140397a30) and the target condition (only 0 = always is supported). The first check,
	 * thunk_FUN_141ce2883, is not identified and is not run.
	 */
	static ResourceLocation castable(ServerPlayer player, ResourceLocation catalyst, boolean leftHand) {
		List<Attuned> list = attuned(player);
		int active = active(player);
		if (active < 0 || active >= list.size()) {
			return null;
		}
		ResourceLocation id = list.get(active).spell();
		Spells.Spell spell = SoulsContent.spell(id);
		Attributes stats = SoulsPlayer.data(player).map(SoulsData::toAttributes).orElse(null);
		if (spell == null || stats == null || groupUses(list, active) < 1
				|| stats.get(Attributes.INTELLIGENCE) < spell.requiredIntelligence()
				|| stats.get(Attributes.FAITH) < spell.requiredFaith()
				|| castSchools(catalyst)[spell.school()] == 0 || !targetOk(player, spell.target(), leftHand)) {
			return null;
		}
		return id;
	}

	private static final java.util.Map<String, int[]> BUILT_IN_TARGETS = Weapons.buffTargets();

	/**
	 * SpellParam +0x05 (FUN_1401a46a0, spells.md "Condición de objetivo 1/2"): 0 always; 1 / 2 the weapon in the OTHER
	 * hand must be enchantable (ItemParam +0x50 bit 1) and a melee weapon (WeaponParam +0x15 = 0) / a shield (3), and
	 * nothing two-handed; a lit torch in that hand is what is checked (it has no ItemParam row: fails).
	 */
	private static boolean targetOk(ServerPlayer player, int target, boolean castLeft) {
		if (target == 0) {
			return true;
		}
		if (target != 1 && target != 2 || SoulsAttack.grip(player) != dev.soulsmode.ds2.Moveset.Grip.ONE_HANDED) {
			return false;
		}
		boolean otherLeft = !castLeft;
		if (otherLeft && SoulsTorch.isHeld(player)) {
			return false;
		}
		ResourceLocation id = SoulsWeapons.movesetFor(otherLeft ? player.getOffhandItem() : player.getMainHandItem());
		int weaponClass;
		boolean enchantable;
		Weapons.Def pack = SoulsContent.weapon(id);
		if (pack != null) {
			weaponClass = pack.weaponClass();
			enchantable = pack.enchantable();
		} else {
			int[] t = SoulsMode.MOD_ID.equals(id.getNamespace()) ? BUILT_IN_TARGETS.get(id.getPath()) : null;
			if (t == null) {
				return false;
			}
			weaponClass = t[0];
			enchantable = t[1] != 0;
		}
		return enchantable && weaponClass == (target == 1 ? 0 : 3);
	}

	private static int[] castSchools(ResourceLocation moveset) {
		Weapons.Def pack = SoulsContent.weapon(moveset);
		if (pack != null) {
			return pack.castSchools();
		}
		int[] builtIn = BUILT_IN_SCHOOLS.get(moveset.getPath());
		return SoulsMode.MOD_ID.equals(moveset.getNamespace()) && builtIn != null ? builtIn : new int[5];
	}

	private static final java.util.Map<String, int[]> BUILT_IN_SCHOOLS = Weapons.castSchools();

	/**
	 * A spell being cast. Built when the Prepare request passes the checks (FUN_1403936a0): the Prepare attack (its
	 * animation, its locks kept until the cast starts, and the stamina charged when ShootStateControl starts), the cast
	 * attack, and what the bullets carry: the catalyst's attack rating plus the spell's flat damage, computed now
	 * (FUN_140391bf0 fills the hit slot here), the catalyst's damage key and the homing range.
	 */
	public record Cast(ResourceLocation spell, Attack prepare, Attack castAttack, Spells.CastAnimation castAnimation,
			float[] attack, ResourceLocation catalyst, String catalystKey, float homingRange, boolean leftHand) {
	}

	private static final java.util.Map<String, float[]> BUILT_IN_FACTORS = Weapons.castFactors();
	private static final float LOCK_ON_BULLET_RANGE = Spells.lockOnBulletRange();

	/** {WeaponTypeParam +0x10c, WeaponParam +0x40} of a catalyst. */
	private static float[] castFactors(ResourceLocation moveset) {
		Weapons.Def pack = SoulsContent.weapon(moveset);
		if (pack != null) {
			return new float[] {pack.castTime(), pack.bulletRange()};
		}
		float[] builtIn = BUILT_IN_FACTORS.get(moveset.getPath());
		return builtIn != null ? builtIn : new float[] {1, 1};
	}

	static Cast prepare(ServerPlayer player, ResourceLocation spellId, ResourceLocation catalyst, float slotCost,
			boolean leftHand, boolean twoHanded, float upgradeLevel) {
		Spells.Spell spell = SoulsContent.spell(spellId);
		Spells.CastAnimation prepare = SoulsContent.castAnimation(spell.prepare());
		Spells.CastAnimation cast = SoulsContent.castAnimation(spell.cast());
		Attributes stats = SoulsPlayer.data(player).map(SoulsData::toAttributes).orElse(null);
		if (prepare == null || cast == null || stats == null || Float.isNaN(prepare.shootState())) {
			return null;
		}
		String key = SoulsAttack.damageKey(catalyst);
		// Cost: the slot's base (WeaponParam +0x38 x WSC) x block +0x10, charged entering ShootStateControl.
		float cost = slotCost * spell.staminaFactor();
		var stamina = SoulsStamina.get(player);
		float staminaLack = stamina != null && stamina.current() < cost && cost > 0
				? (cost - Math.max(0, stamina.current())) / cost : 0;
		float[] factors = castFactors(catalyst);
		float wait = Spells.castWait(spell, factors[0], stats, Ds2Data.physicalStats(),
				dev.soulsmode.ds2.Ds2Damage.requirementLack(key, stats, twoHanded), staminaLack);
		float castAt = prepare.shootState() + wait;
		// Prepare, then the wait: attacks and guard stay locked until the cast; walking and dodging are not locked
		// (no CancelControl_Move / _Avoid in Prepare). A dodge ends the spell: no cast, the stamina stays spent.
		Attack prep = new Attack(prepare.animation(), 0, castAt, List.of(), 0, 0, 0, castAt, castAt, castAt, castAt, 0, 0,
				false, prepare.leftAuthored(), List.of(), List.of(), cost, List.of(prepare.shootState()),
				prepare.regenBlock());
		// The hit slot: the catalyst's attack values (FUN_140391fe0, no resin: the cast hand's weapon is the catalyst)
		// plus the bullet's flat damage on its element (PlayerDamageParam +0x0c / +0x19).
		float[][] buffs = SoulsSpEffects.weaponBuffs(player, leftHand);
		float[] attack = dev.soulsmode.ds2.Ds2Damage.attackRating(key, upgradeLevel, stats, twoHanded, leftHand, -1, 0,
				buffs[0], buffs[1]);
		Bullets.Def root = (spell.bullet() == null ? null : spell.bullets().get(spell.bullet()));
		if (root != null && root.damage() != null && root.damage().flatElement() >= 1 && root.damage().flatElement() <= 4) {
			attack[root.damage().flatElement()] += root.damage().flat();
		}
		// Homing range: WeaponParam +0x40 x SpellParam +0xb4 x LockOnParam f1 (FUN_140337fb0).
		float range = factors[1] * spell.rangeFactor() * LOCK_ON_BULLET_RANGE;
		return new Cast(spellId, prep, attack(cast), cast, attack, catalyst, key, range, leftHand);
	}

	/**
	 * What the Start payload of a cast plays on clients: the failed cast, the spell's Prepare (its locks held until
	 * the cast Start replaces it) or its cast animation.
	 */
	public static Attack castAttack(ResourceLocation id, boolean castPhase) {
		if (FAILED_CAST.equals(id)) {
			return failedCast();
		}
		Spells.Spell spell = SoulsContent.spell(id);
		if (spell == null) {
			return null;
		}
		if (castPhase) {
			return attack(SoulsContent.castAnimation(spell.cast()));
		}
		Spells.CastAnimation p = SoulsContent.castAnimation(spell.prepare());
		// The server ends the wait; this bound only keeps a lost Start from locking the client for ever.
		float bound = p.duration() + 10;
		return new Attack(p.animation(), 0, bound, List.of(), 0, 0, 0, bound, bound, bound, bound, 0, 0, false,
				p.leftAuthored(), List.of(), List.of(), 0, List.of(), List.of());
	}

	/**
	 * The cast animation's TAE 2300 events: each distinct time launches the spell's bullet (slots 0 and 5 fire
	 * together; only the hit slot is filled) and spends one use (FUN_14037d300 reacts to the bullet id). Returns the
	 * number of events done.
	 */
	static int fire(ServerPlayer player, Cast cast, int done, float t) {
		List<Float> times = cast.castAnimation().bullets().stream().map(b -> b[0]).distinct().toList();
		while (done < times.size() && t >= times.get(done)) {
			done++;
			Spells.Spell spell = SoulsContent.spell(cast.spell());
			Bullets.Def root = spell == null ? null : (spell.bullet() == null ? null : spell.bullets().get(spell.bullet()));
			if (root == null && (spell == null || spell.script() == null)) {
				continue;
			}
			spendUse(player);
			if (root != null) {
				launch(player, cast, spell, root, times.get(done - 1));
			}
			SoulsScripts.onCast(player, cast.spell(), cast.attack(), cast.catalystKey(), cast.homingRange());
		}
		return done;
	}
	/** ChrCommonParam 100 +0x70 / +0x74 and +0x78 / +0x7c: the head look-at limits (casting.md section 7.7). */
	private static final double LOOK_YAW = 0.5236;
	private static final double LOOK_UP = 0.2618;
	private static final double LOOK_DOWN = 0.7854;

	/**
	 * Launches the spell's bullet (FUN_140337fb0). Direction: without lock-on the character's facing, flat
	 * (PlayerLookAtCtrl looks straight ahead); locked on, towards the target point within the head's look-at limits
	 * (+-30 deg, +15 / -45 deg), then the bullet's auto-aim turns it further, at most +0x1c sideways and +0x20 in
	 * pitch, aiming where the target will be (distance / speed ahead, target speed capped at 8; +0x2c height,
	 * +0x2d horizontal). Then the BulletParam forward offset +0x10. The homing target is the lock-on target.
	 * Not ported: the look-at smoothing, the first-person aim camera and homing on a lock-on candidate (+0x07).
	 */
	private static void launch(ServerPlayer player, Cast cast, Spells.Spell spell, Bullets.Def root, float time) {
		net.minecraft.world.entity.Entity target = lockTarget(player);
		if (origin(player, cast, root, time) == null) {
			SoulsMode.LOGGER.error("[BSE] spell {}: no bullet origin (catalyst dummy poly or bone data missing)", cast.spell());
			return;
		}
		// The emitter follows the caster: origin and look direction are taken again for every shot.
		dev.soulsmode.entity.SoulsBullet.emit(player.serverLevel(), player, root, spell.bullets(), () -> {
			net.minecraft.world.phys.Vec3 origin = origin(player, cast, root, time);
			return origin == null ? null : new dev.soulsmode.entity.SoulsBullet.Frame(origin, look(player, origin, target));
		}, cast.attack().clone(), cast.catalystKey(), target != null && target.isAlive() ? target : null,
				cast.homingRange(), cast.spell());
	}

	/**
	 * PlayerLookAtCtrl (casting.md section 7.7): without a target the character's facing, flat; with one, towards its
	 * point within the head's limits (+-30 deg, +15 / -45 deg). The bullet's own auto-aim comes after (SoulsBullet).
	 */
	private static net.minecraft.world.phys.Vec3 look(ServerPlayer player, net.minecraft.world.phys.Vec3 origin,
			net.minecraft.world.entity.Entity target) {
		double yaw = Math.toRadians(player.getYRot());
		if (target == null || !target.isAlive()) {
			return new net.minecraft.world.phys.Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		}
		net.minecraft.world.phys.Vec3 to = new net.minecraft.world.phys.Vec3(target.getX(), target.getY(0.5), target.getZ())
				.subtract(origin);
		double dyaw = Math.atan2(Math.sin(Math.atan2(-to.x, to.z) - yaw), Math.cos(Math.atan2(-to.x, to.z) - yaw));
		dyaw = Math.max(-LOOK_YAW, Math.min(LOOK_YAW, dyaw));
		double pitch = Math.max(-LOOK_DOWN, Math.min(LOOK_UP, Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z))));
		double y = yaw + dyaw;
		return new net.minecraft.world.phys.Vec3(-Math.sin(y) * Math.cos(pitch), Math.sin(pitch), Math.cos(y) * Math.cos(pitch));
	}

	/** Metres of the DS2 rig to Minecraft blocks: tools/re/ds2_anims.py PX_PER_M (12 px per 0.97 m) / 16 px. */
	private static final double BLOCKS_PER_METRE = 12.0 / 0.97 / 16.0;
	private static final java.util.Map<String, java.util.Map<Integer, float[]>> BUILT_IN_DUMMIES = Weapons.dummyPolys();

	/**
	 * The bullet origin (docs/research/dummypoly.md): the catalyst's dummy poly PDP +0x31 (200, or 1 on the grip) on the
	 * animated R_Weapon bone at the bullet time, from the character's model space to the world, scaled like the
	 * retargeted rig. A left-hand cast mirrors it: the left plays the same animation mirrored (casting.md section 6).
	 */
	private static net.minecraft.world.phys.Vec3 origin(ServerPlayer player, Cast cast, Bullets.Def root, float time) {
		float[] bone = cast.castAnimation().bulletBone(time);
		Weapons.Def pack = SoulsContent.weapon(cast.catalyst());
		java.util.Map<Integer, float[]> dummies = pack != null ? pack.dummyPolys()
				: BUILT_IN_DUMMIES.getOrDefault(cast.catalyst().getPath(), java.util.Map.of());
		float[] d = root.damage() == null ? null : dummies.get(root.damage().dummyPoly());
		if (bone == null || d == null) {
			return null;
		}
		double[] luf = new double[3];
		for (int i = 0; i < 3; i++) {
			luf[i] = bone[1 + i] + bone[4 + i * 3] * d[0] + bone[5 + i * 3] * d[1] + bone[6 + i * 3] * d[2];
		}
		double left = (cast.leftHand() ? -luf[0] : luf[0]) * BLOCKS_PER_METRE;
		double up = luf[1] * BLOCKS_PER_METRE;
		double forward = luf[2] * BLOCKS_PER_METRE;
		double yaw = Math.toRadians(player.getYRot());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		// Yaw 0 faces +z; its left is +x.
		double lx = Math.cos(yaw);
		double lz = Math.sin(yaw);
		return new net.minecraft.world.phys.Vec3(player.getX() + fx * forward + lx * left, player.getY() + up,
				player.getZ() + fz * forward + lz * left);
	}

	/** The failed cast (StickSpellNoBullet): its CancelControl windows, no bullet, nothing spent. */
	public static Attack failedCast() {
		return Objects.requireNonNull(attack(Spells.builtInCastAnimation(Spells.FAILED_CAST)));
	}

	/** One cast animation as an Attack: a missing CancelControl track does not lock; AttackAll locks every hand. */
	static Attack attack(Spells.CastAnimation a) {
		if (a == null) {
			return null;
		}
		float all = Float.isNaN(a.attackLock()) ? 0 : a.attackLock();
		return new Attack(a.animation(), 0, a.duration(), List.of(),
				Float.isNaN(a.avoidStart()) ? 0 : a.avoidStart(), Float.isNaN(a.avoidEnd()) ? 0 : a.avoidEnd(),
				Float.isNaN(a.moveLock()) ? 0 : a.moveLock(), lock(a.lightLock(), all), lock(a.heavyLock(), all),
				lock(a.leftLightLock(), all), lock(a.leftHeavyLock(), all), 0, 0, false, a.leftAuthored(), List.of(),
				List.of(), 0, List.of(), a.regenBlock());
	}

	private static float lock(float own, float all) {
		return Math.max(Float.isNaN(own) ? 0 : own, all);
	}

	/** The catalyst a hand holds, or null when that hand's weapon cannot cast anything. */
	static ResourceLocation catalyst(ServerPlayer player, boolean leftHand) {
		ResourceLocation id = SoulsWeapons.movesetFor(leftHand ? player.getOffhandItem() : player.getMainHandItem());
		int[] schools = castSchools(id);
		for (int s : schools) {
			if (s != 0) {
				return id;
			}
		}
		return null;
	}
}
