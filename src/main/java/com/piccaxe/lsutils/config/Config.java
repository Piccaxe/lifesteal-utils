package com.piccaxe.lsutils.config;

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

	// --- Persisted death location (set by DeathTracker) ---
	public boolean hasDeath = false;
	public double deathX;
	public double deathY;
	public double deathZ;
	/** Dimension id where the last death happened, e.g. "minecraft:overworld". */
	public String deathDim = "";
}
