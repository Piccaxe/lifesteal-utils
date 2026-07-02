package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

/**
 * Insta-mend: on the keybind, rapidly throws your entire stack(s) of Experience Bottles to repair
 * Mending gear fast. Toggles a dumping mode that throws one bottle every {@link #INTERVAL} ticks
 * (from the hotbar) until none are left; press again to stop early.
 *
 * <p>NOTE: this is a rapid item-use macro — automating a legitimate action, but fast auto-use can
 * draw anticheat attention on some servers. Your call.
 */
public final class InstaMend {
	private static boolean active = false;

	private InstaMend() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(InstaMend::tick);
	}

	/** Toggle dumping (called from the keybind). */
	public static void toggle(MinecraftClient mc) {
		active = !active;
		if (mc.player != null) {
			mc.player.sendMessage(Text.literal(active ? "Insta-mend: throwing XP bottles…" : "Insta-mend: stopped")
				.formatted(active ? Formatting.GREEN : Formatting.YELLOW), true);
		}
	}

	private static void tick(MinecraftClient mc) {
		if (!active) {
			return;
		}
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || mc.player == null || mc.interactionManager == null
			|| mc.currentScreen != null || !mc.player.isAlive()) {
			active = false;
			return;
		}

		// Throw several bottles per tick (bypassing the vanilla right-click delay) for max speed.
		int perTick = Math.max(1, cfg.instaMendPerTick);
		for (int n = 0; n < perTick; n++) {
			int slot = findBottle(mc);
			if (slot < 0) {
				active = false;
				mc.player.sendMessage(Text.literal("Insta-mend: out of XP bottles").formatted(Formatting.YELLOW), true);
				return;
			}
			mc.player.getInventory().setSelectedSlot(slot);
			mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
		}
		mc.player.swingHand(Hand.MAIN_HAND);
	}

	private static int findBottle(MinecraftClient mc) {
		for (int i = 0; i < 9; i++) {
			if (mc.player.getInventory().getStack(i).isOf(Items.EXPERIENCE_BOTTLE)) {
				return i;
			}
		}
		return -1;
	}
}
