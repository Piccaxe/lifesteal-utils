package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * While you're mining a block, switches your held hotbar slot to the fastest tool for that block.
 * Only acts when actively attacking a block with no GUI open; the held-slot change is synced to the
 * server by vanilla's normal slot-sync.
 */
public final class AutoTool {
	private AutoTool() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoTool::tick);
	}

	private static void tick(MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.autoTool || mc.player == null || mc.world == null) {
			return;
		}
		if (mc.currentScreen != null || !mc.options.attackKey.isPressed()) {
			return;
		}
		if (!(mc.crosshairTarget instanceof BlockHitResult hit) || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockState state = mc.world.getBlockState(hit.getBlockPos());
		if (state.isAir()) {
			return;
		}

		PlayerInventory inv = mc.player.getInventory();
		int best = inv.getSelectedSlot();
		float bestSpeed = inv.getStack(best).getMiningSpeedMultiplier(state);
		for (int i = 0; i < 9; i++) {
			float speed = inv.getStack(i).getMiningSpeedMultiplier(state);
			if (speed > bestSpeed) {
				bestSpeed = speed;
				best = i;
			}
		}
		if (best != inv.getSelectedSlot()) {
			inv.setSelectedSlot(best);
		}
	}
}
