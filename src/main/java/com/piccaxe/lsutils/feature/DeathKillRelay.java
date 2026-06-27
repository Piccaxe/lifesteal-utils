package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;

import java.util.Locale;

/**
 * Auto-posts your own deaths and kills to Discord. Works off the server's system death messages
 * (vanilla-style phrasing) and only relays lines that involve <em>you</em>: if your name appears
 * before the death phrase you're the victim (death); after it, you're the killer (kill).
 */
public final class DeathKillRelay {
	private static final String[] DEATH_PHRASES = {
		"slain", "killed", "shot", "blown up", "blew up", "fireballed", "pricked",
		"impaled", "squished", "squashed", "poked to death", "died", "drowned",
		"burned", "went up in flames", "walked into fire", "lava", "struck by lightning",
		"starved", "suffocated", "withered", "froze", "stung to death", "obliterated",
		"doomed", "fell", "hit the ground too hard", "kinetic energy", "skewered"
	};

	private static String lastRaw = "";
	private static long lastTime = 0L;

	private DeathKillRelay() {
	}

	public static void register() {
		// Death messages arrive as system (GAME) messages, not player chat.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				handle(message.getString());
			}
		});
	}

	private static void handle(String raw) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.deathKillRelay || raw == null || raw.isBlank() || isDuplicate(raw)) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.getGameProfile() == null) {
			return;
		}
		String myName = mc.getGameProfile().name();
		if (myName == null || myName.isBlank()) {
			return;
		}

		String lower = raw.toLowerCase(Locale.ROOT);
		String lowerName = myName.toLowerCase(Locale.ROOT);
		int phraseIndex = firstPhraseIndex(lower);
		if (phraseIndex < 0) {
			return; // not a death message
		}
		int nameIndex = lower.indexOf(lowerName);
		if (nameIndex < 0) {
			return; // doesn't involve me
		}

		boolean iAmVictim = nameIndex < phraseIndex;
		if (iAmVictim && !cfg.relayMyDeaths) {
			return;
		}
		if (!iAmVictim && !cfg.relayMyKills) {
			return;
		}

		Config.WebhookEntry webhook = ConfigManager.webhook(cfg.deathKillWebhook);
		if (webhook == null || webhook.url == null || webhook.url.isBlank()) {
			return;
		}
		String ping = iAmVictim && cfg.deathKillPing != null ? cfg.deathKillPing.trim() : "";
		String content = (ping.isEmpty() ? "" : ping + " ") + (iAmVictim ? "💀 " : "⚔️ ") + raw;
		DiscordWebhook.sendThrottled(webhook, content, !ping.isEmpty());
	}

	/** -1 = not a death message involving you, 0 = you died, 1 = you got the kill. */
	public static int involvement(String raw) {
		if (raw == null || raw.isBlank()) {
			return -1;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.getGameProfile() == null) {
			return -1;
		}
		String myName = mc.getGameProfile().name();
		if (myName == null || myName.isBlank()) {
			return -1;
		}
		String lower = raw.toLowerCase(Locale.ROOT);
		int phrase = firstPhraseIndex(lower);
		if (phrase < 0) {
			return -1;
		}
		int nameIndex = lower.indexOf(myName.toLowerCase(Locale.ROOT));
		if (nameIndex < 0) {
			return -1;
		}
		return nameIndex < phrase ? 0 : 1;
	}

	private static int firstPhraseIndex(String lower) {
		int best = -1;
		for (String phrase : DEATH_PHRASES) {
			int i = lower.indexOf(phrase);
			if (i >= 0 && (best < 0 || i < best)) {
				best = i;
			}
		}
		return best;
	}

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
