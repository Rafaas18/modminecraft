package com.extremo;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * Panel lateral (sidebar del marcador) con las muertes de cada participante.
 * Usa el marcador vanilla, asi que los jugadores NO necesitan instalar nada.
 */
public final class DeathPanel {
	public static final String OBJECTIVE = "extremo_muertes";
	/** El sidebar vanilla muestra como maximo 15 lineas. */
	private static final int MAX_LINES = 15;

	private static final int TOP_INFO = 1_000_000_002;
	private static final int TOP_SEPARATOR = 1_000_000_001;
	private static final int BOTTOM_SEPARATOR = -1;
	private static final int BOTTOM_TOTAL = -2;

	private DeathPanel() {
	}

	/** Reconstruye el panel completo a partir de los datos guardados. */
	public static void refresh(MinecraftServer server) {
		ServerScoreboard scoreboard = server.getScoreboard();
		Objective old = scoreboard.getObjective(OBJECTIVE);
		if (old != null) {
			scoreboard.removeObjective(old);
		}

		ExtremoConfig config = ExtremoConfig.get();
		ExtremoData data = ExtremoData.get();
		Objective objective = scoreboard.addObjective(
			OBJECTIVE,
			ObjectiveCriteria.DUMMY,
			Component.literal(config.tituloPanel).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
			ObjectiveCriteria.RenderType.INTEGER,
			false,
			null
		);

		int infoLines = 0;
		if (config.mostrarNumeroMundo) {
			setLine(scoreboard, objective, "extremo.mundo", TOP_INFO,
				Component.literal("Mundo ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal("#" + data.numeroMundo).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
			setLine(scoreboard, objective, "extremo.separador1", TOP_SEPARATOR, Component.literal(" "));
			infoLines += 2;
		}

		if (config.mostrarTotalMuertes) {
			setLine(scoreboard, objective, "extremo.separador2", BOTTOM_SEPARATOR, Component.literal("  "));
			setLine(scoreboard, objective, "extremo.total", BOTTOM_TOTAL,
				Component.literal("Total: ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(String.valueOf(data.totalMuertes)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
			infoLines += 2;
		}

		List<ExtremoData.Participante> ranking = data.ranking();
		int maxPlayers = MAX_LINES - infoLines;
		for (int i = 0; i < ranking.size() && i < maxPlayers; i++) {
			ExtremoData.Participante participante = ranking.get(i);
			ScoreAccess score = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(participante.nombre), objective);
			score.set(participante.muertes);
			score.display(Component.literal(participante.nombre).withStyle(ChatFormatting.WHITE));
		}

		scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
	}

	private static void setLine(ServerScoreboard scoreboard, Objective objective, String holder, int order, Component text) {
		ScoreAccess score = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective);
		score.set(order);
		score.display(text);
		score.numberFormatOverride(BlankFormat.INSTANCE);
	}
}
