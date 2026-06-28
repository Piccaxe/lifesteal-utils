package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;

/**
 * Low-HP alert: tints the screen red and plays a warning sound when the player's health drops
 * below {@code lowHpThreshold} (default 10 HP = 5 hearts). The red hue intensifies the closer to
 * death you are. The sound fires once each time you cross below the threshold (not every frame).
 */
public final class LowHpAlert {
	private static boolean wasLow = false;

	private LowHpAlert() {
	}

	public static void render(DrawContext ctx, MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.lowHpAlert || mc.player == null
			|| mc.player.isSpectator() || mc.player.isDead()) {
			wasLow = false;
			return;
		}

		float hp = mc.player.getHealth();
		float thr = (float) cfg.lowHpThreshold;
		if (hp <= 0.0F || hp > thr) {
			wasLow = false;
			return;
		}

		// Crossed below the threshold this frame -> one-shot warning sound.
		if (!wasLow) {
			wasLow = true;
			if (cfg.lowHpSound) {
				mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0F, 0.5F);
			}
		}

		// Intensity ramps from faint at the threshold to strong near death.
		float t = MathHelper.clamp(1.0F - hp / thr, 0.18F, 1.0F);
		int w = mc.getWindow().getScaledWidth();
		int h = mc.getWindow().getScaledHeight();

		// Flat red wash over the whole screen for the hue.
		int washA = (int) (t * 60.0F);
		ctx.fill(0, 0, w, h, (washA << 24) | 0x00FF1010);

		// Stronger red gradient along the top and bottom edges for a vignette feel.
		int edge = (int) (t * 170.0F);
		int edgeColor = (edge << 24) | 0x00FF0000;
		int clear = 0x00FF0000;
		int band = Math.max(24, h / 4);
		ctx.fillGradient(0, 0, w, band, edgeColor, clear);
		ctx.fillGradient(0, h - band, w, h, clear, edgeColor);
	}
}
