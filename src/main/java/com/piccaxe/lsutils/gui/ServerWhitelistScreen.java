package com.piccaxe.lsutils.gui;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

/**
 * Configures the per-server gate: turn the whitelist on/off, add the server you're currently on, edit
 * the address list, and see whether the mod is active here right now. When the gate is on, the mod only
 * runs on servers whose address contains one of the listed entries (singleplayer is always allowed).
 */
public class ServerWhitelistScreen extends Screen {
	private final Screen parent;

	public ServerWhitelistScreen(Screen parent) {
		super(Text.literal("Server Whitelist"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config cfg = ConfigManager.get();
		int cx = this.width / 2;

		addDrawableChild(ButtonWidget.builder(enableLabel(cfg), b -> {
			cfg.setWhitelistEnabled(!cfg.serverWhitelistEnabled);
			ConfigManager.save();
			this.clearAndInit();
		}).dimensions(cx - 154, 40, 200, 20).build());

		String here = currentAddress();
		ButtonWidget addHere = ButtonWidget.builder(Text.literal("Add current server"), b -> {
			String a = currentAddress();
			if (a != null) {
				String e = a.toLowerCase(Locale.ROOT);
				if (!cfg.serverWhitelist.contains(e)) {
					cfg.serverWhitelist.add(e);
					ConfigManager.save();
				}
				this.clearAndInit();
			}
		}).dimensions(cx + 50, 40, 104, 20).build();
		addHere.active = here != null;
		addDrawableChild(addHere);

		addDrawableChild(ButtonWidget.builder(Text.literal("Edit address list (" + cfg.serverWhitelist.size() + ")"),
			b -> this.client.setScreen(new StringListScreen(this, "Server Whitelist", cfg.serverWhitelist, true)))
			.dimensions(cx - 154, 66, 308, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.close())
			.dimensions(cx - 100, this.height - 28, 200, 20).build());
	}

	private static Text enableLabel(Config cfg) {
		boolean on = cfg.serverWhitelistEnabled;
		return Text.literal("Whitelist gate: ")
			.append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED));
	}

	private static String currentAddress() {
		MinecraftClient mc = MinecraftClient.getInstance();
		var info = mc.getCurrentServerEntry();
		return info != null ? info.address : null;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		Config cfg = ConfigManager.get();
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFFFF);

		String here = currentAddress();
		String hereText = here == null ? "Current: singleplayer / none" : "Current: " + here;
		ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(hereText).formatted(Formatting.GRAY),
			this.width / 2, this.height - 64, 0xFFAAAAAA);

		boolean activeHere = !cfg.serverWhitelistEnabled || cfg.serverAllowed();
		ctx.drawCenteredTextWithShadow(this.textRenderer,
			Text.literal(activeHere ? "Mod is ACTIVE here" : "Mod is DISABLED here (not whitelisted)")
				.formatted(activeHere ? Formatting.GREEN : Formatting.RED),
			this.width / 2, this.height - 50, 0xFFFFFFFF);
	}

	@Override
	public void close() {
		ConfigManager.save();
		if (this.client != null) {
			this.client.setScreen(parent);
		}
	}
}
