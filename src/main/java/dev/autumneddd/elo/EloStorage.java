package dev.autumneddd.elo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class EloStorage {
	public static final double BASE_ELO = 1000.0;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final class Entry {
		public String name;
		public double elo;
		public int kills;
		public int deaths;

		Entry(String name, double elo) {
			this.name = name;
			this.elo = elo;
		}
	}

	private final Path file;
	private Map<UUID, Entry> entries = new LinkedHashMap<>();

	public EloStorage(Path file) {
		this.file = file;
	}

	public void load() {
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			Map<UUID, Entry> loaded = GSON.fromJson(reader, new TypeToken<LinkedHashMap<UUID, Entry>>() {}.getType());
			if (loaded != null) {
				entries = loaded;
			}
		} catch (Exception e) {
			EloMod.LOGGER.error("Failed to load Elo data from {}", file, e);
		}
	}

	public void save() {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp)) {
				GSON.toJson(entries, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			EloMod.LOGGER.error("Failed to save Elo data to {}", file, e);
		}
	}

	public Entry get(UUID id, String name) {
		Entry entry = entries.computeIfAbsent(id, k -> new Entry(name, BASE_ELO));
		entry.name = name;
		return entry;
	}

	public Optional<Entry> findByName(String name) {
		return entries.values().stream().filter(e -> e.name.equalsIgnoreCase(name)).findFirst();
	}

	public List<String> names() {
		return entries.values().stream().map(e -> e.name).toList();
	}

	public List<Entry> top(int count) {
		List<Entry> sorted = new ArrayList<>(entries.values());
		sorted.sort(Comparator.comparingDouble((Entry e) -> e.elo).reversed());
		return sorted.subList(0, Math.min(count, sorted.size()));
	}
}
