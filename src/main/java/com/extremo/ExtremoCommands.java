package com.extremo;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

/**
 * /extremo                      -> informacion y ranking de muertes (todos)
 * /extremo reiniciar            -> fuerza un reinicio del mundo (OP)
 * /extremo cancelar             -> cancela la cuenta atras (OP)
 * /extremo activar|desactivar   -> activa/desactiva el reinicio al morir (OP)
 * /extremo cuentaatras <seg>    -> segundos de cuenta atras (OP)
 * /extremo muertes <jugador> <n>-> cambia las muertes de un jugador (OP)
 * /extremo borrarestadisticas   -> pone todas las muertes y el contador de mundos a 0 (OP)
 */
public final class ExtremoCommands {
	private ExtremoCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("extremo")
			.executes(ctx -> info(ctx.getSource()))
			.then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
			.then(Commands.literal("reiniciar")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> forceReset(ctx.getSource())))
			.then(Commands.literal("cancelar")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> cancel(ctx.getSource())))
			.then(Commands.literal("activar")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> setEnabled(ctx.getSource(), true)))
			.then(Commands.literal("desactivar")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> setEnabled(ctx.getSource(), false)))
			.then(Commands.literal("cuentaatras")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("segundos", IntegerArgumentType.integer(0, 300))
					.executes(ctx -> setCountdown(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "segundos")))))
			.then(Commands.literal("muertes")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("jugador", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
						ExtremoData.get().participantes.values().stream().map(p -> p.nombre), builder))
					.then(Commands.argument("cantidad", IntegerArgumentType.integer(0))
						.executes(ctx -> setDeaths(ctx.getSource(), StringArgumentType.getString(ctx, "jugador"),
							IntegerArgumentType.getInteger(ctx, "cantidad"))))))
			.then(Commands.literal("borrarestadisticas")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> clearStats(ctx.getSource())));
		dispatcher.register(root);
	}

	private static int info(CommandSourceStack source) {
		ExtremoData data = ExtremoData.get();
		ExtremoConfig config = ExtremoConfig.get();
		MutableComponent text = Component.empty()
			.append(Component.literal("━━━━ ☠ MINECRAFT EXTREMO ☠ ━━━━").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
			.append(line("Mundo actual", "#" + data.numeroMundo))
			.append(line("Muertes totales", String.valueOf(data.totalMuertes)))
			.append(line("Reinicio al morir", config.reinicioActivado ? "activado" : "DESACTIVADO"))
			.append(line("Cuenta atrás", config.segundosCuentaAtras + " s"));
		if (!data.ultimaMuerte.isEmpty()) {
			text.append(line("Última muerte", data.ultimaMuerte));
		}

		if (Commands.LEVEL_GAMEMASTERS.check(source.permissions())) {
			text.append(line("Seed", String.valueOf(source.getServer().getWorldGenSettings().options().seed())));
		}

		text.append(Component.literal("\nMuertes por jugador:").withStyle(ChatFormatting.GOLD));
		List<ExtremoData.Participante> ranking = data.ranking();
		if (ranking.isEmpty()) {
			text.append(Component.literal("\n  (todavía nadie)").withStyle(ChatFormatting.GRAY));
		}

		int position = 1;
		for (ExtremoData.Participante p : ranking) {
			text.append(Component.literal("\n  " + position++ + ". ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(p.nombre).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(String.valueOf(p.muertes)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		}

		source.sendSuccess(() -> text, false);
		return 1;
	}

	private static Component line(String label, String value) {
		return Component.literal("\n" + label + ": ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(value).withStyle(ChatFormatting.WHITE));
	}

	private static int forceReset(CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		if (!server.isDedicatedServer()) {
			source.sendFailure(Component.literal("El reinicio del mundo solo funciona en un servidor dedicado."));
			return 0;
		}

		if (!WorldResetter.startCountdown(server, "Reinicio forzado por " + source.getTextName())) {
			source.sendFailure(Component.literal("Ya hay un reinicio en marcha."));
			return 0;
		}

		server.getPlayerList().broadcastSystemMessage(WorldResetter.prefix()
			.append(Component.literal(source.getTextName() + " ha forzado un reinicio del mundo. Reinicio en "
				+ ExtremoConfig.get().segundosCuentaAtras + " segundos.").withStyle(ChatFormatting.YELLOW)), false);
		return 1;
	}

	private static int cancel(CommandSourceStack source) {
		if (!WorldResetter.cancel(source.getServer())) {
			source.sendFailure(Component.literal("No hay ninguna cuenta atrás que cancelar."));
			return 0;
		}

		source.getServer().getPlayerList().broadcastSystemMessage(WorldResetter.prefix()
			.append(Component.literal(source.getTextName() + " ha cancelado el reinicio del mundo.").withStyle(ChatFormatting.GREEN)), false);
		return 1;
	}

	private static int setEnabled(CommandSourceStack source, boolean enabled) {
		ExtremoConfig.get().reinicioActivado = enabled;
		ExtremoConfig.save();
		source.sendSuccess(() -> WorldResetter.prefix().append(Component.literal(enabled
			? "Reinicio al morir ACTIVADO."
			: "Reinicio al morir DESACTIVADO (las muertes se siguen contando).").withStyle(ChatFormatting.YELLOW)), true);
		return 1;
	}

	private static int setCountdown(CommandSourceStack source, int seconds) {
		ExtremoConfig.get().segundosCuentaAtras = seconds;
		ExtremoConfig.save();
		source.sendSuccess(() -> WorldResetter.prefix()
			.append(Component.literal("Cuenta atrás establecida en " + seconds + " segundos.").withStyle(ChatFormatting.YELLOW)), true);
		return 1;
	}

	private static int setDeaths(CommandSourceStack source, String name, int amount) {
		ExtremoData data = ExtremoData.get();
		ExtremoData.Participante participante = data.findByName(name);
		if (participante == null) {
			source.sendFailure(Component.literal("No hay ningún participante llamado " + name + " (tiene que haber entrado al servidor al menos una vez)."));
			return 0;
		}

		data.totalMuertes = Math.max(0, data.totalMuertes - participante.muertes + amount);
		participante.muertes = amount;
		ExtremoData.save();
		DeathPanel.refresh(source.getServer());
		source.sendSuccess(() -> WorldResetter.prefix()
			.append(Component.literal("Muertes de " + participante.nombre + " = " + amount).withStyle(ChatFormatting.YELLOW)), true);
		return 1;
	}

	private static int clearStats(CommandSourceStack source) {
		ExtremoData data = ExtremoData.get();
		data.participantes.values().forEach(p -> p.muertes = 0);
		data.totalMuertes = 0;
		data.numeroMundo = 1;
		data.ultimaMuerte = "";
		ExtremoData.save();
		DeathPanel.refresh(source.getServer());
		source.sendSuccess(() -> WorldResetter.prefix()
			.append(Component.literal("Estadísticas borradas: todas las muertes a 0 y contador de mundos a #1.").withStyle(ChatFormatting.YELLOW)), true);
		return 1;
	}
}
