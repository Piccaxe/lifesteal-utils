package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Notifies when another player enters your render distance (loads into the world),
 * via any of: chat message, sound, on-screen banner, and Discord webhook.
 *
 * <p>Detected by diffing the set of loaded player entities each tick. The first
 * snapshot after (re)connecting is taken silently so you aren't spammed for everyone
 * already nearby when you join. This is the wide "someone is around" early warning;
 * {@link ProximityAlert} is the close-range (radius) warning.
 */
public final class PlayerNotifier {
	private static final Set<UUID> known = new HashSet<>();
	private static boolean initialized = false;
	private static long bannerUntil = 0L;
	private static String bannerText = "";

	private PlayerNotifier() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(PlayerNotifier::tick);
	}

	private static void tick(MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.playerNotifier || mc.player == null || mc.world == null) {
			known.clear();
			initialized = false;
			return;
		}

		Set<UUID> current = new HashSet<>();
		List<String> joined = new ArrayList<>();
		for (var player : mc.world.getPlayers()) {
			if (player == mc.player) {
				continue;
			}
			UUID id = player.getUuid();
			current.add(id);
			if (initialized && !known.contains(id)) {
				String name = player.getName().getString();
				if (!isIgnored(cfg, name)) {
					joined.add(name);
				}
			}
		}

		known.clear();
		known.addAll(current);

		if (!initialized) {
			initialized = true;
			return;
		}
		for (String name : joined) {
			notify(mc, cfg, name);
		}
	}

	private static boolean isIgnored(Config cfg, String name) {
		return cfg.notifierIgnore.contains(name.toLowerCase(Locale.ROOT));
	}

	private static void notify(MinecraftClient mc, Config cfg, String name) {
		if (cfg.notifierChat) {
			mc.player.sendMessage(Text.literal("+ " + name + " entered render distance").formatted(Formatting.YELLOW), false);
		}
		if (cfg.notifierSound) {
			mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0F, 1.5F);
		}
		if (cfg.notifierBanner) {
			bannerText = "+ " + name;
			bannerUntil = System.currentTimeMillis() + 3000L;
		}
		if (cfg.notifierDiscord) {
			Config.WebhookEntry webhook = ConfigManager.webhook(cfg.notifierWebhook);
			if (webhook != null) {
				DiscordWebhook.sendThrottled(webhook, name + " entered render distance", false);
			}
		}
	}

	public static void renderBanner(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.playerNotifier || !cfg.notifierBanner
			|| System.currentTimeMillis() > bannerUntil) {
			return;
		}
		int screenWidth = mc.getWindow().getScaledWidth();
		ctx.drawCenteredTextWithShadow(tr, Text.literal(bannerText), screenWidth / 2, 32, 0xFFFFFF55);
	}
}
