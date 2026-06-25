package com.piccaxe.lsutils.mixin;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Anti-invis: treat invisible entities as "not invisible to me" so the renderer takes its existing
 * spectator path — drawing them with the translucent layer and keeping name tags — instead of
 * hiding them. Combined with {@link AntiInvisAlphaMixin} for a clearer semi-transparent look.
 */
@Mixin(Entity.class)
public class AntiInvisMixin {
	@Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
	private void piccaxelsutils$revealInvisible(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
		Config cfg = ConfigManager.get();
		if (cfg.masterEnabled && cfg.antiInvis) {
			cir.setReturnValue(false);
		}
	}
}
