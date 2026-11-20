package com.extremo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * Panel lateral (sidebar del marcador vanilla) con el mundo actual, el tiempo que lleva vivo,
 * el record y las muertes de cada participante. Los jugadores NO necesitan instalar nada.
 *
 * Cada linea es una "puntuacion" falsa: el texto de la izquierda va en el nombre a mostrar y el
 * de la derecha en un formato fijo, y el orden se controla con el valor de la puntuacion.
 */
public final class DeathPanel {
	public static final String OBJECTIVE = "extremo_muertes";
	/** El sidebar vanilla muestra como maximo 15 lineas. */
	private static final int MAX_LINES = 15;
	private static final String TIME_HOLDER = "extremo.tiempo";

	private static final TextColor LABEL = TextColor.fromRgb(0xA8A8B3);
	private static final TextColor VALUE = TextColor.fromRgb(0xF2F2F2);
	private static final TextColor GOLD = TextColor.fromRgb(0xFFC23D);
	private static final TextColor DEATH = TextColor.fromRgb(0xFF4D4D);
	private static final TextColor NO_DEATHS = TextColor.fromRgb(0x5E5E66);
	private static final TextColor ONLINE = TextColor.fromRgb(0x4CE06A);
	private static final TextColor OFFLINE = TextColor.fromRgb(0x5E5E66);
	private static final TextColor HEADER = TextColor.fromRgb(0xFF6B4A);

	private static boolean dirty;

	private DeathPanel() {
	}

	/** Pide reconstruir el panel al final del tick actual. */
	public static void markDirty() {
		dirty = true;
	}

	/** Llamado al final de cada tick: reconstruye si hace falta y actualiza el tiempo cada segundo. */
	public static void tick(MinecraftServer server, boolean newSecond) {
		if (dirty) {
			dirty = false;
			refresh(server);
		} else if (newSecond) {
			updateTime(server);
		}
	}

	private record Line(String holder, Component left, Component right, boolean optional) {
	}

	/** Reconstruye el panel completo a partir de los datos guardados. */
	public static void refresh(MinecraftServer server) {
		dirty = false;
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
			gradientTitle(config.tituloPanel, parseColor(config.colorTitulo1, 0xFF3B3B), parseColor(config.colorTitulo2, 0xFFB020)),
			ObjectiveCriteria.RenderType.INTEGER,
			false,
			BlankFormat.INSTANCE
		);

		List<Line> top = new ArrayList<>();
		if (config.mostrarNumeroMundo) {
			top.add(new Line("extremo.mundo", label("✦ ", "Mundo", GOLD), text("#" + data.numeroMundo, GOLD, true), false));
		}

		if (config.mostrarTiempo) {
			top.add(new Line(TIME_HOLDER, label("⌛ ", "Tiempo", VALUE), text(formatTicks(data.ticksMundo), VALUE, false), false));
		}

		if (config.mostrarRecord && data.recordTicks > 0) {
			top.add(new Line("extremo.record", label("★ ", "Récord", GOLD), text(formatTicks(data.recordTicks), GOLD, false), true));
		}

