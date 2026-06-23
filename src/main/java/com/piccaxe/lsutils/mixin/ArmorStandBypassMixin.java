package com.piccaxe.lsutils.mixin;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes armor stands non-targetable while the bypass is on, so your crosshair (and clicks) pass
 * through them to whatever's behind — letting you interact while standing in/among armor stands.
 * {@code canHit()} gates crosshair targeting and attacks, so forcing it false achieves click-through.
 */
@Mixin(ArmorStandEntity.class)
public class ArmorStandBypassMixin {
	@Inject(method = "canHit", at = @At("HEAD"), cancellable = true)
	private void piccaxelsutils$bypass(CallbackInfoReturnable<Boolean> cir) {
		Config cfg = ConfigManager.get();
		if (cfg.masterEnabled && cfg.armorStandBypass) {
			cir.setReturnValue(false);
		}
	}
}
