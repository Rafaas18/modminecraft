package com.extremo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * Datos que sobreviven a los reinicios del mundo (muertes, numero de mundo, seeds usadas).
 * Se guardan en config/extremo/datos.json, FUERA de la carpeta del mundo, para que no se
 * borren al regenerarlo.
 */
public final class ExtremoData {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final SecureRandom RANDOM = new SecureRandom();

	public int numeroMundo = 1;
	public int totalMuertes = 0;
	public boolean reinicioPendiente = false;
	public String ultimaMuerte = "";
	public long seedActual = 0L;
	/** Ticks que lleva vivo el mundo actual (solo cuentan mientras hay alguien conectado). */
	public long ticksMundo = 0L;
	/** Lo maximo que ha durado un mundo, en ticks. */
	public long recordTicks = 0L;
	public List<Long> seedsUsadas = new ArrayList<>();
	/** UUID del jugador -> participante. */
	public Map<String, Participante> participantes = new LinkedHashMap<>();

	public static final class Participante {
		public String nombre;
		public int muertes;

		public Participante() {
		}

		Participante(String nombre) {
			this.nombre = nombre;
		}
	}

	private static ExtremoData instance = new ExtremoData();

	public static ExtremoData get() {
		return instance;
	}

	private static Path file() {
		return ExtremoConfig.folder().resolve("datos.json");
	}

	public static void load() {
		Path path = file();
		ExtremoData loaded = null;
		if (Files.exists(path)) {
			try {
				loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), ExtremoData.class);
			} catch (IOException | JsonParseException e) {
				ExtremoMod.LOGGER.error("[Extremo] No se pudo leer {}. Se empezara con datos nuevos.", path, e);
				try {
					Files.copy(path, path.resolveSibling("datos.json.corrupto"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ignored) {
				}
			}
		}

		instance = loaded != null ? loaded : new ExtremoData();
		instance.sanitize();
	}

	private void sanitize() {
		if (this.seedsUsadas == null) {
			this.seedsUsadas = new ArrayList<>();
		}

		if (this.participantes == null) {
			this.participantes = new LinkedHashMap<>();
		}

		this.participantes.values().removeIf(p -> p == null || p.nombre == null || p.nombre.isBlank());
		this.participantes.values().forEach(p -> p.muertes = Math.max(0, p.muertes));
		if (this.ultimaMuerte == null) {
			this.ultimaMuerte = "";
		}

		this.numeroMundo = Math.max(1, this.numeroMundo);
		this.ticksMundo = Math.max(0L, this.ticksMundo);
		this.recordTicks = Math.max(0L, this.recordTicks);
		this.totalMuertes = Math.max(0, this.totalMuertes);
	}

	public static void save() {
		ExtremoConfig.writeAtomically(file(), GSON.toJson(instance));
	}

	/** Registra (o actualiza el nombre de) un participante. Devuelve true si algo cambio. */
	public boolean registerParticipant(UUID uuid, String name) {
		Participante existing = this.participantes.get(uuid.toString());
		if (existing == null) {
			this.participantes.put(uuid.toString(), new Participante(name));
			return true;
		}

		if (!name.equals(existing.nombre)) {
			existing.nombre = name;
			return true;
		}

		return false;
	}

	public int addDeath(UUID uuid, String name) {
		this.registerParticipant(uuid, name);
		Participante participante = this.participantes.get(uuid.toString());
		participante.muertes++;
		this.totalMuertes++;
		this.ultimaMuerte = name;
		return participante.muertes;
	}

	public List<Participante> ranking() {
		return this.rankingEntries().stream().map(Map.Entry::getValue).toList();
	}

	/** Participantes (UUID -> datos) ordenados de mas a menos muertes. */
	public List<Map.Entry<String, Participante>> rankingEntries() {
		List<Map.Entry<String, Participante>> list = new ArrayList<>(this.participantes.entrySet());
		list.sort(Comparator.<Map.Entry<String, Participante>>comparingInt(e -> e.getValue().muertes).reversed()
			.thenComparing(e -> e.getValue().nombre, String.CASE_INSENSITIVE_ORDER));
		return list;
	}

	/** Se llama al pasar a un mundo nuevo: guarda el record y pone el tiempo a cero. */
	public void finishWorld() {
		if (this.ticksMundo > this.recordTicks) {
			this.recordTicks = this.ticksMundo;
		}

		this.ticksMundo = 0L;
	}

	public Participante findByName(String name) {
		for (Participante p : this.participantes.values()) {
			if (p.nombre.equalsIgnoreCase(name)) {
				return p;
			}
		}

		return null;
	}

	public void rememberSeed(long seed) {
		this.seedActual = seed;
		if (!this.seedsUsadas.contains(seed)) {
			this.seedsUsadas.add(seed);
		}
	}

	/** Devuelve una seed aleatoria que nunca se ha usado antes. */
	public long pickNewSeed() {
		Set<Long> used = new HashSet<>(this.seedsUsadas);
		used.add(this.seedActual);
		long seed;
		do {
			seed = RANDOM.nextLong();
		} while (seed == 0L || used.contains(seed));

		return seed;
	}
}
