package dev.soulsmode;

import dev.soulsmode.entity.ThrownSoulsItem;
import dev.soulsmode.item.SoulsItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Thrown DS2 items (docs/research/thrown_items.md). The use is DS2's: the item's use animation, the item leaving the
 * hand at its TAE 220000 (Weapons.lloydsTalisman). What it does when it lands is the user's adaptation:
 * <ul>
 * <li>Lloyd's Talisman (user, 2026-11-03): where it lands, a zone of {@link #LLOYD_RADIUS} blocks lasts
 * {@link #LLOYD_SECONDS} s; nothing inside it heals (no healing of any kind: food, potions, Regeneration...), except the
 * one who threw it.</li>
 * <li>Firebomb (user, 2026-11-03): a 3 x 3 Minecraft explosion that breaks no blocks, smoke, and a 10 % chance to set
 * one of those blocks on fire.</li>
 * </ul>
 * Not from DS2 (the effects are adapted): the flight (Minecraft's thrown item, speed {@link #THROW_SPEED}) and the
 * zone's particles (a design choice to show its edge).
 */
public final class SoulsThrown {
	public static final ResourceLocation LLOYD_USE = SoulsMode.id("lloyds_talisman_use");
	public static final dev.soulsmode.ds2.Weapons.Effigy LLOYD = dev.soulsmode.ds2.Weapons.lloydsTalisman();
	/** User (2026-11-03): radius of the zone, blocks. */
	public static final double LLOYD_RADIUS = 3;
	/** User (2026-11-03): how long the zone lasts. */
	public static final int LLOYD_SECONDS = 30;
	public static final ResourceLocation FIREBOMB_USE = SoulsMode.id("firebomb_use");
	public static final dev.soulsmode.ds2.Weapons.Effigy FIREBOMB = dev.soulsmode.ds2.Weapons.firebomb();
	/** User (2026-11-03): a 3 x 3 explosion, Minecraft's damage, no blocks broken: explosion power 1.5. */
	private static final float FIREBOMB_POWER = 1.5f;
	/** User (2026-11-03): 10 % to set one block of the 3 x 3 on fire. */
	private static final float FIREBOMB_FIRE_CHANCE = 0.10f;
	/** Design choice: Minecraft's snowball throw speed. */
	private static final float THROW_SPEED = 1.5f;

	private record Using(long start, InteractionHand hand, net.minecraft.world.item.Item item,
			dev.soulsmode.ds2.Weapons.Effigy data) {
	}

	/** A talisman zone: where, until when, and who threw it (not affected). */
	private record Zone(ServerLevel level, Vec3 center, long until, UUID owner) {
	}

	private static final Map<UUID, Using> USING = new HashMap<>();
	private static final List<Zone> ZONES = new ArrayList<>();
	/** Entities inside a zone this tick (game time of the mark). */
	private static final Map<LivingEntity, Long> BLOCKED = new WeakHashMap<>();

	/** User (2026-11-03): the wandering trader sells it for 2 emeralds, 4 uses. */
	private static final int TRADER_PRICE = 2;
	private static final int TRADER_USES = 4;
	/** User: pillager outpost and woodland mansion chests, 25 %; witches killed by a player, 10 %. */
	private static final float CHEST_CHANCE = 0.25f;
	private static final float WITCH_CHANCE = 0.10f;

	private SoulsThrown() {
	}

	/** User (2026-11-03, proposal A): 1 emerald for 4 firebombs, 8 uses; dungeon, mineshaft and outpost chests 30 %, 1-3. */
	private static final float FIREBOMB_CHEST_CHANCE = 0.30f;

	public static void init() {
		var bombChests = java.util.Set.of(net.minecraft.world.level.storage.loot.BuiltInLootTables.SIMPLE_DUNGEON,
				net.minecraft.world.level.storage.loot.BuiltInLootTables.ABANDONED_MINESHAFT,
				net.minecraft.world.level.storage.loot.BuiltInLootTables.PILLAGER_OUTPOST);
		net.fabricmc.fabric.api.loot.v3.LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (source.isBuiltin() && bombChests.contains(key)) {
				builder.withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
						.add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(SoulsItems.FIREBOMB)
								.apply(net.minecraft.world.level.storage.loot.functions.SetItemCountFunction.setCount(
										net.minecraft.world.level.storage.loot.providers.number.UniformGenerator.between(1, 3))))
						.when(net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition
								.randomChance(FIREBOMB_CHEST_CHANCE)));
			}
		});
		var chests = java.util.Set.of(net.minecraft.world.level.storage.loot.BuiltInLootTables.PILLAGER_OUTPOST,
				net.minecraft.world.level.storage.loot.BuiltInLootTables.WOODLAND_MANSION);
		var witch = net.minecraft.world.entity.EntityType.WITCH.getDefaultLootTable();
		net.fabricmc.fabric.api.loot.v3.LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			if (chests.contains(key)) {
				builder.withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
						.add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(SoulsItems.LLOYDS_TALISMAN))
						.when(net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition
								.randomChance(CHEST_CHANCE)));
			} else if (key.equals(witch)) {
				builder.withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
						.add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(SoulsItems.LLOYDS_TALISMAN))
						.when(net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition.killedByPlayer())
						.when(net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition
								.randomChance(WITCH_CHANCE)));
			}
		});
	}

	/** Firebombs at the wandering trader (WanderingTraderMixin). */
	public static net.minecraft.world.item.trading.MerchantOffer firebombOffer() {
		return new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(
				net.minecraft.world.item.Items.EMERALD, 1), new ItemStack(SoulsItems.FIREBOMB, 4), 8, 1, 0.05f);
	}

	/** Added to every new wandering trader's trades (WanderingTraderMixin). */
	public static net.minecraft.world.item.trading.MerchantOffer traderOffer() {
		return new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(
				net.minecraft.world.item.Items.EMERALD, TRADER_PRICE), new ItemStack(SoulsItems.LLOYDS_TALISMAN),
				TRADER_USES, 1, 0.05f);
	}

	/** Using the talisman: the throw animation when nothing locks it. Souls Mode only, like the other DS2 items. */
	public static boolean useLloyd(ServerPlayer player, InteractionHand hand) {
		return use(player, hand, SoulsItems.LLOYDS_TALISMAN, LLOYD_USE, LLOYD);
	}

	/** Using a firebomb: its DS2 throw animation, the bomb leaving the hand at its TAE 220000. */
	public static boolean useFirebomb(ServerPlayer player, InteractionHand hand) {
		return use(player, hand, SoulsItems.FIREBOMB, FIREBOMB_USE, FIREBOMB);
	}

	private static boolean use(ServerPlayer player, InteractionHand hand, net.minecraft.world.item.Item item,
			ResourceLocation id, dev.soulsmode.ds2.Weapons.Effigy data) {
		if (!SoulsGameMode.isActive(player) || USING.containsKey(player.getUUID())
				|| !SoulsAttack.startItemUse(player, id, data.use())) {
			return false;
		}
		USING.put(player.getUUID(), new Using(player.serverLevel().getGameTime(), hand, item, data));
		return true;
	}

	public static void tick(MinecraftServer server) {
		USING.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Using using = entry.getValue();
			if (player == null || !player.isAlive() || !SoulsAttack.isDoing(player, using.data().use())) {
				return true;
			}
			if ((player.serverLevel().getGameTime() - using.start()) / 20f < using.data().effectAt()) {
				return false;
			}
			ItemStack stack = player.getItemInHand(using.hand());
			if (stack.is(using.item())) {
				ThrownSoulsItem thrown = new ThrownSoulsItem(player.serverLevel(), player);
				thrown.setItem(stack.copyWithCount(1));
				thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, THROW_SPEED, 1);
				player.serverLevel().addFreshEntity(thrown);
				stack.consume(1, player);
			}
			return true;
		});
		ZONES.removeIf(zone -> zone.level().getGameTime() >= zone.until() || zone.level().getServer() != server);
		for (Zone zone : ZONES) {
			long now = zone.level().getGameTime();
			AABB box = AABB.ofSize(zone.center(), LLOYD_RADIUS * 2, LLOYD_RADIUS * 2, LLOYD_RADIUS * 2);
			for (LivingEntity entity : zone.level().getEntitiesOfClass(LivingEntity.class, box,
					e -> !e.getUUID().equals(zone.owner())
							&& e.getBoundingBox().getCenter().distanceTo(zone.center()) <= LLOYD_RADIUS)) {
				BLOCKED.put(entity, now);
			}
			if (now % 10 == 0) {
				for (int i = 0; i < 16; i++) {
					double a = Math.PI * 2 * i / 16;
					zone.level().sendParticles(ParticleTypes.WHITE_ASH, zone.center().x + Math.cos(a) * LLOYD_RADIUS,
							zone.center().y + 0.1, zone.center().z + Math.sin(a) * LLOYD_RADIUS, 1, 0, 0.05, 0, 0);
				}
			}
		}
	}

	/** A thrown item landed. */
	public static void land(ThrownSoulsItem thrown, HitResult hit) {
		if (!(thrown.level() instanceof ServerLevel level)) {
			return;
		}
		if (thrown.getItem().is(SoulsItems.LLOYDS_TALISMAN)) {
			UUID owner = thrown.getOwner() == null ? null : thrown.getOwner().getUUID();
			ZONES.add(new Zone(level, hit.getLocation(), level.getGameTime() + LLOYD_SECONDS * 20L, owner));
		} else if (thrown.getItem().is(SoulsItems.FIREBOMB)) {
			Vec3 at = hit.getLocation();
			// A Minecraft explosion that breaks no blocks (its own damage and knockback), and smoke.
			level.explode(thrown, thrown.getOwner() == null ? null
					: level.damageSources().explosion(thrown, thrown.getOwner()), null, at.x, at.y, at.z,
					FIREBOMB_POWER, false, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, 24, 0.8, 0.4, 0.8, 0.02);
			if (level.random.nextFloat() < FIREBOMB_FIRE_CHANCE) {
				// One block of the 3 x 3 around the landing point catches fire, where fire can stand.
				net.minecraft.core.BlockPos base = net.minecraft.core.BlockPos.containing(at);
				net.minecraft.core.BlockPos pos = base.offset(level.random.nextInt(3) - 1, 0, level.random.nextInt(3) - 1);
				for (int dy = 1; dy >= -1; dy--) {
					net.minecraft.core.BlockPos p = pos.above(dy);
					if (net.minecraft.world.level.block.BaseFireBlock.canBePlacedAt(level, p, net.minecraft.core.Direction.UP)) {
						level.setBlockAndUpdate(p, net.minecraft.world.level.block.BaseFireBlock.getState(level, p));
						break;
					}
				}
			}
		}
	}

	/** Whether the entity stands in a talisman zone now (this tick or the last one). */
	public static boolean healBlocked(LivingEntity entity) {
		Long at = BLOCKED.get(entity);
		return at != null && entity.level().getGameTime() - at <= 1;
	}

	public static void forget(ServerPlayer player) {
		USING.remove(player.getUUID());
	}
}
