package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/**
 * Records the player's position the moment they die, so the HUD can show a
 * "run back to your stuff" waypoint. Fires once on the death edge and persists
 * to config so it survives relogs.
 */
public final class DeathTracker {
	private static boolean wasDead = false;

	private DeathTracker() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(DeathTracker::tick);
	}

	private static void tick(MinecraftClient mc) {
		if (mc.player == null) {
			wasDead = false;
			return;
		}

		boolean dead = mc.player.isDead() || mc.player.getHealth() <= 0.0F;
		if (dead && !wasDead) {
			Config cfg = ConfigManager.get();
			cfg.hasDeath = true;
			cfg.deathX = mc.player.getX();
			cfg.deathY = mc.player.getY();
			cfg.deathZ = mc.player.getZ();
			cfg.deathDim = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";
			ConfigManager.save();
		}
		wasDead = dead;
	}
}
