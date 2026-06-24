package com.piccaxe.lsutils.gui;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Add or edit a keyword→webhook rule: keyword text, target webhook (cycle), optional label and ping,
 * and an enabled toggle. {@code editing} null = new rule.
 */
public class RuleEditScreen extends Screen {
	private final Screen parent;
	private final Config.WebhookRule editing;

	private TextFieldWidget keywordField;
	private TextFieldWidget labelField;
	private TextFieldWidget pingField;
	private String webhook;
	private boolean enabled;

	public RuleEditScreen(Screen parent, Config.WebhookRule editing) {
		super(Text.literal(editing == null ? "Add Rule" : "Edit Rule"));
		this.parent = parent;
		this.editing = editing;
		this.webhook = editing != null ? editing.webhook : firstWebhookName();
		this.enabled = editing == null || editing.enabled;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		keywordField = field(cx, 50, "keyword (e.g. tpa)", editing != null ? editing.keyword : "");
		labelField = field(cx, 94, "label (optional)", editing != null ? editing.label : "");
		pingField = field(cx, 138, "ping (optional, e.g. @here or <@id>)", editing != null ? editing.ping : "");

		addDrawableChild(ButtonWidget.builder(webhookLabel(), b -> {
			webhook = nextWebhookName(webhook);
			b.setMessage(webhookLabel());
		}).dimensions(cx - 154, 172, 150, 20).build());
		addDrawableChild(ButtonWidget.builder(enabledLabel(), b -> {
			enabled = !enabled;
			b.setMessage(enabledLabel());
		}).dimensions(cx + 4, 172, 150, 20).build());

		int by = this.height - 30;
		addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> save()).dimensions(cx - 154, by, 150, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> this.client.setScreen(parent)).dimensions(cx + 4, by, 150, 20).build());
	}

	private TextFieldWidget field(int cx, int y, String placeholder, String value) {
		TextFieldWidget f = new TextFieldWidget(this.textRenderer, cx - 154, y, 308, 20, Text.literal(placeholder));
		f.setMaxLength(200);
		f.setPlaceholder(Text.literal(placeholder).formatted(Formatting.DARK_GRAY));
		f.setText(value);
		addDrawableChild(f);
		return f;
	}

	private Text webhookLabel() {
		return Text.literal("→ " + (webhook == null || webhook.isBlank() ? "(pick webhook)" : webhook));
	}

	private Text enabledLabel() {
		return Text.literal("Enabled: ").append(Text.literal(enabled ? "ON" : "OFF").formatted(enabled ? Formatting.GREEN : Formatting.RED));
	}

	private static String firstWebhookName() {
		List<Config.WebhookEntry> hooks = ConfigManager.get().webhooks;
		return hooks.isEmpty() ? "" : hooks.get(0).name;
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

	private void save() {
		Config.WebhookRule target = editing;
		if (target == null) {
			target = new Config.WebhookRule();
			ConfigManager.get().webhookRules.add(target);
		}
		target.keyword = keywordField.getText().trim();
		target.label = labelField.getText().trim();
		target.ping = pingField.getText().trim();
		target.webhook = webhook;
		target.enabled = enabled;
		ConfigManager.save();
		this.client.setScreen(parent);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFFFFFFF);
		ctx.drawTextWithShadow(this.textRenderer, Text.literal("Keyword"), this.width / 2 - 154, 40, 0xFFAAAAAA);
		ctx.drawTextWithShadow(this.textRenderer, Text.literal("Label"), this.width / 2 - 154, 84, 0xFFAAAAAA);
		ctx.drawTextWithShadow(this.textRenderer, Text.literal("Ping"), this.width / 2 - 154, 128, 0xFFAAAAAA);
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}
}
