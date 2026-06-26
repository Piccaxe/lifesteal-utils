package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Tracks your lifesteal max-heart count: announces when you gain or lose hearts (max-health changes),
 * keeps a session net total, and can ping Discord when you lose a heart. A short settle window after
 * joining avoids false reports while the max-health attribute is still syncing.
 */
public final class HeartTracker {
	private static final int SETTLE_TICKS = 40; // ~2s for attributes to sync before tracking

	private static float lastMax = -1.0F;
	private static float sessionStartMax = -1.0F;
	private static int ticks = 0;

	private HeartTracker() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(HeartTracker::tick);
	}

	/** Current max hearts (max health / 2), or -1 if unknown. */
	public static int currentHearts() {
		return lastMax < 0 ? -1 : Math.round(lastMax / 2.0F);
	}

	/** Net hearts gained (+) or lost (-) since this session's baseline. */
	public static int sessionNet() {
		if (lastMax < 0 || sessionStartMax < 0) {
			return 0;
		}
		return Math.round((lastMax - sessionStartMax) / 2.0F);
	}

	private static void tick(MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.heartTracker || mc.player == null || mc.world == null) {
			lastMax = -1.0F;
			ticks = 0;
			return;
		}

		float max = mc.player.getMaxHealth();
		ticks++;
		if (ticks <= SETTLE_TICKS) {
			// Continuously rebaseline while attributes settle, without reporting.
			lastMax = max;
			sessionStartMax = max;
			return;
		}
		if (lastMax < 0) {
			lastMax = max;
			return;
		}
		if (Math.abs(max - lastMax) < 0.01F) {
			return;
		}

		int deltaHearts = Math.round((max - lastMax) / 2.0F);
		lastMax = max;
		if (deltaHearts == 0) {
			return; // sub-heart change (e.g. effect), ignore
		}
		announce(mc, cfg, deltaHearts, Math.round(max / 2.0F));
	}

	private static void announce(MinecraftClient mc, Config cfg, int deltaHearts, int nowHearts) {
		boolean gain = deltaHearts > 0;
		String word = Math.abs(deltaHearts) == 1 ? "heart" : "hearts";
		String text = (gain ? "❤ +" + deltaHearts : "💔 " + deltaHearts) + " " + word + " (now " + nowHearts + ")";

		if (cfg.heartTrackerChat && mc.player != null) {
			mc.player.sendMessage(Text.literal(text).formatted(gain ? Formatting.GREEN : Formatting.RED), false);
		}
		if (!gain && cfg.heartTrackerDiscord) {
			Config.WebhookEntry webhook = ConfigManager.webhook(cfg.heartWebhook);
			if (webhook != null && webhook.url != null && !webhook.url.isBlank()) {
				String ping = cfg.heartLossPing == null ? "" : cfg.heartLossPing.trim();
				String content = (ping.isEmpty() ? "" : ping + " ") + "💔 Lost " + (-deltaHearts) + " " + word
					+ " — now at " + nowHearts + " hearts";
				DiscordWebhook.sendThrottled(webhook, content, !ping.isEmpty());
			}
		}
	}
}
