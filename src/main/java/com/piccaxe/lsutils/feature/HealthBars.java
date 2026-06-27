package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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

	/** {health, max, estimatedFlag} — damage estimate first, server-reported health as the fallback. */
	private static float[] hp(LivingEntity le, Config cfg) {
		float max = Math.max(1.0F, le.getMaxHealth());
		float absorption = Math.max(0.0F, le.getAbsorptionAmount());
		float health;
		boolean est = false;
		if (le instanceof PlayerEntity p && cfg.healthBarDamageEstimate) {
			Float e = DamageTracker.estimate(p.getUuid());
			if (e != null) {
				health = e;          // damage estimate takes priority
				est = true;
			} else {
				health = le.getHealth(); // fall back to the server's count
			}
		} else {
			health = le.getHealth();
		}
		return new float[]{MathHelper.clamp(health, 0.0F, max), max, est ? 1.0F : 0.0F, absorption};
	}

	private static int colorFor(float fraction) {
		return fraction > 0.5F ? 0xFF55FF55 : (fraction > 0.25F ? 0xFFFFFF55 : 0xFFFF5555);
	}

	private static Formatting tierFor(float fraction) {
		return fraction > 0.5F ? Formatting.GREEN : (fraction > 0.25F ? Formatting.YELLOW : Formatting.RED);
	}

	private static final int MAX_HEART_ICONS = 20;

	/** Builds the value portion: heart icons or the "cur/max" number (+ gold absorption), styled. */
	private static MutableText valueText(float health, float max, boolean estimated, float absorption, Config cfg) {
		float frac = max <= 0 ? 0 : health / max;
		int absHearts = (int) Math.ceil(absorption / 2.0F);
		if (cfg.healthBarHearts) {
			int total = MathHelper.clamp((int) Math.ceil(max / 2.0F), 1, MAX_HEART_ICONS);
			int cur = MathHelper.clamp(Math.round(health / 2.0F), 0, total);
			MutableText t = Text.literal("❤".repeat(cur)).formatted(Formatting.RED);
			if (total - cur > 0) {
				t.append(Text.literal("❤".repeat(total - cur)).formatted(Formatting.DARK_GRAY));
			}
			if (absHearts > 0) {
				t.append(Text.literal("❤".repeat(Math.min(absHearts, MAX_HEART_ICONS))).formatted(Formatting.GOLD));
			}
			if (estimated) {
				t.append(Text.literal(" ~").formatted(Formatting.GRAY));
			}
			return t;
		}
		MutableText t = Text.literal((estimated ? "~" : "") + (int) Math.ceil(health) + "/" + (int) Math.ceil(max))
			.formatted(tierFor(frac));
		if (absorption > 0) {
			t.append(Text.literal(" +" + (int) Math.ceil(absorption)).formatted(Formatting.GOLD));
		}
		return t;
	}

	/** Screen list of nearby players' HP (the PLAYERHP HUD element). Returns {width, height}. */
	public static int[] renderList(DrawContext ctx, MinecraftClient mc, int x, int y, boolean sample) {
		TextRenderer tr = mc.textRenderer;
		Config cfg = ConfigManager.get();
		List<MutableText> lines = new ArrayList<>();

		if (sample) {
			lines.add(Text.literal("Steve  ").append(valueText(14, 20, false, 0, cfg)));
			lines.add(Text.literal("Alex  ").append(valueText(6, 20, false, 0, cfg)));
		} else {
			for (LivingEntity le : targets(mc, cfg)) {
				float[] h = hp(le, cfg);
				lines.add(Text.literal(le.getName().getString() + "  ").append(valueText(h[0], h[1], h[2] > 0, h[3], cfg)));
			}
		}

		int w = 0;
		int lineH = 10;
		for (int i = 0; i < lines.size(); i++) {
			ctx.drawTextWithShadow(tr, lines.get(i), x, y + i * lineH, 0xFFFFFFFF);
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
			MutableText txt = valueText(h[0], h[1], h[2] > 0, h[3], cfg);
			int tw = tr.getWidth(txt);
			ctx.drawTextWithShadow(tr, txt, (int) (screen[0] - tw / 2.0F), (int) (screen[1] - 10), 0xFFFFFFFF);
		}
	}
}
