package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Renders a color-coded health bar + HP number, billboarded above each living entity within range.
 * Uses the entity's real synced health (and max-health, so lifesteal heart counts show correctly).
 * When {@code healthBarDamageEstimate} is on, a player's bar is additionally pulled down to
 * {@link DamageTracker}'s damage-dealt estimate if that's lower than the synced value (marked {@code ~}),
 * which covers servers that under-report a player's health. Drawn through walls via the see-through layer.
 */
public final class HealthBars {
	private static final int SEGMENTS = 10;
	private static long lastDebug = 0L;

	private HealthBars() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(HealthBars::render);
	}

	private static void render(WorldRenderContext ctx) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.healthBars) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) {
			return;
		}
		MatrixStack matrices = ctx.matrices();
		VertexConsumerProvider consumers = ctx.consumers();

		boolean dbg = cfg.healthBarDebug && (System.currentTimeMillis() - lastDebug > 2000);
		if (dbg) {
			lastDebug = System.currentTimeMillis();
			com.piccaxe.lsutils.PiccaxeLsUtils.LOGGER.info("[hb] fired matNull={} consNull={} imm={} range={} playersOnly={}",
				matrices == null, consumers == null,
				consumers instanceof VertexConsumerProvider.Immediate, cfg.healthBarRange, cfg.healthBarPlayersOnly);
		}
		if (matrices == null || consumers == null) {
			return;
		}

		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		double rangeSq = cfg.healthBarRange * cfg.healthBarRange;
		TextRenderer tr = mc.textRenderer;

		int living = 0;
		int players = 0;
		int drawn = 0;
		double nearest = -1;
		for (Entity entity : mc.world.getEntities()) {
			if (!(entity instanceof LivingEntity le) || entity == mc.player) {
				continue;
			}
			living++;
			boolean isPlayer = entity instanceof PlayerEntity;
			if (isPlayer) {
				players++;
				double d = Math.sqrt(le.squaredDistanceTo(mc.player));
				if (nearest < 0 || d < nearest) {
					nearest = d;
				}
			}
			if (cfg.healthBarPlayersOnly && !isPlayer) {
				continue;
			}
			if (le.isRemoved()) {
				continue;
			}
			// Hide dead mobs. Don't gate players on health — some servers hide/zero other players'
			// health, which would otherwise skip them entirely (then we'd never draw the estimate).
			if (!isPlayer && le.getHealth() <= 0.0F) {
				continue;
			}
			if (le.squaredDistanceTo(mc.player) > rangeSq) {
				continue;
			}
			drawBar(mc, matrices, consumers, tr, le, cam, cfg);
			drawn++;
		}
		if (dbg) {
			com.piccaxe.lsutils.PiccaxeLsUtils.LOGGER.info("[hb] living={} players={} nearestPlayer={} drawn={}",
				living, players, nearest < 0 ? "none" : String.format("%.1f", nearest), drawn);
		}

		// AFTER_ENTITIES fires after the engine already flushed the entity buffers, so our text would
		// never be drawn unless we flush the immediate ourselves.
		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw();
		}
	}

	private static void drawBar(MinecraftClient mc, MatrixStack matrices, VertexConsumerProvider consumers,
			TextRenderer tr, LivingEntity le, Vec3d cam, Config cfg) {
		float max = Math.max(1.0F, le.getMaxHealth());
		float health;
		boolean estimated = false;
		if (le instanceof PlayerEntity player) {
			if (DamageTracker.isLive(player.getUuid())) {
				// Server genuinely syncs this player's health -> follows damage AND healing.
				health = le.getHealth();
			} else if (cfg.healthBarDamageEstimate) {
				// Server hides/freezes it -> use the damage-dealt estimate (full until we've hit them).
				Float est = DamageTracker.estimate(player.getUuid());
				health = est != null ? est : max;
				estimated = true;
			} else {
				health = le.getHealth();
			}
		} else {
			health = le.getHealth();
		}
		float fraction = MathHelper.clamp(health / max, 0.0F, 1.0F);
		int filled = Math.round(fraction * SEGMENTS);
		int empty = SEGMENTS - filled;

		Formatting tier = fraction > 0.5F ? Formatting.GREEN : (fraction > 0.25F ? Formatting.YELLOW : Formatting.RED);

		MutableText text = Text.literal((estimated ? "~" : "") + (int) Math.ceil(health) + "/" + (int) Math.ceil(max) + " ").formatted(tier);
		if (filled > 0) {
			text.append(Text.literal("█".repeat(filled)).formatted(tier));
		}
		if (empty > 0) {
			text.append(Text.literal("█".repeat(empty)).formatted(Formatting.DARK_GRAY));
		}

		matrices.push();
		// Sit above the entity's name tag (which is ~height+0.5) so it doesn't overlap the username.
		matrices.translate(le.getX() - cam.x, le.getY() + le.getHeight() + 0.9 - cam.y, le.getZ() - cam.z);
		matrices.multiply(mc.gameRenderer.getCamera().getRotation());
		matrices.scale(-0.025F, -0.025F, 0.025F);
		var matrix = matrices.peek().getPositionMatrix();
		float width = tr.getWidth(text);
		tr.draw(text, -width / 2.0F, 0.0F, 0xFFFFFFFF, false, matrix, consumers,
			TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
		matrices.pop();
	}
}
