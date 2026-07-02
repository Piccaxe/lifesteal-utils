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

import static com.piccaxe.lsutils.gui.ModuleSettingsScreen.b;
import static com.piccaxe.lsutils.gui.ModuleSettingsScreen.color;
import static com.piccaxe.lsutils.gui.ModuleSettingsScreen.d;
import static com.piccaxe.lsutils.gui.ModuleSettingsScreen.i;
import static com.piccaxe.lsutils.gui.ModuleSettingsScreen.str;

/**
 * Tabbed settings screen: modules grouped into categories (Combat, Visual, HUD, ESP, Utility, Chat).
 * Each module row has an ON/OFF toggle, plus a "…" button opening its settings — either a dedicated
 * screen or a generic {@link ModuleSettingsScreen} built from the module's settings list. Global tools
 * stay pinned at the bottom. Saves on close. Opened via the keybind (default U) or {@code /piccaxeutils}.
 */
public class SettingsScreen extends Screen {
	private static final class Module {
		final String label;
		final Predicate<Config> get;
		final BiConsumer<Config, Boolean> set;
		final Function<Screen, Screen> screen;
		final List<ModuleSettingsScreen.Setting> settingsList;

		Module(String label, Predicate<Config> get, BiConsumer<Config, Boolean> set,
			   Function<Screen, Screen> screen, List<ModuleSettingsScreen.Setting> settingsList) {
			this.label = label;
			this.get = get;
			this.set = set;
			this.screen = screen;
			this.settingsList = settingsList;
		}

		boolean hasSettings() {
			return screen != null || (settingsList != null && !settingsList.isEmpty());
		}
	}

	private record Category(String name, List<Module> modules) {
	}

	private static Module m(String label, Predicate<Config> get, BiConsumer<Config, Boolean> set) {
		return new Module(label, get, set, null, null);
	}

	private static Module m(String label, Predicate<Config> get, BiConsumer<Config, Boolean> set, Function<Screen, Screen> screen) {
		return new Module(label, get, set, screen, null);
	}

	private static Module m(String label, Predicate<Config> get, BiConsumer<Config, Boolean> set, List<ModuleSettingsScreen.Setting> list) {
		return new Module(label, get, set, null, list);
	}

