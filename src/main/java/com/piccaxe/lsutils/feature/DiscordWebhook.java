package com.piccaxe.lsutils.feature;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.piccaxe.lsutils.PiccaxeLsUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fire-and-forget Discord webhook poster. Sends run on a single daemon thread so
 * the game thread never blocks on the network, with light rate-limiting and one
 * retry on HTTP 429. Forwarded text never pings (allowed_mentions is cleared).
 */
public final class DiscordWebhook {
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "piccaxe-discord-webhook");
		thread.setDaemon(true);
		return thread;
	});
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.build();
	private static final Gson GSON = new Gson();

	private static volatile long nextAllowedSend = 0L;

	private DiscordWebhook() {
	}

	public static void send(String url, String username, String content) {
		if (url == null || url.isBlank() || content == null || content.isBlank()) {
			return;
		}
		EXECUTOR.submit(() -> post(url, username, content));
	}

	private static void post(String url, String username, String content) {
		try {
			long now = System.currentTimeMillis();
			if (now < nextAllowedSend) {
				Thread.sleep(nextAllowedSend - now);
			}

			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.header("Content-Type", "application/json")
				.header("User-Agent", "PiccaxeLifestealUtils/1.0")
				.timeout(Duration.ofSeconds(15))
				.POST(HttpRequest.BodyPublishers.ofString(buildBody(username, content), StandardCharsets.UTF_8))
				.build();

			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 429) {
				long retryMs = retryAfterMs(response);
				nextAllowedSend = System.currentTimeMillis() + retryMs;
				Thread.sleep(retryMs);
				HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			} else {
				if (response.statusCode() >= 400) {
					PiccaxeLsUtils.LOGGER.warn("[discord] webhook returned HTTP {}", response.statusCode());
				}
				nextAllowedSend = System.currentTimeMillis() + 600L;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			PiccaxeLsUtils.LOGGER.warn("[discord] webhook send failed: {}", e.toString());
		}
	}

	private static String buildBody(String username, String content) {
		JsonObject obj = new JsonObject();
		obj.addProperty("content", content.length() > 2000 ? content.substring(0, 2000) : content);
		if (username != null && !username.isBlank()) {
			obj.addProperty("username", username.length() > 80 ? username.substring(0, 80) : username);
		}
		// Never let forwarded chat ping @everyone / roles / users.
		JsonObject allowedMentions = new JsonObject();
		allowedMentions.add("parse", new JsonArray());
		obj.add("allowed_mentions", allowedMentions);
		return GSON.toJson(obj);
	}

	private static long retryAfterMs(HttpResponse<String> response) {
		return response.headers().firstValue("Retry-After").map(value -> {
			try {
				return (long) (Double.parseDouble(value) * 1000.0);
			} catch (NumberFormatException e) {
				return 1500L;
			}
		}).orElse(1500L);
	}
}
