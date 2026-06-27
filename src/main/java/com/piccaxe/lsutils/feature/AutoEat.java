package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.Locale;

/**
 * Auto-eat: when your health drops to/below {@code autoEatHp} and a matching food (default golden
 * apples) is in your hotbar, it selects it and holds the use key until it's eaten, then restores your
 * slot. The client itself drives the eat while the use key reads as held. A combat macro.
 */
public final class AutoEat {
	private static boolean eating = false;
	private static boolean started = false;
	private static int prevSlot = -1;
	private static long eatStart = 0L;

	private AutoEat() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoEat::tick);
	}

	private static void tick(MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (mc.player == null || !cfg.masterEnabled || !cfg.autoEat || mc.currentScreen != null) {
			stop(mc);
			return;
		}

		if (eating) {
			mc.options.useKey.setPressed(true);
			if (mc.player.isUsingItem()) {
				started = true;
			}
			boolean done = started && !mc.player.isUsingItem();
			boolean recovered = mc.player.getHealth() > cfg.autoEatHp;
			boolean timedOut = System.currentTimeMillis() - eatStart > 4000L;
			if (done || timedOut || (recovered && !mc.player.isUsingItem())) {
				stop(mc);
			}
			return;
		}

		if (mc.player.getHealth() > cfg.autoEatHp) {
			return;
		}
		int slot = findFood(mc, cfg);
		if (slot < 0) {
			return;
		}
		prevSlot = mc.player.getInventory().getSelectedSlot();
		mc.player.getInventory().setSelectedSlot(slot);
		mc.options.useKey.setPressed(true);
		eating = true;
		started = false;
		eatStart = System.currentTimeMillis();
	}

	private static void stop(MinecraftClient mc) {
		if (!eating) {
			return;
		}
		eating = false;
		started = false;
		if (mc != null) {
			mc.options.useKey.setPressed(false);
			if (prevSlot >= 0 && mc.player != null) {
				mc.player.getInventory().setSelectedSlot(prevSlot);
			}
		}
		prevSlot = -1;
	}

	private static int findFood(MinecraftClient mc, Config cfg) {
		String kw = (cfg.autoEatKeyword == null || cfg.autoEatKeyword.isBlank())
			? "golden_apple" : cfg.autoEatKeyword.toLowerCase(Locale.ROOT);
		PlayerInventory inv = mc.player.getInventory();
		for (int i = 0; i < 9; i++) {
			ItemStack st = inv.getStack(i);
			if (!st.isEmpty() && Registries.ITEM.getId(st.getItem()).getPath().contains(kw)) {
				return i;
			}
		}
		return -1;
	}
}
