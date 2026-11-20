package com.extremo;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.locks.LockSupport;

import com.extremo.mixin.MinecraftServerAccessor;
import com.extremo.mixin.WorldGenSettingsAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Se encarga de la cuenta atras y de regenerar el mundo con una seed nueva SIN apagar el
 * servidor (necesario en hostings, donde no se puede reiniciar el proceso a mano).
 *
 * Proceso:
 * 1. Cuenta atras con titulos y sonidos.
 * 2. Se expulsa a todos los jugadores con un mensaje.
 * 3. Cuando el servidor esta vacio: se cierran las dimensiones, se borran sus archivos y los
 *    datos de los jugadores, se cambia la seed y se vuelven a crear las dimensiones.
 * 4. Los jugadores vuelven a entrar y aparecen en un mundo totalmente nuevo.
 */
public final class WorldResetter {
	private enum Phase {
		IDLE,
		COUNTDOWN,
		KICKING
	}

	private static final int TICKS_PER_SECOND = 20;
	private static final long DRAIN_TIMEOUT_NANOS = 15_000_000_000L;
	private static final String[] LEGACY_FOLDERS = {"region", "entities", "poi", "DIM-1", "DIM1", "playerdata", "advancements", "stats"};

	private static Phase phase = Phase.IDLE;
	private static int ticksLeft;
	private static int kickTicks;
	private static String reason = "";
	private static boolean regenerating;

	private WorldResetter() {
	}

	public static boolean isRegenerating() {
		return regenerating;
	}

	/** true mientras se esta expulsando a la gente o regenerando: nadie puede entrar. */
	public static boolean isClosedForJoining() {
		return phase == Phase.KICKING || regenerating;
	}

	public static boolean isCountingDown() {
		return phase == Phase.COUNTDOWN;
	}

	public static void resetState() {
		phase = Phase.IDLE;
		ticksLeft = 0;
		kickTicks = 0;
		reason = "";
		regenerating = false;
	}

