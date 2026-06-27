package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;

/**
 * Tracks whether you're "in combat" — refreshed whenever you hit a living entity or take damage.
 * The {@code COMBAT} HUD element shows the remaining timer (handy for knowing when it's safe to log).
 */
public final class CombatTracker {
	private static long lastCombat = 0L;
	private static float lastHealth = -1.0F;

	private CombatTracker() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (world.isClient() && player == mc.player && entity instanceof LivingEntity) {
				mark();
			}
			return ActionResult.PASS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(CombatTracker::tick);
	}

	private static void tick(MinecraftClient mc) {
		if (mc.player == null) {
			lastHealth = -1.0F;
			return;
		}
		float h = mc.player.getHealth();
		if (lastHealth >= 0.0F && h < lastHealth - 0.01F) {
			mark(); // took damage
		}
		lastHealth = h;
	}

	private static void mark() {
		lastCombat = System.currentTimeMillis();
	}

	public static int secondsLeft() {
		long rem = lastCombat + ConfigManager.get().combatTagSeconds * 1000L - System.currentTimeMillis();
		return rem > 0 ? (int) Math.ceil(rem / 1000.0) : 0;
	}
}
