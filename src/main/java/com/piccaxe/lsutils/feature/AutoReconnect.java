package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

/**
 * Auto-rejoins the last multiplayer server after a disconnect/kick.
 *
 * <p>The server is captured on join. While a {@link DisconnectedScreen} is open and
 * the feature is enabled, a countdown ticks down and then re-initiates the connection
 * (up to a configurable attempt cap). Leaving the disconnect screen, or hitting the
 * cap, stops it; a successful join resets the attempt counter.
 */
public final class AutoReconnect {
	private static ServerInfo lastServer = null;
	private static int attempts = 0;
	private static int ticksOnScreen = 0;
	private static boolean reconnecting = false;

	private AutoReconnect() {
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ServerInfo info = client.getCurrentServerEntry();
			if (info != null) {
				lastServer = info;
				attempts = 0;
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(AutoReconnect::tick);

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof DisconnectedScreen) {
				ScreenEvents.afterRender(screen).register((scr, ctx, mouseX, mouseY, delta) -> renderStatus(client, ctx));
			}
		});
	}

	private static void tick(MinecraftClient client) {
		Config cfg = ConfigManager.get();

		if (!(client.currentScreen instanceof DisconnectedScreen)) {
			ticksOnScreen = 0;
			reconnecting = false;
			return;
		}

		if (!cfg.masterEnabled || !cfg.autoReconnect || lastServer == null || reconnecting) {
			return;
		}
		if (attempts >= cfg.autoReconnectMaxAttempts) {
			return;
		}

		ticksOnScreen++;
		int delayTicks = Math.max(1, cfg.autoReconnectDelaySeconds) * 20;
		if (ticksOnScreen >= delayTicks) {
			ticksOnScreen = 0;
			attempts++;
			reconnecting = true;
			reconnect(client);
		}
	}

	private static void reconnect(MinecraftClient client) {
		ServerInfo info = lastServer;
		ServerAddress address = ServerAddress.parse(info.address);
		ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), client, address, info, false, null);
	}

	private static void renderStatus(MinecraftClient client, DrawContext ctx) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.autoReconnect || lastServer == null) {
			return;
		}

		String msg;
		if (attempts >= cfg.autoReconnectMaxAttempts) {
			msg = "Auto-reconnect: max attempts reached";
		} else {
			int delayTicks = Math.max(1, cfg.autoReconnectDelaySeconds) * 20;
			int remaining = Math.max(0, (delayTicks - ticksOnScreen + 19) / 20);
			msg = "Auto-reconnecting in " + remaining + "s (attempt "
				+ (attempts + 1) + "/" + cfg.autoReconnectMaxAttempts + ")";
		}

		int screenWidth = client.getWindow().getScaledWidth();
		ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal(msg), screenWidth / 2, 8, 0xFFFFFF55);
	}
}