	/** Llamado cuando un jugador muere. */
	public static void onPlayerDeath(MinecraftServer server, ServerPlayer player) {
		String name = player.getGameProfile().name();
		ExtremoData data = ExtremoData.get();
		int deaths = data.addDeath(player.getUUID(), name);
		ExtremoData.save();
		DeathPanel.refresh(server);
		ExtremoMod.LOGGER.info("[Extremo] {} ha muerto (muertes: {}, total: {})", name, deaths, data.totalMuertes);

		// El audio de muerte suena en cuanto alguien muere, a todos los jugadores.
		playSoundToAll(server, ResourcePack.deathSound(), ExtremoConfig.get().volumenSonidoMuerte, 1.0F);

		if (!ExtremoConfig.get().reinicioActivado) {
			server.getPlayerList().broadcastSystemMessage(
				prefix().append(Component.literal(name + " ha muerto. El reinicio automático está desactivado.").withStyle(ChatFormatting.GRAY)), false);
			return;
		}

		if (phase != Phase.IDLE) {
			return;
		}

		if (!server.isDedicatedServer()) {
			server.getPlayerList().broadcastSystemMessage(
				prefix().append(Component.literal("El reinicio del mundo solo funciona en un servidor dedicado. La muerte se ha contado igualmente.")
					.withStyle(ChatFormatting.YELLOW)), false);
			return;
		}

		startCountdown(server, name + " ha muerto");
		Component title = Component.literal("☠ " + name + " ☠").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
		Component subtitle = Component.literal("ha muerto - el mundo se va a reiniciar").withStyle(ChatFormatting.GRAY);
		broadcastTitle(server, title, subtitle);
		server.getPlayerList().broadcastSystemMessage(
			prefix().append(Component.literal(name).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
				.append(Component.literal(" ha muerto (" + deaths + (deaths == 1 ? " muerte" : " muertes") + "). ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal("El mundo se reiniciará en " + ExtremoConfig.get().segundosCuentaAtras + " segundos.").withStyle(ChatFormatting.YELLOW)),
			false);
	}

	/** Empieza la cuenta atras. Devuelve false si ya habia un reinicio en marcha. */
	public static boolean startCountdown(MinecraftServer server, String why) {
		if (phase != Phase.IDLE) {
			return false;
		}

		reason = why;
		ticksLeft = ExtremoConfig.get().segundosCuentaAtras * TICKS_PER_SECOND;
		phase = Phase.COUNTDOWN;
		ExtremoData.get().reinicioPendiente = true;
		ExtremoData.save();
		return true;
	}

	/** Cancela la cuenta atras (solo mientras no se haya empezado a expulsar a la gente). */
	public static boolean cancel(MinecraftServer server) {
		if (phase != Phase.COUNTDOWN) {
			return false;
		}

		resetState();
		ExtremoData.get().reinicioPendiente = false;
		ExtremoData.save();
		server.getPlayerList().broadcastAll(new ClientboundSetActionBarTextPacket(Component.literal("Reinicio cancelado").withStyle(ChatFormatting.GREEN)));
		return true;
	}

	/** Usado al arrancar el servidor si se apago en mitad de un reinicio. */
	public static void resumePendingReset() {
		reason = "Reinicio pendiente";
		phase = Phase.KICKING;
		kickTicks = 0;
	}

	public static void tick(MinecraftServer server) {
		switch (phase) {
			case IDLE -> {
			}
			case COUNTDOWN -> tickCountdown(server);
			case KICKING -> tickKicking(server);
		}
	}

	private static void tickCountdown(MinecraftServer server) {
		if (ticksLeft % TICKS_PER_SECOND == 0) {
			int seconds = ticksLeft / TICKS_PER_SECOND;
			if (seconds > 0) {
				server.getPlayerList().broadcastAll(new ClientboundSetActionBarTextPacket(
					Component.literal("El mundo se reiniciará en ").withStyle(ChatFormatting.RED)
						.append(Component.literal(seconds + "s").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))));
				if (seconds <= 3) {
					playSoundToAll(server, SoundEvents.NOTE_BLOCK_HAT, 1.0F, 0.6F);
				}
			}
		}

		if (ticksLeft <= 0) {
			phase = Phase.KICKING;
			kickTicks = 0;
			kickEveryone(server);
			return;
		}

		ticksLeft--;
	}

	private static void tickKicking(MinecraftServer server) {
		if (server.getPlayerList().getPlayers().isEmpty()) {
			regenerate(server);
			return;
		}

		kickTicks++;
		if (kickTicks % (5 * TICKS_PER_SECOND) == 0) {
			ExtremoMod.LOGGER.warn("[Extremo] Todavia quedan jugadores conectados, volviendo a expulsarlos...");
			kickEveryone(server);
		}
	}

	private static void kickEveryone(MinecraftServer server) {
		Component message = kickMessage();
		for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
			player.connection.disconnect(message);
		}
	}

	private static Component kickMessage() {
		ExtremoData data = ExtremoData.get();
		return Component.empty()
			.append(Component.literal("☠ MINECRAFT EXTREMO ☠").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
			.append(Component.literal("\n\n"))
			.append(Component.literal(reason).withStyle(ChatFormatting.RED))
			.append(Component.literal("\n\nGenerando el mundo #" + (data.numeroMundo + 1) + " con una seed nueva...").withStyle(ChatFormatting.GRAY))
			.append(Component.literal("\n\nVuelve a entrar en unos segundos.").withStyle(ChatFormatting.YELLOW));
	}

	public static Component joinBlockedMessage() {
		return Component.empty()
			.append(Component.literal("☠ MINECRAFT EXTREMO ☠").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
			.append(Component.literal("\n\nSe está generando un mundo nuevo.").withStyle(ChatFormatting.GRAY))
			.append(Component.literal("\nVuelve a entrar en unos segundos.").withStyle(ChatFormatting.YELLOW));
	}

	/**
	 * Regenera todas las dimensiones con una seed nueva. Se ejecuta en el hilo del servidor al
	 * final de un tick y con el servidor vacio.
	 */
	private static void regenerate(MinecraftServer server) {
		ExtremoData data = ExtremoData.get();
		long newSeed = data.pickNewSeed();
		long startTime = System.currentTimeMillis();
		ExtremoMod.LOGGER.info("[Extremo] Regenerando el mundo #{} con la seed {}...", data.numeroMundo + 1, newSeed);

		MinecraftServerAccessor accessor = (MinecraftServerAccessor) server;
		Map<ResourceKey<Level>, ServerLevel> levels = accessor.extremo$getLevels();
		regenerating = true;
		try {
			// 1. Terminar el trabajo pendiente de chunks (igual que hace vanilla al apagar).
			drainChunkWork(levels);

			// 2. Descargar y cerrar todas las dimensiones.
			List<ServerLevel> oldLevels = new ArrayList<>(levels.values());
			for (ServerLevel level : oldLevels) {
				ServerLevelEvents.UNLOAD.invoker().onLevelUnload(server, level);
			}

			for (ServerLevel level : oldLevels) {
				try {
					level.close();
				} catch (IOException e) {
					ExtremoMod.LOGGER.error("[Extremo] Error al cerrar la dimension {}", level.dimension().identifier(), e);
				}
			}

			levels.clear();

			// 3. Borrar los archivos del mundo y de los jugadores.
			Path worldRoot = accessor.extremo$getStorageSource().getLevelPath(LevelResource.ROOT).toAbsolutePath().normalize();
			deleteWorldFiles(worldRoot);

			// 4. Nueva seed.
			WorldGenSettings genSettings = server.getWorldGenSettings();
			((WorldGenSettingsAccessor) (Object) genSettings).extremo$setOptions(genSettings.options().withSeed(OptionalLong.of(newSeed)));
			genSettings.setDirty();

			// 5. Reiniciar datos globales del mundo.
			server.getWorldData().overworldData().setInitialized(false);
			server.getRandomSequences().clear();
			server.setWeatherParameters(0, 0, false, false);

			// 6. Volver a crear las dimensiones (genera el nuevo spawn).
			accessor.extremo$createLevels();
			server.updateMobSpawningFlags();
			accessor.extremo$updateEffectiveRespawnData();
		} finally {
			regenerating = false;
		}

		runSilentCommand(server, "time set 0");

		data.numeroMundo++;
		data.rememberSeed(newSeed);
		data.reinicioPendiente = false;
		ExtremoData.save();
		resetState();
		DeathPanel.refresh(server);
		server.saveEverything(true, false, true);
		ExtremoMod.LOGGER.info("[Extremo] Mundo #{} listo en {} ms (seed {}).", data.numeroMundo, System.currentTimeMillis() - startTime, newSeed);
	}

	private static void drainChunkWork(Map<ResourceKey<Level>, ServerLevel> levels) {
		long deadline = System.nanoTime() + DRAIN_TIMEOUT_NANOS;
		while (levels.values().stream().anyMatch(level -> level.getChunkSource().chunkMap.hasWork())) {
			if (System.nanoTime() > deadline) {
				ExtremoMod.LOGGER.warn("[Extremo] Tiempo de espera agotado esperando a los chunks, se continua igualmente.");
				return;
			}

			for (ServerLevel level : levels.values()) {
				level.getChunkSource().deactivateTicketsOnClosing();
				level.getChunkSource().tick(() -> true, false);
				while (level.getChunkSource().pollTask()) {
					// Ejecutar todas las tareas pendientes del hilo principal para esta dimension.
				}
			}

			LockSupport.parkNanos(1_000_000L);
		}
	}

	private static void deleteWorldFiles(Path worldRoot) {
		List<Path> targets = new ArrayList<>();
		targets.add(worldRoot.resolve("dimensions"));
		targets.add(worldRoot.resolve("players"));
		for (String legacy : LEGACY_FOLDERS) {
			targets.add(worldRoot.resolve(legacy));
		}

		for (Path target : targets) {
			if (!target.startsWith(worldRoot) || target.equals(worldRoot) || !Files.exists(target)) {
				continue;
			}

			try {
				deleteRecursively(target);
			} catch (IOException e) {
				ExtremoMod.LOGGER.error("[Extremo] No se pudo borrar {}", target, e);
			}
		}
	}

	private static void deleteRecursively(Path path) throws IOException {
		Files.walkFileTree(path, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.deleteIfExists(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				if (exc != null) {
					throw exc;
				}

				Files.deleteIfExists(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void runSilentCommand(MinecraftServer server, String command) {
		try {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
		} catch (Exception e) {
			ExtremoMod.LOGGER.warn("[Extremo] No se pudo ejecutar '{}'", command, e);
		}
	}

	static MutableComponent prefix() {
		return Component.literal("[Extremo] ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
	}

	private static void broadcastTitle(MinecraftServer server, Component title, Component subtitle) {
		server.getPlayerList().broadcastAll(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
		server.getPlayerList().broadcastAll(new ClientboundSetSubtitleTextPacket(subtitle));
		server.getPlayerList().broadcastAll(new ClientboundSetTitleTextPacket(title));
	}

	private static void playSoundToAll(MinecraftServer server, Holder<SoundEvent> sound, float volume, float pitch) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.connection.send(new ClientboundSoundPacket(
				sound, SoundSource.MASTER, player.getX(), player.getY(), player.getZ(), volume, pitch,
				player.getRandom().nextLong()));
		}
	}
}
