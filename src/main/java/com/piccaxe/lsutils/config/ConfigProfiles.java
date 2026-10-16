package com.piccaxe.lsutils.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.piccaxe.lsutils.PiccaxeLsUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Named config profiles (full snapshots in {@code config/piccaxes-lifesteal-utils-profiles/}) that can be
 * bound to servers and switched automatically on join, plus share codes ({@code PLSC1:…}) to copy a
 * whole setup to a friend. Share codes leave webhook URLs out unless asked, since those are secrets.
 */
public final class ConfigProfiles {
	private static final String CODE_PREFIX = "PLSC1:";
	private static final Gson GSON = new Gson();
	private static String active = "";

	private ConfigProfiles() {
	}

	private static Path dir() {
		return ConfigManager.dir().resolve("piccaxes-lifesteal-utils-profiles");
	}

	private static Path bindingsFile() {
		return dir().resolve("_bindings.json");
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> autoSwitch(client)));
	}

	public static String active() {
		return active;
	}

	private static void autoSwitch(MinecraftClient mc) {
		if (!ConfigManager.get().profileAutoSwitch || mc.getCurrentServerEntry() == null) {
			return;
		}
		String addr = mc.getCurrentServerEntry().address.toLowerCase(Locale.ROOT);
		for (Map.Entry<String, String> b : bindings().entrySet()) {
			if (addr.contains(b.getKey()) && !b.getValue().equalsIgnoreCase(active)) {
				if (load(b.getValue()) && mc.player != null) {
					mc.player.sendMessage(Text.literal("[LSU] ").formatted(Formatting.GOLD)
						.append(Text.literal("Switched to profile \"" + b.getValue() + "\" for this server.").formatted(Formatting.AQUA)), false);
				}
				return;
			}
		}
	}

	// --- profiles ---

	private static String clean(String name) {
		return name.trim().replaceAll("[^A-Za-z0-9_\\- ]", "");
	}

	public static List<String> list() {
		List<String> out = new ArrayList<>();
		if (!Files.isDirectory(dir())) {
			return out;
		}
		try (Stream<Path> files = Files.list(dir())) {
			files.map(p -> p.getFileName().toString())
				.filter(n -> n.endsWith(".json") && !n.startsWith("_"))
				.map(n -> n.substring(0, n.length() - 5))
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.forEach(out::add);
		} catch (IOException e) {
			PiccaxeLsUtils.LOGGER.warn("[profiles] list failed: {}", e.toString());
		}
		return out;
	}

	public static boolean save(String name) {
		String n = clean(name);
		if (n.isEmpty()) {
			return false;
		}
		try {
			Files.createDirectories(dir());
			Files.writeString(dir().resolve(n + ".json"), ConfigManager.toJson(ConfigManager.get()));
			active = n;
			return true;
		} catch (IOException e) {
			PiccaxeLsUtils.LOGGER.warn("[profiles] save failed: {}", e.toString());
			return false;
		}
	}

	public static boolean load(String name) {
		Path p = dir().resolve(clean(name) + ".json");
		try {
			if (!Files.exists(p)) {
				return false;
			}
			Config c = ConfigManager.fromJson(Files.readString(p));
			if (c == null) {
				return false;
			}
			ConfigManager.replace(c);
			active = clean(name);
			return true;
		} catch (Exception e) {
			PiccaxeLsUtils.LOGGER.warn("[profiles] load failed: {}", e.toString());
			return false;
		}
	}

	public static boolean delete(String name) {
		try {
			return Files.deleteIfExists(dir().resolve(clean(name) + ".json"));
		} catch (IOException e) {
			return false;
		}
	}

	// --- server bindings (address substring -> profile) ---

	public static Map<String, String> bindings() {
		try {
			if (Files.exists(bindingsFile())) {
				Map<String, String> m = GSON.fromJson(Files.readString(bindingsFile()),
					new TypeToken<LinkedHashMap<String, String>>() { }.getType());
				if (m != null) {
					return m;
				}
			}
		} catch (Exception e) {
			PiccaxeLsUtils.LOGGER.warn("[profiles] bindings read failed: {}", e.toString());
		}
		return new LinkedHashMap<>();
	}

	private static void writeBindings(Map<String, String> m) {
		try {
			Files.createDirectories(dir());
			Files.writeString(bindingsFile(), GSON.toJson(m));
		} catch (IOException e) {
			PiccaxeLsUtils.LOGGER.warn("[profiles] bindings write failed: {}", e.toString());
		}
	}

	public static void bind(String address, String profile) {
		Map<String, String> m = bindings();
		m.put(address.toLowerCase(Locale.ROOT).trim(), clean(profile));
		writeBindings(m);
	}

	public static boolean unbind(String address) {
		Map<String, String> m = bindings();
		boolean removed = m.remove(address.toLowerCase(Locale.ROOT).trim()) != null;
		writeBindings(m);
		return removed;
	}

	// --- share codes ---

	public static String exportCode(boolean includeWebhooks) {
		Config copy = ConfigManager.fromJson(ConfigManager.toJson(ConfigManager.get()));
		if (!includeWebhooks) {
			copy.webhooks = new ArrayList<>();
			copy.discordWebhookUrl = "";
		}
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
				gz.write(ConfigManager.toJson(copy).getBytes(StandardCharsets.UTF_8));
			}
			return CODE_PREFIX + Base64.getEncoder().encodeToString(bos.toByteArray());
		} catch (IOException e) {
			return "";
		}
	}

	/** Applies a share code. Keeps your own webhooks when the code has none. Returns false if invalid. */
	public static boolean importCode(String code) {
		if (code == null || !code.trim().startsWith(CODE_PREFIX)) {
			return false;
		}
		try {
			byte[] data = Base64.getDecoder().decode(code.trim().substring(CODE_PREFIX.length()));
			String json;
			try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(data))) {
				json = new String(gz.readAllBytes(), StandardCharsets.UTF_8);
			}
			Config c = ConfigManager.fromJson(json);
			if (c == null) {
				return false;
			}
			Config mine = ConfigManager.get();
			if (c.webhooks == null || c.webhooks.isEmpty()) {
				c.webhooks = mine.webhooks;
				c.discordWebhookUrl = mine.discordWebhookUrl;
			}
			ConfigManager.replace(c);
			return true;
		} catch (Exception e) {
			PiccaxeLsUtils.LOGGER.warn("[profiles] import failed: {}", e.toString());
			return false;
		}
	}
}
