package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;

/**
 * Crosshair hit-markers: when you land a hit on a living entity, briefly draws an X over the crosshair
 * and plays a click, like an FPS hitmarker. Drawn in the HUD layer (called from HudManager).
 */
public final class HitMarkers {
	private static long showUntil = 0L;

	private HitMarkers() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (world.isClient() && player == mc.player && entity instanceof LivingEntity) {
				Config cfg = ConfigManager.get();
				if (cfg.masterEnabled && cfg.hitMarkers) {
					showUntil = System.currentTimeMillis() + 250L;
					if (cfg.hitMarkerSound && mc.player != null) {
						mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.6F);
					}
				}
			}
			return ActionResult.PASS;
		});
	}

	public static void render(DrawContext ctx, MinecraftClient mc) {
		if (System.currentTimeMillis() > showUntil) {
			return;
		}
		int cx = mc.getWindow().getScaledWidth() / 2;
		int cy = mc.getWindow().getScaledHeight() / 2;
		int color = 0xFFFF5050;
		for (int i = 3; i <= 7; i++) {
			ctx.fill(cx - i, cy - i, cx - i + 1, cy - i + 1, color);
			ctx.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
			ctx.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, color);
			ctx.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
		}
	}
}
