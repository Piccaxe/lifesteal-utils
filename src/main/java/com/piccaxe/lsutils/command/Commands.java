package com.piccaxe.lsutils.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import com.piccaxe.lsutils.gui.SettingsScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client commands: {@code /piccaxeutils} (alias {@code /piccaxe}).
 * Bare {@code /piccaxeutils} opens the settings GUI. Subcommands support a status
 * readout, master on/off/toggle, and per-feature on/off/toggle.
 */
public final class Commands {
	private Commands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
			LiteralArgumentBuilder<FabricClientCommandSource> root = literal("piccaxeutils")
				.executes(ctx -> {
					openSettings(ctx.getSource());
					return 1;
				});

			root.then(literal("status").executes(ctx -> {
				printStatus(ctx.getSource());
				return 1;
			}));
			root.then(literal("settings").executes(ctx -> {
				openSettings(ctx.getSource());
				return 1;
			}));
			root.then(literal("toggle").executes(ctx -> setMaster(ctx.getSource(), !ConfigManager.get().masterEnabled)));
			root.then(literal("on").executes(ctx -> setMaster(ctx.getSource(), true)));
			root.then(literal("off").executes(ctx -> setMaster(ctx.getSource(), false)));

			addFeature(root, "heart", c -> c.heartHud, (c, v) -> c.heartHud = v);
			addFeature(root, "totem", c -> c.totemHud, (c, v) -> c.totemHud = v);
			addFeature(root, "proximity", c -> c.proximityAlert, (c, v) -> c.proximityAlert = v);
			addFeature(root, "coords", c -> c.coordsHud, (c, v) -> c.coordsHud = v);
			addFeature(root, "death", c -> c.deathWaypoint, (c, v) -> c.deathWaypoint = v);
			addFeature(root, "reconnect", c -> c.autoReconnect, (c, v) -> c.autoReconnect = v);
			addFeature(root, "fullbright", c -> c.fullbright, (c, v) -> c.fullbright = v);
			addFeature(root, "hurtcam", c -> c.noHurtCam, (c, v) -> c.noHurtCam = v);
			addFeature(root, "trickster", c -> c.antiTrickster, (c, v) -> c.antiTrickster = v);

			root.then(outlineCommand());

			root.then(literal("cleardeath").executes(ctx -> {
				ConfigManager.get().hasDeath = false;
				ConfigManager.save();
				ctx.getSource().sendFeedback(prefix().append(
					Text.literal("Death waypoint cleared.").formatted(Formatting.GRAY)));
				return 1;
			}));

