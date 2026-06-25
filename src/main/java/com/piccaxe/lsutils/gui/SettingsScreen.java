package com.piccaxe.lsutils.gui;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Simple two-column toggle screen for every feature. Saves on close.
 * Opened via the keybind (default U) or {@code /lsutils settings}.
 */
public class SettingsScreen extends Screen {
	private record Toggle(String label, Predicate<Config> get, BiConsumer<Config, Boolean> set) {
	}

	private static final List<Toggle> TOGGLES = List.of(
		new Toggle("All Features", c -> c.masterEnabled, (c, v) -> c.masterEnabled = v),
		new Toggle("Heart HUD", c -> c.heartHud, (c, v) -> c.heartHud = v),
		new Toggle("Totem Counter", c -> c.totemHud, (c, v) -> c.totemHud = v),
		new Toggle("Proximity Alert", c -> c.proximityAlert, (c, v) -> c.proximityAlert = v),
		new Toggle("Player Notifier", c -> c.playerNotifier, (c, v) -> c.playerNotifier = v),
		new Toggle("Health Bars", c -> c.healthBars, (c, v) -> c.healthBars = v),
		new Toggle("Coordinates", c -> c.coordsHud, (c, v) -> c.coordsHud = v),
		new Toggle("Compass HUD", c -> c.directionHud, (c, v) -> c.directionHud = v),
		new Toggle("Death Waypoint", c -> c.deathWaypoint, (c, v) -> c.deathWaypoint = v),
		new Toggle("Auto-Reconnect", c -> c.autoReconnect, (c, v) -> c.autoReconnect = v),
		new Toggle("Fullbright", c -> c.fullbright, (c, v) -> c.fullbright = v),
		new Toggle("No Hurt-Cam", c -> c.noHurtCam, (c, v) -> c.noHurtCam = v),
		new Toggle("No Fog: Water", c -> c.noFogWater, (c, v) -> c.noFogWater = v),
		new Toggle("No Fog: Lava", c -> c.noFogLava, (c, v) -> c.noFogLava = v),
		new Toggle("No Fog: Biome", c -> c.noFogBiome, (c, v) -> c.noFogBiome = v),
		new Toggle("Anti-Trickster", c -> c.antiTrickster, (c, v) -> c.antiTrickster = v),
		new Toggle("Anti-Sign", c -> c.antiSign, (c, v) -> c.antiSign = v),
		new Toggle("Armor Stand Bypass", c -> c.armorStandBypass, (c, v) -> c.armorStandBypass = v),
		new Toggle("Nether Portal Bypass", c -> c.netherPortalBypass, (c, v) -> c.netherPortalBypass = v),
		new Toggle("Player Outliner", c -> c.playerOutliner, (c, v) -> c.playerOutliner = v),
		new Toggle("Loot Chests", c -> c.enderChestOutliner, (c, v) -> c.enderChestOutliner = v),
		new Toggle("Discord Relay", c -> c.discordRelay, (c, v) -> c.discordRelay = v)
	);

	private final Screen parent;

	public SettingsScreen(Screen parent) {
		super(Text.translatable("piccaxelsutils.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config cfg = ConfigManager.get();
		int cols = 3;
		int colWidth = 100;
		int gap = 4;
		int bh = 18;
		int vGap = 21;
		int totalWidth = cols * colWidth + (cols - 1) * gap;
		int baseX = this.width / 2 - totalWidth / 2;
		int startY = 32;

		for (int i = 0; i < TOGGLES.size(); i++) {
			Toggle toggle = TOGGLES.get(i);
			int x = baseX + (i % cols) * (colWidth + gap);
			int y = startY + (i / cols) * vGap;
			addToggleButton(toggle, cfg, x, y, colWidth, bh);
		}

		int rows = (TOGGLES.size() + cols - 1) / cols;
		int navY = startY + rows * vGap + 8;
		int cx = this.width / 2;
		addDrawableChild(ButtonWidget.builder(Text.literal("HUD Editor"), b -> this.client.setScreen(new HudEditScreen(this)))
			.dimensions(cx - 152, navY, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Webhooks"), b -> this.client.setScreen(new WebhookListScreen(this)))
			.dimensions(cx - 50, navY, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Tuning & Lists"), b -> this.client.setScreen(new TuningScreen(this)))
			.dimensions(cx + 52, navY, 100, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> this.close())
			.dimensions(cx - 100, navY + 24, 200, 20).build());
	}

	private void addToggleButton(Toggle toggle, Config cfg, int x, int y, int width, int height) {
		ButtonWidget button = ButtonWidget.builder(labelFor(toggle.label(), toggle.get().test(cfg)), b -> {
			boolean newValue = !toggle.get().test(cfg);
			toggle.set().accept(cfg, newValue);
			ConfigManager.save();
			b.setMessage(labelFor(toggle.label(), newValue));
		}).dimensions(x, y, width, height).build();
		addDrawableChild(button);
	}

	private static Text labelFor(String label, boolean on) {
		return Text.literal(label + ": ")
			.append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFFFFFFF);
	}

	@Override
	public void close() {
		ConfigManager.save();
		if (this.client != null) {
			this.client.setScreen(parent);
		}
	}
}
