package com.piccaxe.lsutils.gui;

import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;

/**
 * Generic add/remove editor for a {@code List<String>} (keywords, ignore lists, watchlists, patterns).
 * Paginated; edits the backing list in place and saves on every change.
 */
public class StringListScreen extends Screen {
	private static final int PER_PAGE = 7;

	private final Screen parent;
	private final List<String> list;
	private final boolean lowercase;
	private int page;
	private TextFieldWidget input;

	public StringListScreen(Screen parent, String title, List<String> list, boolean lowercase) {
		super(Text.literal(title));
		this.parent = parent;
		this.list = list;
		this.lowercase = lowercase;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		input = new TextFieldWidget(this.textRenderer, cx - 154, 34, 220, 20, Text.literal("entry"));
		input.setMaxLength(256);
		addDrawableChild(input);
		addDrawableChild(ButtonWidget.builder(Text.literal("+ Add"), b -> addEntry())
			.dimensions(cx + 70, 34, 84, 20).build());

		int start = page * PER_PAGE;
		int y = 64;
		for (int i = start; i < Math.min(start + PER_PAGE, list.size()); i++) {
			int idx = i;
			addDrawableChild(ButtonWidget.builder(Text.literal(trim(list.get(i), 40)), b -> {
			}).dimensions(cx - 154, y, 286, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.literal("X").formatted(Formatting.RED), b -> {
				list.remove(idx);
				ConfigManager.save();
				clearAndInit();
			}).dimensions(cx + 134, y, 20, 20).build());
			y += 24;
		}

		int navY = this.height - 52;
		if (page > 0) {
			addDrawableChild(ButtonWidget.builder(Text.literal("< Prev"), b -> {
				page--;
				clearAndInit();
			}).dimensions(cx - 154, navY, 70, 20).build());
		}
		if (start + PER_PAGE < list.size()) {
			addDrawableChild(ButtonWidget.builder(Text.literal("Next >"), b -> {
				page++;
				clearAndInit();
			}).dimensions(cx + 84, navY, 70, 20).build());
		}
		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
			.dimensions(cx - 60, this.height - 28, 120, 20).build());
	}

	private void addEntry() {
		String text = input.getText().trim();
		if (text.isEmpty()) {
			return;
		}
		if (lowercase) {
			text = text.toLowerCase(Locale.ROOT);
		}
		if (!list.contains(text)) {
			list.add(text);
			ConfigManager.save();
		}
		input.setText("");
		clearAndInit();
	}

	private static String trim(String s, int max) {
		return s.length() > max ? s.substring(0, max - 1) + "…" : s;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFFFF);
	}

	@Override
	public void close() {
		ConfigManager.save();
		this.client.setScreen(parent);
	}
}
