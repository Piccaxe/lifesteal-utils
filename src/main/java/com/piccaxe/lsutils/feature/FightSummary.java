package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks each fight (per opponent) — confirmed hits, crits, estimated damage dealt and totems popped —
 * and posts a summary to chat (and optionally Discord) when you kill them or die to them.
 * Fights with no action for {@link #FIGHT_TIMEOUT_MS} are dropped quietly.
 */
public final class FightSummary {
	private static final long FIGHT_TIMEOUT_MS = 30_000L;

	private static final class Fight {
		String name;
		int hits;
		int crits;
		float damage;
		int pops;
		long start;
		long last;
	}

	private static final Map<UUID, Fight> fights = new HashMap<>();

	private FightSummary() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			long now = System.currentTimeMillis();
			fights.values().removeIf(f -> now - f.last > FIGHT_TIMEOUT_MS);
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				onMessage(message.getString());
			}
		});
	}

	private static Fight fight(PlayerEntity p) {
		long now = System.currentTimeMillis();
		Fight f = fights.computeIfAbsent(p.getUuid(), id -> {
			Fight nf = new Fight();
			nf.start = now;
			return nf;
		});
		f.name = p.getName().getString();
		f.last = now;
		return f;
	}

	/** A hit the server confirmed landed. */
	public static void onHit(PlayerEntity target, float damage, boolean crit) {
		Fight f = fight(target);
		f.hits++;
		f.damage += damage;
		if (crit) {
			f.crits++;
		}
	}

	public static void onPop(PlayerEntity target) {
		if (fights.containsKey(target.getUuid())) {
			fight(target).pops++;
		}
	}

	public static boolean isFighting(UUID id) {
		Fight f = fights.get(id);
		return f != null && System.currentTimeMillis() - f.last <= FIGHT_TIMEOUT_MS;
	}

	private static void onMessage(String raw) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.fightSummary || fights.isEmpty()) {
			return;
		}
		int involvement = DeathKillRelay.involvement(raw);
		if (involvement < 0) {
			return;
		}
		UUID match = null;
		for (Map.Entry<UUID, Fight> e : fights.entrySet()) {
			String name = e.getValue().name;
			boolean hit = involvement == 1 ? DeathKillRelay.isVictim(raw, name) : DeathKillRelay.isKiller(raw, name);
			if (hit) {
				match = e.getKey();
				break;
			}
		}
		if (match == null && involvement == 0) {
			// Died to something unnamed (fall, lava…) mid-fight: summarize the most recent fight.
			long best = -1;
			for (Map.Entry<UUID, Fight> e : fights.entrySet()) {
				if (e.getValue().last > best) {
					best = e.getValue().last;
					match = e.getKey();
				}
			}
		}
		if (match == null) {
			return;
		}
		Fight f = fights.remove(match);
		if (f.hits < cfg.fightSummaryMinHits && f.pops == 0) {
			return;
		}
		post(f, involvement == 1, cfg);
	}

	private static void post(Fight f, boolean won, Config cfg) {
		long secs = Math.max(1, (System.currentTimeMillis() - f.start) / 1000);
		String body = f.hits + " hits (" + f.crits + " crits) · ~" + Math.round(f.damage) + " dmg · "
			+ f.pops + " pops · " + secs + "s";
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			MutableText t = Text.literal("[LSU] ").formatted(Formatting.GOLD)
				.append(Text.literal((won ? "Won vs " : "Lost to ") + f.name + ": ").formatted(won ? Formatting.GREEN : Formatting.RED))
				.append(Text.literal(body).formatted(Formatting.GRAY));
			mc.player.sendMessage(t, false);
		}
		if (cfg.fightSummaryDiscord) {
			Config.WebhookEntry wh = ConfigManager.webhook(cfg.deathKillWebhook);
			if (wh != null && wh.url != null && !wh.url.isBlank()) {
				DiscordWebhook.sendThrottled(wh, (won ? "🏆 Won vs **" : "☠️ Lost to **") + f.name + "** — " + body, false);
			}
		}
	}
}