	private static final List<Category> CATEGORIES = List.of(
		new Category("Combat", List.of(
			m("Auto-Totem", c -> c.autoTotem, (c, v) -> c.autoTotem = v),
			m("Auto-Eat", c -> c.autoEat, (c, v) -> c.autoEat = v, List.of(
				d("Auto-eat HP", () -> g().autoEatHp, v -> g().autoEatHp = v, 1, 20, 1),
				str("Food keyword", () -> g().autoEatKeyword, v -> g().autoEatKeyword = v))),
			m("Armor Swapper", c -> c.armorSwapper, (c, v) -> c.armorSwapper = v, ArmorSwapScreen::new),
			m("Auto-Tool", c -> c.autoTool, (c, v) -> c.autoTool = v),
			m("Hitmarkers", c -> c.hitMarkers, (c, v) -> c.hitMarkers = v, List.of(
				b("Hit sound", () -> g().hitMarkerSound, v -> g().hitMarkerSound = v))),
			m("Combat Tag HUD", c -> c.combatTag, (c, v) -> c.combatTag = v, List.of(
				i("Tag seconds", () -> g().combatTagSeconds, v -> g().combatTagSeconds = v, 1, 120, 1))),
			m("Anti-Trickster", c -> c.antiTrickster, (c, v) -> c.antiTrickster = v, List.of(
				i("Min items moved", () -> g().antiTricksterMinItems, v -> g().antiTricksterMinItems = v, 2, 9, 1),
				b("Debug", () -> g().antiTricksterDebug, v -> g().antiTricksterDebug = v))),
			m("Auto-Clip", c -> c.autoClip, (c, v) -> c.autoClip = v, List.of(
				b("On kill", () -> g().autoClipOnKill, v -> g().autoClipOnKill = v),
				b("On death", () -> g().autoClipOnDeath, v -> g().autoClipOnDeath = v),
				i("Cooldown (s)", () -> g().clipCooldownSeconds, v -> g().clipCooldownSeconds = v, 0, 60, 1),
				str("Hotkey", () -> g().clipHotkey, v -> g().clipHotkey = v))),
			m("Screenshot on Kill", c -> c.screenshotOnKill, (c, v) -> c.screenshotOnKill = v),
			m("Low-HP Alert", c -> c.lowHpAlert, (c, v) -> c.lowHpAlert = v, List.of(
				d("Threshold HP", () -> g().lowHpThreshold, v -> g().lowHpThreshold = v, 1, 20, 1),
				b("Sound", () -> g().lowHpSound, v -> g().lowHpSound = v))),
			m("Potion Warning", c -> c.potionWarn, (c, v) -> c.potionWarn = v, List.of(
				i("Warn at (s)", () -> g().potionWarnSeconds, v -> g().potionWarnSeconds = v, 1, 120, 1),
				i("Sound at (s)", () -> g().potionSoundSeconds, v -> g().potionSoundSeconds = v, 1, 120, 1))),
			m("Durability Notifier", c -> c.durabilityWarn, (c, v) -> c.durabilityWarn = v, List.of(
				i("Warn at (%)", () -> g().durabilityThreshold, v -> g().durabilityThreshold = v, 1, 99, 1),
				b("Sound", () -> g().durabilityWarnSound, v -> g().durabilityWarnSound = v)))
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
			m("Health Indicators", c -> c.healthBars, (c, v) -> c.healthBars = v, List.of(
				d("Range", () -> g().healthBarRange, v -> g().healthBarRange = v, 4, 64, 2),
				b("Players only", () -> g().healthBarPlayersOnly, v -> g().healthBarPlayersOnly = v),
				b("Screen list", () -> g().healthBarList, v -> g().healthBarList = v),
				b("Over heads", () -> g().healthBarOverhead, v -> g().healthBarOverhead = v),
				b("Heart icons", () -> g().healthBarHearts, v -> g().healthBarHearts = v),
				b("Damage estimate", () -> g().healthBarDamageEstimate, v -> g().healthBarDamageEstimate = v))),
			m("Coordinates", c -> c.coordsHud, (c, v) -> c.coordsHud = v),
			m("Compass HUD", c -> c.directionHud, (c, v) -> c.directionHud = v, CompassScreen::new),
			m("Potion HUD", c -> c.potionHud, (c, v) -> c.potionHud = v),
			m("Inventory HUD", c -> c.inventoryHud, (c, v) -> c.inventoryHud = v),
			m("Armor HUD", c -> c.armorHud, (c, v) -> c.armorHud = v),
			m("FPS / Ping HUD", c -> c.fpsHud, (c, v) -> c.fpsHud = v),
			m("Music Overlay", c -> c.musicOverlay, (c, v) -> c.musicOverlay = v),
			m("Cooldown HUD", c -> c.cooldownHud, (c, v) -> c.cooldownHud = v, List.of(
				b("Ready sound", () -> g().cooldownReadySound, v -> g().cooldownReadySound = v))),
			m("Hotbar Key Labels", c -> c.hotbarKeyLabels, (c, v) -> c.hotbarKeyLabels = v)
		)),
		new Category("ESP", List.of(
			m("Player Outliner", c -> c.playerOutliner, (c, v) -> c.playerOutliner = v, List.of(
				color("Teammate color", () -> g().outlineColorTeammate, v -> g().outlineColorTeammate = v),
				color("Ally color", () -> g().outlineColorAlly, v -> g().outlineColorAlly = v),
				color("Enemy color", () -> g().outlineColorEnemy, v -> g().outlineColorEnemy = v))),
			m("Loot Chests", c -> c.enderChestOutliner, (c, v) -> c.enderChestOutliner = v, List.of(
				i("Radius", () -> g().enderChestRadius, v -> g().enderChestRadius = v, 8, 96, 4),
				color("Color", () -> g().enderChestColor, v -> g().enderChestColor = v),
				b("Distance label", () -> g().enderChestDistanceLabel, v -> g().enderChestDistanceLabel = v),
				b("Tracer", () -> g().enderChestTracer, v -> g().enderChestTracer = v),
				b("Path tracer", () -> g().enderChestPathTracer, v -> g().enderChestPathTracer = v))),
			m("Trap Outlines", c -> c.trapOutliner, (c, v) -> c.trapOutliner = v, List.of(
				i("Radius", () -> g().trapRadius, v -> g().trapRadius = v, 4, 64, 2),
				color("Color", () -> g().trapColor, v -> g().trapColor = v))),
			m("Spawner ESP", c -> c.spawnerOutliner, (c, v) -> c.spawnerOutliner = v, List.of(
				i("Activation range", () -> g().spawnerRange, v -> g().spawnerRange = v, 1, 64, 1),
				i("Scan radius", () -> g().spawnerScanRadius, v -> g().spawnerScanRadius = v, 8, 96, 4),
				color("Active color", () -> g().spawnerActiveColor, v -> g().spawnerActiveColor = v),
				color("Idle color", () -> g().spawnerInactiveColor, v -> g().spawnerInactiveColor = v)))
		)),
		new Category("Utility", List.of(
			m("Auto-Reconnect", c -> c.autoReconnect, (c, v) -> c.autoReconnect = v, List.of(
				i("Delay (s)", () -> g().autoReconnectDelaySeconds, v -> g().autoReconnectDelaySeconds = v, 1, 60, 1),
				i("Max attempts", () -> g().autoReconnectMaxAttempts, v -> g().autoReconnectMaxAttempts = v, 0, 100, 1))),
			m("Death Waypoint", c -> c.deathWaypoint, (c, v) -> c.deathWaypoint = v),
			m("Anti-Sign", c -> c.antiSign, (c, v) -> c.antiSign = v),
			m("Armor Stand Bypass", c -> c.armorStandBypass, (c, v) -> c.armorStandBypass = v),
			m("Nether Portal Bypass", c -> c.netherPortalBypass, (c, v) -> c.netherPortalBypass = v),
			m("Player Notifier", c -> c.playerNotifier, (c, v) -> c.playerNotifier = v, List.of(
				b("Chat", () -> g().notifierChat, v -> g().notifierChat = v),
				b("Sound", () -> g().notifierSound, v -> g().notifierSound = v),
				b("Banner", () -> g().notifierBanner, v -> g().notifierBanner = v),
				b("Discord", () -> g().notifierDiscord, v -> g().notifierDiscord = v),
				i("Re-alert cooldown (s)", () -> g().notifierCooldownSeconds, v -> g().notifierCooldownSeconds = v, 0, 600, 5))),
			m("Proximity Alert", c -> c.proximityAlert, (c, v) -> c.proximityAlert = v, IgnoredPlayersScreen::new)
		)),
		new Category("Chat", List.of(
			m("Discord Relay", c -> c.discordRelay, (c, v) -> c.discordRelay = v, WebhookListScreen::new),
			m("Death/Kill Relay", c -> c.deathKillRelay, (c, v) -> c.deathKillRelay = v, List.of(
				b("Relay my deaths", () -> g().relayMyDeaths, v -> g().relayMyDeaths = v),
				b("Relay my kills", () -> g().relayMyKills, v -> g().relayMyKills = v))),
			m("Chat Timestamps", c -> c.chatTimestamps, (c, v) -> c.chatTimestamps = v),
			m("Chat Anti-Spam", c -> c.chatAntiSpam, (c, v) -> c.chatAntiSpam = v)
		))
	);

