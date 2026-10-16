package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ThrowablePotionItem;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Projectile path simulation drawn in the HUD layer, using vanilla's per-tick physics:
 * <ul>
 *   <li><b>Pearl predictor</b> — every ender pearl in flight gets its remaining path + landing marker,
 *   labelled with the thrower and how far the landing spot is from you.</li>
 *   <li><b>Trajectory preview</b> — your own shot (drawn bow, charged crossbow, trident, pearl,
 *   snowball/egg, splash potion, XP bottle) before you release it.</li>
 * </ul>
 */
public final class Trajectories {
	private static final int MAX_TICKS = 300;

	/** Physics for one projectile type. {@code arrowStyle}: move→drag→gravity (arrows/tridents); else gravity→drag→move. */
	private record Physics(double gravity, double waterDrag, boolean arrowStyle) {
	}

	private static final Physics THROWN = new Physics(0.03, 0.8, false);
	private static final Physics POTION = new Physics(0.05, 0.8, false);
	private static final Physics XP_BOTTLE = new Physics(0.07, 0.8, false);
	private static final Physics ARROW = new Physics(0.05, 0.6, true);
	private static final Physics TRIDENT = new Physics(0.05, 0.99, true);

	private record Path(List<Vec3d> points, Vec3d landing) {
	}

	private Trajectories() {
	}

	public static void render(DrawContext ctx, MinecraftClient mc) {
		Config cfg = ConfigManager.get();
		if (mc.player == null || mc.world == null) {
			return;
		}
		if (cfg.pearlPredictor) {
			renderPearls(ctx, mc, cfg);
		}
		if (cfg.trajectoryPreview) {
			renderOwn(ctx, mc, cfg);
		}
	}

	private static void renderPearls(DrawContext ctx, MinecraftClient mc, Config cfg) {
		int argb = 0xFF000000 | (cfg.pearlColor & 0xFFFFFF);
		for (Entity e : mc.world.getEntities()) {
			if (!(e instanceof EnderPearlEntity pearl) || pearl.isRemoved()) {
				continue;
			}
			Path path = simulate(mc, new Vec3d(pearl.getX(), pearl.getY(), pearl.getZ()), pearl.getVelocity(), THROWN, pearl);
			HudDraw.polyline(ctx, path.points(), argb, 2);
			if (path.landing() != null) {
				HudDraw.marker(ctx, path.landing(), argb);
				float[] s = ProjectionUtil.project(path.landing(), mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
				if (s != null) {
					Entity owner = pearl.getOwner();
					String who = owner instanceof PlayerEntity p ? (p == mc.player ? "You" : p.getName().getString()) : "Pearl";
					int dist = (int) Math.round(path.landing().distanceTo(mc.player.getEyePos()));
					Text label = Text.literal(who + " · " + dist + "m");
					ctx.drawTextWithShadow(mc.textRenderer, label, (int) s[0] - mc.textRenderer.getWidth(label) / 2, (int) s[1] - 13, argb);
				}
			}
		}
	}

	private static void renderOwn(DrawContext ctx, MinecraftClient mc, Config cfg) {
		PlayerEntity pl = mc.player;
		for (Hand hand : Hand.values()) {
			ItemStack stack = pl.getStackInHand(hand);
			Shot shot = shotFor(pl, stack);
			if (shot == null) {
				continue;
			}
			float yaw = pl.getYaw();
			float pitch = pl.getPitch();
			double rad = Math.PI / 180.0;
			Vec3d dir = new Vec3d(
				-Math.sin(yaw * rad) * Math.cos(pitch * rad),
				-Math.sin((pitch + shot.roll()) * rad),
				Math.cos(yaw * rad) * Math.cos(pitch * rad)).normalize().multiply(shot.speed());
			Vec3d motion = pl.getVelocity();
			Vec3d vel = dir.add(motion.x, pl.isOnGround() ? 0.0 : motion.y, motion.z);
			Vec3d start = ProjectionUtil.cameraPos().subtract(0.0, 0.1, 0.0);
			Path path = simulate(mc, start, vel, shot.physics(), pl);
			int argb = 0xFF000000 | (cfg.trajectoryColor & 0xFFFFFF);
			// Skip the first couple of points: they're right at the camera and project badly.
			List<Vec3d> pts = path.points().size() > 2 ? path.points().subList(2, path.points().size()) : path.points();
			HudDraw.polyline(ctx, pts, argb, 2);
			if (path.landing() != null) {
				HudDraw.marker(ctx, path.landing(), argb);
			}
			return; // one preview is enough
		}
	}

	private record Shot(double speed, float roll, Physics physics) {
	}

	private static Shot shotFor(PlayerEntity pl, ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		if (stack.getItem() instanceof BowItem) {
			if (!pl.isUsingItem() || pl.getActiveItem() != stack) {
				return null;
			}
			float pull = BowItem.getPullProgress(pl.getItemUseTime());
			return pull < 0.1F ? null : new Shot(pull * 3.0, 0.0F, ARROW);
		}
		if (stack.getItem() instanceof CrossbowItem) {
			return CrossbowItem.isCharged(stack) ? new Shot(3.15, 0.0F, ARROW) : null;
		}
		if (stack.isOf(Items.TRIDENT)) {
			return pl.isUsingItem() && pl.getActiveItem() == stack ? new Shot(2.5, 0.0F, TRIDENT) : null;
		}
		if (stack.isOf(Items.ENDER_PEARL) || stack.isOf(Items.SNOWBALL) || stack.isOf(Items.EGG)) {
			return new Shot(1.5, 0.0F, THROWN);
		}
		if (stack.getItem() instanceof ThrowablePotionItem) {
			return new Shot(0.5, -20.0F, POTION);
		}
		if (stack.isOf(Items.EXPERIENCE_BOTTLE)) {
			return new Shot(0.7, -20.0F, XP_BOTTLE);
		}
		return null;
	}

	private static Path simulate(MinecraftClient mc, Vec3d pos, Vec3d vel, Physics ph, Entity ctxEntity) {
		List<Vec3d> pts = new ArrayList<>();
		pts.add(pos);
		int bottom = mc.world.getBottomY();
		for (int t = 0; t < MAX_TICKS; t++) {
			boolean water = mc.world.getFluidState(BlockPos.ofFloored(pos)).isIn(FluidTags.WATER);
			double drag = water ? ph.waterDrag() : 0.99;
			if (!ph.arrowStyle()) {
				vel = new Vec3d(vel.x, vel.y - ph.gravity(), vel.z).multiply(drag);
			}
			Vec3d next = pos.add(vel);
			BlockHitResult hit = mc.world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, ctxEntity));
			if (hit.getType() != HitResult.Type.MISS) {
				pts.add(hit.getPos());
				return new Path(pts, hit.getPos());
			}
			pos = next;
			pts.add(pos);
			if (ph.arrowStyle()) {
				vel = vel.multiply(drag);
				vel = new Vec3d(vel.x, vel.y - ph.gravity(), vel.z);
			}
			if (pos.y < bottom - 8) {
				break;
			}
		}
		return new Path(pts, null);
	}
}
