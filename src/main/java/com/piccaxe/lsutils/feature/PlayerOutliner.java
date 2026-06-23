package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.Locale;

/**
 * Decides what color (if any) to glow-outline a player with.
 *
 * <p>Category is taken from the player's nametag/team color by default
 * (green → teammate, blue/aqua → ally, red → enemy), and can be overridden
 * per-player via {@code /piccaxeutils outline set <player> <category>}.
 *
 * <p>The actual rendering is done by {@code PlayerOutlineMixin}, which calls
 * {@link #outlineColorFor(PlayerEntity)} when building each entity's render state.
 */
public final class PlayerOutliner {
	public enum Category {
		TEAMMATE, ALLY, ENEMY, NONE
	}

	private PlayerOutliner() {
	}

	/** ARGB outline color for this player, or 0 (no outline). */
	public static int outlineColorFor(PlayerEntity player) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.playerOutliner) {
			return 0;
		}
		int rgb = switch (categoryFor(player, cfg)) {
			case TEAMMATE -> cfg.outlineColorTeammate;
			case ALLY -> cfg.outlineColorAlly;
			case ENEMY -> cfg.outlineColorEnemy;
			case NONE -> 0;
		};
		return rgb == 0 ? 0 : (0xFF000000 | (rgb & 0xFFFFFF));
	}

	public static Category categoryFor(PlayerEntity player, Config cfg) {
		String key = player.getName().getString().toLowerCase(Locale.ROOT);
		String override = cfg.outlineOverrides.get(key);
		if (override != null) {
			Category parsed = parse(override);
			if (parsed != null) {
				return parsed;
			}
		}
		return detectFromColor(player);
	}

	private static Category detectFromColor(PlayerEntity player) {
		Formatting color = teamColor(player);
		if (color == null) {
			color = nameColor(player);
		}
		if (color == null) {
			return Category.NONE;
		}
		return switch (color) {
			case GREEN, DARK_GREEN -> Category.TEAMMATE;
			case BLUE, DARK_BLUE, AQUA, DARK_AQUA -> Category.ALLY;
			case RED, DARK_RED -> Category.ENEMY;
			default -> Category.NONE;
		};
	}

	private static Formatting teamColor(PlayerEntity player) {
		Team team = player.getScoreboardTeam();
		if (team != null) {
			Formatting color = team.getColor();
			if (color != null && color.isColor()) {
				return color;
			}
		}
		return null;
	}

	private static Formatting nameColor(PlayerEntity player) {
		TextColor textColor = player.getDisplayName().getStyle().getColor();
		if (textColor == null) {
			return null;
		}
		int rgb = textColor.getRgb();
		for (Formatting fmt : Formatting.values()) {
			if (fmt.isColor()) {
				Integer value = fmt.getColorValue();
				if (value != null && value == rgb) {
					return fmt;
				}
			}
		}
		return null;
	}

	public static Category parse(String value) {
		try {
			return Category.valueOf(value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
