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
 * Entity health is synced via the {@code LivingEntity.HEALTH} tracked data, so this works for mobs
 * and (on vanilla-style servers) other players. Drawn through walls via the see-through text layer.
 */
public final class HealthBars {
	private static final int SEGMENTS = 10;

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
		if (matrices == null || consumers == null) {
			return;
		}

		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		double rangeSq = cfg.healthBarRange * cfg.healthBarRange;
		TextRenderer tr = mc.textRenderer;

		for (Entity entity : mc.world.getEntities()) {
			if (!(entity instanceof LivingEntity le) || entity == mc.player) {
				continue;
			}
			if (!le.isAlive() || le.getHealth() <= 0.0F) {
				continue;
			}
			if (cfg.healthBarPlayersOnly && !(entity instanceof PlayerEntity)) {
				continue;
			}
			if (le.squaredDistanceTo(mc.player) > rangeSq) {
				continue;
			}
			drawBar(mc, matrices, consumers, tr, le, cam);
		}
	}

	private static void drawBar(MinecraftClient mc, MatrixStack matrices, VertexConsumerProvider consumers,
			TextRenderer tr, LivingEntity le, Vec3d cam) {
		float health = le.getHealth();
		float max = Math.max(1.0F, le.getMaxHealth());
		float fraction = MathHelper.clamp(health / max, 0.0F, 1.0F);
		int filled = Math.round(fraction * SEGMENTS);
		int empty = SEGMENTS - filled;

		Formatting tier = fraction > 0.5F ? Formatting.GREEN : (fraction > 0.25F ? Formatting.YELLOW : Formatting.RED);

		MutableText text = Text.literal((int) Math.ceil(health) + "/" + (int) Math.ceil(max) + " ").formatted(tier);
		if (filled > 0) {
			text.append(Text.literal("█".repeat(filled)).formatted(tier));
		}
		if (empty > 0) {
			text.append(Text.literal("█".repeat(empty)).formatted(Formatting.DARK_GRAY));
		}

		matrices.push();
		matrices.translate(le.getX() - cam.x, le.getY() + le.getHeight() + 0.5 - cam.y, le.getZ() - cam.z);
		matrices.multiply(mc.gameRenderer.getCamera().getRotation());
		matrices.scale(-0.025F, -0.025F, 0.025F);
		var matrix = matrices.peek().getPositionMatrix();
		float width = tr.getWidth(text);
		tr.draw(text, -width / 2.0F, 0.0F, 0xFFFFFFFF, false, matrix, consumers,
			TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
		matrices.pop();
	}
}
