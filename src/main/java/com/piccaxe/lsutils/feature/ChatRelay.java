package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

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
			handle(message.getString(), false, sender != null ? sender.name() : null));
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				handle(message.getString(), true, null);
			}
		});
	}

	private static void handle(String raw, boolean isServer, String sender) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.discordRelay || raw == null || raw.isBlank() || isDuplicate(raw)) {
			return;
		}

		// 1) Category relay (team / whisper / mention / keyword) -> the chat-assigned webhook.
		Config.WebhookEntry chatWebhook = ConfigManager.webhook(cfg.chatWebhook);
		if (chatWebhook != null && chatWebhook.url != null && !chatWebhook.url.isBlank()) {
			String category = categoryMatch(cfg, raw);
			if (category != null) {
				DiscordWebhook.sendThrottled(chatWebhook, "**[" + category + "]** " + raw, false);
			}
		}

		// 2) Custom keyword rules -> any webhook, with optional label/ping.
		String lower = raw.toLowerCase(Locale.ROOT);
		for (Config.WebhookRule rule : cfg.webhookRules) {
			if (rule == null || !rule.enabled) {
				continue;
			}
			// Server-only rules ignore player chat entirely.
			if (rule.serverOnly && !isServer) {
				continue;
			}
			// Skip messages from ignored players, or containing ignored text.
			if (isIgnored(rule, lower, raw, sender)) {
				continue;
			}
			boolean hasKeyword = rule.keyword != null && !rule.keyword.isBlank();
			if (hasKeyword) {
				if (!lower.contains(rule.keyword.toLowerCase(Locale.ROOT))) {
					continue;
				}
			} else if (!rule.serverOnly) {
				// No keyword is only meaningful for a server-only rule (forward all server messages);
				// otherwise it would forward everything, so skip.
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
			DiscordWebhook.sendThrottled(wh, content.toString(), !ping.isEmpty());
		}
	}

	/**
	 * A rule ignores a message when an ignore entry matches it. Entries that name an actual player
	 * (the chat sender, or someone currently online) are matched by <em>authorship</em> — the message
	 * is only skipped if that player wrote it, not merely if their name appears in the body. Other
	 * entries are treated as plain text and matched anywhere.
	 */
	private static boolean isIgnored(Config.WebhookRule rule, String lowerRaw, String raw, String sender) {
		if (rule.ignore == null || rule.ignore.isEmpty()) {
			return false;
		}
		String lowerSender = sender == null ? null : sender.toLowerCase(Locale.ROOT);
		for (String entry : rule.ignore) {
			if (entry == null || entry.isBlank()) {
				continue;
			}
			String e = entry.trim();
			String le = e.toLowerCase(Locale.ROOT);
			boolean isSender = lowerSender != null && lowerSender.equals(le);
			if (isSender || isKnownPlayer(e)) {
				// Recognized player: ignore only if they authored this line.
				if (isSender || authoredBy(raw, e)) {
					return true;
				}
			} else if (lowerRaw.contains(le)) {
				// Plain text: match anywhere.
				return true;
			}
		}
		return false;
	}

	/** True if {@code name} is a player currently in the tab list. */
	private static boolean isKnownPlayer(String name) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.getNetworkHandler() == null) {
			return false;
		}
		for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
			if (entry.getProfile() != null && name.equalsIgnoreCase(entry.getProfile().name())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether {@code player} appears as a whole word in the author portion of a chat line — the text
	 * before the first message separator (e.g. {@code <Name> msg}, {@code [Rank] Name: msg},
	 * {@code Name » msg}). Only the first ~48 chars are considered, so a name buried in the body
	 * doesn't count as authorship.
	 */
	private static boolean authoredBy(String raw, String player) {
		int cap = Math.min(raw.length(), 48);
		int idx = -1;
		for (String sep : SEPARATORS) {
			int i = raw.indexOf(sep);
			if (i >= 0 && i < cap && (idx < 0 || i < idx)) {
				idx = i;
			}
		}
		if (idx < 0) {
			return false;
		}
		for (String token : raw.substring(0, idx).split("[^A-Za-z0-9_]+")) {
			if (token.equalsIgnoreCase(player)) {
				return true;
			}
		}
		return false;
	}

	private static final String[] SEPARATORS = {":", "»", "›", ">", "▸", "|"};

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
