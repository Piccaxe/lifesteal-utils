package com.piccaxe.lsutils.feature;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws colored box outlines around ender chests ("loot chests") within range — ESP style,
 * visible through walls.
 *
 * <p>Ender chests are block entities (sparse), so we walk loaded chunks' block-entity maps
 * every 10 ticks and cache hits, then render them each frame in
 * {@link WorldRenderEvents#AFTER_ENTITIES}.
 *
 * <p>Through-walls is achieved with a custom lines render layer: vanilla's depth-tested
 * lines layer is cloned from the public {@code RENDERTYPE_LINES_SNIPPET} but with
 * {@link DepthTestFunction#NO_DEPTH_TEST} so the box always passes the depth test.
 */
public final class EnderChestOutliner {
	private static final RenderPipeline LINES_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
		.withLocation(Identifier.of("piccaxelsutils", "pipeline/lines_no_depth"))
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.build();

	private static final RenderLayer LINES_NO_DEPTH = RenderLayer.of(
		"piccaxelsutils_lines_no_depth",
		RenderSetup.builder(LINES_NO_DEPTH_PIPELINE)
			.layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
			.build());

	private static volatile List<BlockPos> cached = List.of();
	private static int sinceScan = 0;
	private static boolean precompiled = false;

	private EnderChestOutliner() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(EnderChestOutliner::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(EnderChestOutliner::render);
	}

	private static void tick(MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.enderChestOutliner || mc.player == null || mc.world == null) {
			cached = List.of();
			return;
		}
		if (++sinceScan < 10) {
			return;
		}
		sinceScan = 0;
		cached = scan(mc.world, mc.player.getBlockPos(), cfg.enderChestRadius);
	}

	private static List<BlockPos> scan(ClientWorld world, BlockPos center, int radius) {
		List<BlockPos> found = new ArrayList<>();
		long radiusSq = (long) radius * radius;
		int chunkRadius = (radius >> 4) + 1;
		int centerChunkX = center.getX() >> 4;
		int centerChunkZ = center.getZ() >> 4;

		for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
			for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
				WorldChunk chunk = world.getChunk(cx, cz);
				for (var entry : chunk.getBlockEntities().entrySet()) {
					BlockEntity be = entry.getValue();
					if (be.getCachedState().isOf(Blocks.ENDER_CHEST)
						&& center.getSquaredDistance(entry.getKey()) <= radiusSq) {
						found.add(entry.getKey().toImmutable());
					}
				}
			}
		}
		return found;
	}

	private static void render(WorldRenderContext context) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.enderChestOutliner) {
			return;
		}
		List<BlockPos> positions = cached;
		if (positions.isEmpty()) {
			return;
		}
		MatrixStack matrices = context.matrices();
		VertexConsumerProvider consumers = context.consumers();
		if (matrices == null || consumers == null) {
			return;
		}

		if (!precompiled) {
			RenderSystem.getDevice().precompilePipeline(LINES_NO_DEPTH_PIPELINE);
			precompiled = true;
		}

		Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
		int color = 0xFF000000 | (cfg.enderChestColor & 0xFFFFFF);
		var lines = consumers.getBuffer(LINES_NO_DEPTH);

		for (BlockPos pos : positions) {
			VertexRendering.drawOutline(matrices, lines, VoxelShapes.fullCube(),
				pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z, color, 2.0F);
		}
	}
}
