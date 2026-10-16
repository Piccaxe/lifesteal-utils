package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Combat-log guard: while you're combat tagged, the pause menu's Disconnect button asks for
 * confirmation first, so you don't lose hearts by logging mid-fight by accident.
 */
public final class CombatLogGuard {
	private CombatLogGuard() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof GameMenuScreen menu) || client.isInSingleplayer()) {
				return;
			}
			Config cfg = ConfigManager.get();
			if (!cfg.masterEnabled || !cfg.combatLogGuard) {
				return;
			}
			Text disconnect = ScreenTexts.returnToMenuOrDisconnect(false);
			List<ClickableWidget> buttons = Screens.getButtons(screen);
			for (int i = 0; i < buttons.size(); i++) {
				ClickableWidget old = buttons.get(i);
				if (!(old instanceof ButtonWidget) || !old.getMessage().getString().equals(disconnect.getString())) {
					continue;
				}
				ButtonWidget guarded = ButtonWidget.builder(old.getMessage(), b -> onDisconnect(client, menu))
					.dimensions(old.getX(), old.getY(), old.getWidth(), old.getHeight()).build();
				buttons.set(i, guarded);
				return;
			}
		});
	}

	private static void onDisconnect(MinecraftClient client, GameMenuScreen menu) {
		int secs = CombatTracker.secondsLeft();
		if (secs <= 0) {
			client.disconnect(ClientWorld.QUITTING_MULTIPLAYER_TEXT);
			return;
		}
		client.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				client.disconnect(ClientWorld.QUITTING_MULTIPLAYER_TEXT);
			} else {
				client.setScreen(menu);
			}
		},
			Text.literal("You're in combat!").formatted(Formatting.RED),
			Text.literal("Combat tag: " + secs + "s left. Logging out now may count as combat logging and cost you hearts."),
			Text.literal("Disconnect anyway").formatted(Formatting.RED),
			Text.literal("Stay")));
	}
}
