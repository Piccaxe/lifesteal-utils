package com.piccaxe.lsutils.gui;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import com.piccaxe.lsutils.hud.HudManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Move/resize/align editor for the HUD elements. Drag to move (snaps to screen edges & centers),
 * scroll to resize, right-click to cycle alignment (left/center/right). Saves on close.
 */
public class HudEditScreen extends Screen {
	private static final int SNAP = 6;

	private final Screen parent;
	private final Map<HudManager.Hud, int[]> sizes = new EnumMap<>(HudManager.Hud.class);

	private HudManager.Hud dragging = null;
	private double dragOffsetX;
	private double dragOffsetY;
	private Integer guideX = null;
	private Integer guideY = null;

	public HudEditScreen(Screen parent) {
		super(Text.literal("HUD Editor"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		addDrawableChild(ButtonWidget.builder(Text.literal("Reset Positions"), b -> resetPositions())
			.dimensions(cx - 154, this.height - 28, 150, 20).build());
		addDrawableChild(ButtonWidget.builder(snapLabel(), b -> {
			Config c = ConfigManager.get();
			c.hudSnap = !c.hudSnap;
			ConfigManager.save();
			b.setMessage(snapLabel());
		}).dimensions(cx + 4, this.height - 28, 150, 20).build());
	}

	private static Text snapLabel() {
		boolean on = ConfigManager.get().hudSnap;
		return Text.literal("Snap: ").append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED));
	}

