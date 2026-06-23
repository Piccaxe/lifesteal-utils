package com.piccaxe.lsutils.feature;

import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Bounded A* walkable-path search from the player to a chest, client-side over loaded blocks.
 *
 * <p>Movement model (an approximation — no parkour jumps): walk across solid ground, step up 1
 * block, drop down up to {@link #MAX_DROP}, swim through water, and climb up/down ladders &amp;
 * other climbables. A position is "occupiable" when feet and head are passable and there is
 * support below it — a solid floor, water, or a climbable. Search is capped at {@code maxNodes}
 * and bounded to a padded box; if the goal isn't reached it returns the best partial route.
 */
public final class ChestPathfinder {
	private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
	private static final int MAX_DROP = 3;
	private static final int BOUND_PAD = 24;

	private ChestPathfinder() {
	}

	public static List<BlockPos> findPath(World world, BlockPos rawStart, BlockPos chest, int maxNodes) {
		BlockPos start = groundStart(world, rawStart);
		if (start == null) {
			return List.of();
		}

		int minX = Math.min(start.getX(), chest.getX()) - BOUND_PAD;
		int maxX = Math.max(start.getX(), chest.getX()) + BOUND_PAD;
		int minY = Math.min(start.getY(), chest.getY()) - BOUND_PAD;
		int maxY = Math.max(start.getY(), chest.getY()) + BOUND_PAD;
		int minZ = Math.min(start.getZ(), chest.getZ()) - BOUND_PAD;
		int maxZ = Math.max(start.getZ(), chest.getZ()) + BOUND_PAD;

		Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
		Map<BlockPos, Double> gScore = new HashMap<>();
		Set<BlockPos> closed = new HashSet<>();
		PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));

		gScore.put(start, 0.0);
		open.add(new Node(start, heuristic(start, chest)));

		BlockPos best = start;
		double bestH = heuristic(start, chest);
		int explored = 0;

		while (!open.isEmpty() && explored < maxNodes) {
			BlockPos current = open.poll().pos;
			if (!closed.add(current)) {
				continue;
			}
			explored++;

			double h = heuristic(current, chest);
			if (h < bestH) {
				bestH = h;
				best = current;
			}
			if (isAdjacent(current, chest)) {
				return reconstruct(cameFrom, current);
			}

			double g = gScore.getOrDefault(current, Double.MAX_VALUE);
			for (Neighbor nb : neighbors(world, current)) {
				BlockPos np = nb.pos;
				if (np.getX() < minX || np.getX() > maxX || np.getY() < minY || np.getY() > maxY
					|| np.getZ() < minZ || np.getZ() > maxZ || closed.contains(np)) {
					continue;
				}
				double tentative = g + nb.cost;
				if (tentative < gScore.getOrDefault(np, Double.MAX_VALUE)) {
					cameFrom.put(np, current);
					gScore.put(np, tentative);
					open.add(new Node(np, tentative + heuristic(np, chest)));
				}
			}
		}

		// No full route within budget — return the best partial path toward the chest.
		return best.equals(start) ? List.of() : reconstruct(cameFrom, best);
	}

	private static List<Neighbor> neighbors(World world, BlockPos p) {
		List<Neighbor> out = new ArrayList<>(6);
		boolean canClimbHere = isClimbable(world, p) || isWater(world, p);

		for (Direction dir : HORIZONTAL) {
			BlockPos n = p.offset(dir);
			if (occupiable(world, n)) {
				out.add(new Neighbor(n.toImmutable(), moveCost(world, n)));
				continue;
			}
			BlockPos up = n.up();
			if (passable(world, p.up(2)) && occupiable(world, up)) {
				out.add(new Neighbor(up.toImmutable(), 1.5));
				continue;
			}
			if (passable(world, n) && passable(world, n.up())) {
				for (int d = 1; d <= MAX_DROP; d++) {
					BlockPos down = n.down(d);
					if (occupiable(world, down)) {
						out.add(new Neighbor(down.toImmutable(), 1.0 + d * 0.5));
						break;
					}
					if (!passable(world, down)) {
						break;
					}
				}
			}
		}

		// Climb / swim up.
		if (canClimbHere) {
			BlockPos up = p.up();
			if (occupiable(world, up)) {
				out.add(new Neighbor(up.toImmutable(), moveCost(world, up)));
			}
		}
		// Climb / sink down.
		BlockPos down = p.down();
		if ((canClimbHere || isClimbable(world, down) || isWater(world, down)) && occupiable(world, down)) {
			out.add(new Neighbor(down.toImmutable(), moveCost(world, down)));
		}

		return out;
	}

	private static BlockPos groundStart(World world, BlockPos start) {
		if (occupiable(world, start)) {
			return start.toImmutable();
		}
		for (int d = 1; d <= 4; d++) {
			BlockPos c = start.down(d);
			if (occupiable(world, c)) {
				return c.toImmutable();
			}
		}
		for (int u = 1; u <= 2; u++) {
			BlockPos c = start.up(u);
			if (occupiable(world, c)) {
				return c.toImmutable();
			}
		}
		return null;
	}

	private static boolean passable(World world, BlockPos pos) {
		return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
	}

	private static boolean isWater(World world, BlockPos pos) {
		return world.getFluidState(pos).isIn(FluidTags.WATER);
	}

	private static boolean isClimbable(World world, BlockPos pos) {
		return world.getBlockState(pos).isIn(BlockTags.CLIMBABLE);
	}

	/** A spot the player can occupy: feet and head clear, with support below (floor, water, or climbable). */
	private static boolean occupiable(World world, BlockPos pos) {
		if (!passable(world, pos) || !passable(world, pos.up())) {
			return false;
		}
		return !passable(world, pos.down()) || isWater(world, pos) || isClimbable(world, pos);
	}

	private static double moveCost(World world, BlockPos pos) {
		// Mildly prefer dry land over swimming.
		return isWater(world, pos) ? 1.3 : 1.0;
	}

	private static boolean isAdjacent(BlockPos a, BlockPos b) {
		int dx = Math.abs(a.getX() - b.getX());
		int dy = Math.abs(a.getY() - b.getY());
		int dz = Math.abs(a.getZ() - b.getZ());
		return dx <= 1 && dy <= 1 && dz <= 1 && (dx + dy + dz) != 0;
	}

	private static double heuristic(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dy = a.getY() - b.getY();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static List<BlockPos> reconstruct(Map<BlockPos, BlockPos> cameFrom, BlockPos end) {
		LinkedList<BlockPos> path = new LinkedList<>();
		BlockPos c = end;
		path.addFirst(c);
		while (cameFrom.containsKey(c)) {
			c = cameFrom.get(c);
			path.addFirst(c);
		}
		return path;
	}

	private record Node(BlockPos pos, double f) {
	}

	private record Neighbor(BlockPos pos, double cost) {
	}
}
