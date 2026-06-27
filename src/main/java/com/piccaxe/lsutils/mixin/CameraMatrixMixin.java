package com.piccaxe.lsutils.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.piccaxe.lsutils.feature.ProjectionUtil;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.memory.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the world camera position + view/projection matrices each frame so {@link ProjectionUtil}
 * can project world positions to the screen for HUD-layer rendering (health text above heads).
 */
@Mixin(WorldRenderer.class)
public class CameraMatrixMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void piccaxelsutils$captureMatrices(ObjectAllocator allocator, RenderTickCounter tickCounter,
			boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f basicProjectionMatrix,
			Matrix4f projectionMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
		ProjectionUtil.update(camera.getCameraPos(), positionMatrix, projectionMatrix);
	}
}
