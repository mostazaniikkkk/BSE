package dev.soulsmode;

import com.google.gson.JsonObject;
import dev.soulsmode.content.SoulsContent;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Datapack consumables (docs/datapacks.md): in Souls Mode, using a stack bound to one plays its item-use animation
 * through the same locks as the Human Effigy, and its effects apply at {@code effectAt} unless the use was cut short.
 */
public final class SoulsConsumables {
	private record Using(long start, InteractionHand hand, ResourceLocation id) {
	}

	private static final Map<UUID, Using> USING = new HashMap<>();
	/** Client side: whether the local player is in Souls Mode (set by the client initializer). */
	public static BooleanSupplier clientSoulsMode = () -> false;

	private SoulsConsumables() {
	}

	public static void init() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			ResourceLocation id = SoulsContent.consumableId(stack);
			if (id == null) {
				return InteractionResultHolder.pass(stack);
			}
			if (level.isClientSide()) {
				// Keeps the vanilla use (eating...) from starting; the server decides.
				return clientSoulsMode.getAsBoolean() ? InteractionResultHolder.success(stack)
						: InteractionResultHolder.pass(stack);
			}
			if (!(player instanceof ServerPlayer serverPlayer) || !SoulsGameMode.isActive(serverPlayer)) {
				return InteractionResultHolder.pass(stack);
			}
			return use(serverPlayer, hand, id) ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
		});
	}

	private static boolean use(ServerPlayer player, InteractionHand hand, ResourceLocation id) {
		SoulsContent.ConsumableDef def = SoulsContent.consumable(id);
		if (def == null || USING.containsKey(player.getUUID()) || !SoulsAttack.startItemUse(player, id, def.use().attack())) {
			return false;
		}
		USING.put(player.getUUID(), new Using(player.serverLevel().getGameTime(), hand, id));
		return true;
	}

	public static void tick(MinecraftServer server) {
		USING.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Using using = entry.getValue();
			SoulsContent.ConsumableDef def = SoulsContent.consumable(using.id());
			if (player == null || !player.isAlive() || def == null || !SoulsAttack.isDoing(player, def.use().attack())) {
				// Cut short (a dodge...) before the effect: nothing happens, the item is kept.
				return true;
			}
			if ((player.serverLevel().getGameTime() - using.start()) / 20f < def.use().effectAt()) {
				return false;
			}
			ItemStack stack = player.getItemInHand(using.hand());
			if (using.id().equals(SoulsContent.consumableId(stack))) {
				for (JsonObject effect : def.effects()) {
					try {
						apply(player, effect);
					} catch (RuntimeException ex) {
						SoulsMode.LOGGER.error("[BSE] consumable {} effect {} failed: {}", using.id(), effect, ex.toString());
					}
				}
				if (def.consume()) {
					stack.consume(1, player);
				}
			}
			return true;
		});
	}

	private static void apply(ServerPlayer player, JsonObject effect) {
		switch (effect.get("type").getAsString()) {
			case "restore_humanity" -> SoulsHollow.restore(player);
			case "weapon_buff" -> SoulsResin.buff(player,
					SoulsResin.Element.valueOf(effect.get("element").getAsString().toUpperCase(Locale.ROOT)));
			case "mob_effect" -> {
				ResourceLocation id = ResourceLocation.parse(effect.get("id").getAsString());
				var holder = player.registryAccess().registryOrThrow(Registries.MOB_EFFECT)
						.getHolder(ResourceKey.create(Registries.MOB_EFFECT, id))
						.orElseThrow(() -> new IllegalArgumentException("unknown effect " + id));
				player.addEffect(new MobEffectInstance(holder, effect.get("duration").getAsInt(),
						effect.has("amplifier") ? effect.get("amplifier").getAsInt() : 0));
			}
			case "function" -> {
				ResourceLocation id = ResourceLocation.parse(effect.get("id").getAsString());
				MinecraftServer server = player.getServer();
				var functions = server.getFunctions();
				var function = functions.get(id).orElseThrow(() -> new IllegalArgumentException("unknown function " + id));
				functions.execute(function, player.createCommandSourceStack().withSuppressedOutput().withPermission(2));
			}
			default -> throw new IllegalArgumentException(effect.get("type").getAsString());
		}
	}

	public static void forget(ServerPlayer player) {
		USING.remove(player.getUUID());
	}
}
