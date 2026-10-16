package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Alerts when a nearby player drops to low health ("Steve is at 3❤, 12m"), using the same health value
 * the indicators show. Fires once per drop; re-arms after they heal a bit above the threshold.
 */
public final class EnemyLowHpAlert {
	private static final Set<UUID> alerted = new HashSet<>();
	private static int tick = 0;

	private EnemyLowHpAlert() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(EnemyLowHpAlert::tick);
	}

	private static void tick(MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (mc.player == null || mc.world == null) {
			alerted.clear();
			return;
		}
		if (!cfg.masterEnabled || !cfg.enemyLowHp || ++tick % 5 != 0) {
			return;
		}
		double rangeSq = (double) cfg.enemyLowHpRange * cfg.enemyLowHpRange;
		for (PlayerEntity p : mc.world.getPlayers()) {
			if (p == mc.player || p.isSpectator() || p.squaredDistanceTo(mc.player) > rangeSq) {
				continue;
			}
			UUID id = p.getUuid();
			float hp = HealthBars.displayedHealth(mc, p);
			if (hp <= 0.0F) {
				continue; // dead or unknown
			}
			if (hp <= cfg.enemyLowHpThreshold) {
				if (alerted.add(id)) {
					int dist = (int) Math.round(Math.sqrt(p.squaredDistanceTo(mc.player)));
					String hearts = String.valueOf(Math.round(hp / 2.0F * 2.0F) / 2.0F).replace(".0", "");
					mc.player.sendMessage(Text.literal("⚠ " + p.getName().getString() + " is at " + hearts + "❤ (" + dist + "m)")
						.formatted(Formatting.RED), true);
					if (cfg.enemyLowHpSound) {
						mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0F, 1.6F);
					}
				}
			} else if (hp > cfg.enemyLowHpThreshold + 2.0) {
				alerted.remove(id);
			}
		}
	}
}
