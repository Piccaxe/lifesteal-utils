package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Tracers: lines from your crosshair to each player (optionally hostile mobs) within range, drawn in the
 * HUD layer. Targets behind you get a line to the matching screen edge so you can still tell where they are.
 * Colour follows the Player Outliner relation (teammate/ally/enemy) when enabled, else a fixed colour,
 * fading with distance.
 */
public final class Tracers {
	private Tracers() {
	}

	public static void render(DrawContext ctx, MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (mc.player == null || mc.world == null || mc.options.hudHidden) {
			return;
		}
		int sw = mc.getWindow().getScaledWidth();
		int sh = mc.getWindow().getScaledHeight();
		float cx = sw / 2.0F;
		float cy = sh / 2.0F;
		double rangeSq = (double) cfg.tracerRange * cfg.tracerRange;

		for (Entity e : mc.world.getEntities()) {
			if (e == mc.player || e.isRemoved()) {
				continue;
			}
			boolean player = e instanceof PlayerEntity p && !p.isSpectator();
			boolean mob = cfg.tracerMobs && e instanceof Monster;
			if (!player && !mob) {
				continue;
			}
			double distSq = e.squaredDistanceTo(mc.player);
			if (distSq > rangeSq) {
				continue;
			}
			int rgb = cfg.tracerColor & 0xFFFFFF;
			if (player && cfg.tracerRelationColors) {
				int rel = PlayerOutliner.outlineColorFor((PlayerEntity) e) & 0xFFFFFF;
				if (rel != 0) {
					rgb = rel;
				}
			} else if (mob) {
				rgb = 0xFF5555;
			}
			// Closer = more opaque.
			float t = (float) Math.sqrt(distSq) / cfg.tracerRange;
			int alpha = (int) (255 * (1.0F - 0.6F * Math.min(1.0F, t)));
			Vec3d target = new Vec3d(e.getX(), e.getY() + e.getHeight() * 0.6, e.getZ());
			float[] s = ProjectionUtil.projectTowards(target, sw, sh);
			HudDraw.line(ctx, cx, cy, s[0], s[1], (alpha << 24) | rgb, 2);
		}
	}
}
