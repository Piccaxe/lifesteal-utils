package com.piccaxe.lsutils.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and saves {@link Config} as JSON in the Fabric config directory.
 */
public final class ConfigManager {
	private static final Path PATH =
		FabricLoader.getInstance().getConfigDir().resolve("piccaxes-lifesteal-utils.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Config instance = new Config();

	private ConfigManager() {
	}

	public static Config get() {
		return instance;
	}

	public static void load() {
		try {
			if (Files.exists(PATH)) {
				Config loaded = GSON.fromJson(Files.readString(PATH), Config.class);
				if (loaded != null) {
					instance = loaded;
				}
			} else {
				save();
			}
		} catch (Exception e) {
			System.err.println("[piccaxelsutils] Failed to load config, using defaults: " + e.getMessage());
		}
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(instance));
		} catch (IOException e) {
			System.err.println("[piccaxelsutils] Failed to save config: " + e.getMessage());
		}
	}
}
