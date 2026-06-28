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
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Tabbed settings screen: modules grouped into categories (Combat, Visual, HUD, ESP, Utility, Chat).
 * Each module row has an ON/OFF toggle, plus a "…" button opening its dedicated settings screen when
 * it has one. Global tools (HUD editor, webhooks, tuning, compass) stay pinned at the bottom. Saves
 * on close. Opened via the keybind (default U) or {@code /piccaxeutils settings}.
 */
public class SettingsScreen extends Screen {
	private static final class Module {
		final String label;
		final Predicate<Config> get;
		final BiConsumer<Config, Boolean> set;
		final Function<Screen, Screen> settings;

		Module(String label, Predicate<Config> get, BiConsumer<Config, Boolean> set, Function<Screen, Screen> settings) {
			this.label = label;
			this.get = get;
			this.set = set;
			this.settings = settings;
		}
	}

	private record Category(String name, List<Module> modules) {
	}

	private static Module m(String label, Predicate<Config> get, BiConsumer<Config, Boolean> set) {
		return new Module(label, get, set, null);
	}

	private static Module m(String label, Predicate<Config> get, BiConsumer<Config, Boolean> set, Function<Screen, Screen> settings) {
		return new Module(label, get, set, settings);
	}

	private static final List<Category> CATEGORIES = List.of(
		new Category("Combat", List.of(
			m("Auto-Totem", c -> c.autoTotem, (c, v) -> c.autoTotem = v),
			m("Auto-Eat", c -> c.autoEat, (c, v) -> c.autoEat = v),
			m("Armor Swapper", c -> c.armorSwapper, (c, v) -> c.armorSwapper = v, ArmorSwapScreen::new),
			m("Auto-Tool", c -> c.autoTool, (c, v) -> c.autoTool = v),
			m("Hitmarkers", c -> c.hitMarkers, (c, v) -> c.hitMarkers = v),
			m("Combat Tag HUD", c -> c.combatTag, (c, v) -> c.combatTag = v),
			m("Anti-Trickster", c -> c.antiTrickster, (c, v) -> c.antiTrickster = v),
			m("Auto-Clip", c -> c.autoClip, (c, v) -> c.autoClip = v),
			m("Screenshot on Kill", c -> c.screenshotOnKill, (c, v) -> c.screenshotOnKill = v),
			m("Low-HP Alert", c -> c.lowHpAlert, (c, v) -> c.lowHpAlert = v),
			m("Potion Warning", c -> c.potionWarn, (c, v) -> c.potionWarn = v)
		)),
		new Category("Visual", List.of(
			m("Fullbright", c -> c.fullbright, (c, v) -> c.fullbright = v),
			m("No Hurt-Cam", c -> c.noHurtCam, (c, v) -> c.noHurtCam = v),
			m("No Fog: Water", c -> c.noFogWater, (c, v) -> c.noFogWater = v),
			m("No Fog: Lava", c -> c.noFogLava, (c, v) -> c.noFogLava = v),
			m("No Fog: Biome", c -> c.noFogBiome, (c, v) -> c.noFogBiome = v),
			m("Anti-Invis", c -> c.antiInvis, (c, v) -> c.antiInvis = v)
		)),
		new Category("HUD", List.of(
			m("Totem Counter", c -> c.totemHud, (c, v) -> c.totemHud = v),
			m("Health Bars", c -> c.healthBars, (c, v) -> c.healthBars = v),
			m("Coordinates", c -> c.coordsHud, (c, v) -> c.coordsHud = v),
			m("Compass HUD", c -> c.directionHud, (c, v) -> c.directionHud = v, CompassScreen::new),
			m("Potion HUD", c -> c.potionHud, (c, v) -> c.potionHud = v),
			m("Inventory HUD", c -> c.inventoryHud, (c, v) -> c.inventoryHud = v),
			m("Armor HUD", c -> c.armorHud, (c, v) -> c.armorHud = v),
			m("FPS / Ping HUD", c -> c.fpsHud, (c, v) -> c.fpsHud = v),
			m("Music Overlay", c -> c.musicOverlay, (c, v) -> c.musicOverlay = v),
			m("Cooldown HUD", c -> c.cooldownHud, (c, v) -> c.cooldownHud = v),
			m("Hotbar Key Labels", c -> c.hotbarKeyLabels, (c, v) -> c.hotbarKeyLabels = v)
		)),
		new Category("ESP", List.of(
			m("Player Outliner", c -> c.playerOutliner, (c, v) -> c.playerOutliner = v),
			m("Loot Chests", c -> c.enderChestOutliner, (c, v) -> c.enderChestOutliner = v),
			m("Trap Outlines", c -> c.trapOutliner, (c, v) -> c.trapOutliner = v),
			m("Spawner ESP", c -> c.spawnerOutliner, (c, v) -> c.spawnerOutliner = v)
		)),
		new Category("Utility", List.of(
			m("Auto-Reconnect", c -> c.autoReconnect, (c, v) -> c.autoReconnect = v),
			m("Death Waypoint", c -> c.deathWaypoint, (c, v) -> c.deathWaypoint = v),
			m("Anti-Sign", c -> c.antiSign, (c, v) -> c.antiSign = v),
			m("Armor Stand Bypass", c -> c.armorStandBypass, (c, v) -> c.armorStandBypass = v),
			m("Nether Portal Bypass", c -> c.netherPortalBypass, (c, v) -> c.netherPortalBypass = v),
			m("Player Notifier", c -> c.playerNotifier, (c, v) -> c.playerNotifier = v),
			m("Proximity Alert", c -> c.proximityAlert, (c, v) -> c.proximityAlert = v, IgnoredPlayersScreen::new)
		)),
		new Category("Chat", List.of(
			m("Discord Relay", c -> c.discordRelay, (c, v) -> c.discordRelay = v, WebhookListScreen::new),
			m("Death/Kill Relay", c -> c.deathKillRelay, (c, v) -> c.deathKillRelay = v),
			m("Chat Timestamps", c -> c.chatTimestamps, (c, v) -> c.chatTimestamps = v),
			m("Chat Anti-Spam", c -> c.chatAntiSpam, (c, v) -> c.chatAntiSpam = v),
			m("Cooldown HUD", c -> c.cooldownHud, (c, v) -> c.cooldownHud = v)
		))
	);

