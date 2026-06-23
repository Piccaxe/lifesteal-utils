package com.piccaxe.lsutils.hud;

import com.piccaxe.lsutils.PiccaxeLsUtils;
import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import com.piccaxe.lsutils.feature.ProximityAlert;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

/**
 * Draws the stacked top-left info HUD (hearts, totems, coordinates, death waypoint)
 * plus the centered proximity-alert banner.
 *
 * <p>Registered after {@link VanillaHudElements#BOSS_BAR} so it inherits vanilla's
 * "hide HUD" (F1) render condition for free.
 */
public final class HudManager {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int RED = 0xFFFF5555;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int GREEN = 0xFF55FF55;
	private static final int GOLD = 0xFFFFAA00;
	private static final int CYAN = 0xFF55FFFF;

	private static final int LINE_HEIGHT = 11;

	private HudManager() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(
			VanillaHudElements.BOSS_BAR,
			Identifier.of(PiccaxeLsUtils.MOD_ID, "overlay"),
			(context, tickCounter) -> render(context));
	}

	private static void render(DrawContext context) {
		MinecraftClient mc = MinecraftClient.getInstance();
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || mc.player == null || mc.world == null) {
			return;
		}

		TextRenderer tr = mc.textRenderer;
		int x = cfg.hudX;
		int y = cfg.hudY;

		if (cfg.heartHud) {
			int hearts = MathHelper.ceil(mc.player.getHealth() / 2.0F);
			int maxHearts = MathHelper.ceil(mc.player.getMaxHealth() / 2.0F);
			int color = hearts <= 5 ? RED : (hearts < maxHearts ? YELLOW : GREEN);
			context.drawTextWithShadow(tr, Text.literal("❤ " + hearts + " / " + maxHearts), x, y, color);
			y += LINE_HEIGHT;
		}

		if (cfg.totemHud) {
			int totems = countTotems(mc);
			boolean low = totems <= cfg.totemWarnThreshold;
			boolean flashOn = (System.currentTimeMillis() % 1000) < 500;
			int color = low ? (flashOn ? RED : 0xFFAA0000) : WHITE;
			context.drawItem(new ItemStack(Items.TOTEM_OF_UNDYING), x, y - 3);
			String label = "x " + totems + (low ? "  !" : "");
			context.drawTextWithShadow(tr, Text.literal(label), x + 18, y + 1, color);
			y += 18;
		}

		if (cfg.coordsHud) {
			int px = MathHelper.floor(mc.player.getX());
			int py = MathHelper.floor(mc.player.getY());
			int pz = MathHelper.floor(mc.player.getZ());
			context.drawTextWithShadow(tr,
				Text.literal("XYZ " + px + " " + py + " " + pz + "  " + facingOf(mc)), x, y, CYAN);
			y += LINE_HEIGHT;
		}

		if (cfg.deathWaypoint && cfg.hasDeath) {
			int dx = MathHelper.floor(cfg.deathX);
			int dy = MathHelper.floor(cfg.deathY);
			int dz = MathHelper.floor(cfg.deathZ);
			String curDim = mc.world.getRegistryKey().getValue().toString();
			String suffix;
			if (curDim.equals(cfg.deathDim)) {
				int dist = (int) Math.sqrt(mc.player.squaredDistanceTo(cfg.deathX, cfg.deathY, cfg.deathZ));
				suffix = "(" + dist + "m)";
			} else {
				suffix = "(" + shortDim(cfg.deathDim) + ")";
			}
			context.drawTextWithShadow(tr,
				Text.literal("☠ Death " + dx + " " + dy + " " + dz + "  " + suffix), x, y, GOLD);
			y += LINE_HEIGHT;
		}

		ProximityAlert.renderBanner(context, mc, tr);
		com.piccaxe.lsutils.feature.PlayerNotifier.renderBanner(context, mc, tr);
	}

	private static int countTotems(MinecraftClient mc) {
		int count = 0;
		var inv = mc.player.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static String facingOf(MinecraftClient mc) {
		Direction dir = mc.player.getHorizontalFacing();
		return switch (dir) {
			case NORTH -> "N";
			case SOUTH -> "S";
			case EAST -> "E";
			case WEST -> "W";
			default -> "?";
		};
	}

	private static String shortDim(String dim) {
		if (dim.contains("nether")) {
			return "Nether";
		}
		if (dim.contains("the_end") || dim.endsWith("end")) {
			return "End";
		}
		if (dim.contains("overworld")) {
			return "Overworld";
		}
		return dim;
	}
}