		Set<String> online = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			online.add(player.getUUID().toString());
		}

		List<Line> players = new ArrayList<>();
		for (Map.Entry<String, ExtremoData.Participante> entry : data.rankingEntries()) {
			ExtremoData.Participante p = entry.getValue();
			boolean isOnline = online.contains(entry.getKey());
			MutableComponent name = Component.empty()
				.append(Component.literal("● ").withStyle(Style.EMPTY.withColor(isOnline ? ONLINE : OFFLINE)))
				.append(Component.literal(p.nombre).withStyle(Style.EMPTY.withColor(isOnline ? VALUE : LABEL)));
			Component deaths = text("☠ " + p.muertes, p.muertes > 0 ? DEATH : NO_DEATHS, p.muertes > 0);
			players.add(new Line("extremo.p." + entry.getKey(), name, deaths, false));
		}

		List<Line> bottom = new ArrayList<>();
		if (config.mostrarTotalMuertes) {
			bottom.add(new Line("extremo.total", label("", "Total", LABEL), text("☠ " + data.totalMuertes, DEATH, true), false));
		}

		// Montar las lineas con separadores y quitar lo opcional si no cabe.
		List<Line> lines = new ArrayList<>();
		if (!top.isEmpty()) {
			lines.add(spacer(1));
			lines.addAll(top);
		}

		lines.add(spacer(2));
		lines.add(new Line("extremo.cabecera", Component.literal("MUERTES").withStyle(Style.EMPTY.withColor(HEADER).withBold(true)), Component.empty(), true));
		lines.addAll(players);
		if (!bottom.isEmpty()) {
			lines.add(spacer(3));
			lines.addAll(bottom);
		}

		for (int i = lines.size() - 1; lines.size() > MAX_LINES && i >= 0; i--) {
			if (lines.get(i).optional()) {
				lines.remove(i);
			}
		}

		while (lines.size() > MAX_LINES) {
			// Siguen sin caber: se quitan los ultimos jugadores (los de menos muertes).
			int lastPlayer = -1;
			for (int i = 0; i < lines.size(); i++) {
				if (lines.get(i).holder().startsWith("extremo.p.")) {
					lastPlayer = i;
				}
			}

			if (lastPlayer < 0) {
				break;
			}

			lines.remove(lastPlayer);
		}

		int order = lines.size();
		for (Line line : lines) {
			ScoreAccess score = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(line.holder()), objective);
			score.set(order--);
			score.display(line.left());
			score.numberFormatOverride(numberFormat(line.right()));
		}

		scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
	}

	/** Actualiza solo la linea del tiempo (se llama una vez por segundo). */
	private static void updateTime(MinecraftServer server) {
		if (!ExtremoConfig.get().mostrarTiempo) {
			return;
		}

		ServerScoreboard scoreboard = server.getScoreboard();
		Objective objective = scoreboard.getObjective(OBJECTIVE);
		if (objective == null) {
			refresh(server);
			return;
		}

		ScoreHolder holder = ScoreHolder.forNameOnly(TIME_HOLDER);
		if (scoreboard.getPlayerScoreInfo(holder, objective) == null) {
			return;
		}

		scoreboard.getOrCreatePlayerScore(holder, objective)
			.numberFormatOverride(numberFormat(text(formatTicks(ExtremoData.get().ticksMundo), VALUE, false)));
	}

	private static NumberFormat numberFormat(Component right) {
		return right.getString().isEmpty() ? BlankFormat.INSTANCE : new FixedFormat(right);
	}

	private static Line spacer(int n) {
		// Cada separador necesita un nombre distinto; se muestra como una linea vacia.
		return new Line("extremo.separador" + n, Component.literal(" ".repeat(n)), Component.empty(), true);
	}

	private static Component label(String icon, String label, TextColor iconColor) {
		MutableComponent result = Component.empty();
		if (!icon.isEmpty()) {
			result.append(Component.literal(icon).withStyle(Style.EMPTY.withColor(iconColor)));
		}

		return result.append(Component.literal(label).withStyle(Style.EMPTY.withColor(LABEL)));
	}

	private static Component text(String value, TextColor color, boolean bold) {
		return Component.literal(value).withStyle(Style.EMPTY.withColor(color).withBold(bold));
	}

	/** Titulo con degradado de color letra a letra. */
	static Component gradientTitle(String title, int from, int to) {
		MutableComponent result = Component.empty();
		int[] codePoints = title.codePoints().toArray();
		int steps = Math.max(1, codePoints.length - 1);
		for (int i = 0; i < codePoints.length; i++) {
			float t = (float) i / steps;
			int r = Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
			int g = Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
			int b = Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
			result.append(Component.literal(new String(Character.toChars(codePoints[i])))
				.withStyle(Style.EMPTY.withColor(TextColor.fromRgb((r << 16) | (g << 8) | b)).withBold(true)));
		}

		return result;
	}

	static int parseColor(String hex, int fallback) {
		if (hex == null) {
			return fallback;
		}

		String clean = hex.trim().replace("#", "");
		if (!clean.matches("[0-9a-fA-F]{6}")) {
			return fallback;
		}

		return Integer.parseInt(clean, 16);
	}

	/** 1234 ticks -> "1:01", 80000 ticks -> "1:06:40". */
	static String formatTicks(long ticks) {
		long totalSeconds = Math.max(0, ticks / 20);
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		if (hours > 0) {
			return String.format("%d:%02d:%02d", hours, minutes, seconds);
		}

		return String.format("%d:%02d", minutes, seconds);
	}
}
