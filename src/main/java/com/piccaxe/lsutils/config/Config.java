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

	// --- Potion effects HUD ---
	public boolean potionHud = false;
	public int potionHudX = 5;
	public int potionHudY = 70;

	// --- Inventory HUD (shows your main inventory) ---
	public boolean inventoryHud = false;
	public int inventoryHudX = 5;
	public int inventoryHudY = 92;

	/** Per-HUD-element scale multiplier, keyed by the Hud enum name (default 1.0). */
	public Map<String, Float> hudScales = new HashMap<>();
	/** Master multiplier applied on top of every element's own scale. */
	public float hudMasterScale = 1.0F;
	/** Per-element horizontal anchor: "LEFT" | "CENTER" | "RIGHT" (keyed by Hud enum name). */
	public Map<String, String> hudAlign = new HashMap<>();
	/** Snap HUD elements to screen edges/centers and to each other while dragging in the editor. */
	public boolean hudSnap = true;

	// --- Direction / compass HUD ---
	public boolean directionHud = false;
	public int directionHudX = 100;
	public int directionHudY = 2;
	public int directionHudWidth = 120;
	/** Minimal mode: just the heading text (e.g. "NE 47°"), no strip. */
	public boolean directionMinimal = false;
	/** Draw minor tick marks every 15° along the strip. */
	public boolean directionTicks = true;
	/** Draw a translucent background box behind the compass. */
	public boolean directionBackground = false;
	public int directionColor = 0xFFFFFF;
	public int directionMarkerColor = 0xFFFF55;
	public int directionNorthColor = 0xFF5555;

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
	/** Show the on-screen nearby-players HP list (draggable HUD element). */
	public boolean healthBarList = true;
	/** Show floating HP text above each entity's head. */
	public boolean healthBarOverhead = true;
	/** Display health as heart icons (♥) instead of a number (14/20). */
	public boolean healthBarHearts = false;
	public int healthBarListX = 5;
	public int healthBarListY = 120;
	/** For players, show health estimated from the damage you deal (vanilla doesn't sync it). */
	public boolean healthBarDamageEstimate = true;

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
	/** Remove the fog/tint while the camera is underwater. */
	public boolean noFogWater = false;
	/** Remove the fog while the camera is in lava (see through it). */
	public boolean noFogLava = false;
	/** Remove the atmospheric/biome distance fog. */
	public boolean noFogBiome = false;
	/** Reveal invisible players/mobs as semi-transparent (nametags still show). */
	public boolean antiInvis = false;

	// --- Armor swapper (auto-swap armor sets by health, matched by item lore/name) ---
	public boolean armorSwapper = false;
	/** At or below this health (HP, 2 = 1 heart) -> equip the defense set. */
	public double armorSwapLowHp = 8.0;
	/** At or above this health -> swap back to the normal set (after having gone defensive). */
	public double armorSwapHighHp = 16.0;
	public ArmorSet armorDefenseSet = new ArmorSet();
	public ArmorSet armorNormalSet = new ArmorSet();
	/** Print a chat message when the swapper fires (or can't find a piece) — handy for diagnosing. */
	public boolean armorSwapFeedback = true;

	// --- Anti-Trickster (auto-unscramble hotbar) ---
	public boolean antiTrickster = true;
	/** Only react when at least this many hotbar slots were displaced (ignores tiny reorders). */
	public int antiTricksterMinItems = 3;
	/** Logs anti-trickster detection details (temporary debugging aid). */
	public boolean antiTricksterDebug = false;

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

	// --- Trap outliner (pistons, pressure plates, string/tripwire, armor stands) ---
	public boolean trapOutliner = false;
	public int trapRadius = 24;
	public int trapColor = 0xFF5050;

	// --- Spawner activation ESP (green when a player is within activation range) ---
	public boolean spawnerOutliner = false;
	/** Player-proximity radius at which a spawner activates (vanilla = 16). */
	public int spawnerRange = 16;
	/** How far out to look for spawners to outline. */
	public int spawnerScanRadius = 48;
	public int spawnerActiveColor = 0x55FF55;
	public int spawnerInactiveColor = 0xFF5555;

	// --- Custom-enchant cooldown HUD (starts a timer when a chat message matches a rule's keyword) ---
	public boolean cooldownHud = false;
	public int cooldownHudX = 5;
	public int cooldownHudY = 220;
	/** Play a ding + chat note when a tracked cooldown finishes. */
	public boolean cooldownReadySound = true;
	public List<CooldownRule> cooldownRules = new ArrayList<>();

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

	// --- Death / Kill relay (auto-posts your deaths & kills to Discord) ---
	public boolean deathKillRelay = false;
	public boolean relayMyDeaths = true;
	public boolean relayMyKills = true;
	public String deathKillWebhook = "";
	/** Optional ping included on your-death posts (e.g. @here). */
	public String deathKillPing = "";

	// --- Auto-clipper (presses your recording app's clip hotkey on a kill) ---
	public boolean autoClip = false;
	public boolean autoClipOnKill = true;
	public boolean autoClipOnDeath = false;
	/** Hotkey to send, e.g. "ALT+F10" (ShadowPlay), or your OBS/Medal clip hotkey. */
	public String clipHotkey = "ALT+F10";
	public int clipCooldownSeconds = 3;

	// --- Auto-totem / auto-tool (combat/QoL macros) ---
	public boolean autoTotem = false;
	public boolean autoTool = false;

	// --- Hitmarkers ---
	public boolean hitMarkers = false;
	public boolean hitMarkerSound = true;

	// --- Combat tag ---
	public boolean combatTag = false;
	public int combatTagSeconds = 15;
	public int combatTagHudX = 5;
	public int combatTagHudY = 140;

	// --- Armor + durability HUD ---
	public boolean armorHud = false;
	public int armorHudX = 5;
	public int armorHudY = 158;

	// --- FPS / ping HUD ---
	public boolean fpsHud = false;
	public int fpsHudX = 5;
	public int fpsHudY = 180;

	/** FOV multiplier while the zoom key is held. */
	public double zoomFactor = 0.3;

	// --- Auto-eat (eats a food at low HP) ---
	public boolean autoEat = false;
	public double autoEatHp = 8.0;
	/** Item id substring to eat (e.g. "golden_apple", "potion"). */
	public String autoEatKeyword = "golden_apple";

	// --- Screenshot to Discord on kill (uses the death/kill webhook) ---
	public boolean screenshotOnKill = false;

	// --- Chat tweaks ---
	public boolean chatTimestamps = false;
	public boolean chatAntiSpam = false;

	// --- Hotbar key remap (honest: items stay where they are; this only changes which physical
	//     slot each number key 1-9 selects, so you can reorganize and keep your muscle memory).
	//     hotbarKeyMap[k] = the 0-based physical slot that pressing key (k+1) should select. ---
	public boolean hotbarRemap = false;
	public int[] hotbarKeyMap = {0, 1, 2, 3, 4, 5, 6, 7, 8};
	/** Draw a small number on each hotbar slot showing which key selects it (reflects the remap). */
	public boolean hotbarKeyLabels = true;

	// --- Music overlay (Windows "Now Playing" via SMTC) ---
	public boolean musicOverlay = false;
	public int musicHudX = 5;
	public int musicHudY = 200;

	// --- Low-HP alert: red screen hue + warning sound when health drops below the threshold ---
	public boolean lowHpAlert = false;
	/** Health points (not hearts) below which the alert fires. 10 HP = 5 hearts. */
	public double lowHpThreshold = 10.0;
	public boolean lowHpSound = true;

	// --- Potion low-duration warning (e.g. fire resistance / strength about to run out) ---
	public boolean potionWarn = false;
	/** Seconds remaining at which to send the chat + action-bar warning. */
	public int potionWarnSeconds = 10;
	/** Seconds remaining at which to play the alert sound. */
	public int potionSoundSeconds = 3;
	/** Effect id paths (or substrings) to watch, e.g. "fire_resistance", "strength". */
	public List<String> potionWarnEffects = new ArrayList<>(List.of("fire_resistance", "strength"));

	/** Returns a valid 9-length key map, repairing/replacing a missing or malformed one. */
	public int[] hotbarKeyMapSafe() {
		if (hotbarKeyMap == null || hotbarKeyMap.length != 9) {
			hotbarKeyMap = new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8};
		}
		for (int i = 0; i < 9; i++) {
			if (hotbarKeyMap[i] < 0 || hotbarKeyMap[i] > 8) {
				hotbarKeyMap[i] = i;
			}
		}
		return hotbarKeyMap;
	}

	// --- Heart tracker (lifesteal max-heart gains/losses) ---
	public boolean heartTracker = false;
	public boolean heartTrackerHud = true;
	public int heartTrackerHudX = 5;
	public int heartTrackerHudY = 56;
	/** Announce heart gains/losses in chat. */
	public boolean heartTrackerChat = true;
	/** Post to Discord when you lose a heart. */
	public boolean heartTrackerDiscord = false;
	public String heartWebhook = "";
	public String heartLossPing = "";

	// --- Auto-updater (checks GitHub releases) ---
	public boolean autoUpdate = true;
	/** Download + swap in the new jar on game close. If false, just notify. */
	public boolean autoUpdateApply = true;
	/** GitHub "owner/repo" to pull releases from (blank = updater disabled). */
	public String updateRepo = "Piccaxe/lifesteal-utils";

	// --- Persisted death location (set by DeathTracker) ---
	public boolean hasDeath = false;
	public double deathX;
	public double deathY;
	public double deathZ;
	/** Dimension id where the last death happened, e.g. "minecraft:overworld". */
	public String deathDim = "";

	/** An armor set identified by item lore/name. {@code keyword} matches any piece; the per-slot
	 *  fields override it for that slot when non-blank. Blank entries are skipped. */
	public static class ArmorSet {
		public String keyword = "";
		public String helmet = "";
		public String chest = "";
		public String legs = "";
		public String boots = "";
	}

	/** A tracked custom-enchant cooldown. When a received chat line contains {@code keyword}, a
	 *  {@code seconds}-long timer named {@code name} starts (or refreshes) in the cooldown HUD. */
	public static class CooldownRule {
		public String name = "";
		public String keyword = "";
		public int seconds = 0;
		public boolean enabled = true;

		public CooldownRule() {
		}

		public CooldownRule(String name, String keyword, int seconds) {
			this.name = name;
			this.keyword = keyword;
			this.seconds = seconds;
		}
	}

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
		/** When true this rule only matches server/system messages, never player chat. */
		public boolean serverOnly = false;
		/** Sender names or substrings to skip (e.g. specific players, or words). */
		public List<String> ignore = new ArrayList<>();

		public WebhookRule() {
		}

		public WebhookRule(String webhook, String keyword) {
			this.webhook = webhook;
			this.keyword = keyword;
		}
	}
}
