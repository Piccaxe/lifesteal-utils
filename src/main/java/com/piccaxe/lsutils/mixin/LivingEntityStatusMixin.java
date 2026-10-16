package com.piccaxe.lsutils.mixin;

import com.piccaxe.lsutils.feature.TotemPops;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Detects totem pops from the server's entity status broadcast (feeds {@link TotemPops}). */
@Mixin(LivingEntity.class)
public class LivingEntityStatusMixin {
	@Inject(method = "handleStatus(B)V", at = @At("HEAD"))
	private void piccaxelsutils$onStatus(byte status, CallbackInfo ci) {
		if (status == EntityStatuses.USE_TOTEM_OF_UNDYING && ((LivingEntity) (Object) this).getEntityWorld().isClient()) {
			TotemPops.onPop((LivingEntity) (Object) this);
		}
	}
}