	private final Screen parent;
	private int activeTab = 0;

	public SettingsScreen(Screen parent) {
		super(Text.translatable("piccaxelsutils.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config cfg = ConfigManager.get();
		int cx = this.width / 2;

		// Master toggle, top-right.
		addDrawableChild(masterButton(cfg, this.width - 96, 8, 88, 16));

		// Tab bar.
		int tabAreaW = Math.min(this.width - 16, 480);
		int tabW = tabAreaW / CATEGORIES.size();
		int tabX = cx - tabAreaW / 2;
		int tabY = 30;
		for (int i = 0; i < CATEGORIES.size(); i++) {
			final int idx = i;
			boolean activeTabSel = i == activeTab;
			Text label = Text.literal(CATEGORIES.get(i).name())
				.formatted(activeTabSel ? Formatting.YELLOW : Formatting.GRAY);
			addDrawableChild(ButtonWidget.builder(label, b -> {
				activeTab = idx;
				this.clearAndInit();
			}).dimensions(tabX + i * tabW, tabY, tabW - 2, 18).build());
		}

		// Module grid for the active category, fit between tabs and the bottom nav.
		List<Module> modules = CATEGORIES.get(activeTab).modules();
		int startY = 54;
		int navY = this.height - 48;
		int doneY = this.height - 24;
		int rowH = 21;
		int available = Math.max(rowH, navY - 6 - startY);
		int cols = 2;
		while ((modules.size() + cols - 1) / cols * rowH > available && cols < 4) {
			cols++;
		}
		int gridW = Math.min(this.width - 16, 480);
		int cellGap = 4;
		int cellW = (gridW - (cols - 1) * cellGap) / cols;
		int baseX = cx - gridW / 2;

		for (int i = 0; i < modules.size(); i++) {
			Module mod = modules.get(i);
			int col = i % cols;
			int row = i / cols;
			int x = baseX + col * (cellW + cellGap);
			int y = startY + row * rowH;
			boolean hasSettings = mod.settings != null;
			int toggleW = hasSettings ? cellW - 20 : cellW;

			ButtonWidget toggle = ButtonWidget.builder(labelFor(mod.label, mod.get.test(cfg)), b -> {
				boolean nv = !mod.get.test(cfg);
				mod.set.accept(cfg, nv);
				ConfigManager.save();
				b.setMessage(labelFor(mod.label, nv));
			}).dimensions(x, y, toggleW, 18).build();
			addDrawableChild(toggle);

			if (hasSettings) {
				addDrawableChild(ButtonWidget.builder(Text.literal("..."), b -> this.client.setScreen(mod.settings.apply(this)))
					.dimensions(x + cellW - 18, y, 18, 18).build());
			}
		}

		// Bottom nav: global tools.
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

	private ButtonWidget masterButton(Config cfg, int x, int y, int w, int h) {
		return ButtonWidget.builder(labelFor("All", cfg.masterEnabled), b -> {
			cfg.masterEnabled = !cfg.masterEnabled;
			ConfigManager.save();
			b.setMessage(labelFor("All", cfg.masterEnabled));
		}).dimensions(x, y, w, h).build();
	}

	private static Text labelFor(String label, boolean on) {
		return Text.literal(label + ": ")
			.append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);
	}

	@Override
	public void close() {
		ConfigManager.save();
		if (this.client != null) {
			this.client.setScreen(parent);
		}
	}
}