	private static Config g() {
		return ConfigManager.get();
	}

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

		addDrawableChild(masterButton(cfg, this.width - 96, 8, 88, 16));
		addDrawableChild(ButtonWidget.builder(
			Text.literal("Servers").formatted(cfg.serverWhitelistEnabled ? Formatting.GREEN : Formatting.GRAY),
			b -> this.client.setScreen(new ServerWhitelistScreen(this)))
			.dimensions(8, 8, 70, 16).build());

		int tabAreaW = Math.min(this.width - 16, 480);
		int tabW = tabAreaW / CATEGORIES.size();
		int tabX = cx - tabAreaW / 2;
		int tabY = 30;
		for (int i = 0; i < CATEGORIES.size(); i++) {
			final int idx = i;
			boolean sel = i == activeTab;
			Text label = Text.literal(CATEGORIES.get(i).name()).formatted(sel ? Formatting.YELLOW : Formatting.GRAY);
			addDrawableChild(ButtonWidget.builder(label, btn -> {
				activeTab = idx;
				this.clearAndInit();
			}).dimensions(tabX + i * tabW, tabY, tabW - 2, 18).build());
		}

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
			int x = baseX + (i % cols) * (cellW + cellGap);
			int y = startY + (i / cols) * rowH;
			boolean hasSettings = mod.hasSettings();
			int toggleW = hasSettings ? cellW - 20 : cellW;

			addDrawableChild(ButtonWidget.builder(labelFor(mod.label, mod.get.test(cfg)), b -> {
				boolean nv = !mod.get.test(cfg);
				mod.set.accept(cfg, nv);
				ConfigManager.save();
				b.setMessage(labelFor(mod.label, nv));
			}).dimensions(x, y, toggleW, 18).build());

			if (hasSettings) {
				addDrawableChild(ButtonWidget.builder(Text.literal("..."), b -> openSettings(mod))
					.dimensions(x + cellW - 18, y, 18, 18).build());
			}
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

	private void openSettings(Module mod) {
		if (mod.screen != null) {
			this.client.setScreen(mod.screen.apply(this));
		} else if (mod.settingsList != null) {
			this.client.setScreen(new ModuleSettingsScreen(this, mod.label,
				() -> mod.get.test(ConfigManager.get()),
				v -> mod.set.accept(ConfigManager.get(), v),
				mod.settingsList));
		}
	}

	private ButtonWidget masterButton(Config cfg, int x, int y, int w, int h) {
		return ButtonWidget.builder(labelFor("All", cfg.masterIntent()), b -> {
			cfg.setMaster(!cfg.masterIntent());
			ConfigManager.save();
			b.setMessage(labelFor("All", cfg.masterIntent()));
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
