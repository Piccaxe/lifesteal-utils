package com.piccaxe.lsutils.hud;

import com.piccaxe.lsutils.PiccaxeLsUtils;
import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import com.piccaxe.lsutils.feature.HealthBars;
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
		HEART, TOTEM, COORDS, DEATH, DIRECTION, MAXHEARTS, POTIONS, INVENTORY, PLAYERHP
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
			renderPlaced(context, mc, Hud.HEART, cfg.heartHudX, cfg.heartHudY, false, cfg);
		}
		if (cfg.totemHud) {
			renderPlaced(context, mc, Hud.TOTEM, cfg.totemHudX, cfg.totemHudY, false, cfg);
		}
		if (cfg.coordsHud) {
			renderPlaced(context, mc, Hud.COORDS, cfg.coordsHudX, cfg.coordsHudY, false, cfg);
		}
		if (cfg.deathWaypoint && cfg.hasDeath) {
			renderPlaced(context, mc, Hud.DEATH, cfg.deathHudX, cfg.deathHudY, false, cfg);
		}
		if (cfg.directionHud) {
			renderPlaced(context, mc, Hud.DIRECTION, cfg.directionHudX, cfg.directionHudY, false, cfg);
		}
		if (cfg.heartTracker && cfg.heartTrackerHud) {
			renderPlaced(context, mc, Hud.MAXHEARTS, cfg.heartTrackerHudX, cfg.heartTrackerHudY, false, cfg);
		}
		if (cfg.potionHud) {
			renderPlaced(context, mc, Hud.POTIONS, cfg.potionHudX, cfg.potionHudY, false, cfg);
		}
		if (cfg.inventoryHud) {
			renderPlaced(context, mc, Hud.INVENTORY, cfg.inventoryHudX, cfg.inventoryHudY, false, cfg);
		}
		if (cfg.healthBars && cfg.healthBarList) {
			renderPlaced(context, mc, Hud.PLAYERHP, cfg.healthBarListX, cfg.healthBarListY, false, cfg);
		}
		if (cfg.healthBars && cfg.healthBarOverhead) {
			HealthBars.renderOverhead(context, mc);
		}

		ProximityAlert.renderBanner(context, mc, mc.textRenderer);
		PlayerNotifier.renderBanner(context, mc, mc.textRenderer);
	}

	private static final java.util.Map<Hud, int[]> LAST_SIZE = new java.util.EnumMap<>(Hud.class);

	/**
	 * Draws an element at its anchor (x,y), applying its effective scale (per-element × master) and
	 * horizontal alignment (the anchor is the left edge / center / right edge per the align setting).
	 * Returns the on-screen {width, height}. Uses the previous frame's size for alignment (1-frame lag).
	 */
	public static int[] renderPlaced(DrawContext ctx, MinecraftClient mc, Hud hud, int x, int y, boolean sample, Config cfg) {
		float scale = getEffectiveScale(cfg, hud);
		int[] last = LAST_SIZE.getOrDefault(hud, new int[]{0, 0});
		int scaledW = Math.round(last[0] * scale);
		int originX = originLeft(cfg, hud, x, scaledW);

		var matrices = ctx.getMatrices();
		matrices.pushMatrix();
		matrices.translate((float) originX, (float) y);
		if (Math.abs(scale - 1.0F) > 0.001F) {
			matrices.scale(scale);
		}
		int[] size = renderElement(ctx, mc, hud, 0, 0, sample);
		matrices.popMatrix();

		LAST_SIZE.put(hud, size);
		return new int[]{Math.round(size[0] * scale), Math.round(size[1] * scale)};
	}

	public static float getScale(Config c, Hud hud) {
		if (c.hudScales == null) {
			return 1.0F;
		}
		return c.hudScales.getOrDefault(hud.name(), 1.0F);
	}

	public static void setScale(Config c, Hud hud, float scale) {
		if (c.hudScales == null) {
			c.hudScales = new java.util.HashMap<>();
		}
		c.hudScales.put(hud.name(), Math.max(0.25F, Math.min(4.0F, scale)));
	}

	public static float getEffectiveScale(Config c, Hud hud) {
		float master = c.hudMasterScale <= 0.0F ? 1.0F : c.hudMasterScale;
		return getScale(c, hud) * master;
	}

	public static String getAlign(Config c, Hud hud) {
		if (c.hudAlign == null) {
			return "LEFT";
		}
		return c.hudAlign.getOrDefault(hud.name(), "LEFT");
	}

	public static String cycleAlign(Config c, Hud hud) {
		String next = switch (getAlign(c, hud)) {
			case "LEFT" -> "CENTER";
			case "CENTER" -> "RIGHT";
			default -> "LEFT";
		};
		if (c.hudAlign == null) {
			c.hudAlign = new java.util.HashMap<>();
		}
		c.hudAlign.put(hud.name(), next);
		return next;
	}

	public static void setAlign(Config c, Hud hud, String align) {
		if (c.hudAlign == null) {
			c.hudAlign = new java.util.HashMap<>();
		}
		c.hudAlign.put(hud.name(), align);
	}

	/** Left edge of where the element actually draws, given its anchor x and on-screen width. */
	public static int originLeft(Config c, Hud hud, int x, int scaledW) {
		return switch (getAlign(c, hud)) {
			case "CENTER" -> x - scaledW / 2;
			case "RIGHT" -> x - scaledW;
			default -> x;
		};
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
			case POTIONS -> {
				return renderPotions(ctx, tr, mc, x, y, live);
			}
			case INVENTORY -> {
				return renderInventory(ctx, tr, mc, x, y, live);
			}
			case PLAYERHP -> {
				return HealthBars.renderList(ctx, mc, x, y, !live);
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

	private static int[] renderPotions(DrawContext ctx, TextRenderer tr, MinecraftClient mc, int x, int y, boolean live) {
		int lineH = 10;
		if (!live) {
			Text a = Text.literal("Speed II  1:23");
			Text b = Text.literal("Poison  0:08");
			ctx.drawTextWithShadow(tr, a, x, y, GREEN);
			ctx.drawTextWithShadow(tr, b, x, y + lineH, RED);
			return new int[]{Math.max(tr.getWidth(a), tr.getWidth(b)), 2 * lineH};
		}
		int w = 0;
		int row = 0;
		for (var effect : mc.player.getStatusEffects()) {
			var se = effect.getEffectType().value();
			String name = se.getName().getString();
			int amp = effect.getAmplifier();
			if (amp > 0) {
				name += " " + roman(amp + 1);
			}
			String time = effect.isInfinite() ? "∞" : formatTicks(effect.getDuration());
			Text t = Text.literal(name + "  " + time);
			ctx.drawTextWithShadow(tr, t, x, y + row * lineH, se.isBeneficial() ? GREEN : RED);
			w = Math.max(w, tr.getWidth(t));
			row++;
		}
		return new int[]{w, row * lineH};
	}

	private static int[] renderInventory(DrawContext ctx, TextRenderer tr, MinecraftClient mc, int x, int y, boolean live) {
		int cols = 9;
		int rows = 3;
		int cell = 18;
		int w = cols * cell;
		int h = rows * cell;
		ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x90000000);
		for (int i = 0; i < cols * rows; i++) {
			int sx = x + (i % cols) * cell + 1;
			int sy = y + (i / cols) * cell + 1;
			ItemStack stack = live ? mc.player.getInventory().getStack(9 + i) : sampleItem(i);
			if (stack != null && !stack.isEmpty()) {
				ctx.drawItem(stack, sx, sy);
				ctx.drawStackOverlay(tr, stack, sx, sy);
			}
		}
		return new int[]{w, h};
	}

	private static ItemStack sampleItem(int i) {
		return switch (i) {
			case 0 -> new ItemStack(Items.DIAMOND, 12);
			case 1 -> new ItemStack(Items.TOTEM_OF_UNDYING, 2);
			case 4 -> new ItemStack(Items.GOLDEN_APPLE, 8);
			case 9 -> new ItemStack(Items.ENDER_PEARL, 16);
			default -> ItemStack.EMPTY;
		};
	}

	private static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			case 6 -> "VI";
			case 7 -> "VII";
			case 8 -> "VIII";
			case 9 -> "IX";
			case 10 -> "X";
			default -> String.valueOf(n);
		};
	}

	private static String formatTicks(int ticks) {
		int secs = ticks / 20;
		return (secs / 60) + ":" + String.format("%02d", secs % 60);
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
			case POTIONS -> c.potionHud;
			case INVENTORY -> c.inventoryHud;
			case PLAYERHP -> c.healthBars && c.healthBarList;
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
			case POTIONS -> c.potionHudX;
			case INVENTORY -> c.inventoryHudX;
			case PLAYERHP -> c.healthBarListX;
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
			case POTIONS -> c.potionHudY;
			case INVENTORY -> c.inventoryHudY;
			case PLAYERHP -> c.healthBarListY;
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
			case POTIONS -> {
				c.potionHudX = x;
				c.potionHudY = y;
			}
			case INVENTORY -> {
				c.inventoryHudX = x;
				c.inventoryHudY = y;
			}
			case PLAYERHP -> {
				c.healthBarListX = x;
				c.healthBarListY = y;
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
			case POTIONS -> "Potions";
			case INVENTORY -> "Inventory";
			case PLAYERHP -> "Player HP";
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
