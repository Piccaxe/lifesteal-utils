package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Warns when watched potion effects (default fire resistance + strength) are about to expire:
 * a chat + action-bar message at {@code potionWarnSeconds} (default 10) and an alert sound at
 * {@code potionSoundSeconds} (default 3). Each threshold fires once per crossing; re-applying the
 * potion (duration jumps back up) re-arms the warnings.
 */
public final class PotionWarning {
	/** Remaining whole seconds seen last tick, per watched effect id — used to detect downward crossings. */
	private static final Map<String, Integer> lastRemaining = new HashMap<>();

	private PotionWarning() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(PotionWarning::tick);
	}

	private static void tick(MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.potionWarn || mc.player == null) {
			if (!lastRemaining.isEmpty()) {
				lastRemaining.clear();
			}
			return;
		}

		int warnSec = Math.max(1, cfg.potionWarnSeconds);
		int soundSec = Math.max(0, cfg.potionSoundSeconds);
		Set<String> present = new HashSet<>();

		for (StatusEffectInstance inst : mc.player.getStatusEffects()) {
			Identifier id = Registries.STATUS_EFFECT.getId(inst.getEffectType().value());
			if (id == null) {
				continue;
			}
			String path = id.getPath();
			if (!watched(cfg, path) || inst.isInfinite()) {
				continue;
			}
			present.add(path);

			int rem = (int) Math.ceil(inst.getDuration() / 20.0);
			int last = lastRemaining.getOrDefault(path, Integer.MAX_VALUE);
			String name = inst.getEffectType().value().getName().getString();

			if (last > warnSec && rem <= warnSec) {
				Text msg = Text.literal("⚠ " + name + " — " + rem + "s left!").formatted(Formatting.GOLD);
				mc.player.sendMessage(msg, false); // chat
				mc.player.sendMessage(msg, true);  // above hotbar
			}
			if (soundSec > 0 && last > soundSec && rem <= soundSec) {
				mc.player.sendMessage(Text.literal("⚠ " + name + " — " + rem + "s!").formatted(Formatting.RED), true);
				mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0F, 0.6F);
			}
			lastRemaining.put(path, rem);
		}

		// Drop effects no longer active so a fresh application re-arms the thresholds.
		lastRemaining.keySet().removeIf(k -> !present.contains(k));
	}

	private static boolean watched(Config cfg, String path) {
		for (String entry : cfg.potionWarnEffects) {
			if (entry == null || entry.isBlank()) {
				continue;
			}
			String e = entry.toLowerCase(Locale.ROOT).trim();
			if (path.equals(e) || path.contains(e)) {
				return true;
			}
		}
		return false;
	}
}