	private void resetPositions() {
		Config c = ConfigManager.get();
		c.heartHudX = 5;
		c.heartHudY = 5;
		c.totemHudX = 5;
		c.totemHudY = 16;
		c.coordsHudX = 5;
		c.coordsHudY = 34;
		c.deathHudX = 5;
		c.deathHudY = 45;
		c.directionHudX = 100;
		c.directionHudY = 2;
		c.heartTrackerHudX = 5;
		c.heartTrackerHudY = 56;
		c.potionHudX = 5;
		c.potionHudY = 70;
		c.inventoryHudX = 5;
		c.inventoryHudY = 92;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		Config cfg = ConfigManager.get();

		for (HudManager.Hud hud : HudManager.Hud.values()) {
			if (!HudManager.toggle(cfg, hud)) {
				continue;
			}
			int x = HudManager.getX(cfg, hud);
			int y = HudManager.getY(cfg, hud);
			int[] size = HudManager.renderPlaced(ctx, this.client, hud, x, y, true, cfg);
			int w = Math.max(size[0], 8);
			int h = Math.max(size[1], 8);
			sizes.put(hud, new int[]{w, h});
			int left = HudManager.originLeft(cfg, hud, x, w);

			boolean hovered = mouseX >= left - 2 && mouseX <= left + w + 2 && mouseY >= y - 2 && mouseY <= y + h + 2;
			int color = dragging == hud ? 0xFF55FF55 : (hovered ? 0xFFFFFF55 : 0x88FFFFFF);
			drawBox(ctx, left - 2, y - 2, left + w + 2, y + h + 2, color);
			float scale = HudManager.getScale(cfg, hud);
			String tag = " [" + HudManager.getAlign(cfg, hud).charAt(0) + "]"
				+ (Math.abs(scale - 1.0F) > 0.001F ? " ×" + String.format("%.2f", scale) : "");
			ctx.drawTextWithShadow(this.client.textRenderer, Text.literal(HudManager.label(hud) + tag), left - 2, y - 12, color);
		}

		if (dragging != null && guideX != null) {
			ctx.fill(guideX, 0, guideX + 1, this.height, 0xFF55FFFF);
		}
		if (dragging != null && guideY != null) {
			ctx.fill(0, guideY, this.width, guideY + 1, 0xFF55FFFF);
		}

		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.client.textRenderer,
			Text.literal("Drag to move (Shift = no snap) · scroll to resize · right-click to align · Esc to save"),
			this.width / 2, 8, 0xFFFFFFFF);
	}

	private static void drawBox(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
		ctx.fill(x1, y1, x2, y1 + 1, color);
		ctx.fill(x1, y2 - 1, x2, y2, color);
		ctx.fill(x1, y1, x1 + 1, y2, color);
		ctx.fill(x2 - 1, y1, x2, y2, color);
	}

	private boolean shiftHeld() {
		var window = this.client.getWindow();
		return InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT)
			|| InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
	}

	private void collectElementTargets(Config cfg, List<Integer> xTargets, List<Integer> yTargets) {
		for (HudManager.Hud other : HudManager.Hud.values()) {
			if (other == dragging || !HudManager.toggle(cfg, other)) {
				continue;
			}
			int[] os = sizes.get(other);
			if (os == null) {
				continue;
			}
			int oleft = HudManager.originLeft(cfg, other, HudManager.getX(cfg, other), os[0]);
			int oy = HudManager.getY(cfg, other);
			xTargets.add(oleft);
			xTargets.add(oleft + os[0] / 2);
			xTargets.add(oleft + os[0]);
			yTargets.add(oy);
			yTargets.add(oy + os[1] / 2);
			yTargets.add(oy + os[1]);
		}
	}

	/** Returns {newStart, guideCoord} if the box (start..start+len) has an edge/center within SNAP of a target. */
	private Integer[] snapAxis(int boxStart, int len, List<Integer> targets) {
		int[] edges = {boxStart, boxStart + len / 2, boxStart + len};
		int bestShift = 0;
		int bestGuide = 0;
		int bestDist = SNAP + 1;
		for (int edge : edges) {
			for (int t : targets) {
				int d = Math.abs(edge - t);
				if (d < bestDist) {
					bestDist = d;
					bestShift = t - edge;
					bestGuide = t;
				}
			}
		}
		return bestDist <= SNAP ? new Integer[]{boxStart + bestShift, bestGuide} : null;
	}

	private HudManager.Hud elementAt(Config cfg, double mx, double my) {
		HudManager.Hud[] values = HudManager.Hud.values();
		for (int i = values.length - 1; i >= 0; i--) {
			HudManager.Hud hud = values[i];
			if (!HudManager.toggle(cfg, hud)) {
				continue;
			}
			int x = HudManager.getX(cfg, hud);
			int y = HudManager.getY(cfg, hud);
			int[] size = sizes.getOrDefault(hud, new int[]{40, 12});
			int left = HudManager.originLeft(cfg, hud, x, size[0]);
			if (mx >= left - 2 && mx <= left + size[0] + 2 && my >= y - 2 && my <= y + size[1] + 2) {
				return hud;
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) {
			return true;
		}
		Config cfg = ConfigManager.get();
		HudManager.Hud hud = elementAt(cfg, click.x(), click.y());
		if (hud == null) {
			return false;
		}
		if (click.button() == 1) {
			HudManager.cycleAlign(cfg, hud);
			ConfigManager.save();
			return true;
		}
		if (click.button() == 0) {
			dragging = hud;
			dragOffsetX = click.x() - HudManager.getX(cfg, hud);
			dragOffsetY = click.y() - HudManager.getY(cfg, hud);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (dragging == null) {
			return super.mouseDragged(click, offsetX, offsetY);
		}
		Config cfg = ConfigManager.get();
		int[] size = sizes.getOrDefault(dragging, new int[]{40, 12});
		int w = size[0];
		int h = size[1];
		int nx = (int) Math.round(click.x() - dragOffsetX);
		int ny = (int) Math.round(click.y() - dragOffsetY);
		nx = Math.max(0, Math.min(nx, this.width));
		ny = Math.max(0, Math.min(ny, this.height - 4));

		int alignOff = nx - HudManager.originLeft(cfg, dragging, nx, w);
		int boxLeft = nx - alignOff;
		guideX = null;
		guideY = null;

		// Snapping (skipped when disabled or while holding Shift for fine placement).
		if (cfg.hudSnap && !shiftHeld()) {
			List<Integer> xTargets = new ArrayList<>(List.of(2, this.width / 2, this.width - 2));
			List<Integer> yTargets = new ArrayList<>(List.of(2, this.height / 2, this.height - 2));
			collectElementTargets(cfg, xTargets, yTargets);

			Integer[] hx = snapAxis(boxLeft, w, xTargets);
			if (hx != null) {
				boxLeft = hx[0];
				guideX = hx[1];
			}
			Integer[] vy = snapAxis(ny, h, yTargets);
			if (vy != null) {
				ny = vy[0];
				guideY = vy[1];
			}
		}
		nx = boxLeft + alignOff;

		HudManager.setPos(cfg, dragging, nx, ny);
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		Config cfg = ConfigManager.get();
		HudManager.Hud hud = elementAt(cfg, mouseX, mouseY);
		if (hud != null) {
			HudManager.setScale(cfg, hud, HudManager.getScale(cfg, hud) + (verticalAmount > 0 ? 0.1F : -0.1F));
			ConfigManager.save();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (dragging != null) {
			dragging = null;
			guideX = null;
			guideY = null;
			ConfigManager.save();
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public void close() {
		ConfigManager.save();
		if (this.client != null) {
			this.client.setScreen(parent);
		}
	}
}
