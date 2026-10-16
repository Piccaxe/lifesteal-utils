package com.piccaxe.lsutils.gui;

import com.piccaxe.lsutils.config.ConfigManager;
import com.piccaxe.lsutils.config.ConfigProfiles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;

/**
 * Config profiles: save the current setup under a name, load one, bind one to the server you're on (it
 * auto-loads when you join that server), and copy/paste share codes for your whole config.
 */
public class ProfilesScreen extends Screen {
	private static final int PER_PAGE = 6;

	private final Screen parent;
	private TextFieldWidget nameField;
	private int page = 0;
	private Text status = Text.empty();

	public ProfilesScreen(Screen parent) {
		super(Text.literal("Config Profiles"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		String keep = nameField != null ? nameField.getText() : ConfigProfiles.active();

		boolean auto = ConfigManager.get().profileAutoSwitch;
		addDrawableChild(ButtonWidget.builder(Text.literal("Auto-switch per server: ")
			.append(Text.literal(auto ? "ON" : "OFF").formatted(auto ? Formatting.GREEN : Formatting.RED)), b -> {
			ConfigManager.get().profileAutoSwitch = !ConfigManager.get().profileAutoSwitch;
			ConfigManager.save();
			this.clearAndInit();
		}).dimensions(cx - 154, 32, 308, 20).build());

		nameField = new TextFieldWidget(this.textRenderer, cx - 154, 58, 200, 20, Text.literal("Profile name"));
		nameField.setMaxLength(32);
		nameField.setText(keep == null ? "" : keep);
		addDrawableChild(nameField);
		addDrawableChild(ButtonWidget.builder(Text.literal("Save current"), b -> {
			boolean ok = ConfigProfiles.save(nameField.getText());
			status = ok ? Text.literal("Saved \"" + nameField.getText().trim() + "\"").formatted(Formatting.GREEN)
				: Text.literal("Enter a name first").formatted(Formatting.RED);
			this.clearAndInit();
		}).dimensions(cx + 50, 58, 104, 20).build());

		List<String> profiles = ConfigProfiles.list();
		Map<String, String> bindings = ConfigProfiles.bindings();
		String here = currentAddress();
		int pages = Math.max(1, (profiles.size() + PER_PAGE - 1) / PER_PAGE);
		page = Math.min(page, pages - 1);
		int y = 86;
		for (int i = page * PER_PAGE; i < Math.min(profiles.size(), (page + 1) * PER_PAGE); i++) {
			String name = profiles.get(i);
			boolean active = name.equalsIgnoreCase(ConfigProfiles.active());
			long bound = bindings.values().stream().filter(v -> v.equalsIgnoreCase(name)).count();
			Text label = Text.literal(name).formatted(active ? Formatting.GREEN : Formatting.WHITE)
				.append(Text.literal(bound > 0 ? "  (" + bound + " server" + (bound > 1 ? "s" : "") + ")" : "").formatted(Formatting.GRAY));
			addDrawableChild(ButtonWidget.builder(label, b -> {
				boolean ok = ConfigProfiles.load(name);
				status = ok ? Text.literal("Loaded \"" + name + "\"").formatted(Formatting.GREEN)
					: Text.literal("Couldn't load \"" + name + "\"").formatted(Formatting.RED);
				nameField.setText(name);
				this.clearAndInit();
			}).dimensions(cx - 154, y, 170, 20).build());
			ButtonWidget bind = ButtonWidget.builder(Text.literal("Bind here"), b -> {
				ConfigProfiles.bind(here, name);
				status = Text.literal("\"" + name + "\" will load on " + here).formatted(Formatting.AQUA);
				this.clearAndInit();
			}).dimensions(cx + 20, y, 70, 20).build();
			bind.active = here != null;
			addDrawableChild(bind);
			addDrawableChild(ButtonWidget.builder(Text.literal("Delete").formatted(Formatting.RED), b -> {
				ConfigProfiles.delete(name);
				status = Text.literal("Deleted \"" + name + "\"").formatted(Formatting.YELLOW);
				this.clearAndInit();
			}).dimensions(cx + 94, y, 60, 20).build());
			y += 24;
		}
		if (pages > 1) {
			addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
				page = (page - 1 + pages) % pages;
				this.clearAndInit();
			}).dimensions(cx - 154, y, 20, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
				page = (page + 1) % pages;
				this.clearAndInit();
			}).dimensions(cx + 134, y, 20, 20).build());
		}

		ButtonWidget unbind = ButtonWidget.builder(Text.literal("Unbind this server"), b -> {
			status = ConfigProfiles.unbind(here) ? Text.literal("Unbound " + here).formatted(Formatting.YELLOW)
				: Text.literal("This server isn't bound").formatted(Formatting.GRAY);
			this.clearAndInit();
		}).dimensions(cx - 154, this.height - 78, 150, 20).build();
		unbind.active = here != null;
		addDrawableChild(unbind);

		addDrawableChild(ButtonWidget.builder(Text.literal("Import from clipboard"), b -> {
			boolean ok = ConfigProfiles.importCode(MinecraftClient.getInstance().keyboard.getClipboard());
			status = ok ? Text.literal("Imported config from clipboard").formatted(Formatting.GREEN)
				: Text.literal("Clipboard doesn't hold a PLSC1 code").formatted(Formatting.RED);
			this.clearAndInit();
		}).dimensions(cx + 4, this.height - 78, 150, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Copy share code"), b -> {
			MinecraftClient.getInstance().keyboard.setClipboard(ConfigProfiles.exportCode(false));
			status = Text.literal("Copied (webhooks left out)").formatted(Formatting.GREEN);
		}).dimensions(cx - 154, this.height - 54, 150, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Copy incl. webhooks").formatted(Formatting.YELLOW), b -> {
			MinecraftClient.getInstance().keyboard.setClipboard(ConfigProfiles.exportCode(true));
			status = Text.literal("Copied WITH webhook URLs — only share with people you trust").formatted(Formatting.YELLOW);
		}).dimensions(cx + 4, this.height - 54, 150, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.close())
			.dimensions(cx - 100, this.height - 28, 200, 20).build());
	}

	private static String currentAddress() {
		var info = MinecraftClient.getInstance().getCurrentServerEntry();
		return info != null ? info.address : null;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFFFF);
		ctx.drawCenteredTextWithShadow(this.textRenderer, status, this.width / 2, this.height - 92, 0xFFFFFFFF);
	}

	@Override
	public void close() {
		ConfigManager.save();
		if (this.client != null) {
			this.client.setScreen(parent);
		}
	}
}
