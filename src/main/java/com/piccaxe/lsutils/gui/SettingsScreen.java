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
		new Toggle("Totem Counter", c -> c.totemHud, (c, v) -> c.totemHud = v),
		new Toggle("Proximity Alert", c -> c.proximityAlert, (c, v) -> c.proximityAlert = v),
		new Toggle("Player Notifier", c -> c.playerNotifier, (c, v) -> c.playerNotifier = v),
		new Toggle("Health Bars", c -> c.healthBars, (c, v) -> c.healthBars = v),
		new Toggle("Coordinates", c -> c.coordsHud, (c, v) -> c.coordsHud = v),
		new Toggle("Compass HUD", c -> c.directionHud, (c, v) -> c.directionHud = v),
		new Toggle("Potion HUD", c -> c.potionHud, (c, v) -> c.potionHud = v),
		new Toggle("Inventory HUD", c -> c.inventoryHud, (c, v) -> c.inventoryHud = v),
		new Toggle("Death Waypoint", c -> c.deathWaypoint, (c, v) -> c.deathWaypoint = v),
		new Toggle("Auto-Reconnect", c -> c.autoReconnect, (c, v) -> c.autoReconnect = v),
		new Toggle("Fullbright", c -> c.fullbright, (c, v) -> c.fullbright = v),
		new Toggle("No Hurt-Cam", c -> c.noHurtCam, (c, v) -> c.noHurtCam = v),
		new Toggle("No Fog: Water", c -> c.noFogWater, (c, v) -> c.noFogWater = v),
		new Toggle("No Fog: Lava", c -> c.noFogLava, (c, v) -> c.noFogLava = v),
		new Toggle("No Fog: Biome", c -> c.noFogBiome, (c, v) -> c.noFogBiome = v),
		new Toggle("Anti-Invis", c -> c.antiInvis, (c, v) -> c.antiInvis = v),
		new Toggle("Anti-Trickster", c -> c.antiTrickster, (c, v) -> c.antiTrickster = v),
		new Toggle("Armor Swapper", c -> c.armorSwapper, (c, v) -> c.armorSwapper = v),
		new Toggle("Anti-Sign", c -> c.antiSign, (c, v) -> c.antiSign = v),
		new Toggle("Armor Stand Bypass", c -> c.armorStandBypass, (c, v) -> c.armorStandBypass = v),
		new Toggle("Nether Portal Bypass", c -> c.netherPortalBypass, (c, v) -> c.netherPortalBypass = v),
		new Toggle("Player Outliner", c -> c.playerOutliner, (c, v) -> c.playerOutliner = v),
		new Toggle("Loot Chests", c -> c.enderChestOutliner, (c, v) -> c.enderChestOutliner = v),
		new Toggle("Trap Outlines", c -> c.trapOutliner, (c, v) -> c.trapOutliner = v),
		new Toggle("Discord Relay", c -> c.discordRelay, (c, v) -> c.discordRelay = v),
		new Toggle("Death/Kill Relay", c -> c.deathKillRelay, (c, v) -> c.deathKillRelay = v),
		new Toggle("Auto-Clip", c -> c.autoClip, (c, v) -> c.autoClip = v),
		new Toggle("Auto-Totem", c -> c.autoTotem, (c, v) -> c.autoTotem = v),
		new Toggle("Auto-Tool", c -> c.autoTool, (c, v) -> c.autoTool = v),
		new Toggle("Hitmarkers", c -> c.hitMarkers, (c, v) -> c.hitMarkers = v),
		new Toggle("Combat Tag HUD", c -> c.combatTag, (c, v) -> c.combatTag = v),
		new Toggle("Armor HUD", c -> c.armorHud, (c, v) -> c.armorHud = v),
		new Toggle("FPS / Ping HUD", c -> c.fpsHud, (c, v) -> c.fpsHud = v),
		new Toggle("Auto-Eat", c -> c.autoEat, (c, v) -> c.autoEat = v),
		new Toggle("Screenshot on Kill", c -> c.screenshotOnKill, (c, v) -> c.screenshotOnKill = v),
		new Toggle("Chat Timestamps", c -> c.chatTimestamps, (c, v) -> c.chatTimestamps = v),
		new Toggle("Chat Anti-Spam", c -> c.chatAntiSpam, (c, v) -> c.chatAntiSpam = v)
	);

	private final Screen parent;

	public SettingsScreen(Screen parent) {
		super(Text.translatable("piccaxelsutils.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config cfg = ConfigManager.get();
		int cx = this.width / 2;
		int gap = 4;
		int bh = 16;
		int vGap = 19;
		int startY = 30;

		// Pin the nav + Done to the bottom so they're always reachable, then fit the toggle grid above
		// them — adding columns as needed so it never pushes the nav off-screen (any GUI scale).
		int navY = this.height - 50;
		int doneY = this.height - 26;
		int available = Math.max(vGap, navY - 8 - startY);
		int maxRows = Math.max(1, available / vGap);
		int cols = Math.max(3, (TOGGLES.size() + maxRows - 1) / maxRows);
		int colWidth = Math.max(58, Math.min(100, (Math.min(this.width - 12, 500) - (cols - 1) * gap) / cols));
		int totalWidth = cols * colWidth + (cols - 1) * gap;
		int baseX = cx - totalWidth / 2;

		for (int i = 0; i < TOGGLES.size(); i++) {
			Toggle toggle = TOGGLES.get(i);
			int x = baseX + (i % cols) * (colWidth + gap);
			int y = startY + (i / cols) * vGap;
			addToggleButton(toggle, cfg, x, y, colWidth, bh);
		}

		addDrawableChild(ButtonWidget.builder(Text.literal("HUD Editor"), b -> this.client.setScreen(new HudEditScreen(this)))
			.dimensions(cx - 154, navY, 74, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Webhooks"), b -> this.client.setScreen(new WebhookListScreen(this)))
			.dimensions(cx - 78, navY, 74, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Tuning"), b -> this.client.setScreen(new TuningScreen(this)))
			.dimensions(cx - 2, navY, 74, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Compass"), b -> this.client.setScreen(new CompassScreen(this)))
			.dimensions(cx + 74, navY, 74, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> this.close())
			.dimensions(cx - 100, doneY, 200, 20).build());
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
