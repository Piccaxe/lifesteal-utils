package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Keeps a Totem of Undying in your offhand: the moment the offhand isn't a totem (used or empty) and
 * one is in your inventory, it swaps one into the offhand via slot clicks (only with no GUI open and
 * an empty cursor). A combat macro, like the armor swapper.
 */
public final class AutoTotem {
	private AutoTotem() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoTotem::tick);
	}

	private static void tick(MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.autoTotem || mc.player == null || mc.interactionManager == null) {
			return;
		}
		if (mc.currentScreen != null || !mc.player.playerScreenHandler.getCursorStack().isEmpty()) {
			return;
		}
		if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
			return;
		}

		ScreenHandler handler = mc.player.playerScreenHandler;
		int source = -1;
		for (int i = 9; i <= 44; i++) {
			if (handler.getSlot(i).getStack().isOf(Items.TOTEM_OF_UNDYING)) {
				source = i;
				break;
			}
		}
		if (source < 0) {
			return;
		}
		int syncId = handler.syncId;
		mc.interactionManager.clickSlot(syncId, source, 0, SlotActionType.PICKUP, mc.player);
		mc.interactionManager.clickSlot(syncId, 45, 0, SlotActionType.PICKUP, mc.player); // 45 = offhand
		mc.interactionManager.clickSlot(syncId, source, 0, SlotActionType.PICKUP, mc.player);
	}
}
