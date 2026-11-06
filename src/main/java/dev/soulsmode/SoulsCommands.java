package dev.soulsmode;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.soulsmode.content.SoulsContent;
import dev.soulsmode.ds2.Attributes;
import dev.soulsmode.ds2.DerivedStats;
import dev.soulsmode.ds2.Ds2Data;
import dev.soulsmode.ds2.StartingClasses.StartingClass;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

public final class SoulsCommands {
	private static final DynamicCommandExceptionType UNKNOWN_CLASS = new DynamicCommandExceptionType(
			name -> Component.translatable("commands.soulsmode.souls.unknown_class", name));
	private static final DynamicCommandExceptionType UNKNOWN_ATTRIBUTE = new DynamicCommandExceptionType(
			name -> Component.translatable("commands.soulsmode.souls.unknown_attribute", name));

	private SoulsCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		// /gamemode souls [targets]: Brigadier merges this into the vanilla gamemode node,
		// and the literal wins over vanilla's game type argument.
		dispatcher.register(Commands.literal("gamemode")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("souls")
						.executes(ctx -> setSoulsMode(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException())))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(ctx -> setSoulsMode(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"))))));

		// /souls status | class <name> | levelup <attribute>: temporary until there is a bonfire UI.
		dispatcher.register(Commands.literal("souls")
				.then(Commands.literal("status")
						.executes(ctx -> status(ctx.getSource(), ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("class")
						.then(Commands.argument("name", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
										Ds2Data.startingClasses().all().stream().map(StartingClass::id), builder))
								.executes(ctx -> chooseClass(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
				.then(Commands.literal("levelup")
						.then(Commands.argument("attribute", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Attributes.NAMES, builder))
								.executes(ctx -> levelUp(ctx.getSource(), StringArgumentType.getString(ctx, "attribute")))))
				.then(Commands.literal("invade").requires(source -> source.hasPermission(2))
						.executes(ctx -> SoulsInvasions.invade(ctx.getSource().getPlayerOrException()) ? 1 : 0))
				.then(Commands.literal("enemy").requires(source -> source.hasPermission(2))
						.then(Commands.argument("id", net.minecraft.commands.arguments.ResourceLocationArgument.id())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(SoulsContent.enemyIds(), builder))
								.executes(ctx -> spawnEnemy(ctx.getSource(),
										net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "id"))))));
	}

	/** Spawns a datapack enemy (docs/datapacks.md, "Enemigos") where the command runs. */
	private static int spawnEnemy(CommandSourceStack source, net.minecraft.resources.ResourceLocation id) {
		if (SoulsContent.enemy(id) == null) {
			source.sendFailure(Component.translatable("commands.soulsmode.souls.enemy.unknown", id.toString()));
			return 0;
		}
		dev.soulsmode.entity.SoulsEnemy enemy = dev.soulsmode.entity.SoulsEntities.ENEMY.create(source.getLevel());
		enemy.moveTo(source.getPosition().x, source.getPosition().y, source.getPosition().z, source.getRotation().y, 0);
		enemy.setEnemy(id);
		source.getLevel().addFreshEntity(enemy);
		source.sendSuccess(() -> Component.translatable("commands.soulsmode.souls.enemy.spawned", id.toString()), true);
		return 1;
	}

	private static int setSoulsMode(CommandSourceStack source, Collection<ServerPlayer> targets) {
		int changed = 0;
		for (ServerPlayer player : targets) {
			if (SoulsGameMode.isActive(player)) {
				continue;
			}
			SoulsGameMode.enter(player);
			changed++;
			if (source.getEntity() == player) {
				source.sendSuccess(() -> Component.translatable("commands.soulsmode.gamemode.success.self"), true);
			} else {
				source.sendSuccess(() -> Component.translatable("commands.soulsmode.gamemode.success.other", player.getDisplayName()), true);
			}
		}
		return changed;
	}

	private static int chooseClass(CommandSourceStack source, String name) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		StartingClass startingClass = Ds2Data.startingClasses().byId(name).orElseThrow(() -> UNKNOWN_CLASS.create(name));
		SoulsPlayer.chooseClass(player, startingClass);
		source.sendSuccess(() -> Component.translatable("commands.soulsmode.souls.class", startingClass.name()), false);
		return status(source, player);
	}

	private static int levelUp(CommandSourceStack source, String attribute) throws CommandSyntaxException {
		int slot = Attributes.NAMES.indexOf(attribute);
		if (slot < 0) {
			throw UNKNOWN_ATTRIBUTE.create(attribute);
		}
		ServerPlayer player = source.getPlayerOrException();
		SoulsPlayer.LevelUpResult result = SoulsPlayer.levelUp(player, slot);
		if (result != SoulsPlayer.LevelUpResult.OK) {
			source.sendFailure(Component.translatable("commands.soulsmode.souls.levelup." + result.name().toLowerCase()));
			return 0;
		}
		return status(source, player);
	}

	private static int status(CommandSourceStack source, ServerPlayer player) {
		SoulsData data = SoulsPlayer.data(player).orElse(null);
		if (data == null) {
			source.sendFailure(Component.translatable("commands.soulsmode.souls.levelup.no_class"));
			return 0;
		}
		Attributes attributes = data.toAttributes();
		int soulLevel = attributes.soulLevel();
		String className = Ds2Data.startingClasses().byIndex(data.classIndex()).map(StartingClass::name).orElse("?");
		DerivedStats derived = DerivedStats.compute(attributes, Ds2Data.physicalStats());
		source.sendSuccess(() -> Component.translatable("commands.soulsmode.souls.status.header", className, soulLevel,
				player.experienceLevel, dev.soulsmode.ds2.LevelUpSoulsTable.EXPERIENCE_LEVELS.cost(soulLevel), data.soulMemory()),
				false);
		StringBuilder line = new StringBuilder();
		for (int slot = 0; slot < Attributes.NAMES.size(); slot++) {
			line.append(Attributes.NAMES.get(slot)).append(' ').append(attributes.get(slot)).append("  ");
		}
		String attributesLine = line.toString().trim();
		source.sendSuccess(() -> Component.literal(attributesLine), false);
		source.sendSuccess(() -> Component.translatable("commands.soulsmode.souls.status.derived", derived.maxHp(),
				(int) derived.maxStamina(), String.format("%.1f", derived.maxEquipLoad()), derived.attunementSlots(),
				dev.soulsmode.ds2.Agility.shown(attributes)), false);
		return soulLevel;
	}
}
