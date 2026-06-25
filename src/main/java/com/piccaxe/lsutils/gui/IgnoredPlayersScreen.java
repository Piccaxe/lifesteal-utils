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
import java.util.Locale;
import java.util.TreeSet;

/**
 * One place to manage every ignored player. "Add" puts a name into both the notifier and proximity
 * ignore lists; each row then lets you toggle that player in/out of each list individually, or remove
 * them from both. Names are stored lowercased to match how the features compare them.
 */
public class IgnoredPlayersScreen extends Screen {
	private static final int PER_PAGE = 6;

	private final Screen parent;
	private int page;
	private TextFieldWidget input;

	public IgnoredPlayersScreen(Screen parent) {
		super(Text.literal("Ignored Players"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config cfg = ConfigManager.get();
		int cx = this.width / 2;

		input = new TextFieldWidget(this.textRenderer, cx - 154, 32, 220, 20, Text.literal("player name"));
		input.setMaxLength(64);
		input.setPlaceholder(Text.literal("player name").formatted(Formatting.DARK_GRAY));
		addDrawableChild(input);
		addDrawableChild(ButtonWidget.builder(Text.literal("+ Add"), b -> addPlayer())
			.dimensions(cx + 70, 32, 84, 20).build());

		List<String> names = allIgnored(cfg);
		int start = page * PER_PAGE;
		int y = 62;
		for (int i = start; i < Math.min(start + PER_PAGE, names.size()); i++) {
			String name = names.get(i);
			addDrawableChild(ButtonWidget.builder(Text.literal(trim(name, 16)), b -> {
			}).dimensions(cx - 154, y, 104, 20).build());
			addDrawableChild(ButtonWidget.builder(memberLabel("Notif", cfg.notifierIgnore.contains(name)),
					b -> toggle(cfg.notifierIgnore, name, b, "Notif"))
				.dimensions(cx - 48, y, 60, 20).build());
			addDrawableChild(ButtonWidget.builder(memberLabel("Prox", cfg.proximityIgnore.contains(name)),
					b -> toggle(cfg.proximityIgnore, name, b, "Prox"))
				.dimensions(cx + 14, y, 60, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.literal("X").formatted(Formatting.RED), b -> {
				cfg.notifierIgnore.remove(name);
				cfg.proximityIgnore.remove(name);
				ConfigManager.save();
				clearAndInit();
			}).dimensions(cx + 76, y, 78, 20).build());
			y += 24;
		}

		int navY = this.height - 52;
		if (page > 0) {
			addDrawableChild(ButtonWidget.builder(Text.literal("< Prev"), b -> {
				page--;
				clearAndInit();
			}).dimensions(cx - 154, navY, 70, 20).build());
		}
		if (start + PER_PAGE < names.size()) {
			addDrawableChild(ButtonWidget.builder(Text.literal("Next >"), b -> {
				page++;
				clearAndInit();
			}).dimensions(cx + 84, navY, 70, 20).build());
		}
		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
			.dimensions(cx - 60, this.height - 26, 120, 20).build());
	}

	private static List<String> allIgnored(Config cfg) {
		TreeSet<String> set = new TreeSet<>();
		set.addAll(cfg.notifierIgnore);
		set.addAll(cfg.proximityIgnore);
		return new ArrayList<>(set);
	}

	private void addPlayer() {
		String name = input.getText().trim().toLowerCase(Locale.ROOT);
		if (name.isEmpty()) {
			return;
		}
		Config cfg = ConfigManager.get();
		if (!cfg.notifierIgnore.contains(name)) {
			cfg.notifierIgnore.add(name);
		}
		if (!cfg.proximityIgnore.contains(name)) {
			cfg.proximityIgnore.add(name);
		}
		ConfigManager.save();
		input.setText("");
		clearAndInit();
	}

	private void toggle(List<String> list, String name, ButtonWidget button, String tag) {
		if (list.contains(name)) {
			list.remove(name);
		} else {
			list.add(name);
		}
		ConfigManager.save();
		button.setMessage(memberLabel(tag, list.contains(name)));
	}

	private static Text memberLabel(String tag, boolean on) {
		return Text.literal(tag + " ").append(Text.literal(on ? "✓" : "✗").formatted(on ? Formatting.GREEN : Formatting.RED));
	}

	private static String trim(String s, int max) {
		return s.length() > max ? s.substring(0, max - 1) + "…" : s;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFFFF);
		if (allIgnored(ConfigManager.get()).isEmpty()) {
			ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No ignored players — add one above.").formatted(Formatting.GRAY),
				this.width / 2, 70, 0xFFAAAAAA);
		}
	}

	@Override
	public void close() {
		ConfigManager.save();
		this.client.setScreen(parent);
	}
}
