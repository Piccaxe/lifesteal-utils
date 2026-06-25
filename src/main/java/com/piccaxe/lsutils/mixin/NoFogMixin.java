package com.piccaxe.lsutils.mixin;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.AtmosphericFogModifier;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.LavaFogModifier;
import net.minecraft.client.render.fog.WaterFogModifier;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional no-fog: after each fog modifier sets its distances, push them past the view so fog (and
 * its tint) effectively disappears. Targets the three environment modifiers — water, lava and the
 * atmospheric/biome haze — each gated by its own config toggle.
 */
@Mixin({WaterFogModifier.class, LavaFogModifier.class, AtmosphericFogModifier.class})
public class NoFogMixin {
	@Inject(method = "applyStartEndModifier", at = @At("TAIL"))
	private void piccaxelsutils$removeFog(FogData data, Camera camera, ClientWorld world, float f,
			RenderTickCounter renderTickCounter, CallbackInfo ci) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled) {
			return;
		}
		Object self = this;
		boolean remove = (self instanceof WaterFogModifier && cfg.noFogWater)
			|| (self instanceof LavaFogModifier && cfg.noFogLava)
			|| (self instanceof AtmosphericFogModifier && cfg.noFogBiome);
		if (!remove) {
			return;
		}
		data.environmentalStart = 1.0E7F;
		data.environmentalEnd = 1.0E8F;
		if (!(self instanceof AtmosphericFogModifier)) {
			// Underwater/lava: also clear the sky/cloud fog so the colored tint goes away entirely.
			data.skyEnd = data.environmentalEnd;
			data.cloudEnd = data.environmentalEnd;
		}
	}
}
