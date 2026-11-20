package com.extremo;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExtremoMod implements ModInitializer {
	public static final String MOD_ID = "extremo";
	public static final Logger LOGGER = LoggerFactory.getLogger("Extremo");

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(ExtremoMod::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ExtremoData.save();
			WorldResetter.resetState();
		});
		ServerTickEvents.END_SERVER_TICK.register(ExtremoMod::onEndTick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DeathPanel.markDirty());

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity instanceof ServerPlayer player) {
				WorldResetter.onPlayerDeath(player.level().getServer(), player);
			}
		});

		ServerPlayerEvents.JOIN.register(ExtremoMod::onPlayerJoin);
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> ExtremoCommands.register(dispatcher));
	}

	private static void onServerStarted(MinecraftServer server) {
		WorldResetter.resetState();
		ExtremoConfig.load();
		ExtremoData.load();
		ResourcePack.init();
		ExtremoData data = ExtremoData.get();
		data.rememberSeed(server.getWorldGenSettings().options().seed());
		ExtremoData.save();
		DeathPanel.refresh(server);
		LOGGER.info("[Extremo] Listo. Mundo #{} | seed {} | {} participantes | {} muertes en total.",
			data.numeroMundo, data.seedActual, data.participantes.size(), data.totalMuertes);

		if (data.reinicioPendiente) {
			if (ExtremoConfig.get().reinicioActivado) {
				LOGGER.info("[Extremo] El servidor se apago en mitad de un reinicio: se generara el mundo nuevo ahora.");
				WorldResetter.resumePendingReset();
			} else {
				data.reinicioPendiente = false;
				ExtremoData.save();
			}
		}
	}

	/** Tiempo maximo que se suma por tick: los parones (p. ej. al generar un mundo) no cuentan. */
	private static final long MAX_MS_PER_TICK = 250L;
	private static long lastTickNanos;
	private static long lastShownSecond = -1L;
	private static long lastSaveSecond;

	private static void onEndTick(MinecraftServer server) {
		WorldResetter.tick(server);
		long now = System.nanoTime();
		long elapsedMs = lastTickNanos == 0L ? 0L : Math.min(MAX_MS_PER_TICK, Math.max(0L, (now - lastTickNanos) / 1_000_000L));
		lastTickNanos = now;

		ExtremoData data = ExtremoData.get();
		if (!server.getPlayerList().getPlayers().isEmpty() && !WorldResetter.isCountingDown()) {
			data.tiempoMundoMs += elapsedMs;
		}

		long second = data.tiempoMundoMs / 1000L;
		boolean newSecond = second != lastShownSecond;
		lastShownSecond = second;
		if (second - lastSaveSecond >= 60L || second < lastSaveSecond) {
			lastSaveSecond = second;
			ExtremoData.save();
		}

		DeathPanel.tick(server, newSecond);
	}

	private static void onPlayerJoin(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ResourcePack.sendTo(player);
		ExtremoData data = ExtremoData.get();
		if (data.registerParticipant(player.getUUID(), player.getGameProfile().name())) {
			ExtremoData.save();
		}

		// Siempre se actualiza para que salga como conectado (punto verde).
		DeathPanel.markDirty();

		player.sendSystemMessage(WorldResetter.prefix()
			.append(Component.literal("Mundo #" + data.numeroMundo).withStyle(ChatFormatting.GOLD))
			.append(Component.literal(" - Si alguien muere, el mundo se reinicia con una seed nueva. ¡Suerte!").withStyle(ChatFormatting.GRAY)));
	}
}
