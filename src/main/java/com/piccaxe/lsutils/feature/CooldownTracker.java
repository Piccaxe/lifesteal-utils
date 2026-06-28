package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks custom-enchant cooldowns by watching incoming chat. Each {@link Config.CooldownRule} says
 * "when a message contains this keyword, start an N-second timer named X". The cooldown HUD then
 * counts each timer down; a finished cooldown flashes READY (with an optional ding) for a few seconds.
 *
 * <p>Lifesteal servers print their own messages when an enchant fires, so the keyword is configurable
 * per rule — e.g. rule {@code Heavenly} / keyword {@code "heavenly is now on cooldown"} / 300s.
 */
public final class CooldownTracker {
	private static final long READY_GRACE_MS = 4000L;

	private static final Map<String, Long> endAt = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> readyNotified = new ConcurrentHashMap<>();

	private CooldownTracker() {
	}

	public static void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> onMessage(message.getString()));
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) -> onMessage(message.getString()));
		ClientTickEvents.END_CLIENT_TICK.register(CooldownTracker::tick);
	}

	private static void onMessage(String raw) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.cooldownHud || raw == null || raw.isEmpty()) {
			return;
		}
		String lower = raw.toLowerCase(Locale.ROOT);
		for (Config.CooldownRule rule : cfg.cooldownRules) {
			if (rule == null || !rule.enabled || rule.seconds <= 0 || rule.keyword == null || rule.keyword.isBlank()) {
				continue;
			}
			if (lower.contains(rule.keyword.toLowerCase(Locale.ROOT))) {
				endAt.put(rule.name, System.currentTimeMillis() + rule.seconds * 1000L);
				readyNotified.remove(rule.name);
			}
		}
	}

	private static void tick(MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (endAt.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		for (Map.Entry<String, Long> e : endAt.entrySet()) {
			String name = e.getKey();
			long end = e.getValue();
			if (now >= end) {
				if (!Boolean.TRUE.equals(readyNotified.get(name))) {
					readyNotified.put(name, true);
					if (cfg.cooldownReadySound && mc.player != null) {
						mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0F, 1.4F);
						mc.player.sendMessage(Text.literal(name + " is ready!").formatted(Formatting.GREEN), true);
					}
				}
				if (now >= end + READY_GRACE_MS) {
					endAt.remove(name);
					readyNotified.remove(name);
				}
			}
		}
	}

	/** Ordered display lines for the HUD (timers first by time left, then READY flashes). */
	public static List<Text> lines() {
		if (endAt.isEmpty()) {
			return List.of();
		}
		long now = System.currentTimeMillis();
		List<Text> out = new ArrayList<>();
		// Preserve config order for stability.
		for (Config.CooldownRule rule : ConfigManager.get().cooldownRules) {
			Long end = endAt.get(rule.name);
			if (end == null) {
				continue;
			}
			long remMs = end - now;
			if (remMs > 0) {
				int s = (int) Math.ceil(remMs / 1000.0);
				Formatting color = s <= 5 ? Formatting.YELLOW : Formatting.RED;
				out.add(Text.literal(rule.name + " ").formatted(Formatting.WHITE)
					.append(Text.literal(fmt(s)).formatted(color)));
			} else {
				out.add(Text.literal(rule.name + " ").formatted(Formatting.WHITE)
					.append(Text.literal("READY").formatted(Formatting.GREEN)));
			}
		}
		return out;
	}

	private static String fmt(int seconds) {
		if (seconds >= 60) {
			return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
		}
		return seconds + "s";
	}

	/** Sample lines for the HUD editor preview. */
	public static List<Text> sampleLines() {
		List<Text> out = new ArrayList<>();
		out.add(Text.literal("Heavenly ").formatted(Formatting.WHITE).append(Text.literal("4:32").formatted(Formatting.RED)));
		out.add(Text.literal("Frenzy ").formatted(Formatting.WHITE).append(Text.literal("READY").formatted(Formatting.GREEN)));
		return out;
	}
}
