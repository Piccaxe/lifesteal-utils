package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.PiccaxeLsUtils;
import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Auto-screenshots the moment you get a kill and posts it to your death/kill Discord webhook.
 * Detects kills from system death messages ({@link DeathKillRelay#involvement}), captures the
 * framebuffer on the render thread, then uploads the PNG via {@link DiscordWebhook#sendFile}.
 */
public final class KillScreenshot {
	private KillScreenshot() {
	}

	public static void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				handle(message.getString());
			}
		});
	}

	private static void handle(String raw) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.screenshotOnKill) {
			return;
		}
		if (DeathKillRelay.involvement(raw) != 1) {
			return; // only our own kills
		}
		shoot("⚔ " + raw, "kill.png");
	}

	/** Captures the screen on the render thread and uploads it to the death/kill webhook (if one is set). */
	public static void shoot(String caption, String fileName) {
		Config.WebhookEntry wh = ConfigManager.webhook(ConfigManager.get().deathKillWebhook);
		if (wh == null || wh.url == null || wh.url.isBlank()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.execute(() -> capture(mc, wh, caption, fileName));
	}

	private static void capture(MinecraftClient mc, Config.WebhookEntry wh, String caption, String fileName) {
		try {
			ScreenshotRecorder.takeScreenshot(mc.getFramebuffer(), image -> {
				try {
					Path tmp = Files.createTempFile("lsu-kill", ".png");
					image.writeTo(tmp);
					byte[] bytes = Files.readAllBytes(tmp);
					Files.deleteIfExists(tmp);
					DiscordWebhook.sendFile(wh.url, wh.username, caption, bytes, fileName);
				} catch (Exception e) {
					PiccaxeLsUtils.LOGGER.warn("[killshot] encode/upload failed: {}", e.toString());
				} finally {
					image.close();
				}
			});
		} catch (Throwable t) {
			PiccaxeLsUtils.LOGGER.warn("[killshot] capture failed: {}", t.toString());
		}
	}
}
