package dev.soulsmode;

import dev.soulsmode.ds2.Weapons;
import dev.soulsmode.item.SoulsItems;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pine resins.
 * <ul>
 * <li>Gathering and crafting are the user's design (2026-10-31): a glass bottle used on a stripped spruce log fills
 * with resin (the log stays); smelting and the recipes are data files.</li>
 * <li>The buff is DS2's (docs/research/resins.md): a00_18_0060 ItemUseEnchantWeapon (walking allowed; dodge, attack
 * and guard locked 2.667 s), the SpEffect applies at 0.667 s and lasts 90 s on the right hand (it stays through a
 * weapon swap); a new resin replaces the old one. Its added element = (B + 40) x 0.2 on top of the weapon's physical
 * base B (FUN_140391fe0 with SpEffect values 20 / 40), here applied as that fraction of the hit's damage: Minecraft
 * damage is not DS2's attack rating.</li>
 * <li>Poison (Rotten): the victim's DS2 poison was not traced; by the user's choice it applies Minecraft's Poison I
 * for 5 s.</li>
 * </ul>
 * Not ported: the Charcoal resin's bit 23 (unidentified), the victim's elemental defences, DS2's weapon SFX (particles
 * stand in, a design choice). When DS2 takes the resin from the inventory is not traced: here, when the effect applies.
 */
