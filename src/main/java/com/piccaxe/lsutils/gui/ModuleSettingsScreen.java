package com.piccaxe.lsutils.gui;

import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Generic per-module settings panel: an enable/disable toggle plus a paged list of typed controls
 * (steppers for numbers, toggles for booleans, text fields for strings/colors). Every change saves
 * the config immediately. Modules describe their settings as a {@link Setting} list — see
 * {@link SettingsScreen} for the wiring.
 */
public final class ModuleSettingsScreen extends Screen {

	public sealed interface Setting permits IntSetting, DoubleSetting, BoolSetting, StringSetting {
	}

	public record IntSetting(String label, IntSupplier get, IntConsumer set, int min, int max, int step) implements Setting {
	}

	public record DoubleSetting(String label, DoubleSupplier get, DoubleConsumer set, double min, double max, double step) implements Setting {
	}

	public record BoolSetting(String label, BooleanSupplier get, Consumer<Boolean> set) implements Setting {
	}

	public record StringSetting(String label, Supplier<String> get, Consumer<String> set) implements Setting {
	}

	public static Setting i(String label, IntSupplier get, IntConsumer set, int min, int max, int step) {
		return new IntSetting(label, get, set, min, max, step);
	}

	public static Setting d(String label, DoubleSupplier get, DoubleConsumer set, double min, double max, double step) {
		return new DoubleSetting(label, get, set, min, max, step);
	}

	public static Setting b(String label, BooleanSupplier get, Consumer<Boolean> set) {
		return new BoolSetting(label, get, set);
	}

	public static Setting str(String label, Supplier<String> get, Consumer<String> set) {
		return new StringSetting(label, get, set);
	}

	/** A hex-colour control (RRGGBB text field). */
	public static Setting color(String label, IntSupplier get, IntConsumer set) {
		return new StringSetting(label,
			() -> String.format("%06X", get.getAsInt() & 0xFFFFFF),
			s -> {
				try {
					set.accept(Integer.parseInt(s.replace("#", "").trim(), 16) & 0xFFFFFF);
				} catch (NumberFormatException ignored) {
					// leave unchanged on a bad value
				}
			});
	}

	private final Screen parent;
	private final String moduleTitle;
	private final BooleanSupplier enableGet;
	private final Consumer<Boolean> enableSet;
	private final List<Setting> settings;
	private int page;
	private final List<int[]> rowLayout = new ArrayList<>(); // {settingIndex, y} for render labels

	public ModuleSettingsScreen(Screen parent, String moduleTitle, BooleanSupplier enableGet,
							   Consumer<Boolean> enableSet, List<Setting> settings) {
		super(Text.literal(moduleTitle));
		this.parent = parent;
		this.moduleTitle = moduleTitle;
		this.enableGet = enableGet;
		this.enableSet = enableSet;
		this.settings = settings;
	}

	@Override
	protected void init() {
		rowLayout.clear();
		int cx = this.width / 2;

		addDrawableChild(ButtonWidget.builder(enableLabel(), b -> {
			enableSet.accept(!enableGet.getAsBoolean());
			ConfigManager.save();
			b.setMessage(enableLabel());
		}).dimensions(cx - 100, 28, 200, 20).build());

		int rowsTop = 56;
		int rowH = 24;
		int doneY = this.height - 28;
		int pageY = this.height - 52;
		int panelW = Math.min(this.width - 20, 300);
		int x0 = cx - panelW / 2;

		int capacity = Math.max(1, (pageY - 6 - rowsTop) / rowH);
		int pageCount = Math.max(1, (settings.size() + capacity - 1) / capacity);
		page = Math.max(0, Math.min(page, pageCount - 1));
		int from = page * capacity;
		int to = Math.min(settings.size(), from + capacity);

		for (int idx = from; idx < to; idx++) {
			Setting s = settings.get(idx);
			int y = rowsTop + (idx - from) * rowH;
			rowLayout.add(new int[]{idx, y});

			if (s instanceof BoolSetting bs) {
				addDrawableChild(ButtonWidget.builder(boolLabel(bs), btn -> {
					bs.set().accept(!bs.get().getAsBoolean());
					ConfigManager.save();
					btn.setMessage(boolLabel(bs));
				}).dimensions(x0, y, panelW, 20).build());
			} else if (s instanceof StringSetting ss) {
				TextFieldWidget field = new TextFieldWidget(this.textRenderer, x0 + panelW - 150, y, 150, 20, Text.literal(ss.label()));
				field.setMaxLength(64);
				field.setText(ss.get().get());
				field.setChangedListener(v -> {
					ss.set().accept(v);
					ConfigManager.save();
				});
				addDrawableChild(field);
			} else {
				// numeric stepper: [-] ... [+]
				addDrawableChild(ButtonWidget.builder(Text.literal("-"), btn -> {
					stepNumeric(s, -1);
				}).dimensions(x0, y, 22, 20).build());
				addDrawableChild(ButtonWidget.builder(Text.literal("+"), btn -> {
					stepNumeric(s, 1);
				}).dimensions(x0 + panelW - 22, y, 22, 20).build());
			}
		}

		if (pageCount > 1) {
			addDrawableChild(ButtonWidget.builder(Text.literal("< Prev"), b -> {
				page = Math.max(0, page - 1);
				this.clearAndInit();
			}).dimensions(cx - 104, pageY, 50, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.literal("Next >"), b -> {
				page++;
				this.clearAndInit();
			}).dimensions(cx + 54, pageY, 50, 20).build());
		}

		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> this.close())
			.dimensions(cx - 100, doneY, 200, 20).build());
	}

	private void stepNumeric(Setting s, int dir) {
		if (s instanceof IntSetting is) {
			int nv = Math.max(is.min(), Math.min(is.max(), is.get().getAsInt() + dir * is.step()));
			is.set().accept(nv);
		} else if (s instanceof DoubleSetting ds) {
			double nv = Math.max(ds.min(), Math.min(ds.max(), ds.get().getAsDouble() + dir * ds.step()));
			ds.set().accept(nv);
		}
		ConfigManager.save();
	}

	private Text enableLabel() {
		boolean on = enableGet.getAsBoolean();
		return Text.literal("Enabled: ").append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED));
	}

	private static Text boolLabel(BoolSetting bs) {
		boolean on = bs.get().getAsBoolean();
		return Text.literal(bs.label() + ": ").append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED));
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(moduleTitle).formatted(Formatting.AQUA), this.width / 2, 12, 0xFFFFFFFF);

		int cx = this.width / 2;
		int panelW = Math.min(this.width - 20, 300);
		int x0 = cx - panelW / 2;
		for (int[] row : rowLayout) {
			Setting s = settings.get(row[0]);
			int y = row[1];
			if (s instanceof IntSetting is) {
				ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(is.label() + ": " + is.get().getAsInt()), cx, y + 6, 0xFFFFFFFF);
			} else if (s instanceof DoubleSetting ds) {
				ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(ds.label() + ": " + trim(ds.get().getAsDouble())), cx, y + 6, 0xFFFFFFFF);
			} else if (s instanceof StringSetting ss) {
				ctx.drawTextWithShadow(this.textRenderer, Text.literal(ss.label()), x0, y + 6, 0xFFCFCFCF);
			}
		}
	}

	private static String trim(double v) {
		if (v == Math.floor(v)) {
			return String.valueOf((int) v);
		}
		return String.format("%.1f", v);
	}

	@Override
	public void close() {
		ConfigManager.save();
		if (this.client != null) {
			this.client.setScreen(parent);
		}
	}
}
