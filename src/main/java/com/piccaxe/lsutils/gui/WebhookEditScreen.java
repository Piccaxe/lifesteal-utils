package com.piccaxe.lsutils.gui;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import com.piccaxe.lsutils.feature.DiscordWebhook;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Add or edit a single named webhook (name / URL / display username).
 * If {@code editing} is null this creates a new webhook.
 */
public class WebhookEditScreen extends Screen {
	private final Screen parent;
	private final Config.WebhookEntry editing;
	private TextFieldWidget nameField;
	private TextFieldWidget urlField;
	private TextFieldWidget usernameField;
	private String status = "";

	public WebhookEditScreen(Screen parent, Config.WebhookEntry editing) {
		super(Text.literal(editing == null ? "Add Webhook" : "Edit Webhook"));
		this.parent = parent;
		this.editing = editing;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = 50;
		nameField = labeledField(cx, y, "name (e.g. shop)", editing != null ? editing.name : "");
		y += 44;
		urlField = labeledField(cx, y, "https://discord.com/api/webhooks/…", editing != null ? editing.url : "");
		urlField.setMaxLength(300);
		y += 44;
		usernameField = labeledField(cx, y, "display name", editing != null ? editing.username : "Lifesteal Utils");

		int by = this.height - 30;
		addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> save()).dimensions(cx - 154, by, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Test"), b -> test()).dimensions(cx - 50, by, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> this.client.setScreen(parent))
			.dimensions(cx + 54, by, 100, 20).build());
	}

	private TextFieldWidget labeledField(int cx, int y, String placeholder, String value) {
		TextFieldWidget field = new TextFieldWidget(this.textRenderer, cx - 154, y, 308, 20, Text.literal(placeholder));
		field.setMaxLength(200);
		field.setPlaceholder(Text.literal(placeholder).formatted(Formatting.DARK_GRAY));
		field.setText(value);
		addDrawableChild(field);
		return field;
	}

	private void save() {
		String name = nameField.getText().trim();
		if (name.isEmpty()) {
			status = "Name can't be empty";
			return;
		}
		Config.WebhookEntry target = editing;
		if (target == null) {
			target = ConfigManager.webhook(name);
			if (target == null) {
				target = new Config.WebhookEntry();
				ConfigManager.get().webhooks.add(target);
			}
		}
		target.name = name;
		target.url = urlField.getText().trim();
		target.username = usernameField.getText().trim().isEmpty() ? "Lifesteal Utils" : usernameField.getText().trim();
		ConfigManager.save();
		this.client.setScreen(parent);
	}

	private void test() {
		String url = urlField.getText().trim();
		if (url.isEmpty()) {
			status = "Enter a URL first";
			return;
		}
		status = "Testing…";
		String username = usernameField.getText().trim();
		DiscordWebhook.send(url, username, "Test from Piccaxe's Lifesteal Utils", false,
			result -> this.client.execute(() -> status = result));
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFFFFFFF);
		ctx.drawTextWithShadow(this.textRenderer, Text.literal("Name"), this.width / 2 - 154, 40, 0xFFAAAAAA);
		ctx.drawTextWithShadow(this.textRenderer, Text.literal("Webhook URL"), this.width / 2 - 154, 84, 0xFFAAAAAA);
		ctx.drawTextWithShadow(this.textRenderer, Text.literal("Posts as"), this.width / 2 - 154, 128, 0xFFAAAAAA);
		if (!status.isEmpty()) {
			ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status),
				this.width / 2, this.height - 46, status.startsWith("sent OK") ? 0xFF55FF55 : 0xFFFFFF55);
		}
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}
}
