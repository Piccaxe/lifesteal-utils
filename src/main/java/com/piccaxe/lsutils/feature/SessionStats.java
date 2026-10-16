package com.piccaxe.lsutils.feature;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Per-connection stats for the {@code SESSION} HUD element: kills, deaths, current/best kill streak,
 * and hearts gained/lost (your max health vs. when you joined). Resets each time you join a server.
 */
public final class SessionStats {
	private static final int SETTLE_TICKS = 60; // let the server sync max health before taking the baseline

	private static int kills;
	private static int deaths;
	private static int streak;
	private static int bestStreak;
	private static float baselineMax = -1.0F;
	private static int sinceJoin;

	private SessionStats() {
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) {
				return;
			}
			int inv = DeathKillRelay.involvement(message.getString());
			if (inv == 1) {
				kills++;
				streak++;
				bestStreak = Math.max(bestStreak, streak);
			} else if (inv == 0) {
				deaths++;
				streak = 0;
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) {
				return;
			}
			if (++sinceJoin == SETTLE_TICKS) {
				baselineMax = mc.player.getMaxHealth();
			}
		});
	}

	private static void reset() {
		kills = deaths = streak = bestStreak = 0;
		baselineMax = -1.0F;
		sinceJoin = 0;
	}

	/** Draws the HUD element and returns {width, height}. */
	public static int[] render(DrawContext ctx, MinecraftClient mc, int x, int y, boolean sample) {
		TextRenderer tr = mc.textRenderer;
		int k = sample ? 7 : kills;
		int d = sample ? 2 : deaths;
		int s = sample ? 3 : streak;
		int best = sample ? 5 : bestStreak;
		float heartDelta = sample ? 2 : (baselineMax < 0 || mc.player == null ? 0 : (mc.player.getMaxHealth() - baselineMax) / 2.0F);

		String kd = d == 0 ? String.valueOf(k) : String.format("%.2f", (double) k / d);
		MutableText l1 = Text.literal("K ").formatted(Formatting.GRAY).append(Text.literal(String.valueOf(k)).formatted(Formatting.GREEN))
			.append(Text.literal("  D ").formatted(Formatting.GRAY)).append(Text.literal(String.valueOf(d)).formatted(Formatting.RED))
			.append(Text.literal("  K/D ").formatted(Formatting.GRAY)).append(Text.literal(kd).formatted(Formatting.WHITE));
		MutableText l2 = Text.literal("Streak ").formatted(Formatting.GRAY).append(Text.literal(String.valueOf(s)).formatted(Formatting.GOLD))
			.append(Text.literal(" (best " + best + ")").formatted(Formatting.DARK_GRAY));
		int hd = Math.round(heartDelta);
		MutableText l3 = Text.literal("Hearts ").formatted(Formatting.GRAY)
			.append(Text.literal((hd > 0 ? "+" : "") + hd + "❤").formatted(hd > 0 ? Formatting.GREEN : hd < 0 ? Formatting.RED : Formatting.WHITE));

		ctx.drawTextWithShadow(tr, l1, x, y, 0xFFFFFFFF);
		ctx.drawTextWithShadow(tr, l2, x, y + 10, 0xFFFFFFFF);
		ctx.drawTextWithShadow(tr, l3, x, y + 20, 0xFFFFFFFF);
		int w = Math.max(tr.getWidth(l1), Math.max(tr.getWidth(l2), tr.getWidth(l3)));
		return new int[]{w, 29};
	}
}
