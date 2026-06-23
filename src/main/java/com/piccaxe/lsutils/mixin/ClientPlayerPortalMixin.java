package com.piccaxe.lsutils.mixin;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets you keep GUIs and chat open while standing in a nether portal.
 *
 * <p>Vanilla {@code ClientPlayerEntity.tickNausea} force-closes any open screen every tick while
 * in a portal (and ramps the nausea). We cancel that portal branch when the bypass is on, so
 * screens stay open and the disorientation stops. The actual teleport is server-side and is not
 * affected by this.
 */
@Mixin(ClientPlayerEntity.class)
public class ClientPlayerPortalMixin {
	@Inject(method = "tickNausea", at = @At("HEAD"), cancellable = true)
	private void piccaxelsutils$keepScreensInPortal(boolean fromPortalEffect, CallbackInfo ci) {
		Config cfg = ConfigManager.get();
		if (fromPortalEffect && cfg.masterEnabled && cfg.netherPortalBypass) {
			ci.cancel();
		}
	}
}
