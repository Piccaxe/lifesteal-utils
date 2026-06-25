package com.piccaxe.lsutils.gui;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * All compass / direction-HUD options in one place: on/off, minimal mode, ticks, background box,
 * width, and the three colors (main / center marker / north). Position is set in the HUD editor.
 */
public class CompassScreen extends Screen {
	private final Screen parent;
	private TextFieldWidget widthField;
	private TextFieldWidget colorField;
	private TextFieldWidget markerField;
	private TextFieldWidget northField;

	public CompassScreen(Screen parent) {
		super(Text.literal("Compass HUD"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config cfg = ConfigManager.get();
		int cx = this.width / 2;

		toggle(cx - 154, 32, "Enabled", cfg.directionHud, v -> ConfigManager.get().directionHud = v);
		toggle(cx + 4, 32, "Minimal", cfg.directionMinimal, v -> ConfigManager.get().directionMinimal = v);
		toggle(cx - 154, 56, "Ticks", cfg.directionTicks, v -> ConfigManager.get().directionTicks = v);
		toggle(cx + 4, 56, "Background", cfg.directionBackground, v -> ConfigManager.get().directionBackground = v);

		widthField = hexOrNumField(cx, 92, String.valueOf(cfg.directionHudWidth), 4);
		colorField = hexOrNumField(cx, 116, hexStr(cfg.directionColor), 6);
		markerField = hexOrNumField(cx, 140, hexStr(cfg.directionMarkerColor), 6);
		northField = hexOrNumField(cx, 164, hexStr(cfg.directionNorthColor), 6);

		int by = this.height - 28;
		addDrawableChild(ButtonWidget.builder(Text.literal("HUD Editor (move)"), b -> {
			save();
			this.client.setScreen(new HudEditScreen(this));
		}).dimensions(cx - 154, by, 150, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Save & Back"), b -> close()).dimensions(cx + 4, by, 150, 20).build());
	}

	private void toggle(int x, int y, String label, boolean initial, java.util.function.Consumer<Boolean> setter) {
		boolean[] state = {initial};
		addDrawableChild(ButtonWidget.builder(tlabel(label, state[0]), b -> {
			state[0] = !state[0];
			setter.accept(state[0]);
			ConfigManager.save();
			b.setMessage(tlabel(label, state[0]));
		}).dimensions(x, y, 150, 20).build());
	}

	private TextFieldWidget hexOrNumField(int cx, int y, String value, int maxLen) {
		TextFieldWidget f = new TextFieldWidget(this.textRenderer, cx + 4, y, 150, 18, Text.literal(value));
		f.setMaxLength(maxLen);
		f.setText(value);
		addDrawableChild(f);
		return f;
	}

	private static Text tlabel(String label, boolean on) {
		return Text.literal(label + ": ").append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED));
	}

	private static String hexStr(int rgb) {
		return String.format("%06X", rgb & 0xFFFFFF);
	}

	private static int parseHex(String s, int fallback) {
		try {
			String t = s.trim().replace("#", "").replace("0x", "").replace("0X", "");
			return (int) (Long.parseLong(t, 16) & 0xFFFFFF);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static int parseInt(String s, int fallback) {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private void save() {
		Config cfg = ConfigManager.get();
		cfg.directionHudWidth = Math.max(40, Math.min(400, parseInt(widthField.getText(), cfg.directionHudWidth)));
		cfg.directionColor = parseHex(colorField.getText(), cfg.directionColor);
		cfg.directionMarkerColor = parseHex(markerField.getText(), cfg.directionMarkerColor);
		cfg.directionNorthColor = parseHex(northField.getText(), cfg.directionNorthColor);
		ConfigManager.save();
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		int cx = this.width / 2;
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 14, 0xFFFFFFFF);
		ctx.drawTextWithShadow(this.textRenderer, Text.literal("Width"), cx - 154, 96, 0xFFAAAAAA);
		ctx.drawTextWithShadow(this.textRenderer, Text.literal("Color (hex)"), cx - 154, 120, 0xFFAAAAAA);
		ctx.drawTextWithShadow(this.textRenderer, Text.literal("Marker (hex)"), cx - 154, 144, 0xFFAAAAAA);
		ctx.drawTextWithShadow(this.textRenderer, Text.literal("North (hex)"), cx - 154, 168, 0xFFAAAAAA);
	}

	@Override
	public void close() {
		save();
		this.client.setScreen(parent);
	}
}
