package com.extremo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Configuracion editable en config/extremo/config.json.
 */
public final class ExtremoConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Si es false, las muertes se cuentan pero el mundo NO se reinicia. */
	public boolean reinicioActivado = true;
	/** Segundos que pasan desde la muerte hasta que se reinicia el mundo. */
	public int segundosCuentaAtras = 10;
	/** Titulo del panel lateral. */
	public String tituloPanel = "☠ MINECRAFT EXTREMO ☠";
	/** Muestra la linea "Mundo #N" en el panel. */
	public boolean mostrarNumeroMundo = true;
	/** Muestra la linea "Total" en el panel. */
	public boolean mostrarTotalMuertes = true;

	private static ExtremoConfig instance = new ExtremoConfig();

	public static ExtremoConfig get() {
		return instance;
	}

	static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("extremo");
	}

	private static Path file() {
		return folder().resolve("config.json");
	}

	public static void load() {
		Path path = file();
		ExtremoConfig loaded = null;
		if (Files.exists(path)) {
			try {
				loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), ExtremoConfig.class);
			} catch (IOException | JsonParseException e) {
				ExtremoMod.LOGGER.error("[Extremo] No se pudo leer {}. Se usara la configuracion por defecto.", path, e);
			}
		}

		instance = loaded != null ? loaded : new ExtremoConfig();
		instance.sanitize();
		save();
	}

	private void sanitize() {
		if (this.segundosCuentaAtras < 0) {
			this.segundosCuentaAtras = 0;
		} else if (this.segundosCuentaAtras > 300) {
			this.segundosCuentaAtras = 300;
		}

		if (this.tituloPanel == null || this.tituloPanel.isBlank()) {
			this.tituloPanel = "☠ MINECRAFT EXTREMO ☠";
		}
	}

	public static void save() {
		instance.sanitize();
		writeAtomically(file(), GSON.toJson(instance));
	}

	static void writeAtomically(Path path, String content) {
		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(tmp, content, StandardCharsets.UTF_8);
			try {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicFailed) {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			ExtremoMod.LOGGER.error("[Extremo] No se pudo guardar {}", path, e);
		}
	}
}
