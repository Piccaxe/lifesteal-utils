package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Warns when another player newly enters a configurable radius around you:
 * plays a sound and shows a short centered banner. Only fires on the
 * rising edge (entering the radius), not every tick they remain in range.
 */
public final class ProximityAlert {
	private static final Set<UUID> known = new HashSet<>();
	private static final Map<UUID, Long> lastAlert = new HashMap<>();
	private static long bannerUntil = 0L;
	private static String bannerText = "";

	private ProximityAlert() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ProximityAlert::tick);
	}

	private static void tick(MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.proximityAlert || mc.player == null || mc.world == null) {
			known.clear();
			lastAlert.clear();
			return;
		}

		long now = System.currentTimeMillis();
		long cdMs = Math.max(0, cfg.proximityCooldownSeconds) * 1000L;
		double radiusSq = cfg.proximityRadius * cfg.proximityRadius;
		Set<UUID> inRange = new HashSet<>();
		double closestSq = Double.MAX_VALUE;
		String closestName = null;

		for (var player : mc.world.getPlayers()) {
			if (player == mc.player) {
				continue;
			}
			double distSq = player.squaredDistanceTo(mc.player);
			if (distSq <= radiusSq) {
				UUID id = player.getUuid();
				inRange.add(id);
				String name = player.getName().getString();
				// Rising edge + per-player cooldown: a player who keeps leaving/re-entering the radius
				// won't re-alert until the cooldown elapses.
				boolean fresh = !known.contains(id) && !isIgnored(cfg, name)
					&& now - lastAlert.getOrDefault(id, 0L) >= cdMs;
				if (fresh) {
					lastAlert.put(id, now);
					if (distSq < closestSq) {
						closestSq = distSq;
						closestName = name;
					}
					if (cfg.proximityDiscord && isWatched(cfg, name)) {
						pingDiscord(cfg, name, (int) Math.sqrt(distSq));
					}
				}
			}
		}

		if (closestName != null) {
			bannerText = "⚠ " + closestName + " nearby (" + (int) Math.sqrt(closestSq) + "m)";
			bannerUntil = System.currentTimeMillis() + 3000L;
			if (cfg.proximitySound) {
				mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0F, 1.2F);
			}
		}

		known.clear();
		known.addAll(inRange);
	}

	private static boolean isIgnored(Config cfg, String name) {
		return cfg.proximityIgnore.contains(name.toLowerCase(Locale.ROOT));
	}

	private static boolean isWatched(Config cfg, String name) {
		return cfg.proximityWatchlist.contains(name.toLowerCase(Locale.ROOT));
	}

	private static void pingDiscord(Config cfg, String name, int distance) {
		Config.WebhookEntry webhook = ConfigManager.webhook(cfg.proximityWebhook);
		if (webhook == null || webhook.url == null || webhook.url.isBlank()) {
			return;
		}
		String ping = cfg.proximityPing == null ? "" : cfg.proximityPing.trim();
		String content = (ping.isEmpty() ? "" : ping + " ") + "⚠ " + name + " is nearby (" + distance + "m)";
		DiscordWebhook.sendThrottled(webhook, content, true);
	}

	public static void renderBanner(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.proximityAlert || System.currentTimeMillis() > bannerUntil) {
			return;
		}
		Text text = Text.literal(bannerText);
		int screenWidth = mc.getWindow().getScaledWidth();
		ctx.drawCenteredTextWithShadow(tr, text, screenWidth / 2, 20, 0xFFFF5555);
	}
}
