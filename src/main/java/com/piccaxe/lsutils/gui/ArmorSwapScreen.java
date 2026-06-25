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
 * Configures the armor swapper: enable, the low/high HP thresholds, and two sets (defense / normal),
 * each with a lore keyword that matches any piece plus optional per-slot overrides.
 */
public class ArmorSwapScreen extends Screen {
	private final Screen parent;
	private TextFieldWidget lowField;
	private TextFieldWidget highField;
	private TextFieldWidget dKeyword;
	private TextFieldWidget dHelm;
	private TextFieldWidget dChest;
	private TextFieldWidget dLegs;
	private TextFieldWidget dBoots;
	private TextFieldWidget nKeyword;
	private TextFieldWidget nHelm;
	private TextFieldWidget nChest;
	private TextFieldWidget nLegs;
	private TextFieldWidget nBoots;

	public ArmorSwapScreen(Screen parent) {
		super(Text.literal("Armor Swapper"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config cfg = ConfigManager.get();
		int cx = this.width / 2;

		boolean[] on = {cfg.armorSwapper};
		addDrawableChild(ButtonWidget.builder(enabledLabel(on[0]), b -> {
			on[0] = !on[0];
			ConfigManager.get().armorSwapper = on[0];
			ConfigManager.save();
			b.setMessage(enabledLabel(on[0]));
		}).dimensions(cx - 158, 26, 120, 20).build());
		lowField = num(cx - 30, 26, 50, str(cfg.armorSwapLowHp));
		highField = num(cx + 100, 26, 50, str(cfg.armorSwapHighHp));

		int lx = cx - 158;
		int rx = cx + 8;
		int w = 150;
		dKeyword = field(lx, 78, w, cfg.armorDefenseSet.keyword, "keyword (any piece)");
		dHelm = field(lx, 102, w, cfg.armorDefenseSet.helmet, "helmet override");
		dChest = field(lx, 126, w, cfg.armorDefenseSet.chest, "chest override");
		dLegs = field(lx, 150, w, cfg.armorDefenseSet.legs, "legs override");
		dBoots = field(lx, 174, w, cfg.armorDefenseSet.boots, "boots override");
		nKeyword = field(rx, 78, w, cfg.armorNormalSet.keyword, "keyword (any piece)");
		nHelm = field(rx, 102, w, cfg.armorNormalSet.helmet, "helmet override");
		nChest = field(rx, 126, w, cfg.armorNormalSet.chest, "chest override");
		nLegs = field(rx, 150, w, cfg.armorNormalSet.legs, "legs override");
		nBoots = field(rx, 174, w, cfg.armorNormalSet.boots, "boots override");

		addDrawableChild(ButtonWidget.builder(Text.literal("Save & Back"), b -> close())
			.dimensions(cx - 60, this.height - 26, 120, 20).build());
	}

	private TextFieldWidget num(int x, int y, int w, String value) {
		TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, w, 20, Text.literal("hp"));
		f.setMaxLength(5);
		f.setText(value);
		addDrawableChild(f);
		return f;
	}

	private TextFieldWidget field(int x, int y, int w, String value, String placeholder) {
		TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, w, 18, Text.literal(placeholder));
		f.setMaxLength(80);
		f.setPlaceholder(Text.literal(placeholder).formatted(Formatting.DARK_GRAY));
		f.setText(value == null ? "" : value);
		addDrawableChild(f);
		return f;
	}

	private static Text enabledLabel(boolean on) {
		return Text.literal("Swapper: ").append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED));
	}

	private static String str(double d) {
		return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d);
	}

	private static double dbl(String s, double fallback) {
		try {
			return Double.parseDouble(s.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private void save() {
		Config cfg = ConfigManager.get();
		cfg.armorSwapLowHp = dbl(lowField.getText(), cfg.armorSwapLowHp);
		cfg.armorSwapHighHp = dbl(highField.getText(), cfg.armorSwapHighHp);
		cfg.armorDefenseSet.keyword = dKeyword.getText().trim();
		cfg.armorDefenseSet.helmet = dHelm.getText().trim();
		cfg.armorDefenseSet.chest = dChest.getText().trim();
		cfg.armorDefenseSet.legs = dLegs.getText().trim();
		cfg.armorDefenseSet.boots = dBoots.getText().trim();
		cfg.armorNormalSet.keyword = nKeyword.getText().trim();
		cfg.armorNormalSet.helmet = nHelm.getText().trim();
		cfg.armorNormalSet.chest = nChest.getText().trim();
		cfg.armorNormalSet.legs = nLegs.getText().trim();
		cfg.armorNormalSet.boots = nBoots.getText().trim();
		ConfigManager.save();
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		int cx = this.width / 2;
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 10, 0xFFFFFFFF);
		ctx.drawTextWithShadow(this.textRenderer, Text.literal("Low"), cx - 48, 32, 0xFFAAAAAA);
		ctx.drawTextWithShadow(this.textRenderer, Text.literal("High"), cx + 78, 32, 0xFFAAAAAA);
		ctx.drawTextWithShadow(this.textRenderer, Text.literal("DEFENSE (≤ low HP)").formatted(Formatting.AQUA), cx - 158, 66, 0xFF55FFFF);
		ctx.drawTextWithShadow(this.textRenderer, Text.literal("NORMAL (≥ high HP)").formatted(Formatting.GREEN), cx + 8, 66, 0xFF55FF55);
	}

	@Override
	public void close() {
		save();
		this.client.setScreen(parent);
	}
}
