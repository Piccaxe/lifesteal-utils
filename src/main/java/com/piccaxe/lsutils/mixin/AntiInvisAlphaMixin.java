package com.piccaxe.lsutils.mixin;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Makes the anti-invis reveal clearly visible: vanilla draws invisible-but-shown entities at ~15%
 * alpha (the tint constant {@code 654311423} = {@code 0x26FFFFFF}); when anti-invis is on we bump
 * that to ~50% ({@code 0x80FFFFFF}). Leaves the value untouched otherwise (e.g. real spectator view).
 */
@Mixin(LivingEntityRenderer.class)
public class AntiInvisAlphaMixin {
	@ModifyConstant(method = "render", constant = @Constant(intValue = 654311423))
	private int piccaxelsutils$invisAlpha(int original) {
		Config cfg = ConfigManager.get();
		return (cfg.masterEnabled && cfg.antiInvis) ? 0x80FFFFFF : original;
	}
}
