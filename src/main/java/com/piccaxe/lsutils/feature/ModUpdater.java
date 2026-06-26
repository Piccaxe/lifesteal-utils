package com.piccaxe.lsutils.feature;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.piccaxe.lsutils.PiccaxeLsUtils;
import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Self-updater. On launch it asks GitHub for the latest release of the configured {@code owner/repo};
 * if that version is newer than the running jar, it downloads the release's jar next to ours (as a
 * {@code .update} file, so Fabric won't load it) and, when the game closes, spawns a tiny detached
 * command that waits for the JVM to exit and swaps the new jar in place. The next launch is updated.
 *
 * <p>Replacing a running jar in-process is impossible on Windows (it's locked), hence the on-exit
 * swap. If anything fails it falls back to just notifying with the release info.
 */
public final class ModUpdater {
	private static final Gson GSON = new Gson();

	private static volatile Text pendingMessage = null;
	private static volatile boolean messageShown = false;
	private static volatile Path stagedJar = null;
	private static volatile Path targetJar = null;

	private ModUpdater() {
	}

	public static void register() {
		checkNow();
		ClientTickEvents.END_CLIENT_TICK.register(ModUpdater::tick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> applyStaged());
	}

	/** Kick off a version check on a background daemon thread. */
	public static void checkNow() {
		Thread thread = new Thread(ModUpdater::check, "piccaxe-mod-updater");
		thread.setDaemon(true);
		thread.start();
	}

	private static void tick(MinecraftClient mc) {
		if (!messageShown && pendingMessage != null && mc.player != null) {
			mc.player.sendMessage(pendingMessage, false);
			messageShown = true;
		}
	}

	private static void check() {
		Config cfg = ConfigManager.get();
		if (!cfg.autoUpdate || cfg.updateRepo == null || cfg.updateRepo.isBlank()) {
			return;
		}
		String repo = cfg.updateRepo.trim();
		try {
			HttpClient http = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(10))
				.build();

			HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
				.header("Accept", "application/vnd.github+json")
				.header("User-Agent", "PiccaxeLifestealUtils-Updater")
				.timeout(Duration.ofSeconds(15)).GET().build();
			HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() != 200) {
				PiccaxeLsUtils.LOGGER.warn("[updater] GitHub returned HTTP {} for {}", resp.statusCode(), repo);
				return;
			}

			JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
			if (json == null || !json.has("tag_name")) {
				return;
			}
			String latest = json.get("tag_name").getAsString();
			String current = currentVersion();
			if (!isNewer(latest, current)) {
				PiccaxeLsUtils.LOGGER.info("[updater] up to date (have {}, latest {})", current, latest);
				return;
			}

			String url = null;
			if (json.has("assets")) {
				for (var element : json.getAsJsonArray("assets")) {
					JsonObject asset = element.getAsJsonObject();
					String name = asset.get("name").getAsString().toLowerCase();
					if (name.endsWith(".jar") && !name.contains("-sources") && !name.contains("-dev")) {
						url = asset.get("browser_download_url").getAsString();
						break;
					}
				}
			}
			if (url == null) {
				notify(Text.literal("[Lifesteal Utils] Update " + latest + " is out, but no jar was attached to the release.")
					.formatted(Formatting.YELLOW));
				return;
			}
			if (!cfg.autoUpdateApply) {
				notify(Text.literal("[Lifesteal Utils] Update " + latest + " available: " + url).formatted(Formatting.YELLOW));
				return;
			}

			Path jar = ourJarPath();
			if (jar == null) {
				notify(Text.literal("[Lifesteal Utils] Update " + latest + " available (couldn't locate my jar to auto-apply): " + url)
					.formatted(Formatting.YELLOW));
				return;
			}

			HttpResponse<byte[]> dl = http.send(HttpRequest.newBuilder(URI.create(url))
				.header("User-Agent", "PiccaxeLifestealUtils-Updater")
				.timeout(Duration.ofSeconds(120)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
			if (dl.statusCode() != 200 || dl.body().length == 0) {
				notify(Text.literal("[Lifesteal Utils] Update " + latest + " download failed (HTTP " + dl.statusCode() + ").")
					.formatted(Formatting.RED));
				return;
			}

			Path staging = jar.resolveSibling(jar.getFileName().toString() + ".update");
			Files.write(staging, dl.body());
			stagedJar = staging;
			targetJar = jar;
			PiccaxeLsUtils.LOGGER.info("[updater] staged {} ({} bytes) for {}", latest, dl.body().length, jar);
			notify(Text.literal("[Lifesteal Utils] Update " + latest + " downloaded — close the game once and it'll apply automatically.")
				.formatted(Formatting.GREEN));
		} catch (Exception e) {
			PiccaxeLsUtils.LOGGER.warn("[updater] check failed: {}", e.toString());
		}
	}

	private static void notify(Text text) {
		pendingMessage = text;
		messageShown = false;
	}

	private static void applyStaged() {
		Path staging = stagedJar;
		Path target = targetJar;
		if (staging == null || target == null || !Files.exists(staging)) {
			return;
		}
		String s = staging.toAbsolutePath().toString();
		String o = target.toAbsolutePath().toString();
		try {
			boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
			ProcessBuilder pb = windows
				? new ProcessBuilder("cmd.exe", "/c", "ping 127.0.0.1 -n 5 > nul & move /y \"" + s + "\" \"" + o + "\"")
				: new ProcessBuilder("sh", "-c", "sleep 4; mv -f '" + s + "' '" + o + "'");
			pb.start();
			PiccaxeLsUtils.LOGGER.info("[updater] swap scheduled after exit: {} -> {}", s, o);
		} catch (Exception e) {
			PiccaxeLsUtils.LOGGER.warn("[updater] couldn't schedule swap: {}", e.toString());
		}
	}

	public static String currentVersion() {
		return FabricLoader.getInstance().getModContainer(PiccaxeLsUtils.MOD_ID)
			.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("0");
	}

	private static Path ourJarPath() {
		var container = FabricLoader.getInstance().getModContainer(PiccaxeLsUtils.MOD_ID);
		if (container.isEmpty()) {
			return null;
		}
		List<Path> paths = container.get().getOrigin().getPaths();
		for (Path p : paths) {
			if (p.toString().toLowerCase().endsWith(".jar")) {
				return p;
			}
		}
		return paths.isEmpty() ? null : paths.get(0);
	}

	static boolean isNewer(String latest, String current) {
		int[] a = parse(latest);
		int[] b = parse(current);
		int n = Math.max(a.length, b.length);
		for (int i = 0; i < n; i++) {
			int x = i < a.length ? a[i] : 0;
			int y = i < b.length ? b[i] : 0;
			if (x != y) {
				return x > y;
			}
		}
		return false;
	}

	private static int[] parse(String version) {
		String s = version == null ? "" : version.trim();
		if (s.startsWith("v") || s.startsWith("V")) {
			s = s.substring(1);
		}
		List<Integer> nums = new ArrayList<>();
		for (String part : s.split("[.+\\-_]")) {
			StringBuilder digits = new StringBuilder();
			for (char c : part.toCharArray()) {
				if (Character.isDigit(c)) {
					digits.append(c);
				} else {
					break;
				}
			}
			if (digits.length() > 0) {
				nums.add(Integer.parseInt(digits.toString()));
			} else if (!nums.isEmpty()) {
				break;
			}
		}
		int[] result = new int[nums.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = nums.get(i);
		}
		return result;
	}
}
