package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.Locale;

/**
 * Chat highlights: lines containing one of your keywords (or your own name, when someone else says it)
 * get a coloured "▌" marker and optional ping. Called from {@code ChatTweaksMixin}.
 */
public final class ChatHighlight {
	private static long lastSound = 0L;

	private ChatHighlight() {
	}

	public static Text apply(Text message) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.chatHighlight || message == null) {
			return message;
		}
		String raw = message.getString();
		String lower = raw.toLowerCase(Locale.ROOT);
		if (!matches(lower, cfg)) {
			return message;
		}
		if (cfg.chatHighlightSound) {
			long now = System.currentTimeMillis();
			MinecraftClient mc = MinecraftClient.getInstance();
			if (now - lastSound > 1000L && mc.player != null) {
				lastSound = now;
				mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.8F, 1.4F);
			}
		}
		MutableText bar = Text.literal("▌ ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(cfg.chatHighlightColor & 0xFFFFFF)));
		return bar.append(message);
	}

	private static boolean matches(String lower, Config cfg) {
		if (cfg.chatHighlightWords != null) {
			for (String w : cfg.chatHighlightWords.split(",")) {
				String k = w.trim().toLowerCase(Locale.ROOT);
				if (!k.isEmpty() && lower.contains(k)) {
					return true;
				}
			}
		}
		if (cfg.chatHighlightName) {
			MinecraftClient mc = MinecraftClient.getInstance();
			String me = mc.getGameProfile() == null ? null : mc.getGameProfile().name();
			if (me != null && !me.isBlank()) {
				String name = me.toLowerCase(Locale.ROOT);
				int at = lower.indexOf(name);
				if (at >= 0) {
					// Skip your own messages: your name in the author part (before the first ':' or '>').
					int sep = firstSeparator(lower);
					boolean authoredByMe = sep >= 0 && at < sep;
					return !authoredByMe || lower.indexOf(name, at + name.length()) >= 0;
				}
			}
		}
		return false;
	}

	private static int firstSeparator(String s) {
		int a = s.indexOf(':');
		int b = s.indexOf('>');
		if (a < 0) {
			return b;
		}
		return b < 0 ? a : Math.min(a, b);
	}
}
