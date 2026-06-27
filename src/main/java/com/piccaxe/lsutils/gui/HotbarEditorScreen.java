package com.piccaxe.lsutils.gui;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/**
 * A visual hotbar tool with two honest modes (no client/server desync — everything you see is
 * what's really there):
 *  - SWAP:  click two of your 9 hotbar slots to physically swap their items (real inventory clicks).
 *  - REMAP: choose which physical slot each number key 1-9 selects, so after reorganizing you can
 *           keep your muscle memory. The selection, highlight, and server packet all reflect the
 *           real slot chosen — only the key→slot mapping changes (like rebinding a key).
 * Opened via the keybind or {@code /piccaxeutils hotbar}.
 */
public class HotbarEditorScreen extends Screen {
	private static final int SLOTS = 9;
	private static final int CELL = 22;
	private static final int GAP = 4;

	private enum Mode { SWAP, REMAP }

	private final Screen parent;
	private Mode mode = Mode.SWAP;
	private int selected = -1;   // SWAP: first picked slot
	private int pending = -1;    // REMAP: slot awaiting a key assignment

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
		int y = rowY() + CELL + 20;
		addDrawableChild(ButtonWidget.builder(modeLabel(), b -> {
			mode = mode == Mode.SWAP ? Mode.REMAP : Mode.SWAP;
			selected = -1;
			pending = -1;
			clearAndInit();
		}).dimensions(this.width / 2 - 154, y, 150, 20).build());

		if (mode == Mode.REMAP) {
			Config cfg = ConfigManager.get();
			addDrawableChild(ButtonWidget.builder(
				Text.literal("Remapping: " + (cfg.hotbarRemap ? "ON" : "OFF"))
					.formatted(cfg.hotbarRemap ? Formatting.GREEN : Formatting.RED),
				b -> {
					cfg.hotbarRemap = !cfg.hotbarRemap;
					ConfigManager.save();
					clearAndInit();
				}).dimensions(this.width / 2 + 4, y, 95, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), b -> {
				cfg.hotbarKeyMap = new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8};
				pending = -1;
				ConfigManager.save();
			}).dimensions(this.width / 2 + 103, y, 50, 20).build());
		}

		addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
			.dimensions(this.width / 2 - 60, y + 24, 120, 20).build());
	}

	private Text modeLabel() {
		return Text.literal("Mode: " + (mode == Mode.SWAP ? "Swap Items" : "Remap Keys"));
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		if (this.client == null || this.client.player == null) {
			return;
		}
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, rowY() - 44, 0xFFFFFFFF);
		ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(hint()).formatted(Formatting.GRAY),
			this.width / 2, rowY() - 30, 0xFFAAAAAA);

		PlayerInventory inv = this.client.player.getInventory();
		Config cfg = ConfigManager.get();
		int[] map = cfg.hotbarKeyMapSafe();
		int x = rowX();
		int y = rowY();
		for (int i = 0; i < SLOTS; i++) {
			int sx = x + i * (CELL + GAP);
			ctx.fill(sx, y, sx + CELL, y + CELL, 0x66000000);
			boolean hi = mode == Mode.SWAP ? i == selected : i == pending;
			int border = hi ? 0xFF55FF55 : (over(mouseX, mouseY, sx, y) ? 0xFFFFFF55 : 0x88FFFFFF);
			drawBorder(ctx, sx, y, sx + CELL, y + CELL, border);
			ItemStack st = inv.getStack(i);
			if (!st.isEmpty()) {
				ctx.drawItem(st, sx + 3, y + 3);
				ctx.drawStackOverlay(this.textRenderer, st, sx + 3, y + 3);
			}
			String label;
			int labelColor;
			if (mode == Mode.SWAP) {
				label = String.valueOf(i + 1);
				labelColor = 0xFFAAAAAA;
			} else {
				int key = keyForSlot(map, i); // which number key selects this physical slot
				label = key < 0 ? "-" : ("→" + (key + 1));
				labelColor = key < 0 ? 0xFF777777 : 0xFF55FF55;
			}
			ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(label), sx + CELL / 2, y + CELL + 3, labelColor);
		}
	}

	private String hint() {
		if (mode == Mode.SWAP) {
			return "Click two slots to swap their items (real, server-synced reorder)";
		}
		if (!ConfigManager.get().hotbarRemap) {
			return "Turn Remapping ON, then click an item and press the key you want for it";
		}
		return pending < 0
			? "Click an item, then press the number key (1-9) you want to select it"
			: "Now press a number key 1-9  (Esc to cancel)";
	}

	/** First number key (0-based) whose mapping points at physical slot {@code slot}, or -1. */
	private static int keyForSlot(int[] map, int slot) {
		for (int k = 0; k < SLOTS; k++) {
			if (map[k] == slot) {
				return k;
			}
		}
		return -1;
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
		if (mode == Mode.SWAP) {
			if (selected < 0) {
				selected = idx;
			} else if (selected == idx) {
				selected = -1;
			} else {
				swap(selected, idx);
				selected = -1;
			}
		} else {
			pending = pending == idx ? -1 : idx;
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (mode == Mode.REMAP && pending >= 0) {
			int n = numberKey(input.getKeycode()); // 0-8 for keys/numpad 1-9
			if (n >= 0) {
				Config cfg = ConfigManager.get();
				cfg.hotbarKeyMapSafe()[n] = pending;
				ConfigManager.save();
				pending = -1;
				return true;
			}
			if (input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
				pending = -1;
				return true;
			}
		}
		return super.keyPressed(input);
	}

	/** Maps a GLFW keycode for 1-9 (top row or numpad) to 0-8, else -1. */
	private static int numberKey(int code) {
		if (code >= GLFW.GLFW_KEY_1 && code <= GLFW.GLFW_KEY_9) {
			return code - GLFW.GLFW_KEY_1;
		}
		if (code >= GLFW.GLFW_KEY_KP_1 && code <= GLFW.GLFW_KEY_KP_9) {
			return code - GLFW.GLFW_KEY_KP_1;
		}
		return -1;
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
