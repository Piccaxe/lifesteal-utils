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
		Config.WebhookEntry wh = ConfigManager.webhook(cfg.deathKillWebhook);
		if (wh == null || wh.url == null || wh.url.isBlank()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.execute(() -> capture(mc, wh, raw));
	}

	private static void capture(MinecraftClient mc, Config.WebhookEntry wh, String raw) {
		try {
			ScreenshotRecorder.takeScreenshot(mc.getFramebuffer(), image -> {
				try {
					Path tmp = Files.createTempFile("lsu-kill", ".png");
					image.writeTo(tmp);
					byte[] bytes = Files.readAllBytes(tmp);
					Files.deleteIfExists(tmp);
					DiscordWebhook.sendFile(wh.url, wh.username, "⚔ " + raw, bytes, "kill.png");
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
