package com.piccaxe.lsutils.gui;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Paginated list of keyword→webhook rules with add / edit / on-off / remove.
 */
public class RuleListScreen extends Screen {
	private static final int PER_PAGE = 6;

	private final Screen parent;
	private int page;

	public RuleListScreen(Screen parent) {
		super(Text.literal("Webhook Rules"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		List<Config.WebhookRule> rules = ConfigManager.get().webhookRules;
		int cx = this.width / 2;
		int start = page * PER_PAGE;
		int y = 40;
		for (int i = start; i < Math.min(start + PER_PAGE, rules.size()); i++) {
			Config.WebhookRule r = rules.get(i);
			int idx = i;
			String desc = "\"" + trim(r.keyword, 18) + "\" → " + trim(r.webhook, 14);
			addDrawableChild(ButtonWidget.builder(Text.literal(desc), b -> this.client.setScreen(new RuleEditScreen(this, r)))
				.dimensions(cx - 154, y, 210, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.literal(r.enabled ? "On" : "Off").formatted(r.enabled ? Formatting.GREEN : Formatting.RED),
					b -> {
						r.enabled = !r.enabled;
						ConfigManager.save();
						b.setMessage(Text.literal(r.enabled ? "On" : "Off").formatted(r.enabled ? Formatting.GREEN : Formatting.RED));
					})
				.dimensions(cx + 60, y, 54, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.literal("X").formatted(Formatting.RED), b -> {
				rules.remove(idx);
				ConfigManager.save();
				clearAndInit();
			}).dimensions(cx + 118, y, 36, 20).build());
			y += 24;
		}

		int navY = this.height - 54;
		if (page > 0) {
			addDrawableChild(ButtonWidget.builder(Text.literal("< Prev"), b -> {
				page--;
				clearAndInit();
			}).dimensions(cx - 154, navY, 70, 20).build());
		}
		if (start + PER_PAGE < rules.size()) {
			addDrawableChild(ButtonWidget.builder(Text.literal("Next >"), b -> {
				page++;
				clearAndInit();
			}).dimensions(cx + 84, navY, 70, 20).build());
		}

		int by = this.height - 28;
		addDrawableChild(ButtonWidget.builder(Text.literal("+ Add Rule"), b -> this.client.setScreen(new RuleEditScreen(this, null)))
			.dimensions(cx - 154, by, 140, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close()).dimensions(cx + 14, by, 140, 20).build());
	}

	private static String trim(String s, int max) {
		return s == null ? "" : (s.length() > max ? s.substring(0, max - 1) + "…" : s);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, 0xFFFFFFFF);
	}

	@Override
	public void close() {
		ConfigManager.save();
		this.client.setScreen(parent);
	}
}
