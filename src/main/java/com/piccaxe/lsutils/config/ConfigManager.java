package com.piccaxe.lsutils.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

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
		migrate();
	}

	/** Folds a legacy single webhook into the named-webhook list so old configs keep working. */
	private static void migrate() {
		if (instance.webhooks == null) {
			instance.webhooks = new ArrayList<>();
		}
		if (instance.webhookRules == null) {
			instance.webhookRules = new ArrayList<>();
		}
		if (instance.webhooks.isEmpty() && instance.discordWebhookUrl != null && !instance.discordWebhookUrl.isBlank()) {
			String username = (instance.discordUsername == null || instance.discordUsername.isBlank())
				? "Lifesteal Utils" : instance.discordUsername;
			instance.webhooks.add(new Config.WebhookEntry("default", instance.discordWebhookUrl, username));
			if (instance.chatWebhook.isBlank()) {
				instance.chatWebhook = "default";
			}
			if (instance.notifierWebhook.isBlank()) {
				instance.notifierWebhook = "default";
			}
			if (instance.proximityWebhook.isBlank()) {
				instance.proximityWebhook = "default";
			}
			save();
		}
	}

	/** Resolves a webhook by name (case-insensitive), or null if none/blank. */
	public static Config.WebhookEntry webhook(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		for (Config.WebhookEntry entry : instance.webhooks) {
			if (entry != null && entry.name != null && entry.name.equalsIgnoreCase(name)) {
				return entry;
			}
		}
		return null;
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
