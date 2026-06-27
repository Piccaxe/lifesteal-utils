package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.PiccaxeLsUtils;
import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Auto-clipper: when you get a kill (or optionally die), it presses your recording app's clip hotkey
 * so the moment is saved. Uses {@link Robot} to emit the key combo at the OS level, which global
 * hotkey listeners (NVIDIA ShadowPlay, OBS replay buffer, Medal, etc.) pick up. Kills are detected
 * from the server's death messages via {@link DeathKillRelay#involvement}.
 */
public final class AutoClipper {
	private static long lastClip = 0L;
	private static volatile boolean pendingNotify = false;

	static {
		// Robot needs a non-headless AWT; make sure that's the case before it initializes.
		System.setProperty("java.awt.headless", "false");
	}

	private AutoClipper() {
	}

	public static void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				handle(message.getString());
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(AutoClipper::tick);
	}

	private static void handle(String raw) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.autoClip) {
			return;
		}
		int inv = DeathKillRelay.involvement(raw);
		if (inv < 0) {
			return;
		}
		boolean kill = inv == 1;
		boolean death = inv == 0;
		if ((kill && cfg.autoClipOnKill) || (death && cfg.autoClipOnDeath)) {
			clipNow();
		}
	}

	private static void tick(MinecraftClient mc) {
		if (pendingNotify && mc.player != null) {
			pendingNotify = false;
			mc.player.sendMessage(Text.literal("Clip saved").formatted(Formatting.AQUA), true);
		}
	}

	/** Sends the configured clip hotkey now (respects the cooldown). */
	public static void clipNow() {
		Config cfg = ConfigManager.get();
		long now = System.currentTimeMillis();
		if (now - lastClip < Math.max(0, cfg.clipCooldownSeconds) * 1000L) {
			return;
		}
		lastClip = now;
		int[] codes = parseHotkey(cfg.clipHotkey);
		if (codes.length == 0) {
			PiccaxeLsUtils.LOGGER.warn("[autoclip] couldn't parse hotkey '{}'", cfg.clipHotkey);
			return;
		}
		new Thread(() -> {
			try {
				Robot robot = new Robot();
				for (int code : codes) {
					robot.keyPress(code);
				}
				Thread.sleep(60);
				for (int i = codes.length - 1; i >= 0; i--) {
					robot.keyRelease(codes[i]);
				}
				pendingNotify = true;
				PiccaxeLsUtils.LOGGER.info("[autoclip] sent clip hotkey {}", cfg.clipHotkey);
			} catch (Throwable t) {
				PiccaxeLsUtils.LOGGER.warn("[autoclip] failed to send hotkey: {}", t.toString());
			}
		}, "piccaxe-autoclip").start();
	}

	private static int[] parseHotkey(String hotkey) {
		if (hotkey == null || hotkey.isBlank()) {
			return new int[0];
		}
		List<Integer> codes = new ArrayList<>();
		for (String raw : hotkey.toUpperCase(Locale.ROOT).split("\\+")) {
			String t = raw.trim();
			if (t.isEmpty()) {
				continue;
			}
			switch (t) {
				case "ALT" -> codes.add(KeyEvent.VK_ALT);
				case "CTRL", "CONTROL" -> codes.add(KeyEvent.VK_CONTROL);
				case "SHIFT" -> codes.add(KeyEvent.VK_SHIFT);
				case "WIN", "META", "SUPER" -> codes.add(KeyEvent.VK_WINDOWS);
				default -> {
					if (t.length() == 1) {
						char c = t.charAt(0);
						if (c >= 'A' && c <= 'Z') {
							codes.add(KeyEvent.VK_A + (c - 'A'));
						} else if (c >= '0' && c <= '9') {
							codes.add(KeyEvent.VK_0 + (c - '0'));
						}
					} else if (t.startsWith("F")) {
						try {
							int n = Integer.parseInt(t.substring(1));
							if (n >= 1 && n <= 12) {
								codes.add(KeyEvent.VK_F1 + (n - 1));
							}
						} catch (NumberFormatException ignored) {
							// not an F-key
						}
					}
				}
			}
		}
		int[] out = new int[codes.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = codes.get(i);
		}
		return out;
	}
}
