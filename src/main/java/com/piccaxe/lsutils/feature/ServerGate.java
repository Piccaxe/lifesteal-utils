package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/**
 * Single chokepoint for the per-server gate. When the server whitelist is enabled, this recomputes
 * the effective {@code masterEnabled = masterEnabledUser && serverAllowed()} each tick, so every
 * feature (which already gates on {@code masterEnabled}) is automatically suppressed on non-whitelisted
 * servers. Registered first so the recompute runs before the feature ticks in the same tick.
 */
public final class ServerGate {
	private ServerGate() {
	}

	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(ServerGate::tick);
	}

	private static void tick(MinecraftClient mc) {
		Config c = ConfigManager.get();
		if (!c.serverWhitelistEnabled) {
			return;
		}
		boolean eff = c.masterEnabledUser && c.serverAllowed();
		if (c.masterEnabled != eff) {
			c.masterEnabled = eff; // derived; not saved here (intent lives in masterEnabledUser)
		}
	}
}
