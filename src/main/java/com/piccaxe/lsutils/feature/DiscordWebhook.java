package com.piccaxe.lsutils.feature;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.piccaxe.lsutils.PiccaxeLsUtils;
import com.piccaxe.lsutils.config.Config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Fire-and-forget Discord webhook poster. Sends run on a small daemon thread pool so the game thread
 * never blocks and different webhooks post in parallel; each webhook is paced independently (Discord
 * rate-limits per webhook) with one retry on HTTP 429. Forwarded text never pings
 * unless {@code allowMentions} is set. Every send is logged with its HTTP status, and an optional
 * {@code onResult} callback receives a human-readable outcome (used by {@code /piccaxeutils discord test}).
 */
public final class DiscordWebhook {
	private static final AtomicInteger THREAD_ID = new AtomicInteger();
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, r -> {
		Thread thread = new Thread(r, "piccaxe-discord-webhook-" + THREAD_ID.incrementAndGet());
		thread.setDaemon(true);
		return thread;
	});
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.build();
	private static final Gson GSON = new Gson();

	/** Per-webhook earliest next-send time (ms epoch), so each webhook is paced independently. */
	private static final Map<String, Long> NEXT_ALLOWED = new ConcurrentHashMap<>();
	/** Per-webhook monitor to serialize slot reservation (not the HTTP call) for ordering. */
	private static final Map<String, Object> LOCKS = new ConcurrentHashMap<>();
	/** Minimum gap between sends to the SAME webhook (Discord allows roughly 2.5/s per hook). */
	private static final long MIN_SPACING_MS = 400L;
	private static final Map<String, Long> LAST_SENT = new ConcurrentHashMap<>();

	private DiscordWebhook() {
	}

	/**
	 * Sends to a webhook, respecting its per-webhook cooldown. Returns false (and sends nothing) if
	 * the webhook is still on cooldown. Use this for event-driven sends (chat relay, rules, alerts).
	 */
	public static boolean sendThrottled(Config.WebhookEntry wh, String content, boolean allowMentions) {
		if (wh == null || wh.url == null || wh.url.isBlank() || content == null || content.isBlank()) {
			return false;
		}
		if (wh.cooldownSeconds > 0) {
			long now = System.currentTimeMillis();
			String key = (wh.name == null || wh.name.isBlank()) ? wh.url : wh.name;
			Long last = LAST_SENT.get(key);
			if (last != null && now - last < wh.cooldownSeconds * 1000L) {
				return false;
			}
			LAST_SENT.put(key, now);
		}
		send(wh.url, wh.username, content, allowMentions);
		return true;
	}

	public static void send(String url, String username, String content) {
		send(url, username, content, false, null);
	}

	public static void send(String url, String username, String content, boolean allowMentions) {
		send(url, username, content, allowMentions, null);
	}

	/**
	 * @param allowMentions true lets the message ping (@everyone/roles/users); default strips all pings.
	 * @param onResult      optional callback (invoked on the webhook thread) with a human-readable outcome.
	 */
	public static void send(String url, String username, String content, boolean allowMentions, Consumer<String> onResult) {
		if (url == null || url.isBlank()) {
			report(onResult, "no webhook URL set");
			return;
		}
		if (content == null || content.isBlank()) {
			report(onResult, "nothing to send (empty message)");
			return;
		}
		EXECUTOR.submit(() -> post(url, username, content, allowMentions, onResult));
	}

	private static void post(String url, String username, String content, boolean allowMentions, Consumer<String> onResult) {
		String key = url.trim();
		try {
			// Per-webhook pacing: briefly lock only to reserve this webhook's next time slot, keeping
			// same-webhook sends ordered and spaced. The HTTP call runs outside the lock, and other
			// webhooks use other locks + pool threads, so they send in parallel with no shared delay.
			long waitMs;
			Object lock = LOCKS.computeIfAbsent(key, k -> new Object());
			synchronized (lock) {
				long now = System.currentTimeMillis();
				long slot = Math.max(now, NEXT_ALLOWED.getOrDefault(key, 0L));
				NEXT_ALLOWED.put(key, slot + MIN_SPACING_MS);
				waitMs = slot - now;
			}
			if (waitMs > 0) {
				Thread.sleep(waitMs);
			}

			HttpRequest request = newRequest(key, username, content, allowMentions);
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			int code = response.statusCode();

			if (code == 429) {
				long retryMs = retryAfterMs(response);
				synchronized (lock) {
					NEXT_ALLOWED.put(key, System.currentTimeMillis() + retryMs + MIN_SPACING_MS);
				}
				Thread.sleep(retryMs);
				response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				code = response.statusCode();
			}

			if (code >= 200 && code < 300) {
				PiccaxeLsUtils.LOGGER.info("[discord] sent OK (HTTP {})", code);
				report(onResult, "sent OK (HTTP " + code + ")");
			} else {
				String hint = switch (code) {
					case 401, 403, 404 -> " — invalid/deleted webhook URL, set it again with /piccaxeutils discord url <url>";
					case 400 -> " — Discord rejected the message (check the webhook name)";
					default -> "";
				};
				PiccaxeLsUtils.LOGGER.warn("[discord] HTTP {} — {}", code, response.body());
				report(onResult, "failed: HTTP " + code + hint);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			report(onResult, "interrupted");
		} catch (Exception e) {
			PiccaxeLsUtils.LOGGER.warn("[discord] send failed: {}", e.toString());
			report(onResult, "error: " + e.getClass().getSimpleName() + (e.getMessage() != null ? " — " + e.getMessage() : ""));
		}
	}

	private static void report(Consumer<String> onResult, String message) {
		if (onResult != null) {
			onResult.accept(message);
		}
	}

	private static HttpRequest newRequest(String url, String username, String content, boolean allowMentions) {
		return HttpRequest.newBuilder(URI.create(url))
			.header("Content-Type", "application/json")
			.header("User-Agent", "PiccaxeLifestealUtils/1.0 (Fabric mod)")
			.timeout(Duration.ofSeconds(15))
			.POST(HttpRequest.BodyPublishers.ofString(buildBody(username, content, allowMentions), StandardCharsets.UTF_8))
			.build();
	}

	private static String buildBody(String username, String content, boolean allowMentions) {
		JsonObject obj = new JsonObject();
		obj.addProperty("content", content.length() > 2000 ? content.substring(0, 2000) : content);
		if (username != null && !username.isBlank()) {
			obj.addProperty("username", username.length() > 80 ? username.substring(0, 80) : username);
		}
		JsonObject allowedMentions = new JsonObject();
		JsonArray parse = new JsonArray();
		if (allowMentions) {
			parse.add("everyone");
			parse.add("roles");
			parse.add("users");
		}
		allowedMentions.add("parse", parse);
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
