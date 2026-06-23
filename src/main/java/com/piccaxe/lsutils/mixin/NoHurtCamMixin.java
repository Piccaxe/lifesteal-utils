package com.piccaxe.lsutils.mixin;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels the camera tilt/shake applied when the player takes damage,
 * when the "No Hurt-Cam" visual tweak is enabled.
 */
@Mixin(GameRenderer.class)
public class NoHurtCamMixin {
	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
	private void piccaxelsutils$noHurtCam(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
		Config cfg = ConfigManager.get();
		if (cfg.masterEnabled && cfg.noHurtCam) {
			ci.cancel();
		}
	}
}
