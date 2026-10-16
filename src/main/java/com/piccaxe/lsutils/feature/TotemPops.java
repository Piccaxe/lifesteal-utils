package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Counts totem pops per player. The server broadcasts a "used totem" entity status (35) to everyone
 * nearby — that's what plays the totem animation — so this works for any player in render distance.
 * Counts reset when that player dies. Fed by {@code LivingEntityStatusMixin}.
 */
public final class TotemPops {
	private static final Map<UUID, Integer> pops = new HashMap<>();
	private static ClientWorld lastWorld;

	private TotemPops() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.world != lastWorld) {
				lastWorld = mc.world;
				pops.clear();
			}
			if (mc.world == null || pops.isEmpty()) {
				return;
			}
			// Dead players start fresh (only trust 0 HP where the server sends real health).
			pops.keySet().removeIf(id -> {
				PlayerEntity p = mc.world.getPlayerByUuid(id);
				return p != null && DamageTracker.isLive(id) && p.isDead();
			});
		});
		// Death messages reset the victim's count on servers that hide health.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (overlay || mc.world == null || pops.isEmpty()) {
				return;
			}
			String raw = message.getString();
			pops.keySet().removeIf(id -> {
				PlayerEntity p = mc.world.getPlayerByUuid(id);
				return p != null && DeathKillRelay.isVictim(raw, p.getName().getString());
			});
		});
	}

	public static int pops(UUID id) {
		return pops.getOrDefault(id, 0);
	}

	/** Called from the mixin when an entity plays the totem-of-undying status. */
	public static void onPop(LivingEntity entity) {
		Config cfg = ConfigManager.get();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!cfg.masterEnabled || !(entity instanceof PlayerEntity p) || mc.player == null) {
			return;
		}
		int n = pops.merge(p.getUuid(), 1, Integer::sum);
		boolean self = p == mc.player;

		if (!self) {
			FightSummary.onPop(p);
		}
		if (cfg.totemPops && cfg.totemPopChat) {
			String who = self ? "You" : p.getName().getString();
			mc.player.sendMessage(Text.literal("[LSU] ").formatted(Formatting.GOLD)
				.append(Text.literal(who + " popped a totem ").formatted(self ? Formatting.RED : Formatting.YELLOW))
				.append(Text.literal("(" + n + ")").formatted(Formatting.GOLD)), false);
		}
		if (self && cfg.screenshotOnPop) {
			KillScreenshot.shoot("🛡 Popped a totem (" + n + " this life)", "pop.png");
		} else if (!self && cfg.screenshotOnEnemyPop && FightSummary.isFighting(p.getUuid())) {
			KillScreenshot.shoot("💥 Popped " + p.getName().getString() + "'s totem (" + n + ")", "enemy-pop.png");
		}
	}
}
