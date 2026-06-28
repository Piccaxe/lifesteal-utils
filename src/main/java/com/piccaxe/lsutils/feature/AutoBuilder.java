package com.piccaxe.lsutils.feature;

import com.piccaxe.lsutils.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * Experimental, fully-legit auto-builder for Litematica schematics: walks to and places missing
 * blocks using real movement input, real look rotation, real placement packets within normal reach —
 * no reach/packet bypass. Honest limits: greedy/bottom-up, reuses the simple A* {@link ChestPathfinder},
 * places only from the hotbar, has no scaffolding/pillaring, and will abandon targets it can't path to
 * or reach. Best for low/simple builds on solid ground; it will struggle with tall or floating builds.
 *
 * <p>NOTE: movement automation like this is against most multiplayer server rules — your call.
 */
public final class AutoBuilder {
	private static final double REACH = 4.0;
	private static final int SCAN_RADIUS = 6;
	private static final int PLACE_EVERY_TICKS = 4;
	private static final Direction[] HORIZ = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	private static boolean active = false;
	private static int tickCounter = 0;
	private static BlockPos currentTarget = null;
	private static List<BlockPos> path = null;
	private static int pathIndex = 0;
	private static int stuckTicks = 0;
	private static BlockPos lastPlayerBlock = null;

