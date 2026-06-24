package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Watches incoming chat and forwards messages matching the enabled filters
 * (team chat, whispers/DMs, mentions of you, custom keywords) to a Discord
 * webhook via {@link DiscordWebhook}.
 *
 * <p>Player chat arrives via {@code CHAT}; whispers/team/system messages via
 * {@code GAME}. Category detection is text-pattern based (servers format these
 * differently), so the patterns live in the config and can be tuned per server.
 */
public final class ChatRelay {
	private static String lastRaw = "";
	private static long lastTime = 0L;

	private ChatRelay() {
	}

	public static void register() {
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) ->
			handle(message.getString()));
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				handle(message.getString());
			}
		});
	}

	private static void handle(String raw) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.discordRelay || raw == null || raw.isBlank() || isDuplicate(raw)) {
			return;
		}

		// 1) Category relay (team / whisper / mention / keyword) -> the chat-assigned webhook.
		Config.WebhookEntry chatWebhook = ConfigManager.webhook(cfg.chatWebhook);
		if (chatWebhook != null && chatWebhook.url != null && !chatWebhook.url.isBlank()) {
			String category = categoryMatch(cfg, raw);
			if (category != null) {
				DiscordWebhook.send(chatWebhook.url, chatWebhook.username, "**[" + category + "]** " + raw);
			}
		}

		// 2) Custom keyword rules -> any webhook, with optional label/ping.
		String lower = raw.toLowerCase(Locale.ROOT);
		for (Config.WebhookRule rule : cfg.webhookRules) {
			if (rule == null || !rule.enabled || rule.keyword == null || rule.keyword.isBlank()) {
				continue;
			}
			if (!lower.contains(rule.keyword.toLowerCase(Locale.ROOT))) {
				continue;
			}
			Config.WebhookEntry wh = ConfigManager.webhook(rule.webhook);
			if (wh == null || wh.url == null || wh.url.isBlank()) {
				continue;
			}
			String ping = rule.ping == null ? "" : rule.ping.trim();
			String label = rule.label == null ? "" : rule.label.trim();
			StringBuilder content = new StringBuilder();
			if (!ping.isEmpty()) {
				content.append(ping).append(' ');
			}
			if (!label.isEmpty()) {
				content.append("**[").append(label).append("]** ");
			}
			content.append(raw);
			DiscordWebhook.send(wh.url, wh.username, content.toString(), !ping.isEmpty());
		}
	}

	private static String categoryMatch(Config cfg, String raw) {
		if (cfg.relayWhispers && matchesAny(cfg.whisperPatterns, raw)) {
			return "Whisper";
		}
		if (cfg.relayTeamChat && matchesAny(cfg.teamChatPatterns, raw)) {
			return "Team";
		}
		if (cfg.relayMentions && mentionsSelf(raw)) {
			return "Mention";
		}
		if (cfg.relayKeywords && matchesKeyword(cfg.keywords, raw)) {
			return "Keyword";
		}
		return null;
	}

	private static boolean matchesAny(List<String> patterns, String raw) {
		if (patterns == null) {
			return false;
		}
		for (String pattern : patterns) {
			if (pattern == null || pattern.isBlank()) {
				continue;
			}
			try {
				if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(raw).find()) {
					return true;
				}
			} catch (PatternSyntaxException e) {
				// Treat an invalid regex as a plain case-insensitive substring.
				if (raw.toLowerCase(Locale.ROOT).contains(pattern.toLowerCase(Locale.ROOT))) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean matchesKeyword(List<String> keywords, String raw) {
		if (keywords == null) {
			return false;
		}
		String lower = raw.toLowerCase(Locale.ROOT);
		for (String keyword : keywords) {
			if (keyword != null && !keyword.isBlank() && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	private static boolean mentionsSelf(String raw) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.getGameProfile() == null) {
			return false;
		}
		String name = mc.getGameProfile().name();
		return name != null && !name.isBlank()
			&& raw.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT));
	}

	/** Drops an exact-duplicate message seen within 1 second (guards double-fires). */
	private static boolean isDuplicate(String raw) {
		long now = System.currentTimeMillis();
		if (raw.equals(lastRaw) && (now - lastTime) < 1000L) {
			return true;
		}
		lastRaw = raw;
		lastTime = now;
		return false;
	}
}
