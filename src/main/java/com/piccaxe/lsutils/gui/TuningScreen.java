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
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Numeric tuning (radii / thresholds / reconnect) plus the sub-option toggles
 * (relay filters, notifier channels, proximity sound, etc.), and a link to the list editors.
 */
public class TuningScreen extends Screen {
	private record Num(String label, java.util.function.Function<Config, String> get, BiConsumer<Config, String> set) {
	}

	private record Toggle(String label, Predicate<Config> get, BiConsumer<Config, Boolean> set) {
	}

	private static final List<Num> NUMS = List.of(
		new Num("Proximity radius", c -> str(c.proximityRadius), (c, v) -> c.proximityRadius = dbl(v, c.proximityRadius)),
		new Num("Loot chest radius", c -> str(c.enderChestRadius), (c, v) -> c.enderChestRadius = intv(v, c.enderChestRadius)),
		new Num("Health bar range", c -> str(c.healthBarRange), (c, v) -> c.healthBarRange = dbl(v, c.healthBarRange)),
		new Num("Totem warn at <=", c -> str(c.totemWarnThreshold), (c, v) -> c.totemWarnThreshold = intv(v, c.totemWarnThreshold)),
		new Num("Reconnect delay (s)", c -> str(c.autoReconnectDelaySeconds), (c, v) -> c.autoReconnectDelaySeconds = intv(v, c.autoReconnectDelaySeconds)),
		new Num("Reconnect max tries", c -> str(c.autoReconnectMaxAttempts), (c, v) -> c.autoReconnectMaxAttempts = intv(v, c.autoReconnectMaxAttempts))
	);

	private static final List<Toggle> TOGGLES = List.of(
		new Toggle("Proximity sound", c -> c.proximitySound, (c, v) -> c.proximitySound = v),
		new Toggle("Health: players only", c -> c.healthBarPlayersOnly, (c, v) -> c.healthBarPlayersOnly = v),
		new Toggle("Health: dmg estimate", c -> c.healthBarDamageEstimate, (c, v) -> c.healthBarDamageEstimate = v),
		new Toggle("Relay: team", c -> c.relayTeamChat, (c, v) -> c.relayTeamChat = v),
		new Toggle("Relay: whispers", c -> c.relayWhispers, (c, v) -> c.relayWhispers = v),
		new Toggle("Relay: mentions", c -> c.relayMentions, (c, v) -> c.relayMentions = v),
		new Toggle("Relay: keywords", c -> c.relayKeywords, (c, v) -> c.relayKeywords = v),
		new Toggle("Notifier: chat", c -> c.notifierChat, (c, v) -> c.notifierChat = v),
		new Toggle("Notifier: sound", c -> c.notifierSound, (c, v) -> c.notifierSound = v),
		new Toggle("Notifier: banner", c -> c.notifierBanner, (c, v) -> c.notifierBanner = v),
		new Toggle("Notifier: discord", c -> c.notifierDiscord, (c, v) -> c.notifierDiscord = v),
		new Toggle("Proximity: discord", c -> c.proximityDiscord, (c, v) -> c.proximityDiscord = v)
	);

	private final Screen parent;
	private final List<TextFieldWidget> fields = new ArrayList<>();

	public TuningScreen(Screen parent) {
		super(Text.literal("Tuning"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config cfg = ConfigManager.get();
		fields.clear();
		int leftX = this.width / 2 - 158;
		int rightX = this.width / 2 + 8;

		int y = 44;
		for (Num n : NUMS) {
			TextFieldWidget f = new TextFieldWidget(this.textRenderer, leftX, y, 60, 18, Text.literal(n.label()));
			f.setText(n.get().apply(cfg));
			addDrawableChild(f);
			fields.add(f);
			y += 26;
		}

		int ty = 30;
		for (Toggle t : TOGGLES) {
			boolean[] state = {t.get().test(cfg)};
			addDrawableChild(ButtonWidget.builder(toggleLabel(t.label(), state[0]), b -> {
				state[0] = !state[0];
				t.set().accept(ConfigManager.get(), state[0]);
				ConfigManager.save();
				b.setMessage(toggleLabel(t.label(), state[0]));
			}).dimensions(rightX, ty, 150, 18).build());
			ty += 20;
		}

		int by = this.height - 28;
		int cx = this.width / 2;
		addDrawableChild(ButtonWidget.builder(Text.literal("Lists…"), b -> {
			saveNumbers();
			this.client.setScreen(new ListsScreen(this));
		}).dimensions(cx - 154, by, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Armor Swap…"), b -> {
			saveNumbers();
			this.client.setScreen(new ArmorSwapScreen(this));
		}).dimensions(cx - 50, by, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close()).dimensions(cx + 54, by, 100, 20).build());
	}

	private void saveNumbers() {
		Config cfg = ConfigManager.get();
		for (int i = 0; i < NUMS.size() && i < fields.size(); i++) {
			NUMS.get(i).set().accept(cfg, fields.get(i).getText().trim());
		}
		ConfigManager.save();
	}

	private static Text toggleLabel(String label, boolean on) {
		return Text.literal(label + ": ").append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED));
	}

	private static String str(double d) {
		return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d);
	}

	private static String str(int i) {
		return String.valueOf(i);
	}

	private static double dbl(String s, double fallback) {
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static int intv(String s, int fallback) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFFFF);
		int leftX = this.width / 2 - 158;
		int y = 44;
		for (Num n : NUMS) {
			ctx.drawTextWithShadow(this.textRenderer, Text.literal(n.label()), leftX + 64, y + 5, 0xFFCCCCCC);
			y += 26;
		}
	}

	@Override
	public void close() {
		saveNumbers();
		this.client.setScreen(parent);
	}
}
