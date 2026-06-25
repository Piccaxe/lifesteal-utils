package com.piccaxe.lsutils.feature;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.List;

/**
 * ESP outlines for common trap parts — pistons/sticky pistons, pressure plates, string (tripwire +
 * hooks) and armor stands — visible through walls. Blocks are scanned in a radius every few ticks
 * and cached; armor stands (entities) are gathered each frame. Same no-depth lines layer technique
 * as {@link EnderChestOutliner}.
 */
public final class TrapOutliner {
	private static final RenderPipeline LINES_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
		.withLocation(Identifier.of("piccaxelsutils", "pipeline/lines_no_depth_trap"))
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.build();

	private static final RenderLayer LINES_NO_DEPTH = RenderLayer.of(
		"piccaxelsutils_trap_lines",
		RenderSetup.builder(LINES_NO_DEPTH_PIPELINE)
			.layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
			.build());

	private static final int MAX_BLOCKS = 600;

	private static volatile List<BlockPos> cached = List.of();
	private static int sinceScan = 0;
	private static boolean precompiled = false;

	private TrapOutliner() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(TrapOutliner::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(TrapOutliner::render);
	}

	private static void tick(MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.trapOutliner || mc.player == null || mc.world == null) {
			cached = List.of();
			return;
		}
		if (++sinceScan >= 15) {
			sinceScan = 0;
			cached = scan(mc, cfg.trapRadius);
		}
	}

	private static List<BlockPos> scan(MinecraftClient mc, int radius) {
		int r = Math.max(4, Math.min(64, radius));
		long rSq = (long) r * r;
		BlockPos center = mc.player.getBlockPos();
		int cx = center.getX();
		int cy = center.getY();
		int cz = center.getZ();
		BlockPos.Mutable p = new BlockPos.Mutable();
		List<BlockPos> found = new ArrayList<>();

		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					if ((long) dx * dx + (long) dy * dy + (long) dz * dz > rSq) {
						continue;
					}
					p.set(cx + dx, cy + dy, cz + dz);
					if (isTrapBlock(mc.world.getBlockState(p))) {
						found.add(p.toImmutable());
						if (found.size() >= MAX_BLOCKS) {
							return found;
						}
					}
				}
			}
		}
		return found;
	}

	private static boolean isTrapBlock(BlockState state) {
		return state.getBlock() instanceof PistonBlock
			|| state.getBlock() instanceof AbstractPressurePlateBlock
			|| state.isOf(Blocks.TRIPWIRE)
			|| state.isOf(Blocks.TRIPWIRE_HOOK);
	}

	private static void render(WorldRenderContext context) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.trapOutliner) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) {
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

		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		int color = 0xFF000000 | (cfg.trapColor & 0xFFFFFF);
		var lines = consumers.getBuffer(LINES_NO_DEPTH);

		for (BlockPos pos : cached) {
			VertexRendering.drawOutline(matrices, lines, VoxelShapes.fullCube(),
				pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z, color, 2.0F);
		}

		double rangeSq = (double) cfg.trapRadius * cfg.trapRadius;
		for (Entity e : mc.world.getEntities()) {
			if (!(e instanceof ArmorStandEntity) || e.squaredDistanceTo(mc.player) > rangeSq) {
				continue;
			}
			Box bb = e.getBoundingBox();
			VoxelShape shape = VoxelShapes.cuboid(0.0, 0.0, 0.0, bb.maxX - bb.minX, bb.maxY - bb.minY, bb.maxZ - bb.minZ);
			VertexRendering.drawOutline(matrices, lines, shape, bb.minX - cam.x, bb.minY - cam.y, bb.minZ - cam.z, color, 2.0F);
		}
	}
}
