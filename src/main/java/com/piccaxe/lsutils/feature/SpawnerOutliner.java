package com.piccaxe.lsutils.feature;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.List;

/**
 * ESP for mob spawners: outlines spawners through walls and colours them <b>green</b> when a player
 * is within the activation radius (vanilla = 16 blocks), red otherwise — so you can see at a glance
 * which spawners are live and about to spawn mobs. Same no-depth lines technique as
 * {@link TrapOutliner}. Activation is judged from nearby players' positions (you're always one).
 */
public final class SpawnerOutliner {
	private static final RenderPipeline LINES_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
		.withLocation(Identifier.of("piccaxelsutils", "pipeline/lines_no_depth_spawner"))
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.build();

	private static final RenderLayer LINES_NO_DEPTH = RenderLayer.of(
		"piccaxelsutils_spawner_lines",
		RenderSetup.builder(LINES_NO_DEPTH_PIPELINE)
			.layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
			.build());

	private static final int MAX_SPAWNERS = 256;

	private static volatile List<BlockPos> cached = List.of();
	private static int sinceScan = 0;
	private static boolean precompiled = false;

	private SpawnerOutliner() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SpawnerOutliner::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(SpawnerOutliner::render);
	}

	private static void tick(MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.spawnerOutliner || mc.player == null || mc.world == null) {
			cached = List.of();
			return;
		}
		if (++sinceScan >= 20) {
			sinceScan = 0;
			cached = scan(mc, cfg.spawnerScanRadius);
		}
	}

	private static List<BlockPos> scan(MinecraftClient mc, int radius) {
		int r = Math.max(8, Math.min(96, radius));
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
					if (isSpawner(mc.world.getBlockState(p))) {
						found.add(p.toImmutable());
						if (found.size() >= MAX_SPAWNERS) {
							return found;
						}
					}
				}
			}
		}
		return found;
	}

	private static boolean isSpawner(BlockState state) {
		return state.isOf(Blocks.SPAWNER) || state.isOf(Blocks.TRIAL_SPAWNER);
	}

	/** A spawner is "activated" if any tracked player is within {@code range} blocks of its centre. */
	private static boolean isActivated(MinecraftClient mc, BlockPos pos, double rangeSq) {
		Vec3d c = Vec3d.ofCenter(pos);
		for (PlayerEntity player : mc.world.getPlayers()) {
			if (player.squaredDistanceTo(c.x, c.y, c.z) <= rangeSq) {
				return true;
			}
		}
		return false;
	}

	private static void render(WorldRenderContext context) {
		Config cfg = ConfigManager.get();
		if (!cfg.masterEnabled || !cfg.spawnerOutliner) {
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
		int activeColor = 0xFF000000 | (cfg.spawnerActiveColor & 0xFFFFFF);
		int inactiveColor = 0xFF000000 | (cfg.spawnerInactiveColor & 0xFFFFFF);
		double rangeSq = (double) cfg.spawnerRange * cfg.spawnerRange;
		var lines = consumers.getBuffer(LINES_NO_DEPTH);

		for (BlockPos pos : cached) {
			int color = isActivated(mc, pos, rangeSq) ? activeColor : inactiveColor;
			VertexRendering.drawOutline(matrices, lines, VoxelShapes.fullCube(),
				pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z, color, 2.0F);
		}

		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw();
		}
	}
}
