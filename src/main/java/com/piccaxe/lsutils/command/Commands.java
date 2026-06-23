package com.piccaxe.lsutils.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import com.piccaxe.lsutils.feature.DiscordWebhook;
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
			root.then(lootChestCommand());
			root.then(notifierCommand());
			root.then(discordCommand());

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

	private static LiteralArgumentBuilder<FabricClientCommandSource> lootChestCommand() {
		return literal("lootchests")
			.executes(ctx -> {
				reportFeature(ctx.getSource(), "lootchests", ConfigManager.get().enderChestOutliner);
				return 1;
			})
			.then(literal("on").executes(ctx -> setLoot(ctx.getSource(), true)))
			.then(literal("off").executes(ctx -> setLoot(ctx.getSource(), false)))
			.then(literal("toggle").executes(ctx -> setLoot(ctx.getSource(), !ConfigManager.get().enderChestOutliner)))
			.then(literal("radius").then(argument("blocks", IntegerArgumentType.integer(8, 256))
				.executes(Commands::setLootRadius)))
			.then(boolNode("label", c -> c.enderChestDistanceLabel, (c, v) -> c.enderChestDistanceLabel = v))
			.then(boolNode("tracer", c -> c.enderChestTracer, (c, v) -> c.enderChestTracer = v))
			.then(boolNode("path", c -> c.enderChestPathTracer, (c, v) -> c.enderChestPathTracer = v))
			.then(literal("pathmode")
				.then(literal("nearest").executes(ctx -> setPathMode(ctx.getSource(), "nearest")))
				.then(literal("looking").executes(ctx -> setPathMode(ctx.getSource(), "looking")))
				.then(literal("all").executes(ctx -> setPathMode(ctx.getSource(), "all"))));
	}

	private static int setLoot(FabricClientCommandSource src, boolean value) {
		ConfigManager.get().enderChestOutliner = value;
		ConfigManager.save();
		reportFeature(src, "lootchests", value);
		return 1;
	}

	private static int setLootRadius(CommandContext<FabricClientCommandSource> ctx) {
		int radius = IntegerArgumentType.getInteger(ctx, "blocks");
		ConfigManager.get().enderChestRadius = radius;
		ConfigManager.save();
		ctx.getSource().sendFeedback(prefix()
			.append(Text.literal("Loot chest scan radius: " + radius + " blocks").formatted(Formatting.AQUA)));
		return 1;
	}

	private static int setPathMode(FabricClientCommandSource src, String mode) {
		ConfigManager.get().enderChestPathMode = mode;
		ConfigManager.save();
		src.sendFeedback(prefix().append(Text.literal("Loot chest path mode: " + mode).formatted(Formatting.AQUA)));
		return 1;
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> notifierCommand() {
		return literal("notifier")
			.executes(ctx -> {
				reportFeature(ctx.getSource(), "notifier", ConfigManager.get().playerNotifier);
				return 1;
			})
			.then(literal("on").executes(ctx -> setNotifier(ctx.getSource(), true)))
			.then(literal("off").executes(ctx -> setNotifier(ctx.getSource(), false)))
			.then(literal("toggle").executes(ctx -> setNotifier(ctx.getSource(), !ConfigManager.get().playerNotifier)))
			.then(boolNode("chat", c -> c.notifierChat, (c, v) -> c.notifierChat = v))
			.then(boolNode("sound", c -> c.notifierSound, (c, v) -> c.notifierSound = v))
			.then(boolNode("banner", c -> c.notifierBanner, (c, v) -> c.notifierBanner = v))
			.then(boolNode("discord", c -> c.notifierDiscord, (c, v) -> c.notifierDiscord = v));
	}

	private static int setNotifier(FabricClientCommandSource src, boolean value) {
		ConfigManager.get().playerNotifier = value;
		ConfigManager.save();
		reportFeature(src, "notifier", value);
		return 1;
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> discordCommand() {
		return literal("discord")
			.executes(ctx -> {
				discordStatus(ctx.getSource());
				return 1;
			})
			.then(literal("on").executes(ctx -> setDiscord(ctx.getSource(), true)))
			.then(literal("off").executes(ctx -> setDiscord(ctx.getSource(), false)))
			.then(literal("toggle").executes(ctx -> setDiscord(ctx.getSource(), !ConfigManager.get().discordRelay)))
			.then(literal("url").then(argument("url", StringArgumentType.greedyString()).executes(Commands::setWebhookUrl)))
			.then(literal("name").then(argument("name", StringArgumentType.greedyString()).executes(Commands::setWebhookName)))
			.then(literal("test").executes(ctx -> {
				discordTest(ctx.getSource());
				return 1;
			}))
			.then(boolNode("team", c -> c.relayTeamChat, (c, v) -> c.relayTeamChat = v))
			.then(boolNode("whispers", c -> c.relayWhispers, (c, v) -> c.relayWhispers = v))
			.then(boolNode("mentions", c -> c.relayMentions, (c, v) -> c.relayMentions = v))
			.then(boolNode("keywords", c -> c.relayKeywords, (c, v) -> c.relayKeywords = v))
			.then(literal("keyword")
				.then(literal("add").then(argument("word", StringArgumentType.greedyString()).executes(Commands::addKeyword)))
				.then(literal("remove").then(argument("word", StringArgumentType.greedyString()).executes(Commands::removeKeyword)))
				.then(literal("list").executes(ctx -> {
					listKeywords(ctx.getSource());
					return 1;
				})));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> boolNode(String name,
			Predicate<Config> getter, BiConsumer<Config, Boolean> setter) {
		return literal(name)
			.executes(ctx -> {
				reportFeature(ctx.getSource(), name, getter.test(ConfigManager.get()));
				return 1;
			})
			.then(literal("on").executes(ctx -> setFeature(ctx.getSource(), name, setter, true)))
			.then(literal("off").executes(ctx -> setFeature(ctx.getSource(), name, setter, false)))
			.then(literal("toggle").executes(ctx ->
				setFeature(ctx.getSource(), name, setter, !getter.test(ConfigManager.get()))));
	}

	private static int setDiscord(FabricClientCommandSource src, boolean value) {
		ConfigManager.get().discordRelay = value;
		ConfigManager.save();
		reportFeature(src, "discord", value);
		if (value && ConfigManager.get().discordWebhookUrl.isBlank()) {
			src.sendFeedback(prefix().append(Text.literal("No webhook set — use /piccaxeutils discord url <url>")
				.formatted(Formatting.YELLOW)));
		}
		return 1;
	}

	private static int setWebhookUrl(CommandContext<FabricClientCommandSource> ctx) {
		String url = StringArgumentType.getString(ctx, "url").trim();
		ConfigManager.get().discordWebhookUrl = url;
		ConfigManager.save();
		boolean looksValid = url.startsWith("https://discord.com/api/webhooks/")
			|| url.startsWith("https://discordapp.com/api/webhooks/")
			|| url.startsWith("https://canary.discord.com/api/webhooks/")
			|| url.startsWith("https://ptb.discord.com/api/webhooks/");
		MutableText msg = prefix().append(Text.literal("Webhook URL saved.").formatted(Formatting.GREEN));
		if (!looksValid) {
			msg.append(Text.literal(" (warning: doesn't look like a Discord webhook URL)").formatted(Formatting.YELLOW));
		}
		ctx.getSource().sendFeedback(msg);
		return 1;
	}

	private static int setWebhookName(CommandContext<FabricClientCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "name").trim();
		ConfigManager.get().discordUsername = name;
		ConfigManager.save();
		ctx.getSource().sendFeedback(prefix().append(Text.literal("Webhook name set to: " + name).formatted(Formatting.GREEN)));
		return 1;
	}

	private static void discordTest(FabricClientCommandSource src) {
		Config c = ConfigManager.get();
		if (c.discordWebhookUrl.isBlank()) {
			src.sendFeedback(prefix().append(Text.literal("No webhook URL set.").formatted(Formatting.RED)));
			return;
		}
		DiscordWebhook.send(c.discordWebhookUrl, c.discordUsername, "Test message from Piccaxe's Lifesteal Utils");
		src.sendFeedback(prefix().append(Text.literal("Test message queued.").formatted(Formatting.GREEN)));
	}

	private static int addKeyword(CommandContext<FabricClientCommandSource> ctx) {
		String word = StringArgumentType.getString(ctx, "word").trim();
		if (!word.isEmpty() && !ConfigManager.get().keywords.contains(word)) {
			ConfigManager.get().keywords.add(word);
			ConfigManager.save();
		}
		ctx.getSource().sendFeedback(prefix().append(Text.literal("Added keyword: " + word).formatted(Formatting.AQUA)));
		return 1;
	}

	private static int removeKeyword(CommandContext<FabricClientCommandSource> ctx) {
		String word = StringArgumentType.getString(ctx, "word").trim();
		ConfigManager.get().keywords.remove(word);
		ConfigManager.save();
		ctx.getSource().sendFeedback(prefix().append(Text.literal("Removed keyword: " + word).formatted(Formatting.GRAY)));
		return 1;
	}

	private static void listKeywords(FabricClientCommandSource src) {
		var keywords = ConfigManager.get().keywords;
		if (keywords.isEmpty()) {
			src.sendFeedback(prefix().append(Text.literal("No keywords set.").formatted(Formatting.GRAY)));
			return;
		}
		src.sendFeedback(Text.literal("Keywords:").formatted(Formatting.GOLD));
		keywords.forEach(k -> src.sendFeedback(Text.literal(" • " + k).formatted(Formatting.GRAY)));
	}

	private static void discordStatus(FabricClientCommandSource src) {
		Config c = ConfigManager.get();
		src.sendFeedback(Text.literal("Discord relay").formatted(Formatting.GOLD, Formatting.BOLD));
		line(src, "Enabled", c.discordRelay);
		src.sendFeedback(Text.literal(" • Webhook: ").formatted(Formatting.GRAY)
			.append(Text.literal(c.discordWebhookUrl.isBlank() ? "not set" : "set")
				.formatted(c.discordWebhookUrl.isBlank() ? Formatting.RED : Formatting.GREEN)));
		line(src, "Team chat", c.relayTeamChat);
		line(src, "Whispers/DMs", c.relayWhispers);
		line(src, "Mentions", c.relayMentions);
		line(src, "Keywords", c.relayKeywords);
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
		line(src, "Player notifier", c.playerNotifier);
		line(src, "Coordinates", c.coordsHud);
		line(src, "Death waypoint", c.deathWaypoint);
		line(src, "Auto-reconnect", c.autoReconnect);
		line(src, "Fullbright", c.fullbright);
		line(src, "No hurt-cam", c.noHurtCam);
		line(src, "Anti-Trickster", c.antiTrickster);
		line(src, "Player outliner", c.playerOutliner);
		line(src, "Loot chest outliner", c.enderChestOutliner);
		line(src, "Discord relay", c.discordRelay);
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
