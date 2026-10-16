package com.piccaxe.lsutils.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Line drawing for the HUD layer (tracers, trajectories). DrawContext has no line primitive, so lines
 * are stepped as small fills — drawn at physical-pixel resolution so they stay thin at any GUI scale.
 */
public final class HudDraw {
	private HudDraw() {
	}

	/** Line between two GUI-scaled points, {@code thickness} physical pixels wide. */
	public static void line(DrawContext ctx, float x0, float y0, float x1, float y1, int argb, int thickness) {
		float f = (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
		var m = ctx.getMatrices();
		m.pushMatrix();
		m.scale(1.0F / f);
		float ax = x0 * f;
		float ay = y0 * f;
		float bx = x1 * f;
		float by = y1 * f;
		int steps = (int) Math.max(Math.abs(bx - ax), Math.abs(by - ay));
		int half = thickness / 2;
		if (steps == 0) {
			ctx.fill((int) ax - half, (int) ay - half, (int) ax - half + thickness, (int) ay - half + thickness, argb);
		} else {
			float dx = (bx - ax) / steps;
			float dy = (by - ay) / steps;
			float x = ax;
			float y = ay;
			for (int i = 0; i <= steps; i++) {
				int px = (int) x - half;
				int py = (int) y - half;
				ctx.fill(px, py, px + thickness, py + thickness, argb);
				x += dx;
				y += dy;
			}
		}
		m.popMatrix();
	}

	/** Projects a world-space polyline and draws the visible segments. Returns the last projected point or null. */
	public static float[] polyline(DrawContext ctx, List<Vec3d> points, int argb, int thickness) {
		MinecraftClient mc = MinecraftClient.getInstance();
		int sw = mc.getWindow().getScaledWidth();
		int sh = mc.getWindow().getScaledHeight();
		float[] prev = null;
		for (Vec3d p : points) {
			float[] s = ProjectionUtil.projectUnclipped(p, sw, sh);
			if (s != null && prev != null) {
				line(ctx, prev[0], prev[1], s[0], s[1], argb, thickness);
			}
			prev = s;
		}
		return prev;
	}

	/** Small square marker centred on a world position (e.g. a landing spot). */
	public static void marker(DrawContext ctx, Vec3d pos, int argb) {
		MinecraftClient mc = MinecraftClient.getInstance();
		float[] s = ProjectionUtil.project(pos, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
		if (s != null) {
			int x = (int) s[0];
			int y = (int) s[1];
			ctx.fill(x - 3, y - 3, x + 3, y + 3, 0x80000000);
			ctx.fill(x - 2, y - 2, x + 2, y + 2, argb);
		}
	}
}
