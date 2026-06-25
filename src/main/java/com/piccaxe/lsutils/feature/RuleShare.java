package com.piccaxe.lsutils.feature;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Export/import of webhook rules as a portable share code, so players can hand their rule setup to
 * teammates who also have the mod. The code is base64-encoded JSON ({@code PLSU1:} prefix) bundling
 * the rules plus the webhooks they reference (URLs included), exchanged via the system clipboard.
 */
public final class RuleShare {
	private static final String PREFIX = "PLSU1:";
	private static final Gson GSON = new Gson();

	private RuleShare() {
	}

	private static final class Payload {
		int v = 1;
		List<Config.WebhookRule> rules = new ArrayList<>();
		List<Config.WebhookEntry> webhooks = new ArrayList<>();
	}

	/** Outcome of an import attempt. */
	public static final class ImportResult {
		public final boolean ok;
		public final int rulesAdded;
		public final int webhooksAdded;
		public final String error;

		private ImportResult(int rules, int webhooks) {
			this.ok = true;
			this.rulesAdded = rules;
			this.webhooksAdded = webhooks;
			this.error = null;
		}

		private ImportResult(String error) {
			this.ok = false;
			this.rulesAdded = 0;
			this.webhooksAdded = 0;
			this.error = error;
		}
	}

	/** Builds a share code for all current rules plus the webhooks they reference. */
	public static String export() {
		Config cfg = ConfigManager.get();
		Payload payload = new Payload();
		payload.rules = new ArrayList<>(cfg.webhookRules);

		Set<String> referenced = new HashSet<>();
		for (Config.WebhookRule r : cfg.webhookRules) {
			if (r != null && r.webhook != null && !r.webhook.isBlank()) {
				referenced.add(r.webhook.toLowerCase(Locale.ROOT));
			}
		}
		for (Config.WebhookEntry w : cfg.webhooks) {
			if (w != null && w.name != null && referenced.contains(w.name.toLowerCase(Locale.ROOT))) {
				payload.webhooks.add(w);
			}
		}

		String json = GSON.toJson(payload);
		return PREFIX + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

	/** Number of rules a share code would carry (used for user feedback before sending). */
	public static int ruleCount() {
		return ConfigManager.get().webhookRules.size();
	}

	/** Copies a fresh share code to the system clipboard and returns it. */
	public static String exportToClipboard() {
		String code = export();
		MinecraftClient.getInstance().keyboard.setClipboard(code);
		return code;
	}

	/** Imports rules + webhooks from the current clipboard contents. */
	public static ImportResult importFromClipboard() {
		return importCode(MinecraftClient.getInstance().keyboard.getClipboard());
	}

	/** Decodes a share code and merges its rules + webhooks into the config (skipping duplicates). */
	public static ImportResult importCode(String code) {
		if (code == null || code.trim().isEmpty()) {
			return new ImportResult("nothing to import (empty)");
		}
		String c = code.trim();
		if (c.startsWith(PREFIX)) {
			c = c.substring(PREFIX.length()).trim();
		}
		Payload payload;
		try {
			byte[] decoded = Base64.getDecoder().decode(c);
			payload = GSON.fromJson(new String(decoded, StandardCharsets.UTF_8), Payload.class);
		} catch (IllegalArgumentException | JsonSyntaxException e) {
			return new ImportResult("not a valid share code");
		}
		if (payload == null || payload.rules == null) {
			return new ImportResult("not a valid share code");
		}

		Config cfg = ConfigManager.get();
		int webhooksAdded = 0;
		if (payload.webhooks != null) {
			for (Config.WebhookEntry w : payload.webhooks) {
				if (w == null || w.name == null || w.name.isBlank()) {
					continue;
				}
				if (ConfigManager.webhook(w.name) == null) {
					cfg.webhooks.add(w);
					webhooksAdded++;
				}
			}
		}

		int rulesAdded = 0;
		for (Config.WebhookRule r : payload.rules) {
			if (r == null) {
				continue;
			}
			if (r.ignore == null) {
				r.ignore = new ArrayList<>();
			}
			if (!isDuplicateRule(cfg, r)) {
				cfg.webhookRules.add(r);
				rulesAdded++;
			}
		}

		ConfigManager.save();
		return new ImportResult(rulesAdded, webhooksAdded);
	}

	private static boolean isDuplicateRule(Config cfg, Config.WebhookRule r) {
		for (Config.WebhookRule e : cfg.webhookRules) {
			if (eq(e.webhook, r.webhook) && eq(e.keyword, r.keyword) && eq(e.label, r.label)
				&& eq(e.ping, r.ping) && e.serverOnly == r.serverOnly) {
				return true;
			}
		}
		return false;
	}

	private static boolean eq(String a, String b) {
		return Objects.equals(a == null ? "" : a.trim(), b == null ? "" : b.trim());
	}
}
