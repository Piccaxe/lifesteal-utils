package com.piccaxe.lsutils.mixin;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With anti-invis on, tags the name label of an invisible entity with " (invis)" so revealed
 * (semi-transparent) entities are obvious at a glance. Hooks the name the renderer is about to use.
 */
@Mixin(EntityRenderer.class)
public class AntiInvisNameMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void piccaxelsutils$markInvisible(Entity entity, CallbackInfoReturnable<Text> cir) {
		Config cfg = ConfigManager.get();
		Text original = cir.getReturnValue();
		if (original != null && cfg.masterEnabled && cfg.antiInvis && entity.isInvisible()) {
			cir.setReturnValue(Text.empty().append(original)
				.append(Text.literal(" (invis)").formatted(Formatting.AQUA, Formatting.ITALIC)));
		}
	}
}
