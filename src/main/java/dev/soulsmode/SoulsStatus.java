package dev.soulsmode;

import dev.soulsmode.ds2.Ds2Damage;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DS2's petrification and curse on the player (docs/research/petrification.md): two meters of the status manager
 * (FUN_140145c60, types 6 and 3). Only datapack sources fill them (the user's choice, 2026-11-18): enemy damage rows
 * ({@code petrify} / {@code curse}), {@code /souls status} and {@code bse.status}.
 * <ul>
 * <li>A hit adds {@code value x (1 - resistance)} (bleed.md section 2 with no guard, no effects, Z = 1; the enemy row's
 * value already x EP +0xd8); reaching 100 (DAT_1410acb18) empties the meter and fires the state.</li>
 * <li>0.5 s after a hit the meter drains: 100 in 45 s (petrification, ChrCommonParam 100 +0x2dc / +0x2e0) or 22 s
 * (curse, +0x2cc / +0x2d0), FUN_1401464b0.</li>
 * <li>Petrification full: SpEffect 901100 and FUN_14016a400 take all the HP: instant death (damage type
 * {@code soulsmode:petrified}); the death counts +5 hollowing (CriminalAndDeceasedParam u8@6, death type 3) and the
 * body stays in stone (DeadTypeParam 101010: the stone FullBodySfx goes at 5.25 s).</li>
 * <li>Curse full: SpEffect 900400: +1 hollowing (1000[00] (17, 0, 1) -> b8+0x316 -> FUN_14038b49e), the message
 * common 21000 and the entry 呪 for 2.0 s, which blocks more curse build-up.</li>
 * <li>Resistance: armour (ARP +0x28 / +0x2c by level) + stats (PSPL +0x50 of (3 Adp + Vgr) / 4, +0x54 of
 * (3 Adp + Att) / 4), FUN_140381350.</li>
 * <li>Resting at a bonfire and respawning clear both (SpEffect 110000010: multiplier x 100 for 3 s).</li>
 * </ul>
 * Not ported (docs/todo-bse-extensible.md "Petrificación"): the guard's reduction (shield output [8] / [9], field not
 * identified), the lack-of-stats factor on armour, rings and items that change the resistance, the Ring of Soul
 * Protection, the curse SFX 653 and the stone FFX 3419 (not opened).
 */
public final class SoulsStatus {
	public enum Kind {
		PETRIFY("petrify", 45), CURSE("curse", 22);

		public final String id;
		final float drainSeconds;

		Kind(String id, float drainSeconds) {
			this.id = id;
			this.drainSeconds = drainSeconds;
		}

		public static Kind byId(String id) {
			for (Kind k : values()) {
				if (k.id.equals(id)) {
					return k;
				}
			}
			return null;
		}
	}

	/** DAT_1410acb18. */
	public static final float THRESHOLD = 100;
	/** FUN_1401462a0: 0.5 s before the meter drains. */
	private static final int DELAY_TICKS = 10;
	/** SpEffect 900400: 100070[00] (1, 872, 3, 2.0), the entry 呪 that blocks curse build-up. */
	private static final int CURSED_TICKS = 40;
	/** CriminalAndDeceasedParam row 0 u8@6: a petrification death (type 3) adds 5. */
	public static final int PETRIFY_HOLLOWING = 5;
	/** SpEffect 900400 1000[00] (17, 0, 1): +1, clamped to 0..32 (FUN_14038b49e). */
	private static final int CURSE_HOLLOWING = 1;
	private static final int MAX_HOLLOWING = 32;
	/** a00_17_0710..0713 DieCurse_00..03 (TAE 170710..170713) and their .nsa length. */
	private static final int DIE_CURSE = 170710;
	private static final float DIE_CURSE_SECONDS = 4.3333335f;
	/** DeadTypeParam 101010 +0x1c: the stone FullBodySfx goes at 5.25 s (StatueLivingEntityMixin removes the body). */
	private static final float STONE_SECONDS = 5.25f;
	public static final ResourceKey<DamageType> PETRIFIED = ResourceKey.create(Registries.DAMAGE_TYPE,
			SoulsMode.id("petrified"));

	private static final class Meters {
		final float[] value = new float[2];
		final int[] delay = new int[2];
		int cursed;
		float[] sent = {-1, -1, -1};
	}

	private static final Map<UUID, Meters> METERS = new HashMap<>();

	private SoulsStatus() {
	}

	/**
	 * A hit's build-up {@code amount} (the attack's status value) on a player in Souls Mode; outside the mode, in
	 * creative or spectator nothing happens.
	 */
	public static void buildUp(ServerPlayer player, Kind kind, float amount) {
		if (!(amount > 0) || !player.isAlive() || player.isCreative() || player.isSpectator()
				|| !SoulsGameMode.isActive(player)) {
			return;
		}
		float points = amount * Math.max(0, Math.min(1, 1 - resistance(player, kind)));
		if (points <= 0) {
			return;
		}
		Meters m = METERS.computeIfAbsent(player.getUUID(), id -> new Meters());
		int i = kind.ordinal();
		if (kind == Kind.CURSE && m.cursed > 0) {
			return;
		}
		if (m.value[i] + points >= THRESHOLD) {
			m.value[i] = 0;
			m.delay[i] = 0;
			sync(player, m);
			if (kind == Kind.PETRIFY) {
				petrify(player);
			} else {
				curse(player, m);
			}
			return;
		}
		m.value[i] += points;
		m.delay[i] = DELAY_TICKS;
		sync(player, m);
	}

