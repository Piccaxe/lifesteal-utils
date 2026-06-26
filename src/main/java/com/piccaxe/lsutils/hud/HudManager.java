package com.piccaxe.lsutils.hud;

import com.piccaxe.lsutils.PiccaxeLsUtils;
import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import com.piccaxe.lsutils.feature.HeartTracker;
import com.piccaxe.lsutils.feature.PlayerNotifier;
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
 * Renders the movable info HUD elements (hearts, totems, coords, death waypoint), each at its own
 * configurable position, plus the centered proximity / notifier banners.
 *
 * <p>{@link #renderElement} both draws an element and returns its {width,height}, so the
 * {@code HudEditScreen} can reuse it to draw drag previews and hit-test.
 */
public final class HudManager {
	public enum Hud {
		HEART, TOTEM, COORDS, DEATH, DIRECTION, MAXHEARTS
	}

	private static final int WHITE = 0xFFFFFFFF;
	private static final int RED = 0xFFFF5555;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int GREEN = 0xFF55FF55;
	private static final int GOLD = 0xFFFFAA00;
	private static final int CYAN = 0xFF55FFFF;

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

		if (cfg.heartHud) {
			renderElement(context, mc, Hud.HEART, cfg.heartHudX, cfg.heartHudY, false);
		}
		if (cfg.totemHud) {
			renderElement(context, mc, Hud.TOTEM, cfg.totemHudX, cfg.totemHudY, false);
		}
		if (cfg.coordsHud) {
			renderElement(context, mc, Hud.COORDS, cfg.coordsHudX, cfg.coordsHudY, false);
		}
		if (cfg.deathWaypoint && cfg.hasDeath) {
			renderElement(context, mc, Hud.DEATH, cfg.deathHudX, cfg.deathHudY, false);
		}
		if (cfg.directionHud) {
			renderElement(context, mc, Hud.DIRECTION, cfg.directionHudX, cfg.directionHudY, false);
		}
		if (cfg.heartTracker && cfg.heartTrackerHud) {
			renderElement(context, mc, Hud.MAXHEARTS, cfg.heartTrackerHudX, cfg.heartTrackerHudY, false);
		}

		ProximityAlert.renderBanner(context, mc, mc.textRenderer);
		PlayerNotifier.renderBanner(context, mc, mc.textRenderer);
	}

	/** Draws a single HUD element at (x,y) and returns its {width, height}. {@code sample} uses placeholder data. */
	public static int[] renderElement(DrawContext ctx, MinecraftClient mc, Hud hud, int x, int y, boolean sample) {
		TextRenderer tr = mc.textRenderer;
		Config cfg = ConfigManager.get();
		boolean live = !sample && mc.player != null && mc.world != null;

		switch (hud) {
			case HEART -> {
				int hearts = live ? MathHelper.ceil(mc.player.getHealth() / 2.0F) : 15;
				int max = live ? MathHelper.ceil(mc.player.getMaxHealth() / 2.0F) : 20;
				int color = hearts <= 5 ? RED : (hearts < max ? YELLOW : GREEN);
				Text t = Text.literal("❤ " + hearts + " / " + max);
				ctx.drawTextWithShadow(tr, t, x, y, color);
				return new int[]{tr.getWidth(t), 9};
			}
			case TOTEM -> {
				int totems = live ? countTotems(mc) : 3;
				boolean low = totems <= cfg.totemWarnThreshold;
				boolean flash = (System.currentTimeMillis() % 1000) < 500;
				int color = low ? (flash ? RED : 0xFFAA0000) : WHITE;
				ctx.drawItem(new ItemStack(Items.TOTEM_OF_UNDYING), x, y);
				Text t = Text.literal("x " + totems + (low ? "  !" : ""));
				ctx.drawTextWithShadow(tr, t, x + 18, y + 4, color);
				return new int[]{18 + tr.getWidth(t), 16};
			}
			case COORDS -> {
				String s = live
					? "XYZ " + MathHelper.floor(mc.player.getX()) + " " + MathHelper.floor(mc.player.getY())
						+ " " + MathHelper.floor(mc.player.getZ()) + "  " + facingOf(mc)
					: "XYZ 100 64 -20  N";
				Text t = Text.literal(s);
				ctx.drawTextWithShadow(tr, t, x, y, CYAN);
				return new int[]{tr.getWidth(t), 9};
			}
			case DEATH -> {
				String s;
				if (live && cfg.hasDeath) {
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
					s = "☠ Death " + dx + " " + dy + " " + dz + "  " + suffix;
				} else {
					s = "☠ Death 120 70 30  (45m)";
				}
				Text t = Text.literal(s);
				ctx.drawTextWithShadow(tr, t, x, y, GOLD);
				return new int[]{tr.getWidth(t), 9};
			}
			case DIRECTION -> {
				return renderCompass(ctx, tr, cfg, x, y, live ? mc.player.getYaw() : 0.0F);
			}
			case MAXHEARTS -> {
				int hearts = live ? (int) Math.ceil(mc.player.getMaxHealth() / 2.0) : 10;
				int net = HeartTracker.sessionNet();
				String netStr = net == 0 ? "" : (net > 0 ? "  (+" + net + ")" : "  (" + net + ")");
				Text t = Text.literal("♥ " + hearts + " hearts" + netStr);
				int color = net < 0 ? RED : (net > 0 ? GREEN : 0xFFFF77AA);
				ctx.drawTextWithShadow(tr, t, x, y, color);
				return new int[]{tr.getWidth(t), 9};
			}
		}
		return new int[]{0, 0};
	}

	/** Draws the compass: heading text (+ optional scrolling strip, ticks, background) with custom colors. */
	private static int[] renderCompass(DrawContext ctx, TextRenderer tr, Config cfg, int x, int y, float yaw) {
		float bearing = ((yaw + 180.0F) % 360.0F + 360.0F) % 360.0F; // 0 = North
		String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
		int color = 0xFF000000 | (cfg.directionColor & 0xFFFFFF);
		int northColor = 0xFF000000 | (cfg.directionNorthColor & 0xFFFFFF);
		int markerColor = 0xFF000000 | (cfg.directionMarkerColor & 0xFFFFFF);
		String head = dirs[Math.round(bearing / 45.0F) % 8] + " " + Math.round(bearing) + "°";

		// Minimal mode: just the heading text, left-aligned like the other text HUDs.
		if (cfg.directionMinimal) {
			if (cfg.directionBackground) {
				ctx.fill(x - 2, y - 2, x + tr.getWidth(head) + 2, y + 10, 0x80000000);
			}
			ctx.drawTextWithShadow(tr, Text.literal(head), x, y, color);
			return new int[]{tr.getWidth(head), 9};
		}

		int w = Math.max(40, cfg.directionHudWidth);
		int centerX = x + w / 2;
		int halfFov = 60;
		int barY = y + 11;

		if (cfg.directionBackground) {
			ctx.fill(x - 2, y - 2, x + w + 2, barY + 12, 0x80000000);
		}
		ctx.drawCenteredTextWithShadow(tr, Text.literal(head), centerX, y, color);
		ctx.fill(x, barY + 9, x + w, barY + 10, 0x66FFFFFF);

		if (cfg.directionTicks) {
			int dim = 0x80000000 | (cfg.directionColor & 0xFFFFFF);
			for (int a = 0; a < 360; a += 15) {
				if (a % 45 == 0) {
					continue; // skip where a letter goes
				}
				float delta = (float) MathHelper.wrapDegrees((double) (a - bearing));
				if (Math.abs(delta) > halfFov) {
					continue;
				}
				int px = centerX + Math.round(delta / halfFov * (w / 2.0F));
				ctx.fill(px, barY + 5, px + 1, barY + 9, dim);
			}
		}

		for (int i = 0; i < 8; i++) {
			float delta = (float) MathHelper.wrapDegrees((double) (i * 45.0F - bearing));
			if (Math.abs(delta) > halfFov) {
				continue;
			}
			int px = centerX + Math.round(delta / halfFov * (w / 2.0F));
			String letter = dirs[i];
			ctx.drawTextWithShadow(tr, Text.literal(letter), px - tr.getWidth(letter) / 2, barY, i == 0 ? northColor : color);
		}
		ctx.fill(centerX, barY - 2, centerX + 1, barY + 11, markerColor);
		return new int[]{w, 20};
	}

	// --- accessors used by the HUD editor ---

	public static boolean toggle(Config c, Hud hud) {
		return switch (hud) {
			case HEART -> c.heartHud;
			case TOTEM -> c.totemHud;
			case COORDS -> c.coordsHud;
			case DEATH -> c.deathWaypoint;
			case DIRECTION -> c.directionHud;
			case MAXHEARTS -> c.heartTracker && c.heartTrackerHud;
		};
	}

	public static int getX(Config c, Hud hud) {
		return switch (hud) {
			case HEART -> c.heartHudX;
			case TOTEM -> c.totemHudX;
			case COORDS -> c.coordsHudX;
			case DEATH -> c.deathHudX;
			case DIRECTION -> c.directionHudX;
			case MAXHEARTS -> c.heartTrackerHudX;
		};
	}

	public static int getY(Config c, Hud hud) {
		return switch (hud) {
			case HEART -> c.heartHudY;
			case TOTEM -> c.totemHudY;
			case COORDS -> c.coordsHudY;
			case DEATH -> c.deathHudY;
			case DIRECTION -> c.directionHudY;
			case MAXHEARTS -> c.heartTrackerHudY;
		};
	}

	public static void setPos(Config c, Hud hud, int x, int y) {
		switch (hud) {
			case HEART -> {
				c.heartHudX = x;
				c.heartHudY = y;
			}
			case TOTEM -> {
				c.totemHudX = x;
				c.totemHudY = y;
			}
			case COORDS -> {
				c.coordsHudX = x;
				c.coordsHudY = y;
			}
			case DEATH -> {
				c.deathHudX = x;
				c.deathHudY = y;
			}
			case DIRECTION -> {
				c.directionHudX = x;
				c.directionHudY = y;
			}
			case MAXHEARTS -> {
				c.heartTrackerHudX = x;
				c.heartTrackerHudY = y;
			}
		}
	}

	public static String label(Hud hud) {
		return switch (hud) {
			case HEART -> "Hearts";
			case TOTEM -> "Totems";
			case COORDS -> "Coords";
			case DEATH -> "Death";
			case DIRECTION -> "Compass";
			case MAXHEARTS -> "Hearts";
		};
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
