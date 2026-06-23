package com.piccaxe.lsutils.hud;

import com.piccaxe.lsutils.PiccaxeLsUtils;
import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
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
		HEART, TOTEM, COORDS, DEATH
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
		}
		return new int[]{0, 0};
	}

	// --- accessors used by the HUD editor ---

	public static boolean toggle(Config c, Hud hud) {
		return switch (hud) {
			case HEART -> c.heartHud;
			case TOTEM -> c.totemHud;
			case COORDS -> c.coordsHud;
			case DEATH -> c.deathWaypoint;
		};
	}

	public static int getX(Config c, Hud hud) {
		return switch (hud) {
			case HEART -> c.heartHudX;
			case TOTEM -> c.totemHudX;
			case COORDS -> c.coordsHudX;
			case DEATH -> c.deathHudX;
		};
	}

	public static int getY(Config c, Hud hud) {
		return switch (hud) {
			case HEART -> c.heartHudY;
			case TOTEM -> c.totemHudY;
			case COORDS -> c.coordsHudY;
			case DEATH -> c.deathHudY;
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
		}
	}

	public static String label(Hud hud) {
		return switch (hud) {
			case HEART -> "Hearts";
			case TOTEM -> "Totems";
			case COORDS -> "Coords";
			case DEATH -> "Death";
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
