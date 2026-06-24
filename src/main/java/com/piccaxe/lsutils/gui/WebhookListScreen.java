package com.piccaxe.lsutils.gui;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import com.piccaxe.lsutils.feature.DiscordWebhook;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Manage named webhooks: per-category assignment (chat/notifier/proximity), and a paginated list
 * with add / edit / test / remove. Links to the rules screen.
 */
public class WebhookListScreen extends Screen {
	private static final int PER_PAGE = 5;

	private final Screen parent;
	private int page;
	private String status = "";

	public WebhookListScreen(Screen parent) {
		super(Text.literal("Webhooks"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config cfg = ConfigManager.get();
		int cx = this.width / 2;

		addDrawableChild(ButtonWidget.builder(assignLabel("Chat", cfg.chatWebhook), b -> cycleAssign(0, b))
			.dimensions(cx - 154, 30, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(assignLabel("Notifier", cfg.notifierWebhook), b -> cycleAssign(1, b))
			.dimensions(cx - 50, 30, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(assignLabel("Proximity", cfg.proximityWebhook), b -> cycleAssign(2, b))
			.dimensions(cx + 54, 30, 100, 20).build());

		List<Config.WebhookEntry> hooks = cfg.webhooks;
		int start = page * PER_PAGE;
		int y = 62;
		for (int i = start; i < Math.min(start + PER_PAGE, hooks.size()); i++) {
			Config.WebhookEntry wh = hooks.get(i);
			int idx = i;
			addDrawableChild(ButtonWidget.builder(Text.literal(trim(wh.name, 24) + (wh.url == null || wh.url.isBlank() ? " §c(no url)" : "")),
					b -> this.client.setScreen(new WebhookEditScreen(this, wh)))
				.dimensions(cx - 154, y, 200, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.literal("Test"), b -> test(wh)).dimensions(cx + 50, y, 64, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.literal("X").formatted(Formatting.RED), b -> {
				hooks.remove(idx);
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
		if (start + PER_PAGE < hooks.size()) {
			addDrawableChild(ButtonWidget.builder(Text.literal("Next >"), b -> {
				page++;
				clearAndInit();
			}).dimensions(cx + 84, navY, 70, 20).build());
		}

		int by = this.height - 28;
		addDrawableChild(ButtonWidget.builder(Text.literal("+ Add Webhook"), b -> this.client.setScreen(new WebhookEditScreen(this, null)))
			.dimensions(cx - 154, by, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Rules…"), b -> this.client.setScreen(new RuleListScreen(this)))
			.dimensions(cx - 50, by, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close()).dimensions(cx + 54, by, 100, 20).build());
	}

	private void cycleAssign(int category, ButtonWidget btn) {
		Config cfg = ConfigManager.get();
		String cur = switch (category) {
			case 0 -> cfg.chatWebhook;
			case 1 -> cfg.notifierWebhook;
			default -> cfg.proximityWebhook;
		};
		String next = nextWebhookName(cur);
		switch (category) {
			case 0 -> cfg.chatWebhook = next;
			case 1 -> cfg.notifierWebhook = next;
			default -> cfg.proximityWebhook = next;
		}
		ConfigManager.save();
		btn.setMessage(assignLabel(switch (category) {
			case 0 -> "Chat";
			case 1 -> "Notifier";
			default -> "Proximity";
		}, next));
	}

	private static String nextWebhookName(String cur) {
		List<String> options = new ArrayList<>();
		options.add("");
		for (Config.WebhookEntry w : ConfigManager.get().webhooks) {
			options.add(w.name);
		}
		int i = options.indexOf(cur);
		return options.get(((i < 0 ? 0 : i) + 1) % options.size());
	}

	private static Text assignLabel(String category, String value) {
		return Text.literal(category + ": " + (value == null || value.isBlank() ? "—" : value));
	}

	private void test(Config.WebhookEntry wh) {
		if (wh.url == null || wh.url.isBlank()) {
			status = "'" + wh.name + "' has no URL";
			return;
		}
		status = "Testing '" + wh.name + "'…";
		DiscordWebhook.send(wh.url, wh.username, "Test from Piccaxe's Lifesteal Utils (" + wh.name + ")", false,
			result -> this.client.execute(() -> status = wh.name + ": " + result));
	}

	private static String trim(String s, int max) {
		return s == null ? "" : (s.length() > max ? s.substring(0, max - 1) + "…" : s);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFFFF);
		if (!status.isEmpty()) {
			ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status),
				this.width / 2, this.height - 68, status.contains("sent OK") ? 0xFF55FF55 : 0xFFFFFF55);
		}
	}

	@Override
	public void close() {
		ConfigManager.save();
		this.client.setScreen(parent);
	}
}
