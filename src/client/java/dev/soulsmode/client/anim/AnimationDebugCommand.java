package dev.soulsmode.client.anim;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import dev.soulsmode.SoulsMode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * Client-only {@code /soulsanim <name>} and {@code /soulsanim stop} to preview animations
 * on the local player (press F5 for third person). Not synced to other players.
 */
public final class AnimationDebugCommand {
	private AnimationDebugCommand() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommandManager.literal("soulsanim")
				.then(ClientCommandManager.literal("stop")
						.executes(ctx -> {
							SoulsAnimations.stop(ctx.getSource().getPlayer());
							return 1;
						}))
				.then(ClientCommandManager.argument("name", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
								PlayerAnimResources.getModAnimations(SoulsMode.MOD_ID).keySet(), builder))
						.executes(ctx -> {
							String name = StringArgumentType.getString(ctx, "name");
							if (!SoulsAnimations.play(ctx.getSource().getPlayer(), SoulsMode.id(name))) {
								ctx.getSource().sendError(Component.translatable("commands.soulsmode.soulsanim.unknown", name));
								return 0;
							}
							return 1;
						})));
	}
}
