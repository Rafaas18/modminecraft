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
	private static final int CURRENT_VERSION = 2;
	public static final String DEFAULT_PACK_URL =
		"https://raw.githubusercontent.com/Rafaas18/modminecraft/main/descargas/extremo-recursos.zip";

	/** Si es false, las muertes se cuentan pero el mundo NO se reinicia. */
	public boolean reinicioActivado = true;
	/** Version del formato de la configuracion (para actualizar valores viejos automaticamente). */
	public int versionConfig = 0;
	/**
	 * Segundos que pasan desde la muerte hasta que se cambia de mundo. Nadie es expulsado y el
	 * audio de muerte sigue sonando durante el cambio. 0 = instantaneo.
	 */
	public int segundosCuentaAtras = 3;
	/** Sonido que suena a todos cuando alguien muere ("extremo:muerte" = el audio personalizado). */
	public String sonidoMuerte = "extremo:muerte";
	/** Volumen del sonido de muerte (0.0 - 1.0). */
	public float volumenSonidoMuerte = 1.0F;
	/** Enviar automaticamente el paquete de recursos (con el audio) a cada jugador al entrar. */
	public boolean enviarPaqueteRecursos = true;
	/** Enlace de descarga directa del paquete de recursos. */
	public String urlPaqueteRecursos = DEFAULT_PACK_URL;
	/**
	 * SHA-1 del paquete. Vacio = se calcula solo a partir del paquete que lleva el mod dentro
	 * (solo vale si el enlace apunta a ese mismo archivo).
	 */
	public String sha1PaqueteRecursos = "";
	/** Si es true, quien rechace el paquete de recursos no puede entrar. */
	public boolean paqueteRecursosObligatorio = false;
	/** Titulo del panel lateral. */
	public String tituloPanel = "☠ MINECRAFT EXTREMO ☠";
	/** Muestra la linea "Mundo #N" en el panel. */
	public boolean mostrarNumeroMundo = true;
	/** Muestra la linea "Total" en el panel. */
	public boolean mostrarTotalMuertes = true;
	/** Muestra cuanto tiempo lleva vivo el mundo actual. */
	public boolean mostrarTiempo = true;
	/** Muestra el record (lo maximo que ha durado un mundo). */
	public boolean mostrarRecord = true;
	/** Colores del degradado del titulo del panel (formato #RRGGBB). */
	public String colorTitulo1 = "#FF3B3B";
	public String colorTitulo2 = "#FFB020";

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
		if (instance.versionConfig < CURRENT_VERSION) {
			// Versiones anteriores expulsaban a todos y necesitaban una cuenta atras larga para que se
			// oyera el audio. Ahora nadie es expulsado, asi que se pasa al nuevo valor por defecto.
			instance.segundosCuentaAtras = 3;
			instance.versionConfig = CURRENT_VERSION;
		}

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

		if (this.sonidoMuerte == null || this.sonidoMuerte.isBlank()) {
			this.sonidoMuerte = "extremo:muerte";
		}

		if (!(this.volumenSonidoMuerte >= 0.0F)) {
			this.volumenSonidoMuerte = 1.0F;
		} else if (this.volumenSonidoMuerte > 1.0F) {
			this.volumenSonidoMuerte = 1.0F;
		}

		if (this.urlPaqueteRecursos == null) {
			this.urlPaqueteRecursos = "";
		}

		this.urlPaqueteRecursos = this.urlPaqueteRecursos.trim();
		if (this.sha1PaqueteRecursos == null) {
			this.sha1PaqueteRecursos = "";
		}

		this.sha1PaqueteRecursos = this.sha1PaqueteRecursos.trim().toLowerCase(java.util.Locale.ROOT);
		if (!this.sha1PaqueteRecursos.isEmpty() && !this.sha1PaqueteRecursos.matches("[0-9a-f]{40}")) {
			ExtremoMod.LOGGER.warn("[Extremo] sha1PaqueteRecursos no es un SHA-1 valido (40 caracteres hexadecimales). Se ignora.");
			this.sha1PaqueteRecursos = "";
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
