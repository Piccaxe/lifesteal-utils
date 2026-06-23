package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.util.ActionResult;

/**
 * The old "Sign Lock" mod, folded in as a module: while enabled, cancels all interaction with
 * signs (standing, wall, hanging) client-side before any packet is sent.
 */
public final class AntiSign {
	private AntiSign() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			Config cfg = ConfigManager.get();
			if (cfg.masterEnabled && cfg.antiSign
				&& world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof AbstractSignBlock) {
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
	}
}