public final class SoulsResin {
	public static final ResourceLocation RESIN_USE = SoulsMode.id("resin_use");
	public static final Weapons.Effigy USE = Weapons.resin();
	/** SpEffect 6025..6028 0000: 90 s. */
	private static final int DURATION_TICKS = 90 * 20;
	/** User's choice (2026-10-31): the Charcoal resin sets the target on fire like Fire Aspect I (4 s). */
	private static final float FIRE_SECONDS = 4;
	/** User's choice (2026-10-31): Lightning resin x2 on targets in water or winged (this tag). */
	public static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> WINGED =
			net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, SoulsMode.id("winged"));
	/** User's choice (2026-10-31): Dark resin x2 on End mobs, vexes and allays (this tag). */
	public static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> DARK_WEAK =
			net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, SoulsMode.id("dark_weak"));
	/** User's choice for the Rotten resin's poison (vanilla Poison I, 5 s). */
	private static final int POISON_TICKS = 5 * 20;

	public enum Element {
		LIGHTNING(ParticleTypes.ELECTRIC_SPARK), FIRE(ParticleTypes.FLAME), DARK(ParticleTypes.SQUID_INK),
		POISON(ParticleTypes.WITCH);

		final ParticleOptions particle;

		Element(ParticleOptions particle) {
			this.particle = particle;
		}
	}

	/**
	 * DS2 physical base B of each weapon at +0 (docs/research/resins.md, FUN_140391fe0): the resin's element is
	 * (B + 40) x 0.2, i.e. this fraction of the physical damage.
	 */
	private static final Map<ResourceLocation, Float> ELEMENT_FRACTION = new HashMap<>();

	static {
		Map<String, Float> base = Map.of("longsword", 112f, "battle_axe", 140f, "mace", 120f, "club", 105f, "spear", 105f,
				"pickaxe", 150f, "fists", 10f, "shield", 86f);
		base.forEach((name, b) -> ELEMENT_FRACTION.put(dev.soulsmode.api.SoulsWeapons.id(name), (b + 40) * 0.2f / b));
	}

	private record Buff(Element element, long until) {
	}

	private record Using(long start, InteractionHand hand, Element element) {
	}

	private static final Map<UUID, Buff> BUFFS = new HashMap<>();
	private static final Map<UUID, Using> USING = new HashMap<>();

	private SoulsResin() {
	}


	/** Resin gathering: a glass bottle used on a stripped spruce log fills with resin; the log stays. */
	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			if (!held.is(Items.GLASS_BOTTLE) || !level.getBlockState(hit.getBlockPos()).is(Blocks.STRIPPED_SPRUCE_LOG)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(SoulsItems.RESIN_BOTTLE)));
				level.playSound(null, hit.getBlockPos(), SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
			}
			return InteractionResult.sidedSuccess(level.isClientSide());
		});
	}

	public static Element elementOf(Item item) {
		if (item == SoulsItems.GOLD_PINE_RESIN) {
			return Element.LIGHTNING;
		}
		if (item == SoulsItems.CHARCOAL_PINE_RESIN) {
			return Element.FIRE;
		}
		if (item == SoulsItems.DARK_PINE_RESIN) {
			return Element.DARK;
		}
		return item == SoulsItems.ROTTEN_PINE_RESIN ? Element.POISON : null;
	}

	/** Using a resin: the enchant animation when nothing locks it (same locks as the torch). Souls Mode only. */
	public static boolean use(ServerPlayer player, InteractionHand hand, Element element) {
		if (!SoulsGameMode.isActive(player) || USING.containsKey(player.getUUID())
				|| !SoulsAttack.startItemUse(player, RESIN_USE, USE.use())) {
			return false;
		}
		USING.put(player.getUUID(), new Using(player.serverLevel().getGameTime(), hand, element));
		return true;
	}

	/** Gives the resin buff of an element directly (datapack consumables, dev.soulsmode.SoulsConsumables). */
	public static void buff(ServerPlayer player, Element element) {
		SoulsSpEffects.clearSlot(player, SoulsSpEffects.RIGHT_WEAPON_SLOT);
		BUFFS.put(player.getUUID(), new Buff(element, player.serverLevel().getGameTime() + DURATION_TICKS));
	}

	public static void tick(MinecraftServer server) {
		USING.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || !SoulsAttack.isDoing(player, USE.use())) {
				return true;
			}
			Using using = entry.getValue();
			long now = player.serverLevel().getGameTime();
			if ((now - using.start()) / 20f < USE.effectAt()) {
				return false;
			}
			ItemStack stack = player.getItemInHand(using.hand());
			if (elementOf(stack.getItem()) == using.element()) {
				stack.consume(1, player);
				// Same slot as the spell weapon buffs (speffects.md s.11.6): the newer replaces the other.
				SoulsSpEffects.clearSlot(player, SoulsSpEffects.RIGHT_WEAPON_SLOT);
				BUFFS.put(player.getUUID(), new Buff(using.element(), now + DURATION_TICKS));
			}
			return true;
		});
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Buff buff = BUFFS.get(player.getUUID());
			if (buff == null) {
				continue;
			}
			long now = player.serverLevel().getGameTime();
			if (now >= buff.until() || !player.isAlive() || !SoulsGameMode.isActive(player)) {
				BUFFS.remove(player.getUUID());
				continue;
			}
			if (now % 4 == 0) {
				// Near the right hand.
				double yaw = Math.toRadians(player.getYRot());
				double x = player.getX() - Math.cos(yaw) * 0.35 - Math.sin(yaw) * 0.3;
				double z = player.getZ() - Math.sin(yaw) * 0.35 + Math.cos(yaw) * 0.3;
				player.serverLevel().sendParticles(buff.element().particle, x, player.getY() + 0.9, z, 1, 0.08, 0.15, 0.08, 0);
			}
		}
	}

	/** The damage type the active resin adds to right-hand hits (Ds2Damage: 2 lightning, 3 fire, 4 dark), or -1. */
	public static int damageType(ServerPlayer player) {
		Buff buff = BUFFS.get(player.getUUID());
		if (buff == null) {
			return -1;
		}
		return switch (buff.element()) {
			case LIGHTNING -> 2;
			case FIRE -> 3;
			case DARK -> 4;
			// The Rotten resin's DS2 poison build-up is not ported: Minecraft's Poison stands in (onHit).
			case POISON -> -1;
		};
	}

	/** After a right-hand hit lands: the Rotten resin poisons; the Charcoal resin burns (the user's choices). */
	public static void onHit(ServerPlayer player, LivingEntity target) {
		Buff buff = BUFFS.get(player.getUUID());
		if (buff != null && buff.element() == Element.POISON) {
			target.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0), player);
		}
		if (buff != null && buff.element() == Element.FIRE && !target.fireImmune()) {
			target.igniteForSeconds(FIRE_SECONDS);
		}
	}

	/**
	 * The user's weaknesses (2026-10-31), on the resin's element only: lightning x2 on a target in water or winged
	 * ({@link #WINGED}), dark x2 on End mobs, vexes and allays ({@link #DARK_WEAK}). {@code attack} is per Ds2Damage type.
	 */
	public static void weakness(int resinType, LivingEntity target, float[] attack) {
		if (resinType == 2 && (target.isInWater() || target.getType().is(WINGED))
				|| resinType == 4 && target.getType().is(DARK_WEAK)) {
			attack[resinType] *= 2;
		}
	}

	public static void forget(ServerPlayer player) {
		BUFFS.remove(player.getUUID());
		USING.remove(player.getUUID());
	}
}
