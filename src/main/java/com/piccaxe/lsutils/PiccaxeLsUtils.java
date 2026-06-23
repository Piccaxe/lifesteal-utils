package com.piccaxe.lsutils;

import com.piccaxe.lsutils.command.Commands;
import com.piccaxe.lsutils.config.ConfigManager;
import com.piccaxe.lsutils.feature.AntiTrickster;
import com.piccaxe.lsutils.feature.AutoReconnect;
import com.piccaxe.lsutils.feature.ChatRelay;
import com.piccaxe.lsutils.feature.DeathTracker;
import com.piccaxe.lsutils.feature.ProximityAlert;
import com.piccaxe.lsutils.hud.HudManager;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for Piccaxe's Lifesteal Utils — a client-side quality-of-life mod.
 * Wires up config, keybinds, the HUD overlay, the per-tick features, and commands.
 */
public class PiccaxeLsUtils implements ClientModInitializer {
	public static final String MOD_ID = "piccaxelsutils";
	public static final Logger LOGGER = LoggerFactory.getLogger("Piccaxe's Lifesteal Utils");

	@Override
	public void onInitializeClient() {
		ConfigManager.load();

		KeyBindings.register();
		HudManager.register();
		ProximityAlert.register();
		DeathTracker.register();
		AutoReconnect.register();
		AntiTrickster.register();
		ChatRelay.register();
		Commands.register();

		LOGGER.info("Piccaxe's Lifesteal Utils loaded.");
	}
}