	private AutoBuilder() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoBuilder::tick);
	}

	public static boolean isActive() {
		return active;
	}

	public static void toggle(MinecraftClient mc) {
		setActive(mc, !active);
	}

	public static void setActive(MinecraftClient mc, boolean on) {
		clearMovement(mc);
		currentTarget = null;
		path = null;
		pathIndex = 0;
		stuckTicks = 0;
		if (on && !LitematicaBridge.isPresent()) {
			msg(mc, "Litematica not detected — can't auto-build.", Formatting.RED);
			active = false;
			return;
		}
		active = on;
		msg(mc, "Auto-build " + (on ? "started" : "stopped"), on ? Formatting.GREEN : Formatting.YELLOW);
	}

	private static void tick(MinecraftClient mc) {
		if (!active) {
			return;
		}
		if (!ConfigManager.get().masterEnabled || mc.player == null || mc.world == null || mc.interactionManager == null) {
			clearMovement(mc);
			return;
		}
		World sch = LitematicaBridge.getSchematicWorld();
		if (sch == null) {
			msg(mc, "No schematic loaded — stopping auto-build.", Formatting.RED);
			setActive(mc, false);
			return;
		}

		if (currentTarget == null || !needsBlock(mc.world, sch, currentTarget)) {
			currentTarget = findNearestMissing(mc, sch);
			path = null;
			pathIndex = 0;
			if (currentTarget == null) {
				msg(mc, "Nothing reachable to build (or missing materials in hotbar). Stopping.", Formatting.YELLOW);
				setActive(mc, false);
				return;
			}
		}

		ClientPlayerEntity p = mc.player;
		double dist = Math.sqrt(p.getEyePos().squaredDistanceTo(Vec3d.ofCenter(currentTarget)));
		if (dist <= REACH) {
			clearMovement(mc);
			if (++tickCounter % PLACE_EVERY_TICKS == 0) {
				placeAt(mc, sch, currentTarget);
			}
		} else {
			navigate(mc, currentTarget);
		}
	}

	/** True if the schematic wants a real (non-air) block here that isn't placed yet. */
	private static boolean needsBlock(World world, World sch, BlockPos pos) {
		BlockState want = sch.getBlockState(pos);
		if (want.isAir() || want.getBlock().asItem() == Items.AIR) {
			return false;
		}
		BlockState have = world.getBlockState(pos);
		return have.getBlock() != want.getBlock() && have.isReplaceable();
	}

	/** Nearest missing block we can actually place right now (have the item + something to place against),
	 *  biased to build bottom-up. */
	private static BlockPos findNearestMissing(MinecraftClient mc, World sch) {
		BlockPos origin = mc.player.getBlockPos();
		World w = mc.world;
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
			for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
				for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
					BlockPos pos = origin.add(dx, dy, dz);
					if (!needsBlock(w, sch, pos)) {
						continue;
					}
					if (hotbarSlotFor(mc, sch.getBlockState(pos)) < 0 || supportFace(w, pos) == null) {
						continue;
					}
					// Prefer lower blocks (build up from the ground), then nearer.
					double score = pos.getSquaredDistance(origin) + (dy + SCAN_RADIUS) * 64.0;
					if (score < bestScore) {
						bestScore = score;
						best = pos;
					}
				}
			}
		}
		return best;
	}

	/** Direction to a solid neighbour we can click to place against, or null if the block is floating. */
	private static Direction supportFace(World w, BlockPos pos) {
		for (Direction d : Direction.values()) {
			BlockState ns = w.getBlockState(pos.offset(d));
			if (!ns.isAir() && !ns.isReplaceable()) {
				return d;
			}
		}
		return null;
	}

	private static int hotbarSlotFor(MinecraftClient mc, BlockState state) {
		Item item = state.getBlock().asItem();
		if (item == Items.AIR) {
			return -1;
		}
		for (int i = 0; i < 9; i++) {
			ItemStack s = mc.player.getInventory().getStack(i);
			if (!s.isEmpty() && s.getItem() == item) {
				return i;
			}
		}
		return -1;
	}

	private static void placeAt(MinecraftClient mc, World sch, BlockPos pos) {
		BlockState want = sch.getBlockState(pos);
		int slot = hotbarSlotFor(mc, want);
		Direction support = supportFace(mc.world, pos);
		if (slot < 0 || support == null) {
			currentTarget = null;
			return;
		}
		mc.player.getInventory().setSelectedSlot(slot);

		BlockPos against = pos.offset(support);
		Direction face = support.getOpposite(); // the face of 'against' that points at our target
		Vec3d hitVec = Vec3d.ofCenter(against).add(Vec3d.of(face.getVector()).multiply(0.5));
		lookAt(mc.player, hitVec);
		BlockHitResult hit = new BlockHitResult(hitVec, face, against, false);
		mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
		mc.player.swingHand(Hand.MAIN_HAND);
	}

	private static void navigate(MinecraftClient mc, BlockPos target) {
		ClientPlayerEntity p = mc.player;
		BlockPos stand = standingSpot(mc.world, target);
		if (stand == null) {
			currentTarget = null; // can't stand near it — give up on this one
			clearMovement(mc);
			return;
		}
		if (path == null || pathIndex >= path.size()) {
			path = ChestPathfinder.findPath(mc.world, p.getBlockPos(), stand, 300);
			pathIndex = 0;
			if (path == null || path.isEmpty()) {
				currentTarget = null;
				clearMovement(mc);
				return;
			}
		}

		BlockPos node = path.get(Math.min(pathIndex, path.size() - 1));
		Vec3d nodeC = Vec3d.ofCenter(node);
		double dx = nodeC.x - p.getX();
		double dz = nodeC.z - p.getZ();
		if (Math.sqrt(dx * dx + dz * dz) < 0.6) {
			pathIndex++;
		}
		p.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
		mc.options.forwardKey.setPressed(true);
		mc.options.jumpKey.setPressed(node.getY() > p.getBlockPos().getY() && p.isOnGround());

		BlockPos pb = p.getBlockPos();
		if (pb.equals(lastPlayerBlock)) {
			stuckTicks++;
		} else {
			stuckTicks = 0;
			lastPlayerBlock = pb;
		}
		if (stuckTicks > 60) { // ~3s with no progress: abandon this target so we don't loop forever
			stuckTicks = 0;
			path = null;
			currentTarget = null;
		}
	}

	/** A standable spot (feet + head clear, solid floor) next to the target, nearest to the player. */
	private static BlockPos standingSpot(World w, BlockPos target) {
		BlockPos pp = MinecraftClient.getInstance().player.getBlockPos();
		BlockPos best = null;
		double bd = Double.MAX_VALUE;
		for (int dy = -1; dy <= 1; dy++) {
			for (Direction d : HORIZ) {
				BlockPos s = target.offset(d).up(dy);
				if (standable(w, s)) {
					double dist = s.getSquaredDistance(pp);
					if (dist < bd) {
						bd = dist;
						best = s;
					}
				}
			}
		}
		return best;
	}

	private static boolean standable(World w, BlockPos feet) {
		return w.getBlockState(feet).isReplaceable()
			&& w.getBlockState(feet.up()).isReplaceable()
			&& !w.getBlockState(feet.down()).isReplaceable();
	}

	private static void lookAt(ClientPlayerEntity p, Vec3d target) {
		Vec3d eye = p.getEyePos();
		double dx = target.x - eye.x;
		double dy = target.y - eye.y;
		double dz = target.z - eye.z;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		p.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
		p.setPitch((float) (-Math.toDegrees(Math.atan2(dy, horiz))));
	}

	private static void clearMovement(MinecraftClient mc) {
		if (mc.options != null) {
			mc.options.forwardKey.setPressed(false);
			mc.options.jumpKey.setPressed(false);
		}
	}

	private static void msg(MinecraftClient mc, String text, Formatting color) {
		if (mc.player != null) {
			mc.player.sendMessage(Text.literal("[Builder] " + text).formatted(color), false);
		}
	}
}
