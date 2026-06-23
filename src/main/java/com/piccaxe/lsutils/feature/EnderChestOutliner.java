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
import net.minecraft.client.font.TextRenderer;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
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
	private static volatile List<List<BlockPos>> cachedPaths = List.of();
	private static int sinceScan = 0;
	private static int sincePath = 0;
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
			cachedPaths = List.of();
			return;
		}
		if (++sinceScan >= 10) {
			sinceScan = 0;
			cached = scan(mc.world, mc.player.getBlockPos(), cfg.enderChestRadius);
		}
		if (cfg.enderChestPathTracer) {
			if (++sincePath >= 20) {
				sincePath = 0;
				cachedPaths = computePaths(mc, cfg);
			}
		} else {
			cachedPaths = List.of();
		}
	}

	private static List<List<BlockPos>> computePaths(MinecraftClient mc, Config cfg) {
		List<BlockPos> chests = cached;
		if (chests.isEmpty()) {
			return List.of();
		}
		int budget = "all".equalsIgnoreCase(cfg.enderChestPathMode) ? 2500 : 6000;
		BlockPos start = mc.player.getBlockPos();
		List<List<BlockPos>> paths = new ArrayList<>();
		for (BlockPos chest : selectTargets(mc, cfg, chests)) {
			List<BlockPos> path = ChestPathfinder.findPath(mc.world, start, chest, budget);
			if (path.size() >= 2) {
				paths.add(path);
			}
		}
		return paths;
	}

	private static List<BlockPos> selectTargets(MinecraftClient mc, Config cfg, List<BlockPos> chests) {
		if ("looking".equalsIgnoreCase(cfg.enderChestPathMode)) {
			Vec3d eye = mc.player.getEyePos();
			Vec3d look = mc.player.getRotationVec(1.0F);
			BlockPos best = null;
			double bestDot = -2.0;
			for (BlockPos c : chests) {
				Vec3d to = new Vec3d(c.getX() + 0.5 - eye.x, c.getY() + 0.5 - eye.y, c.getZ() + 0.5 - eye.z).normalize();
				double dot = to.dotProduct(look);
				if (dot > bestDot) {
					bestDot = dot;
					best = c;
				}
			}
			return best == null ? List.of() : List.of(best);
		}

		Vec3d pp = mc.player.getEyePos();
		List<BlockPos> sorted = new ArrayList<>(chests);
		sorted.sort(Comparator.comparingDouble(c -> {
			double dx = c.getX() + 0.5 - pp.x;
			double dy = c.getY() + 0.5 - pp.y;
			double dz = c.getZ() + 0.5 - pp.z;
			return dx * dx + dy * dy + dz * dz;
		}));
		if ("all".equalsIgnoreCase(cfg.enderChestPathMode)) {
			return sorted.subList(0, Math.min(3, sorted.size()));
		}
		return List.of(sorted.get(0));
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

		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			return;
		}
		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		int color = 0xFF000000 | (cfg.enderChestColor & 0xFFFFFF);
		var lines = consumers.getBuffer(LINES_NO_DEPTH);
		Vec3d look = cfg.enderChestTracer ? mc.player.getRotationVec(1.0F) : null;

		for (BlockPos pos : positions) {
			double ox = pos.getX() - cam.x;
			double oy = pos.getY() - cam.y;
			double oz = pos.getZ() - cam.z;

			VertexRendering.drawOutline(matrices, lines, VoxelShapes.fullCube(), ox, oy, oz, color, 2.0F);

			if (look != null) {
				var entry = matrices.peek();
				float sx = (float) (look.x * 0.3);
				float sy = (float) (look.y * 0.3);
				float sz = (float) (look.z * 0.3);
				float ex = (float) (ox + 0.5);
				float ey = (float) (oy + 0.5);
				float ez = (float) (oz + 0.5);
				Vector3f dir = new Vector3f(ex - sx, ey - sy, ez - sz);
				if (dir.lengthSquared() > 1.0e-6F) {
					dir.normalize();
					lines.vertex(entry, sx, sy, sz).color(color).normal(entry, dir).lineWidth(1.5F);
					lines.vertex(entry, ex, ey, ez).color(color).normal(entry, dir).lineWidth(1.5F);
				}
			}

			if (cfg.enderChestDistanceLabel) {
				drawLabel(mc, matrices, consumers, ox, oy, oz);
			}
		}

		if (cfg.enderChestPathTracer && !cachedPaths.isEmpty()) {
			int pathColor = 0xFF000000 | (cfg.enderChestPathColor & 0xFFFFFF);
			var entry = matrices.peek();
			for (List<BlockPos> path : cachedPaths) {
				for (int i = 0; i + 1 < path.size(); i++) {
					BlockPos a = path.get(i);
					BlockPos b = path.get(i + 1);
					float ax = (float) (a.getX() + 0.5 - cam.x);
					float ay = (float) (a.getY() + 0.5 - cam.y);
					float az = (float) (a.getZ() + 0.5 - cam.z);
					float bx = (float) (b.getX() + 0.5 - cam.x);
					float by = (float) (b.getY() + 0.5 - cam.y);
					float bz = (float) (b.getZ() + 0.5 - cam.z);
					Vector3f dir = new Vector3f(bx - ax, by - ay, bz - az);
					if (dir.lengthSquared() < 1.0e-6F) {
						continue;
					}
					dir.normalize();
					lines.vertex(entry, ax, ay, az).color(pathColor).normal(entry, dir).lineWidth(2.5F);
					lines.vertex(entry, bx, by, bz).color(pathColor).normal(entry, dir).lineWidth(2.5F);
				}
			}
		}
	}

	private static void drawLabel(MinecraftClient mc, MatrixStack matrices, VertexConsumerProvider consumers,
			double ox, double oy, double oz) {
		double cx = ox + 0.5;
		double cy = oy + 0.5;
		double cz = oz + 0.5;
		int distance = (int) Math.round(Math.sqrt(cx * cx + cy * cy + cz * cz));
		String label = distance + "m";

		matrices.push();
		matrices.translate(cx, cy + 0.6, cz);
		matrices.multiply(mc.gameRenderer.getCamera().getRotation());
		matrices.scale(-0.025F, -0.025F, 0.025F);
		var matrix = matrices.peek().getPositionMatrix();
		float width = mc.textRenderer.getWidth(label);
		mc.textRenderer.draw(label, -width / 2.0F, 0.0F, 0xFFFFFFFF, false, matrix, consumers,
			TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
		matrices.pop();
	}
}
