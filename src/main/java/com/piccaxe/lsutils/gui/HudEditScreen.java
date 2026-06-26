package com.piccaxe.lsutils.gui;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import com.piccaxe.lsutils.hud.HudManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.EnumMap;
import java.util.Map;

/**
 * Drag-to-position editor for the movable HUD elements. Each enabled element is drawn with sample
 * data inside a draggable box; left-click and drag to move it. Positions persist on close.
 * Opened via the "HUD Editor" keybind or {@code /piccaxeutils hudedit}.
 */
public class HudEditScreen extends Screen {
	private final Screen parent;
	private final Map<HudManager.Hud, int[]> sizes = new EnumMap<>(HudManager.Hud.class);

	private HudManager.Hud dragging = null;
	private double dragOffsetX;
	private double dragOffsetY;

	public HudEditScreen(Screen parent) {
		super(Text.literal("HUD Editor"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addDrawableChild(ButtonWidget.builder(Text.literal("Reset Positions"), b -> resetPositions())
			.dimensions(this.width / 2 - 100, this.height - 28, 200, 20).build());
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
			int[] size = HudManager.renderElement(ctx, this.client, hud, x, y, true);
			int w = Math.max(size[0], 8);
			int h = Math.max(size[1], 8);
			sizes.put(hud, new int[]{w, h});

			boolean hovered = mouseX >= x - 2 && mouseX <= x + w + 2 && mouseY >= y - 2 && mouseY <= y + h + 2;
			int color = dragging == hud ? 0xFF55FF55 : (hovered ? 0xFFFFFF55 : 0x88FFFFFF);
			drawBox(ctx, x - 2, y - 2, x + w + 2, y + h + 2, color);
			ctx.drawTextWithShadow(this.client.textRenderer, Text.literal(HudManager.label(hud)), x - 2, y - 12, color);
		}

		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.client.textRenderer,
			Text.literal("Drag HUD elements — Esc to save"), this.width / 2, 8, 0xFFFFFFFF);
	}

	private static void drawBox(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
		ctx.fill(x1, y1, x2, y1 + 1, color);
		ctx.fill(x1, y2 - 1, x2, y2, color);
		ctx.fill(x1, y1, x1 + 1, y2, color);
		ctx.fill(x2 - 1, y1, x2, y2, color);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) {
			return true;
		}
		if (click.button() != 0) {
			return false;
		}
		Config cfg = ConfigManager.get();
		HudManager.Hud[] values = HudManager.Hud.values();
		for (int i = values.length - 1; i >= 0; i--) {
			HudManager.Hud hud = values[i];
			if (!HudManager.toggle(cfg, hud)) {
				continue;
			}
			int x = HudManager.getX(cfg, hud);
			int y = HudManager.getY(cfg, hud);
			int[] size = sizes.getOrDefault(hud, new int[]{40, 12});
			int w = size[0];
			int h = size[1];
			if (click.x() >= x - 2 && click.x() <= x + w + 2 && click.y() >= y - 2 && click.y() <= y + h + 2) {
				dragging = hud;
				dragOffsetX = click.x() - x;
				dragOffsetY = click.y() - y;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (dragging != null) {
			int nx = (int) Math.round(click.x() - dragOffsetX);
			int ny = (int) Math.round(click.y() - dragOffsetY);
			nx = Math.max(0, Math.min(nx, this.width - 4));
			ny = Math.max(0, Math.min(ny, this.height - 4));
			HudManager.setPos(ConfigManager.get(), dragging, nx, ny);
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (dragging != null) {
			dragging = null;
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
