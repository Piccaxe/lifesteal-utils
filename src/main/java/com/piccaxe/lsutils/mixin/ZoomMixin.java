package com.piccaxe.lsutils.mixin;

import com.piccaxe.lsutils.KeyBindings;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optifine-style zoom: shrinks the FOV while the zoom key is held. */
@Mixin(GameRenderer.class)
public class ZoomMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void piccaxelsutils$zoom(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
		if (KeyBindings.zoom != null && KeyBindings.zoom.isPressed() && ConfigManager.get().masterEnabled) {
			cir.setReturnValue(cir.getReturnValue() * (float) ConfigManager.get().zoomFactor);
		}
	}
}
