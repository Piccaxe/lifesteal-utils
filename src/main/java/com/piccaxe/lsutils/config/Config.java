package com.piccaxe.lsutils.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain data holder for all settings. Serialized to/from JSON by {@link ConfigManager}.
 * Public fields with sane defaults keep the Gson mapping trivial.
 */
public class Config {
	/** Master switch — when false, every feature is suppressed. */
	public boolean masterEnabled = true;

	// --- HUD elements (each independently positioned; drag via the HUD editor) ---
	public boolean heartHud = true;
	public int heartHudX = 5;
	public int heartHudY = 5;

	public boolean totemHud = true;
	public int totemHudX = 5;
	public int totemHudY = 16;
	/** Totem count at or below this turns the counter red and flashing. */
	public int totemWarnThreshold = 1;

	public boolean coordsHud = true;
	public int coordsHudX = 5;
	public int coordsHudY = 34;

	public boolean deathWaypoint = true;
	public int deathHudX = 5;
	public int deathHudY = 45;

	// --- Player notifier (someone enters render distance) ---
	public boolean playerNotifier = true;
	public boolean notifierChat = true;
	public boolean notifierSound = true;
	public boolean notifierBanner = true;
	/** Off by default to avoid spamming the webhook on busy servers. */
	public boolean notifierDiscord = false;
	/** Lowercased player names the notifier should never alert for. */
	public List<String> notifierIgnore = new ArrayList<>();

	// --- Health bars above entities ---
	public boolean healthBars = true;
	public double healthBarRange = 24.0;
	public boolean healthBarPlayersOnly = false;

	// --- Proximity alert (close range, within radius) ---
	public boolean proximityAlert = true;
	/** Alert when another player is within this many blocks. */
	public double proximityRadius = 64.0;
	public boolean proximitySound = true;
	/** Lowercased player names the proximity alert should never fire for. */
	public List<String> proximityIgnore = new ArrayList<>();
	/** Send a (pinging) Discord webhook when a watchlisted player enters proximity. */
	public boolean proximityDiscord = false;
	/** Lowercased player names that trigger the Discord ping when they enter proximity. */
	public List<String> proximityWatchlist = new ArrayList<>();
	/** Ping prefix included in the webhook message, e.g. "@here", "@everyone", "&lt;@USERID&gt;", "&lt;@&amp;ROLEID&gt;". */
	public String proximityPing = "@here";

	// --- Auto-reconnect ---
	public boolean autoReconnect = false;
	public int autoReconnectDelaySeconds = 5;
	public int autoReconnectMaxAttempts = 10;

	// --- Visual tweaks ---
	public boolean fullbright = false;
	public boolean noHurtCam = false;

	// --- Anti-Trickster (auto-unscramble hotbar) ---
	public boolean antiTrickster = true;

	// --- Bypass / utility modules ---
	/** Sign passthrough: makes signs fully click-through so you interact with the block behind them. */
	public boolean antiSign = false;
	/** Makes armor stands click-through so you can interact past them. */
	public boolean armorStandBypass = false;
	/** Removes the nether portal screen overlay so you can see/use screens while standing in one. */
	public boolean netherPortalBypass = false;

	// --- Player outliner (glow by nametag color, with per-player overrides) ---
	public boolean playerOutliner = false;
	public int outlineColorTeammate = 0x55FF55;
	public int outlineColorAlly = 0x5555FF;
	public int outlineColorEnemy = 0xFF5555;
	/** Lowercased player name -> "teammate"/"ally"/"enemy"/"none". Overrides auto color detection. */
	public Map<String, String> outlineOverrides = new HashMap<>();

	// --- Ender chest ("loot chest") outliner ---
	public boolean enderChestOutliner = true;
	public int enderChestColor = 0xC04BFF;
	public int enderChestRadius = 48;
	public boolean enderChestDistanceLabel = true;
	public boolean enderChestTracer = false;
	/** Walking-route path tracer (A* around obstacles), and which chest(s) it routes to. */
	public boolean enderChestPathTracer = false;
	/** "nearest", "looking", or "all" (capped). */
	public String enderChestPathMode = "nearest";
	public int enderChestPathColor = 0x44FF88;

	// --- Discord webhooks (named; each category below routes to one by name) ---
	public List<WebhookEntry> webhooks = new ArrayList<>();
	public String chatWebhook = "";
	public String notifierWebhook = "";
	public String proximityWebhook = "";
	/** Custom keyword -> webhook rules: any keyword routes to any webhook with optional label/ping. */
	public List<WebhookRule> webhookRules = new ArrayList<>();

	// --- Discord chat relay ---
	public boolean discordRelay = false;
	/** Legacy single-webhook fields, kept only to migrate old configs into {@link #webhooks}. */
	public String discordWebhookUrl = "";
	public String discordUsername = "Lifesteal Utils";

	public boolean relayTeamChat = true;
	public boolean relayWhispers = true;
	public boolean relayMentions = true;
	public boolean relayKeywords = true;

	/** Regex (case-insensitive) that mark a message as a whisper/DM. Tune per server. */
	public List<String> whisperPatterns = new ArrayList<>(List.of(
		"(?i)whispers? to you",
		"(?i)you whisper to",
		"(?i)^from\\s+\\w",
		"(?i)->\\s*you\\b"
	));
	/** Regex (case-insensitive) that mark a message as team chat. Tune per server. */
	public List<String> teamChatPatterns = new ArrayList<>(List.of(
		"(?i)^\\[team\\]",
		"(?i)^team\\s*[>|:]",
		"(?i)\\bteam chat\\b"
	));
	/** Case-insensitive substrings; any match forwards the message. */
	public List<String> keywords = new ArrayList<>();

	// --- Persisted death location (set by DeathTracker) ---
	public boolean hasDeath = false;
	public double deathX;
	public double deathY;
	public double deathZ;
	/** Dimension id where the last death happened, e.g. "minecraft:overworld". */
	public String deathDim = "";

	/** A named Discord webhook target. */
	public static class WebhookEntry {
		public String name = "";
		public String url = "";
		public String username = "Lifesteal Utils";
		/** Minimum seconds between sends to this webhook (0 = no cooldown). */
		public int cooldownSeconds = 0;

		public WebhookEntry() {
		}

		public WebhookEntry(String name, String url, String username) {
			this.name = name;
			this.url = url;
			this.username = username;
		}
	}

	/** A rule: when a chat message contains {@link #keyword}, forward it to webhook {@link #webhook}. */
	public static class WebhookRule {
		public String webhook = "";
		public String keyword = "";
		public String label = "";
		public String ping = "";
		public boolean enabled = true;

		public WebhookRule() {
		}

		public WebhookRule(String webhook, String keyword) {
			this.webhook = webhook;
			this.keyword = keyword;
		}
	}
}