	private static void petrify(ServerPlayer player) {
		player.setAttached(SoulsAttachments.PETRIFIED_DEATH, true);
		Holder<DamageType> type = player.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
				.getHolderOrThrow(PETRIFIED);
		player.hurt(new DamageSource(type), Float.MAX_VALUE);
		if (player.isAlive()) {
			// Something kept it alive (a totem, an invulnerable player): no stone.
			player.removeAttached(SoulsAttachments.PETRIFIED_DEATH);
			return;
		}
		// SM_Die, DieCategory 110 (transition 0x2085): one of DieCurse_00..03 at random (RandomFloat [0, 8) on the
		// switch {1, 3, 5, 7}), 4.333 s; the body then keeps its last frame (node 0x183c not identified) until it goes.
		int clip = DIE_CURSE + player.getRandom().nextInt(4);
		SoulsAttack.playClip(player, "soulsmode:ds2_" + clip, "soulsmode:ds2_" + clip + "_hold", DIE_CURSE_SECONDS,
				STONE_SECONDS);
	}

	private static void curse(ServerPlayer player, Meters m) {
		m.cursed = CURSED_TICKS;
		player.setAttached(SoulsAttachments.HOLLOWING,
				Math.max(0, Math.min(MAX_HOLLOWING, SoulsHollow.count(player) + CURSE_HOLLOWING)));
		SoulsStats.apply(player);
		player.displayClientMessage(Component.translatable("message.soulsmode.cursed"), true);
	}

	/** The hollowing a death adds: 5 for a petrification death (type 3), else {@code normal}. */
	public static int deathHollowing(ServerPlayer player, int normal) {
		return Boolean.TRUE.equals(player.getAttached(SoulsAttachments.PETRIFIED_DEATH)) ? PETRIFY_HOLLOWING : normal;
	}

	/** The player's resistance (0..1) to {@code kind}: stats and armour. */
	public static float resistance(ServerPlayer player, Kind kind) {
		boolean curse = kind == Kind.CURSE;
		var stats = SoulsPlayer.data(player).map(SoulsData::toAttributes).orElse(null);
		float total = stats == null ? 0 : Ds2Damage.statusStatAbsorption(curse, stats);
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.FEET, EquipmentSlot.LEGS};
		for (int i = 0; i < 4; i++) {
			ItemStack stack = player.getItemBySlot(slots[i]);
			if (stack.getItem() instanceof ArmorItem armor) {
				total += Ds2Damage.pieceStatus(curse, SoulsUpgrade.armorSet(armor.getMaterial()), i,
						SoulsUpgrade.level(stack));
			}
		}
		return Math.max(0, Math.min(1, total));
	}

	public static float meter(ServerPlayer player, Kind kind) {
		Meters m = METERS.get(player.getUUID());
		return m == null ? 0 : m.value[kind.ordinal()];
	}

	/** Resting at a bonfire or respawning (SpEffect 110000010): both meters and the curse entry end. */
	public static void clear(ServerPlayer player) {
		Meters m = METERS.remove(player.getUUID());
		if (m != null || player.hasAttached(SoulsAttachments.STATUS_METERS)) {
			player.removeAttached(SoulsAttachments.STATUS_METERS);
		}
	}

	public static void tick(MinecraftServer server) {
		var it = METERS.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			Meters m = e.getValue();
			if (m.cursed > 0) {
				m.cursed--;
			}
			for (Kind kind : Kind.values()) {
				int i = kind.ordinal();
				if (m.delay[i] > 0) {
					m.delay[i]--;
				} else if (m.value[i] > 0) {
					m.value[i] = Math.max(0, m.value[i] - THRESHOLD / kind.drainSeconds / 20f);
				}
			}
			sync(player, m);
			if (m.value[0] <= 0 && m.value[1] <= 0 && m.cursed <= 0) {
				it.remove();
				player.removeAttached(SoulsAttachments.STATUS_METERS);
			}
		}
	}

	/**
	 * Sends {petrification, curse, the curse entry's time left / 2 s} when a meter moved by a tenth of a point or more
	 * (the HUD shows a meter above 2 %, and the entry as the "active" bar).
	 */
	private static void sync(ServerPlayer player, Meters m) {
		float cursed = m.cursed / (float) CURSED_TICKS;
		if (Math.abs(m.value[0] - m.sent[0]) < 0.1f && Math.abs(m.value[1] - m.sent[1]) < 0.1f
				&& Math.abs(cursed - m.sent[2]) < 0.01f) {
			return;
		}
		m.sent = new float[] {m.value[0], m.value[1], cursed};
		player.setAttached(SoulsAttachments.STATUS_METERS, List.of(m.value[0], m.value[1], cursed));
	}
}
