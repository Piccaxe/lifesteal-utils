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

	// --- HUD ---
	/** Top-left origin for the stacked info HUD. */
	public int hudX = 5;
	public int hudY = 5;

	public boolean heartHud = true;
	public boolean totemHud = true;
	/** Totem count at or below this turns the counter red and flashing. */
	public int totemWarnThreshold = 1;
	public boolean coordsHud = true;
	public boolean deathWaypoint = true;

	// --- Proximity alert ---
	public boolean proximityAlert = true;
	/** Alert when another player is within this many blocks. */
	public double proximityRadius = 64.0;
	public boolean proximitySound = true;

	// --- Auto-reconnect ---
	public boolean autoReconnect = false;
	public int autoReconnectDelaySeconds = 5;
	public int autoReconnectMaxAttempts = 10;

	// --- Visual tweaks ---
	public boolean fullbright = false;
	public boolean noHurtCam = false;

	// --- Anti-Trickster (auto-unscramble hotbar) ---
	public boolean antiTrickster = true;

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

	// --- Discord chat relay (webhook) ---
	public boolean discordRelay = false;
	public String discordWebhookUrl = "";
	/** Display name the webhook posts under. */
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
}
