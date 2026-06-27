package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shows other entities' health two ways, both drawn in the HUD layer (which renders reliably here,
 * unlike the world pass): a screen-corner list of nearby players (the {@code PLAYERHP} HUD element),
 * and floating HP text above each head, positioned by projecting world coords to the screen.
 *
 * <p>Players use real synced health when the server reports it (live), otherwise the
 * {@link DamageTracker} estimate. Mobs use real health.
 */
public final class HealthBars {
	private HealthBars() {
	}

	public static void register() {
		// No event registration: HudManager drives renderList()/renderOverhead() from the HUD layer.
	}

	/** Living entities to show — range + players-only filtered, nearest first. */
	public static List<LivingEntity> targets(MinecraftClient mc, Config cfg) {
		List<LivingEntity> out = new ArrayList<>();
		if (mc.player == null || mc.world == null) {
			return out;
		}
		double rangeSq = cfg.healthBarRange * cfg.healthBarRange;
		for (Entity e : mc.world.getEntities()) {
			if (!(e instanceof LivingEntity le) || e == mc.player || le.isRemoved()) {
				continue;
			}
			boolean isPlayer = e instanceof PlayerEntity;
			if (cfg.healthBarPlayersOnly && !isPlayer) {
				continue;
			}
			if (!isPlayer && le.getHealth() <= 0.0F) {
				continue;
			}
			if (le.squaredDistanceTo(mc.player) > rangeSq) {
				continue;
			}
			out.add(le);
		}
		out.sort(Comparator.comparingDouble(le -> le.squaredDistanceTo(mc.player)));
		return out;
	}

	/** {health, max, estimatedFlag} chosen for display. */
	private static float[] hp(LivingEntity le, Config cfg) {
		float max = Math.max(1.0F, le.getMaxHealth());
		float health;
		boolean est = false;
		if (le instanceof PlayerEntity p) {
			if (DamageTracker.isLive(p.getUuid())) {
				health = le.getHealth();
			} else if (cfg.healthBarDamageEstimate) {
				Float e = DamageTracker.estimate(p.getUuid());
				health = e != null ? e : max;
				est = true;
			} else {
				health = le.getHealth();
			}
		} else {
			health = le.getHealth();
		}
		return new float[]{MathHelper.clamp(health, 0.0F, max), max, est ? 1.0F : 0.0F};
	}

	private static int colorFor(float fraction) {
		return fraction > 0.5F ? 0xFF55FF55 : (fraction > 0.25F ? 0xFFFFFF55 : 0xFFFF5555);
	}

	/** Screen list of nearby players' HP (the PLAYERHP HUD element). Returns {width, height}. */
	public static int[] renderList(DrawContext ctx, MinecraftClient mc, int x, int y, boolean sample) {
		TextRenderer tr = mc.textRenderer;
		Config cfg = ConfigManager.get();
		List<String> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();

		if (sample) {
			lines.add("Steve  14/20");
			colors.add(colorFor(0.7F));
			lines.add("Alex  6/20");
			colors.add(colorFor(0.3F));
		} else {
			for (LivingEntity le : targets(mc, cfg)) {
				float[] h = hp(le, cfg);
				float frac = h[1] <= 0 ? 0 : h[0] / h[1];
				String line = (h[2] > 0 ? "~" : "") + le.getName().getString()
					+ "  " + (int) Math.ceil(h[0]) + "/" + (int) Math.ceil(h[1]);
				lines.add(line);
				colors.add(colorFor(frac));
			}
		}

		int w = 0;
		int lineH = 10;
		for (int i = 0; i < lines.size(); i++) {
			ctx.drawTextWithShadow(tr, Text.literal(lines.get(i)), x, y + i * lineH, colors.get(i));
			w = Math.max(w, tr.getWidth(lines.get(i)));
		}
		return new int[]{Math.max(w, 8), Math.max(lines.size() * lineH, 8)};
	}

	/** Floating HP text above each target's head, projected from world space into the HUD layer. */
	public static void renderOverhead(DrawContext ctx, MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (mc.player == null) {
			return;
		}
		TextRenderer tr = mc.textRenderer;
		int sw = mc.getWindow().getScaledWidth();
		int sh = mc.getWindow().getScaledHeight();
		for (LivingEntity le : targets(mc, cfg)) {
			Vec3d head = new Vec3d(le.getX(), le.getY() + le.getHeight() + 0.5, le.getZ());
			float[] screen = ProjectionUtil.project(head, sw, sh);
			if (screen == null) {
				continue;
			}
			float[] h = hp(le, cfg);
			float frac = h[1] <= 0 ? 0 : h[0] / h[1];
			String txt = (h[2] > 0 ? "~" : "") + (int) Math.ceil(h[0]) + "/" + (int) Math.ceil(h[1]);
			int tw = tr.getWidth(txt);
			ctx.drawTextWithShadow(tr, Text.literal(txt), (int) (screen[0] - tw / 2.0F), (int) (screen[1] - 10), colorFor(frac));
		}
	}
}
