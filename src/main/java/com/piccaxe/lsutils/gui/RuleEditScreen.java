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
 * Add or edit a keyword→webhook rule: keyword, target webhook, optional label/ping, enabled,
 * a "server messages only" mode, and a per-rule ignore list (players/text to skip).
 * {@code editing} null = new rule. Field edits are preserved across the ignore sub-screen.
 */
public class RuleEditScreen extends Screen {
	private final Screen parent;
	private final Config.WebhookRule editing;

	private TextFieldWidget keywordField;
	private TextFieldWidget labelField;
	private TextFieldWidget pingField;
	private String keyword;
	private String label;
	private String ping;
	private String webhook;
	private boolean enabled;
	private boolean serverOnly;
	private final List<String> ignore;

	public RuleEditScreen(Screen parent, Config.WebhookRule editing) {
		super(Text.literal(editing == null ? "Add Rule" : "Edit Rule"));
		this.parent = parent;
		this.editing = editing;
		this.keyword = editing != null ? editing.keyword : "";
		this.label = editing != null ? editing.label : "";
		this.ping = editing != null ? editing.ping : "";
		this.webhook = editing != null ? editing.webhook : firstWebhookName();
		this.enabled = editing == null || editing.enabled;
		this.serverOnly = editing != null && editing.serverOnly;
		this.ignore = new ArrayList<>(editing != null && editing.ignore != null ? editing.ignore : List.of());
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		keywordField = field(cx, 46, "keyword (blank = all, if server-only)", keyword);
		labelField = field(cx, 84, "label (optional)", label);
		pingField = field(cx, 122, "ping (optional, e.g. @here)", ping);

		addDrawableChild(ButtonWidget.builder(webhookLabel(), b -> {
			webhook = nextWebhookName(webhook);
			b.setMessage(webhookLabel());
		}).dimensions(cx - 154, 150, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(enabledLabel(), b -> {
			enabled = !enabled;
			b.setMessage(enabledLabel());
		}).dimensions(cx - 50, 150, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(serverOnlyLabel(), b -> {
			serverOnly = !serverOnly;
			b.setMessage(serverOnlyLabel());
		}).dimensions(cx + 54, 150, 100, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Ignore list (" + ignore.size() + ")"), b -> {
			capture();
			this.client.setScreen(new StringListScreen(this, "Rule ignore (players / text)", ignore, false));
		}).dimensions(cx - 154, 174, 308, 20).build());

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

	/** Pull current text-field values into state so they survive navigating to the ignore sub-screen. */
	private void capture() {
		keyword = keywordField.getText();
		label = labelField.getText();
		ping = pingField.getText();
	}

	private Text webhookLabel() {
		return Text.literal("→ " + (webhook == null || webhook.isBlank() ? "(pick)" : webhook));
	}

	private Text enabledLabel() {
		return Text.literal("On: ").append(Text.literal(enabled ? "YES" : "NO").formatted(enabled ? Formatting.GREEN : Formatting.RED));
	}

	private Text serverOnlyLabel() {
		return Text.literal("Server-only: ").append(Text.literal(serverOnly ? "YES" : "NO").formatted(serverOnly ? Formatting.GREEN : Formatting.GRAY));
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
		capture();
		Config.WebhookRule target = editing;
		if (target == null) {
			target = new Config.WebhookRule();
			ConfigManager.get().webhookRules.add(target);
		}
		target.keyword = keyword.trim();
		target.label = label.trim();
		target.ping = ping.trim();
		target.webhook = webhook;
		target.enabled = enabled;
		target.serverOnly = serverOnly;
		target.ignore = ignore;
		ConfigManager.save();
		this.client.setScreen(parent);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, 0xFFFFFFFF);
		ctx.drawTextWithShadow(this.textRenderer, Text.literal("Keyword"), this.width / 2 - 154, 36, 0xFFAAAAAA);
		ctx.drawTextWithShadow(this.textRenderer, Text.literal("Label"), this.width / 2 - 154, 74, 0xFFAAAAAA);
		ctx.drawTextWithShadow(this.textRenderer, Text.literal("Ping"), this.width / 2 - 154, 112, 0xFFAAAAAA);
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}
}
