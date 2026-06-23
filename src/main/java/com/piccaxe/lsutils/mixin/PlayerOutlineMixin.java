package com.piccaxe.lsutils.mixin;

import com.piccaxe.lsutils.feature.PlayerOutliner;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forces a colored glow outline on categorized players.
 *
 * <p>Vanilla sets {@code state.outlineColor} near the end of
 * {@link EntityRenderer#updateRenderState} (0 = no outline). We override it at TAIL:
 * a non-zero color both enables the outline ({@code hasOutline()} checks {@code != 0})
 * and sets its color, so this single hook covers both.
 */
@Mixin(EntityRenderer.class)
public class PlayerOutlineMixin {
	@Inject(method = "updateRenderState", at = @At("TAIL"))
	private void piccaxelsutils$outline(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
		if (!(entity instanceof PlayerEntity player) || entity == MinecraftClient.getInstance().player) {
			return;
		}
		int color = PlayerOutliner.outlineColorFor(player);
		if (color != 0) {
			state.outlineColor = color;
		}
	}
}
