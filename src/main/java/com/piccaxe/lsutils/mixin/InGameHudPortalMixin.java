package com.piccaxe.lsutils.mixin;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the nether portal screen overlay (the purple tint) while the bypass is on, so your
 * view stays clear while standing in a portal.
 */
@Mixin(InGameHud.class)
public class InGameHudPortalMixin {
	@Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
	private void piccaxelsutils$noPortalOverlay(DrawContext context, float nauseaStrength, CallbackInfo ci) {
		Config cfg = ConfigManager.get();
		if (cfg.masterEnabled && cfg.netherPortalBypass) {
			ci.cancel();
		}
	}
}