			LiteralCommandNode<FabricClientCommandSource> node = dispatcher.register(root);
			dispatcher.register(literal("piccaxe").redirect(node));
		});
	}

	private static void addFeature(LiteralArgumentBuilder<FabricClientCommandSource> root, String name,
								   Predicate<Config> getter, BiConsumer<Config, Boolean> setter) {
		root.then(literal(name)
			.executes(ctx -> {
				reportFeature(ctx.getSource(), name, getter.test(ConfigManager.get()));
				return 1;
			})
			.then(literal("on").executes(ctx -> setFeature(ctx.getSource(), name, setter, true)))
			.then(literal("off").executes(ctx -> setFeature(ctx.getSource(), name, setter, false)))
			.then(literal("toggle").executes(ctx ->
				setFeature(ctx.getSource(), name, setter, !getter.test(ConfigManager.get())))));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> outlineCommand() {
		return literal("outline")
			.executes(ctx -> {
				reportFeature(ctx.getSource(), "outline", ConfigManager.get().playerOutliner);
				return 1;
			})
			.then(literal("on").executes(ctx -> setOutliner(ctx.getSource(), true)))
			.then(literal("off").executes(ctx -> setOutliner(ctx.getSource(), false)))
			.then(literal("toggle").executes(ctx ->
				setOutliner(ctx.getSource(), !ConfigManager.get().playerOutliner)))
			.then(literal("set").then(argument("player", StringArgumentType.word())
				.then(literal("teammate").executes(ctx -> setOverride(ctx, "teammate")))
				.then(literal("ally").executes(ctx -> setOverride(ctx, "ally")))
				.then(literal("enemy").executes(ctx -> setOverride(ctx, "enemy")))
				.then(literal("none").executes(ctx -> setOverride(ctx, "none")))))
			.then(literal("clear").then(argument("player", StringArgumentType.word())
				.executes(Commands::clearOverride)))
			.then(literal("list").executes(ctx -> {
				listOverrides(ctx.getSource());
				return 1;
			}));
	}

	private static int setOutliner(FabricClientCommandSource src, boolean value) {
		ConfigManager.get().playerOutliner = value;
		ConfigManager.save();
		reportFeature(src, "outline", value);
		return 1;
	}

	private static int setOverride(CommandContext<FabricClientCommandSource> ctx, String category) {
		String player = StringArgumentType.getString(ctx, "player");
		ConfigManager.get().outlineOverrides.put(player.toLowerCase(Locale.ROOT), category);
		ConfigManager.save();
		ctx.getSource().sendFeedback(prefix()
			.append(Text.literal(player + " → " + category).formatted(Formatting.AQUA)));
		return 1;
	}

	private static int clearOverride(CommandContext<FabricClientCommandSource> ctx) {
		String player = StringArgumentType.getString(ctx, "player");
		ConfigManager.get().outlineOverrides.remove(player.toLowerCase(Locale.ROOT));
		ConfigManager.save();
		ctx.getSource().sendFeedback(prefix()
			.append(Text.literal("Cleared outline override for " + player).formatted(Formatting.GRAY)));
		return 1;
	}

	private static void listOverrides(FabricClientCommandSource src) {
		var overrides = ConfigManager.get().outlineOverrides;
		if (overrides.isEmpty()) {
			src.sendFeedback(prefix().append(Text.literal("No outline overrides set.").formatted(Formatting.GRAY)));
			return;
		}
		src.sendFeedback(Text.literal("Outline overrides:").formatted(Formatting.GOLD));
		overrides.forEach((name, cat) ->
			src.sendFeedback(Text.literal(" • " + name + ": " + cat).formatted(Formatting.GRAY)));
	}

	private static int setFeature(FabricClientCommandSource src, String name,
								  BiConsumer<Config, Boolean> setter, boolean value) {
		setter.accept(ConfigManager.get(), value);
		ConfigManager.save();
		reportFeature(src, name, value);
		return 1;
	}

	private static void reportFeature(FabricClientCommandSource src, String name, boolean value) {
		src.sendFeedback(prefix()
			.append(Text.literal(name + ": ").formatted(Formatting.GRAY))
			.append(state(value)));
	}

	private static int setMaster(FabricClientCommandSource src, boolean value) {
		ConfigManager.get().masterEnabled = value;
		ConfigManager.save();
		src.sendFeedback(prefix().append(Text.literal("All features ").formatted(Formatting.GRAY)).append(state(value)));
		return 1;
	}

	private static void openSettings(FabricClientCommandSource src) {
		MinecraftClient client = src.getClient();
		client.execute(() -> client.setScreen(new SettingsScreen(null)));
	}

	private static void printStatus(FabricClientCommandSource src) {
		Config c = ConfigManager.get();
		src.sendFeedback(Text.literal("Piccaxe's Lifesteal Utils").formatted(Formatting.GOLD, Formatting.BOLD));
		line(src, "All features", c.masterEnabled);
		line(src, "Heart HUD", c.heartHud);
		line(src, "Totem counter", c.totemHud);
		line(src, "Proximity alert", c.proximityAlert);
		line(src, "Coordinates", c.coordsHud);
		line(src, "Death waypoint", c.deathWaypoint);
		line(src, "Auto-reconnect", c.autoReconnect);
		line(src, "Fullbright", c.fullbright);
		line(src, "No hurt-cam", c.noHurtCam);
		line(src, "Anti-Trickster", c.antiTrickster);
		line(src, "Player outliner", c.playerOutliner);
	}

	private static void line(FabricClientCommandSource src, String name, boolean on) {
		src.sendFeedback(Text.literal(" • " + name + ": ").formatted(Formatting.GRAY).append(state(on)));
	}

	private static MutableText state(boolean on) {
		return Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED);
	}

	private static MutableText prefix() {
		return Text.literal("[LSU] ").formatted(Formatting.GOLD);
	}
}
