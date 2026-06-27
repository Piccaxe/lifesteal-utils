package com.piccaxe.lsutils.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * A visual hotbar editor: shows your 9 hotbar slots; click two to swap them. The swaps are real
 * inventory clicks on the player screen handler, so the change applies for you AND the server (no
 * client/server desync). Opened via the keybind or {@code /piccaxeutils hotbar}.
 */
public class HotbarEditorScreen extends Screen {
	private static final int SLOTS = 9;
	private static final int CELL = 22;
	private static final int GAP = 4;

	private final Screen parent;
	private int selected = -1;

	public HotbarEditorScreen(Screen parent) {
		super(Text.literal("Hotbar Editor"));
		this.parent = parent;
	}

	private int rowX() {
		return this.width / 2 - (SLOTS * CELL + (SLOTS - 1) * GAP) / 2;
	}

	private int rowY() {
		return this.height / 2 - CELL / 2;
	}

	@Override
	protected void init() {
		addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
			.dimensions(this.width / 2 - 60, rowY() + CELL + 22, 120, 20).build());
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		if (this.client == null || this.client.player == null) {
			return;
		}
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, rowY() - 40, 0xFFFFFFFF);
		ctx.drawCenteredTextWithShadow(this.textRenderer,
			Text.literal(selected < 0 ? "Click a slot, then another to swap them" : "Click another slot to swap (or the same to cancel)")
				.formatted(Formatting.GRAY), this.width / 2, rowY() - 26, 0xFFAAAAAA);

		PlayerInventory inv = this.client.player.getInventory();
		int x = rowX();
		int y = rowY();
		for (int i = 0; i < SLOTS; i++) {
			int sx = x + i * (CELL + GAP);
			ctx.fill(sx, y, sx + CELL, y + CELL, 0x66000000);
			int border = i == selected ? 0xFF55FF55 : (over(mouseX, mouseY, sx, y) ? 0xFFFFFF55 : 0x88FFFFFF);
			drawBorder(ctx, sx, y, sx + CELL, y + CELL, border);
			ItemStack st = inv.getStack(i);
			if (!st.isEmpty()) {
				ctx.drawItem(st, sx + 3, y + 3);
				ctx.drawStackOverlay(this.textRenderer, st, sx + 3, y + 3);
			}
			ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(String.valueOf(i + 1)), sx + CELL / 2, y + CELL + 3, 0xFFAAAAAA);
		}
	}

	private boolean over(double mx, double my, int sx, int y) {
		return mx >= sx && mx <= sx + CELL && my >= y && my <= y + CELL;
	}

	private int slotAt(double mx, double my) {
		int x = rowX();
		int y = rowY();
		for (int i = 0; i < SLOTS; i++) {
			if (over(mx, my, x + i * (CELL + GAP), y)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (super.mouseClicked(click, doubled)) {
			return true;
		}
		if (click.button() != 0) {
			return false;
		}
		int idx = slotAt(click.x(), click.y());
		if (idx < 0) {
			return false;
		}
		if (selected < 0) {
			selected = idx;
		} else if (selected == idx) {
			selected = -1;
		} else {
			swap(selected, idx);
			selected = -1;
		}
		return true;
	}

	private void swap(int a, int b) {
		if (this.client == null || this.client.player == null || this.client.interactionManager == null) {
			return;
		}
		ScreenHandler handler = this.client.player.playerScreenHandler;
		// Hotbar slot 'a' lives at handler index 36+a; SWAP exchanges it with hotbar index 'b'.
		this.client.interactionManager.clickSlot(handler.syncId, 36 + a, b, SlotActionType.SWAP, this.client.player);
	}

	private static void drawBorder(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
		ctx.fill(x1, y1, x2, y1 + 1, color);
		ctx.fill(x1, y2 - 1, x2, y2, color);
		ctx.fill(x1, y1, x1 + 1, y2, color);
		ctx.fill(x2 - 1, y1, x2, y2, color);
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}
}
