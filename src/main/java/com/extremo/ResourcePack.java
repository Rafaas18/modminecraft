package com.extremo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;

/**
 * Paquete de recursos con el audio de muerte. El mod lo lleva dentro, calcula su SHA-1 y se lo
 * envia a cada jugador al entrar, asi que los jugadores no tienen que instalar nada a mano.
 */
public final class ResourcePack {
	private static final String EMBEDDED_PATH = "/extremo-recursos.zip";

	private static String embeddedSha1 = "";

	private ResourcePack() {
	}

	/** Lee el paquete que va dentro del mod, calcula su SHA-1 y deja una copia en config/extremo/. */
	public static void init() {
		try (InputStream in = ResourcePack.class.getResourceAsStream(EMBEDDED_PATH)) {
			if (in == null) {
				ExtremoMod.LOGGER.error("[Extremo] El mod no contiene {}", EMBEDDED_PATH);
				return;
			}

			byte[] bytes = in.readAllBytes();
			embeddedSha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
			Path copy = ExtremoConfig.folder().resolve("extremo-recursos.zip");
			Files.createDirectories(copy.getParent());
			Path tmp = copy.resolveSibling("extremo-recursos.zip.tmp");
			Files.write(tmp, bytes);
			Files.move(tmp, copy, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException | NoSuchAlgorithmException e) {
			ExtremoMod.LOGGER.error("[Extremo] No se pudo preparar el paquete de recursos", e);
		}
	}

	private static String hashToSend(ExtremoConfig config) {
		if (!config.sha1PaqueteRecursos.isEmpty()) {
			return config.sha1PaqueteRecursos;
		}

		return embeddedSha1;
	}

	/** Envia el paquete de recursos al jugador (si esta activado en la configuracion). */
	public static void sendTo(ServerPlayer player) {
		ExtremoConfig config = ExtremoConfig.get();
		if (!config.enviarPaqueteRecursos || config.urlPaqueteRecursos.isEmpty()) {
			return;
		}

		String hash = hashToSend(config);
		UUID id = UUID.nameUUIDFromBytes(("extremo:" + config.urlPaqueteRecursos).getBytes(StandardCharsets.UTF_8));
		Component prompt = Component.empty()
			.append(Component.literal("☠ Minecraft Extremo ☠").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
			.append(Component.literal("\nEste paquete añade el sonido que suena cuando alguien muere.").withStyle(ChatFormatting.GRAY));
		player.connection.send(new ClientboundResourcePackPushPacket(id, config.urlPaqueteRecursos, hash, config.paqueteRecursosObligatorio, Optional.of(prompt)));
	}

	/** Devuelve el sonido de muerte configurado (vanilla o del paquete de recursos). */
	public static Holder<SoundEvent> deathSound() {
		Identifier id = Identifier.tryParse(ExtremoConfig.get().sonidoMuerte);
		if (id == null) {
			ExtremoMod.LOGGER.warn("[Extremo] sonidoMuerte '{}' no es valido, se usa extremo:muerte", ExtremoConfig.get().sonidoMuerte);
			id = Identifier.fromNamespaceAndPath(ExtremoMod.MOD_ID, "muerte");
		}

		// Si es un sonido de Minecraft se usa el registrado; si no (p. ej. extremo:muerte, que solo
		// existe en el paquete de recursos) se envia "directo" para que el cliente lo busque.
		Optional<Holder.Reference<SoundEvent>> registered = BuiltInRegistries.SOUND_EVENT.get(id);
		if (registered.isPresent()) {
			return registered.get();
		}

		return Holder.direct(SoundEvent.createVariableRangeEvent(id));
	}
}
