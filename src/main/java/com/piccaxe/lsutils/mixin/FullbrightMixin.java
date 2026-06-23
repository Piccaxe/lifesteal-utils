package com.piccaxe.lsutils.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Implements fullbright by overriding the gamma value the lightmap reads.
 *
 * <p>In {@code LightmapTextureManager.update(float)} the brightness is computed from
 * {@code this.client.options.getGamma().getValue()}. That is the third
 * {@code SimpleOption.getValue()} call in the method (after hide-lightning-flashes and
 * darkness-effect-scale), so we target it by ordinal and return a large value when
 * fullbright is enabled. MixinExtras is bundled with the Fabric loader.
 */
@Mixin(LightmapTextureManager.class)
public class FullbrightMixin {
	@ModifyExpressionValue(
		method = "update(F)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;",
			ordinal = 2
		)
	)
	private Object piccaxelsutils$fullbright(Object original) {
		Config cfg = ConfigManager.get();
		if (cfg.masterEnabled && cfg.fullbright) {
			return 15.0d;
		}
		return original;
	}
}
