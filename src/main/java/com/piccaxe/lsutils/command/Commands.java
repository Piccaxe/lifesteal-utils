package com.piccaxe.lsutils.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import com.piccaxe.lsutils.feature.DiscordWebhook;
import com.piccaxe.lsutils.gui.HudEditScreen;
import com.piccaxe.lsutils.gui.SettingsScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
			root.then(literal("hudedit").executes(ctx -> {
				openHudEditor(ctx.getSource());
				return 1;
			}));
			root.then(literal("toggle").executes(ctx -> setMaster(ctx.getSource(), !ConfigManager.get().masterEnabled)));
			root.then(literal("on").executes(ctx -> setMaster(ctx.getSource(), true)));
			root.then(literal("off").executes(ctx -> setMaster(ctx.getSource(), false)));

			addFeature(root, "heart", c -> c.heartHud, (c, v) -> c.heartHud = v);
			addFeature(root, "totem", c -> c.totemHud, (c, v) -> c.totemHud = v);
			root.then(boolNode("proximity", c -> c.proximityAlert, (c, v) -> c.proximityAlert = v)
				.then(literal("ignore")
					.then(literal("add").then(argument("player", StringArgumentType.word()).executes(Commands::addProximityIgnore)))
					.then(literal("remove").then(argument("player", StringArgumentType.word()).executes(Commands::removeProximityIgnore)))
					.then(literal("list").executes(ctx -> {
						listProximityIgnore(ctx.getSource());
						return 1;
					})))
				.then(boolNode("discord", c -> c.proximityDiscord, (c, v) -> c.proximityDiscord = v))
				.then(literal("watch")
					.then(literal("add").then(argument("player", StringArgumentType.word()).executes(Commands::addProximityWatch)))
					.then(literal("remove").then(argument("player", StringArgumentType.word()).executes(Commands::removeProximityWatch)))
					.then(literal("list").executes(ctx -> {
						listProximityWatch(ctx.getSource());
						return 1;
					})))
				.then(literal("ping")
					.executes(ctx -> {
						showProximityPing(ctx.getSource());
						return 1;
					})
					.then(literal("set").then(argument("text", StringArgumentType.greedyString()).executes(Commands::setProximityPing)))
					.then(literal("clear").executes(ctx -> {
						ConfigManager.get().proximityPing = "";
						ConfigManager.save();
						ctx.getSource().sendFeedback(prefix().append(Text.literal("Proximity ping cleared.").formatted(Formatting.GRAY)));
						return 1;
					}))));
			addFeature(root, "coords", c -> c.coordsHud, (c, v) -> c.coordsHud = v);
			addFeature(root, "death", c -> c.deathWaypoint, (c, v) -> c.deathWaypoint = v);
			addFeature(root, "reconnect", c -> c.autoReconnect, (c, v) -> c.autoReconnect = v);
			addFeature(root, "fullbright", c -> c.fullbright, (c, v) -> c.fullbright = v);
			addFeature(root, "hurtcam", c -> c.noHurtCam, (c, v) -> c.noHurtCam = v);
			addFeature(root, "trickster", c -> c.antiTrickster, (c, v) -> c.antiTrickster = v);
			addFeature(root, "antisign", c -> c.antiSign, (c, v) -> c.antiSign = v);
			addFeature(root, "armorstand", c -> c.armorStandBypass, (c, v) -> c.armorStandBypass = v);
			addFeature(root, "portal", c -> c.netherPortalBypass, (c, v) -> c.netherPortalBypass = v);

			root.then(outlineCommand());
			root.then(lootChestCommand());
			root.then(notifierCommand());
			root.then(healthBarsCommand());
			root.then(discordCommand());
			root.then(literal("players").executes(ctx -> {
				listShardPlayers(ctx.getSource());
				return 1;
			}));

			root.then(literal("cleardeath").executes(ctx -> {
				ConfigManager.get().hasDeath = false;
				ConfigManager.save();
				ctx.getSource().sendFeedback(prefix().append(
					Text.literal("Death waypoint cleared.").formatted(Formatting.GRAY)));
				return 1;
			}));

			LiteralCommandNode<FabricClientCommandSource> node = dispatcher.register(root);
			dispatcher.register(literal("piccaxe").redirect(node));
			dispatcher.register(literal("shard").executes(ctx -> {
				listShardPlayers(ctx.getSource());
				return 1;
			}));
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

	private static LiteralArgumentBuilder<FabricClientCommandSource> healthBarsCommand() {
		return literal("healthbars")
			.executes(ctx -> {
				reportFeature(ctx.getSource(), "healthbars", ConfigManager.get().healthBars);
				return 1;
			})
			.then(literal("on").executes(ctx -> setHealthBars(ctx.getSource(), true)))
			.then(literal("off").executes(ctx -> setHealthBars(ctx.getSource(), false)))
			.then(literal("toggle").executes(ctx -> setHealthBars(ctx.getSource(), !ConfigManager.get().healthBars)))
			.then(literal("range").then(argument("blocks", IntegerArgumentType.integer(4, 128))
				.executes(ctx -> {
					int r = IntegerArgumentType.getInteger(ctx, "blocks");
					ConfigManager.get().healthBarRange = r;
					ConfigManager.save();
					ctx.getSource().sendFeedback(prefix()
						.append(Text.literal("Health bar range: " + r + " blocks").formatted(Formatting.AQUA)));
					return 1;
				})))
			.then(boolNode("playersonly", c -> c.healthBarPlayersOnly, (c, v) -> c.healthBarPlayersOnly = v));
	}

	private static int setHealthBars(FabricClientCommandSource src, boolean value) {
		ConfigManager.get().healthBars = value;
		ConfigManager.save();
		reportFeature(src, "healthbars", value);
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
			.then(boolNode("discord", c -> c.notifierDiscord, (c, v) -> c.notifierDiscord = v))
			.then(literal("ignore")
				.then(literal("add").then(argument("player", StringArgumentType.word()).executes(Commands::addNotifierIgnore)))
				.then(literal("remove").then(argument("player", StringArgumentType.word()).executes(Commands::removeNotifierIgnore)))
				.then(literal("list").executes(ctx -> {
					listNotifierIgnore(ctx.getSource());
					return 1;
				})));
	}

	private static int setNotifier(FabricClientCommandSource src, boolean value) {
		ConfigManager.get().playerNotifier = value;
		ConfigManager.save();
		reportFeature(src, "notifier", value);
		return 1;
	}

	private static int addNotifierIgnore(CommandContext<FabricClientCommandSource> ctx) {
		String player = StringArgumentType.getString(ctx, "player").toLowerCase(Locale.ROOT);
		var list = ConfigManager.get().notifierIgnore;
		if (!list.contains(player)) {
			list.add(player);
			ConfigManager.save();
		}
		ctx.getSource().sendFeedback(prefix()
			.append(Text.literal("Notifier will ignore: " + player).formatted(Formatting.AQUA)));
		return 1;
	}

	private static int removeNotifierIgnore(CommandContext<FabricClientCommandSource> ctx) {
		String player = StringArgumentType.getString(ctx, "player").toLowerCase(Locale.ROOT);
		ConfigManager.get().notifierIgnore.remove(player);
		ConfigManager.save();
		ctx.getSource().sendFeedback(prefix()
			.append(Text.literal("Notifier no longer ignores: " + player).formatted(Formatting.GRAY)));
		return 1;
	}

	private static void listNotifierIgnore(FabricClientCommandSource src) {
		var list = ConfigManager.get().notifierIgnore;
		if (list.isEmpty()) {
			src.sendFeedback(prefix().append(Text.literal("Notifier ignore list is empty.").formatted(Formatting.GRAY)));
			return;
		}
		src.sendFeedback(Text.literal("Notifier ignore list:").formatted(Formatting.GOLD));
		list.forEach(name -> src.sendFeedback(Text.literal(" • " + name).formatted(Formatting.GRAY)));
	}

	private static int addProximityIgnore(CommandContext<FabricClientCommandSource> ctx) {
		String player = StringArgumentType.getString(ctx, "player").toLowerCase(Locale.ROOT);
		var list = ConfigManager.get().proximityIgnore;
		if (!list.contains(player)) {
			list.add(player);
			ConfigManager.save();
		}
		ctx.getSource().sendFeedback(prefix()
			.append(Text.literal("Proximity alert will ignore: " + player).formatted(Formatting.AQUA)));
		return 1;
	}

	private static int removeProximityIgnore(CommandContext<FabricClientCommandSource> ctx) {
		String player = StringArgumentType.getString(ctx, "player").toLowerCase(Locale.ROOT);
		ConfigManager.get().proximityIgnore.remove(player);
		ConfigManager.save();
		ctx.getSource().sendFeedback(prefix()
			.append(Text.literal("Proximity alert no longer ignores: " + player).formatted(Formatting.GRAY)));
		return 1;
	}

	private static void listProximityIgnore(FabricClientCommandSource src) {
		var list = ConfigManager.get().proximityIgnore;
		if (list.isEmpty()) {
			src.sendFeedback(prefix().append(Text.literal("Proximity ignore list is empty.").formatted(Formatting.GRAY)));
			return;
		}
		src.sendFeedback(Text.literal("Proximity ignore list:").formatted(Formatting.GOLD));
		list.forEach(name -> src.sendFeedback(Text.literal(" • " + name).formatted(Formatting.GRAY)));
	}

	private static int addProximityWatch(CommandContext<FabricClientCommandSource> ctx) {
		String player = StringArgumentType.getString(ctx, "player").toLowerCase(Locale.ROOT);
		var list = ConfigManager.get().proximityWatchlist;
		if (!list.contains(player)) {
			list.add(player);
			ConfigManager.save();
		}
		ctx.getSource().sendFeedback(prefix()
			.append(Text.literal("Proximity Discord ping added for: " + player).formatted(Formatting.AQUA)));
		return 1;
	}

	private static int removeProximityWatch(CommandContext<FabricClientCommandSource> ctx) {
		String player = StringArgumentType.getString(ctx, "player").toLowerCase(Locale.ROOT);
		ConfigManager.get().proximityWatchlist.remove(player);
		ConfigManager.save();
		ctx.getSource().sendFeedback(prefix()
			.append(Text.literal("Proximity Discord ping removed for: " + player).formatted(Formatting.GRAY)));
		return 1;
	}

	private static void listProximityWatch(FabricClientCommandSource src) {
		var list = ConfigManager.get().proximityWatchlist;
		if (list.isEmpty()) {
			src.sendFeedback(prefix().append(Text.literal("Proximity watchlist is empty.").formatted(Formatting.GRAY)));
			return;
		}
		src.sendFeedback(Text.literal("Proximity Discord watchlist:").formatted(Formatting.GOLD));
		list.forEach(name -> src.sendFeedback(Text.literal(" • " + name).formatted(Formatting.GRAY)));
	}

	private static int setProximityPing(CommandContext<FabricClientCommandSource> ctx) {
		String text = StringArgumentType.getString(ctx, "text").trim();
		ConfigManager.get().proximityPing = text;
		ConfigManager.save();
		ctx.getSource().sendFeedback(prefix()
			.append(Text.literal("Proximity ping set to: " + text).formatted(Formatting.AQUA)));
		return 1;
	}

	private static void showProximityPing(FabricClientCommandSource src) {
		String ping = ConfigManager.get().proximityPing;
		src.sendFeedback(prefix().append(Text.literal("Proximity ping: "
			+ (ping == null || ping.isBlank() ? "(none)" : ping)).formatted(Formatting.GRAY)));
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
			.then(literal("test").executes(ctx -> {
				Config c = ConfigManager.get();
				return doTest(ctx.getSource(), ConfigManager.webhook(c.chatWebhook), orNone(c.chatWebhook));
			}))
			.then(literal("webhook")
				.then(literal("add").then(argument("name", StringArgumentType.word())
					.then(argument("url", StringArgumentType.greedyString()).executes(Commands::addWebhook))))
				.then(literal("remove").then(argument("name", StringArgumentType.word()).executes(Commands::removeWebhook)))
				.then(literal("username").then(argument("name", StringArgumentType.word())
					.then(argument("displayname", StringArgumentType.greedyString()).executes(Commands::setWebhookUsername))))
				.then(literal("cooldown").then(argument("name", StringArgumentType.word())
					.then(argument("seconds", IntegerArgumentType.integer(0, 3600)).executes(Commands::setWebhookCooldown))))
				.then(literal("test").then(argument("name", StringArgumentType.word()).executes(Commands::testWebhook)))
				.then(literal("list").executes(ctx -> {
					listWebhooks(ctx.getSource());
					return 1;
				})))
			.then(literal("assign")
				.then(literal("chat").then(argument("name", StringArgumentType.word()).executes(ctx -> assignWebhook(ctx, "chat"))))
				.then(literal("notifier").then(argument("name", StringArgumentType.word()).executes(ctx -> assignWebhook(ctx, "notifier"))))
				.then(literal("proximity").then(argument("name", StringArgumentType.word()).executes(ctx -> assignWebhook(ctx, "proximity")))))
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
				})))
			.then(literal("rule")
				.then(literal("add").then(argument("webhook", StringArgumentType.word())
					.then(argument("keyword", StringArgumentType.greedyString()).executes(Commands::addRule))))
				.then(literal("remove").then(argument("index", IntegerArgumentType.integer(1)).executes(Commands::removeRule)))
				.then(literal("label").then(argument("index", IntegerArgumentType.integer(1))
					.then(argument("text", StringArgumentType.greedyString()).executes(Commands::setRuleLabel))))
				.then(literal("ping").then(argument("index", IntegerArgumentType.integer(1))
					.then(argument("text", StringArgumentType.greedyString()).executes(Commands::setRulePing))))
				.then(literal("toggle").then(argument("index", IntegerArgumentType.integer(1)).executes(Commands::toggleRule)))
				.then(literal("list").executes(ctx -> {
					listRules(ctx.getSource());
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

	private static int addWebhook(CommandContext<FabricClientCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		String url = StringArgumentType.getString(ctx, "url").trim();
		Config.WebhookEntry existing = ConfigManager.webhook(name);
		if (existing != null) {
			existing.url = url;
		} else {
			ConfigManager.get().webhooks.add(new Config.WebhookEntry(name, url, "Lifesteal Utils"));
		}
		ConfigManager.save();
		boolean looksValid = url.startsWith("https://discord.com/api/webhooks/")
			|| url.startsWith("https://discordapp.com/api/webhooks/")
			|| url.startsWith("https://canary.discord.com/api/webhooks/")
			|| url.startsWith("https://ptb.discord.com/api/webhooks/");
		MutableText msg = prefix().append(Text.literal("Webhook '" + name + "' saved.").formatted(Formatting.GREEN));
		if (!looksValid) {
			msg.append(Text.literal(" (warning: doesn't look like a Discord webhook URL)").formatted(Formatting.YELLOW));
		}
		ctx.getSource().sendFeedback(msg);
		return 1;
	}

	private static int removeWebhook(CommandContext<FabricClientCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		ConfigManager.get().webhooks.removeIf(w -> w.name != null && w.name.equalsIgnoreCase(name));
		Config c = ConfigManager.get();
		if (name.equalsIgnoreCase(c.chatWebhook)) {
			c.chatWebhook = "";
		}
		if (name.equalsIgnoreCase(c.notifierWebhook)) {
			c.notifierWebhook = "";
		}
		if (name.equalsIgnoreCase(c.proximityWebhook)) {
			c.proximityWebhook = "";
		}
		ConfigManager.save();
		ctx.getSource().sendFeedback(prefix().append(Text.literal("Removed webhook '" + name + "'.").formatted(Formatting.GRAY)));
		return 1;
	}

	private static int setWebhookUsername(CommandContext<FabricClientCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		String displayName = StringArgumentType.getString(ctx, "displayname").trim();
		Config.WebhookEntry wh = ConfigManager.webhook(name);
		if (wh == null) {
			ctx.getSource().sendFeedback(prefix().append(Text.literal("No webhook named '" + name + "'.").formatted(Formatting.RED)));
			return 0;
		}
		wh.username = displayName;
		ConfigManager.save();
		ctx.getSource().sendFeedback(prefix().append(Text.literal("Webhook '" + name + "' posts as: " + displayName).formatted(Formatting.GREEN)));
		return 1;
	}

	private static int testWebhook(CommandContext<FabricClientCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		return doTest(ctx.getSource(), ConfigManager.webhook(name), name);
	}

	private static int setWebhookCooldown(CommandContext<FabricClientCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
		Config.WebhookEntry wh = ConfigManager.webhook(name);
		if (wh == null) {
			ctx.getSource().sendFeedback(prefix().append(Text.literal("No webhook named '" + name + "'.").formatted(Formatting.RED)));
			return 0;
		}
		wh.cooldownSeconds = seconds;
		ConfigManager.save();
		ctx.getSource().sendFeedback(prefix().append(Text.literal("Webhook '" + name + "' cooldown: "
			+ (seconds == 0 ? "off" : seconds + "s")).formatted(Formatting.AQUA)));
		return 1;
	}

	private static int assignWebhook(CommandContext<FabricClientCommandSource> ctx, String category) {
		String name = StringArgumentType.getString(ctx, "name");
		if (ConfigManager.webhook(name) == null) {
			ctx.getSource().sendFeedback(prefix().append(Text.literal(
				"No webhook named '" + name + "' — add it with /piccaxeutils discord webhook add").formatted(Formatting.RED)));
			return 0;
		}
		Config c = ConfigManager.get();
		switch (category) {
			case "chat" -> c.chatWebhook = name;
			case "notifier" -> c.notifierWebhook = name;
			case "proximity" -> c.proximityWebhook = name;
			default -> {
			}
		}
		ConfigManager.save();
		ctx.getSource().sendFeedback(prefix().append(Text.literal(category + " → webhook '" + name + "'").formatted(Formatting.AQUA)));
		return 1;
	}

	private static void listWebhooks(FabricClientCommandSource src) {
		Config c = ConfigManager.get();
		if (c.webhooks.isEmpty()) {
			src.sendFeedback(prefix().append(Text.literal(
				"No webhooks. Add one with /piccaxeutils discord webhook add <name> <url>").formatted(Formatting.GRAY)));
			return;
		}
		src.sendFeedback(Text.literal("Webhooks:").formatted(Formatting.GOLD, Formatting.BOLD));
		for (Config.WebhookEntry w : c.webhooks) {
			src.sendFeedback(Text.literal(" • " + w.name + " (as " + w.username + ")"
				+ (w.cooldownSeconds > 0 ? " cd:" + w.cooldownSeconds + "s" : "")
				+ (w.url == null || w.url.isBlank() ? " [no url]" : "")).formatted(Formatting.GRAY));
		}
		src.sendFeedback(Text.literal(" Assigned: chat=" + orNone(c.chatWebhook)
			+ ", notifier=" + orNone(c.notifierWebhook) + ", proximity=" + orNone(c.proximityWebhook)).formatted(Formatting.AQUA));
	}

	private static int doTest(FabricClientCommandSource src, Config.WebhookEntry wh, String label) {
		if (wh == null || wh.url == null || wh.url.isBlank()) {
			src.sendFeedback(prefix().append(Text.literal("No webhook '" + label + "' set (or it has no URL).").formatted(Formatting.RED)));
			return 0;
		}
		MinecraftClient client = src.getClient();
		src.sendFeedback(prefix().append(Text.literal("Testing webhook '" + label + "'…").formatted(Formatting.GRAY)));
		DiscordWebhook.send(wh.url, wh.username, "Test from Piccaxe's Lifesteal Utils (" + label + ")", false,
			result -> client.execute(() -> {
				if (client.player != null) {
					client.player.sendMessage(Text.literal("[LSU] Webhook '" + label + "' " + result)
						.formatted(result.startsWith("sent OK") ? Formatting.GREEN : Formatting.RED), false);
				}
			}));
		return 1;
	}

	private static String orNone(String s) {
		return s == null || s.isBlank() ? "(none)" : s;
	}

	private static int addRule(CommandContext<FabricClientCommandSource> ctx) {
		String webhook = StringArgumentType.getString(ctx, "webhook");
		String keyword = StringArgumentType.getString(ctx, "keyword").trim();
		ConfigManager.get().webhookRules.add(new Config.WebhookRule(webhook, keyword));
		ConfigManager.save();
		MutableText msg = prefix().append(Text.literal("Rule " + ConfigManager.get().webhookRules.size()
			+ " added: \"" + keyword + "\" -> " + webhook).formatted(Formatting.GREEN));
		if (ConfigManager.webhook(webhook) == null) {
			msg.append(Text.literal(" (no webhook '" + webhook + "' yet — add it with /piccaxeutils discord webhook add)")
				.formatted(Formatting.YELLOW));
		}
		ctx.getSource().sendFeedback(msg);
		return 1;
	}

	private static int removeRule(CommandContext<FabricClientCommandSource> ctx) {
		int oneBased = IntegerArgumentType.getInteger(ctx, "index");
		List<Config.WebhookRule> rules = ConfigManager.get().webhookRules;
		if (oneBased < 1 || oneBased > rules.size()) {
			ctx.getSource().sendFeedback(prefix().append(Text.literal("No rule #" + oneBased + ".").formatted(Formatting.RED)));
			return 0;
		}
		Config.WebhookRule removed = rules.remove(oneBased - 1);
		ConfigManager.save();
		ctx.getSource().sendFeedback(prefix().append(Text.literal("Removed rule: \"" + removed.keyword + "\"").formatted(Formatting.GRAY)));
		return 1;
	}

	private static int setRuleLabel(CommandContext<FabricClientCommandSource> ctx) {
		Config.WebhookRule rule = ruleAt(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "index"));
		if (rule == null) {
			return 0;
		}
		rule.label = StringArgumentType.getString(ctx, "text").trim();
		ConfigManager.save();
		ctx.getSource().sendFeedback(prefix().append(Text.literal("Rule label set: " + rule.label).formatted(Formatting.AQUA)));
		return 1;
	}

	private static int setRulePing(CommandContext<FabricClientCommandSource> ctx) {
		Config.WebhookRule rule = ruleAt(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "index"));
		if (rule == null) {
			return 0;
		}
		rule.ping = StringArgumentType.getString(ctx, "text").trim();
		ConfigManager.save();
		ctx.getSource().sendFeedback(prefix().append(Text.literal("Rule ping set: " + rule.ping).formatted(Formatting.AQUA)));
		return 1;
	}

	private static int toggleRule(CommandContext<FabricClientCommandSource> ctx) {
		Config.WebhookRule rule = ruleAt(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "index"));
		if (rule == null) {
			return 0;
		}
		rule.enabled = !rule.enabled;
		ConfigManager.save();
		ctx.getSource().sendFeedback(prefix().append(Text.literal("Rule \"" + rule.keyword + "\" "
			+ (rule.enabled ? "ON" : "OFF")).formatted(rule.enabled ? Formatting.GREEN : Formatting.RED)));
		return 1;
	}

	private static Config.WebhookRule ruleAt(FabricClientCommandSource src, int oneBased) {
		List<Config.WebhookRule> rules = ConfigManager.get().webhookRules;
		if (oneBased < 1 || oneBased > rules.size()) {
			src.sendFeedback(prefix().append(Text.literal("No rule #" + oneBased + " (see /piccaxeutils discord rule list)")
				.formatted(Formatting.RED)));
			return null;
		}
		return rules.get(oneBased - 1);
	}

	private static void listRules(FabricClientCommandSource src) {
		List<Config.WebhookRule> rules = ConfigManager.get().webhookRules;
		if (rules.isEmpty()) {
			src.sendFeedback(prefix().append(Text.literal(
				"No webhook rules. Add one with /piccaxeutils discord rule add <webhook> <keyword>").formatted(Formatting.GRAY)));
			return;
		}
		src.sendFeedback(Text.literal("Webhook rules:").formatted(Formatting.GOLD, Formatting.BOLD));
		for (int i = 0; i < rules.size(); i++) {
			Config.WebhookRule r = rules.get(i);
			String desc = (i + 1) + ". \"" + r.keyword + "\" -> " + r.webhook
				+ (r.label == null || r.label.isBlank() ? "" : "  label:" + r.label)
				+ (r.ping == null || r.ping.isBlank() ? "" : "  ping:" + r.ping)
				+ (r.enabled ? "" : "  (off)");
			src.sendFeedback(Text.literal(" " + desc).formatted(r.enabled ? Formatting.GRAY : Formatting.DARK_GRAY));
		}
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
		src.sendFeedback(Text.literal(" • Webhooks: " + c.webhooks.size() + "  (chat=" + orNone(c.chatWebhook)
			+ ", notifier=" + orNone(c.notifierWebhook) + ", proximity=" + orNone(c.proximityWebhook) + ")")
			.formatted(Formatting.GRAY));
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

	private static void openHudEditor(FabricClientCommandSource src) {
		MinecraftClient client = src.getClient();
		client.execute(() -> client.setScreen(new HudEditScreen(null)));
	}

	private static void printStatus(FabricClientCommandSource src) {
		Config c = ConfigManager.get();
		src.sendFeedback(Text.literal("Piccaxe's Lifesteal Utils").formatted(Formatting.GOLD, Formatting.BOLD));
		line(src, "All features", c.masterEnabled);
		line(src, "Heart HUD", c.heartHud);
		line(src, "Totem counter", c.totemHud);
		line(src, "Proximity alert", c.proximityAlert);
		line(src, "Player notifier", c.playerNotifier);
		line(src, "Health bars", c.healthBars);
		line(src, "Coordinates", c.coordsHud);
		line(src, "Death waypoint", c.deathWaypoint);
		line(src, "Auto-reconnect", c.autoReconnect);
		line(src, "Fullbright", c.fullbright);
		line(src, "No hurt-cam", c.noHurtCam);
		line(src, "Anti-Trickster", c.antiTrickster);
		line(src, "Anti-Sign", c.antiSign);
		line(src, "Armor stand bypass", c.armorStandBypass);
		line(src, "Nether portal bypass", c.netherPortalBypass);
		line(src, "Player outliner", c.playerOutliner);
		line(src, "Loot chest outliner", c.enderChestOutliner);
		line(src, "Discord relay", c.discordRelay);
	}

	/**
	 * Lists players currently loaded in your world (the only client-side-certain "same shard" set,
	 * since the network-wide custom tab has no shard markers and reports 0 ping for everyone here).
	 * Limited to players within render distance; sorted by distance with coordinates.
	 */
	private static void listShardPlayers(FabricClientCommandSource src) {
		MinecraftClient mc = src.getClient();
		if (mc.world == null || mc.player == null) {
			src.sendFeedback(prefix().append(Text.literal("Not in a world.").formatted(Formatting.RED)));
			return;
		}
		var players = new ArrayList<>(mc.world.getPlayers());
		players.sort(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p)));

		src.sendFeedback(Text.literal("Shard players in range (" + players.size() + "):")
			.formatted(Formatting.GOLD, Formatting.BOLD));
		for (var p : players) {
			String name = p.getName().getString();
			String coords = " [" + (int) Math.floor(p.getX()) + ", " + (int) Math.floor(p.getY())
				+ ", " + (int) Math.floor(p.getZ()) + "]";
			if (p == mc.player) {
				src.sendFeedback(Text.literal(" " + name + " (you)" + coords).formatted(Formatting.AQUA));
			} else {
				src.sendFeedback(Text.literal(" " + name + " — " + (int) mc.player.distanceTo(p) + "m" + coords)
					.formatted(Formatting.GRAY));
			}
		}
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
