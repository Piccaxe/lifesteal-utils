package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws colored box outlines around ender chests ("loot chests") within range.
 *
 * <p>Ender chests are block entities, which are sparse, so instead of scanning every
 * block we walk the loaded chunks' block-entity maps every 10 ticks and cache the hits.
 * The cached positions are rendered each frame in {@link WorldRenderEvents#AFTER_ENTITIES}
 * with the same {@code VertexRendering.drawOutline} + {@code RenderLayers.lines()} path
 * vanilla uses for the block-targeting outline (so the boxes are depth-tested).
 */
public final class EnderChestOutliner {
	private static volatile List<BlockPos> cached = List.of();
	private static int sinceScan = 0;

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

		Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
		int color = 0xFF000000 | (cfg.enderChestColor & 0xFFFFFF);
		var lines = consumers.getBuffer(RenderLayers.lines());

		for (BlockPos pos : positions) {
			VertexRendering.drawOutline(matrices, lines, VoxelShapes.fullCube(),
				pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z, color, 2.0F);
		}
	}
}
