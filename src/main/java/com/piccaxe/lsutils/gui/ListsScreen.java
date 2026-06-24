package com.piccaxe.lsutils.gui;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Hub linking to the per-list editors (keywords, ignore lists, watchlists, chat patterns).
 */
public class ListsScreen extends Screen {
	private final Screen parent;

	public ListsScreen(Screen parent) {
		super(Text.literal("Lists"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config cfg = ConfigManager.get();
		int cx = this.width / 2;
		int y = 40;
		y = listButton(cx, y, "Relay keywords", cfg.keywords, false);
		y = listButton(cx, y, "Notifier ignore", cfg.notifierIgnore, true);
		y = listButton(cx, y, "Proximity ignore", cfg.proximityIgnore, true);
		y = listButton(cx, y, "Proximity watchlist", cfg.proximityWatchlist, true);
		y = listButton(cx, y, "Whisper patterns", cfg.whisperPatterns, false);
		y = listButton(cx, y, "Team-chat patterns", cfg.teamChatPatterns, false);

		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
			.dimensions(cx - 100, this.height - 28, 200, 20).build());
	}

	private int listButton(int cx, int y, String label, java.util.List<String> list, boolean lowercase) {
		addDrawableChild(ButtonWidget.builder(Text.literal(label + "  (" + list.size() + ")"),
				b -> this.client.setScreen(new StringListScreen(this, label, list, lowercase)))
			.dimensions(cx - 150, y, 300, 20).build());
		return y + 24;
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
