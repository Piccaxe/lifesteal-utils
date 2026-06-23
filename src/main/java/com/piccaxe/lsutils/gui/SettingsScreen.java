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
		new Toggle("Coordinates", c -> c.coordsHud, (c, v) -> c.coordsHud = v),
		new Toggle("Death Waypoint", c -> c.deathWaypoint, (c, v) -> c.deathWaypoint = v),
		new Toggle("Auto-Reconnect", c -> c.autoReconnect, (c, v) -> c.autoReconnect = v),
		new Toggle("Fullbright", c -> c.fullbright, (c, v) -> c.fullbright = v),
		new Toggle("No Hurt-Cam", c -> c.noHurtCam, (c, v) -> c.noHurtCam = v)
	);

	private final Screen parent;

	public SettingsScreen(Screen parent) {
		super(Text.translatable("piccaxelsutils.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config cfg = ConfigManager.get();
		int colWidth = 150;
		int buttonHeight = 20;
		int vGap = 24;
		int leftX = this.width / 2 - colWidth - 5;
		int rightX = this.width / 2 + 5;
		int startY = 40;

		for (int i = 0; i < TOGGLES.size(); i++) {
			Toggle toggle = TOGGLES.get(i);
			int x = (i % 2 == 0) ? leftX : rightX;
			int y = startY + (i / 2) * vGap;
			addToggleButton(toggle, cfg, x, y, colWidth, buttonHeight);
		}

		int doneY = startY + ((TOGGLES.size() + 1) / 2) * vGap + 8;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> this.close())
			.dimensions(this.width / 2 - 100, doneY, 200, buttonHeight).build());
	}

	private void addToggleButton(Toggle toggle, Config cfg, int x, int y, int width, int height) {
		ButtonWidget button = ButtonWidget.builder(labelFor(toggle.label(), toggle.get().test(cfg)), b -> {
			boolean newValue = !toggle.get().test(cfg);
			toggle.set().accept(cfg, newValue);
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
