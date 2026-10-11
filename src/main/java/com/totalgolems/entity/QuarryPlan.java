package com.totalgolems.entity;

import com.totalgolems.GolemTier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The dandelion pattern: dig out one chunk layer by layer.
 * <p>
 * The golem sweeps the inner 14x14 of the chunk in lanes, reaching into the outer ring of cells from
 * there. That ring is kept for a spiral staircase that goes down one block per cell: one block of
 * every ring column is left in place (or placed) as a step, which is how the golem gets back to its
 * chest. Local coordinates below are 0..15 inside the chunk.
 */
public class QuarryPlan {
	private static final int SIZE = 16;
	private static final int RING_LENGTH = 60;
	/** How many stair steps ahead the golem aims for when walking up or down, to keep paths short. */
	private static final int STAIR_HOP = 10;
	private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private final int minX;
	private final int minZ;
	/** Feet height of the first layer, where the staircase starts. */
	private final int topY;
	/** Ring index of the top of the staircase. */
	private final int stairStart;
	private final int radius;
	private final int height;
	private final List<int[]> waypoints;
	/** Floor blocks kept so the golem can walk from the top of the stairs to a chest inside the chunk. */
	private final Set<BlockPos> walkway;
	private int layer;
	private int stairsBuilt;
	private int routeIndex;

	private QuarryPlan(final int minX, final int minZ, final int topY, final int stairStart, final GolemTier tier, final @Nullable BlockPos chest) {
		this.minX = minX;
		this.minZ = minZ;
		this.topY = topY;
		this.stairStart = stairStart;
		this.radius = tier.tunnelRadius();
		this.height = tier.tunnelHeight();
		this.waypoints = buildWaypoints(this.radius);
		this.walkway = this.buildWalkway(chest);
	}

	public static QuarryPlan create(final BlockPos feet, final @Nullable BlockPos chest, final GolemTier tier) {
		int minX = feet.getX() & ~15;
		int minZ = feet.getZ() & ~15;
		BlockPos towards = chest != null ? chest : feet;
		int targetX = towards.getX() - minX;
		int targetZ = towards.getZ() - minZ;
		int best = 0;
		int bestDistance = Integer.MAX_VALUE;
		for (int index = 0; index < RING_LENGTH; index++) {
			int[] cell = ringCell(index);
			int distance = Math.abs(cell[0] - targetX) + Math.abs(cell[1] - targetZ);
			// The stairs cannot start on the chest itself.
			if (distance > 0 && distance < bestDistance) {
				best = index;
				bestDistance = distance;
			}
		}

		return new QuarryPlan(minX, minZ, feet.getY(), best, tier, chest);
	}

	public static @Nullable QuarryPlan load(final int[] data, final GolemTier tier, final @Nullable BlockPos chest) {
		if (data.length != 7) {
			return null;
		}

		QuarryPlan plan = new QuarryPlan(data[0], data[1], data[2], Math.floorMod(data[3], RING_LENGTH), tier, chest);
		plan.layer = Math.max(0, data[4]);
		plan.stairsBuilt = Mth.clamp(data[5], 0, plan.layer * plan.height);
		plan.routeIndex = Math.max(0, data[6]);
		return plan;
	}

	public int[] save() {
		return new int[]{this.minX, this.minZ, this.topY, this.stairStart, this.layer, this.stairsBuilt, this.routeIndex};
	}

	private static int[] ringCell(final int index) {
		if (index < 15) {
			return new int[]{index, 0};
		} else if (index < 30) {
			return new int[]{15, index - 15};
		} else if (index < 45) {
			return new int[]{45 - index, 15};
		} else {
			return new int[]{0, 60 - index};
		}
	}

	private static int ringIndex(final int x, final int z) {
		if (z == 0 && x <= 14) {
			return x;
		} else if (x == 15 && z <= 14) {
			return 15 + z;
		} else if (z == 15 && x >= 1) {
			return 45 - x;
		} else {
			return x == 0 && z >= 1 ? 60 - z : -1;
		}
	}

	private static boolean isInner(final int x, final int z) {
		return x >= 1 && x <= 14 && z >= 1 && z <= 14;
	}

	private static boolean isInside(final int x, final int z) {
		return x >= 0 && x < SIZE && z >= 0 && z < SIZE;
	}

	/** Lanes along X covering the inner area, joined at alternating ends into one serpentine. */
	private static List<int[]> buildWaypoints(final int radius) {
		List<Integer> lanes = new ArrayList<>();
		int lane = 1 + radius;
		for (; lane + radius <= 14; lane += 2 * radius + 1) {
			lanes.add(lane);
		}

		if (lanes.getLast() + radius < 14) {
			lanes.add(14 - radius);
		}

		List<int[]> cells = new ArrayList<>();
		for (int i = 0; i < lanes.size(); i++) {
			boolean forwards = i % 2 == 0;
			if (i > 0) {
				for (int z = lanes.get(i - 1) + 1; z < lanes.get(i); z++) {
					cells.add(new int[]{forwards ? 1 : 14, z});
				}
			}

			for (int step = 0; step < 14; step++) {
				cells.add(new int[]{forwards ? 1 + step : 14 - step, lanes.get(i)});
			}
		}

		return cells;
	}

	private Set<BlockPos> buildWalkway(final @Nullable BlockPos chest) {
		Set<BlockPos> blocks = new HashSet<>();
		if (chest == null || chest.getY() != this.topY || !this.contains(chest)) {
			return blocks;
		}

		int[] start = ringCell(this.stairStart);
		int x = start[0];
		int z = start[1];
		int chestX = chest.getX() - this.minX;
		int chestZ = chest.getZ() - this.minZ;
		while (x != chestX || z != chestZ) {
			blocks.add(new BlockPos(this.minX + x, this.topY - 1, this.minZ + z));
			if (x != chestX) {
				x += Integer.signum(chestX - x);
			} else {
				z += Integer.signum(chestZ - z);
			}
		}

		return blocks;
	}

	public boolean contains(final BlockPos pos) {
		return isInside(pos.getX() - this.minX, pos.getZ() - this.minZ);
	}

	/** Whether {@code pos} is in this quarry's chunk and no higher than where it was started. */
	public boolean isInsidePit(final BlockPos pos) {
		return this.contains(pos) && pos.getY() <= this.topY;
	}

	public int layerFeetY() {
		return this.topY - this.layer * this.height;
	}

	/** True while the golem still has to dig the stairs down to the current layer. */
	public boolean isDescending() {
		return this.stairsBuilt < this.layer * this.height;
	}

	public int stairsBuilt() {
		return this.stairsBuilt;
	}

	public void stairStepDone() {
		this.stairsBuilt++;
	}

	public void startNextLayer() {
		this.layer++;
		this.routeIndex = 0;
	}

	/** Where the golem stands on step {@code step} of the staircase; step 0 is the top. */
	public BlockPos stairStand(final int step) {
		int[] cell = ringCell(Math.floorMod(this.stairStart + step, RING_LENGTH));
		return new BlockPos(this.minX + cell[0], this.topY - step, this.minZ + cell[1]);
	}

	/** Blocks the golem never breaks: the staircase steps and the walkway to its chest. */
	public boolean isProtected(final BlockPos pos) {
		if (this.walkway.contains(pos)) {
			return true;
		}

		int x = pos.getX() - this.minX;
		int z = pos.getZ() - this.minZ;
		int index = isInside(x, z) ? ringIndex(x, z) : -1;
		int step = this.topY - 1 - pos.getY();
		return index >= 0 && step >= 0 && Math.floorMod(this.stairStart + step, RING_LENGTH) == index;
	}

	/**
	 * When the golem has to change height by more than a step, the stair position it should head for
	 * first. Null once it can go straight to its destination.
	 */
	public @Nullable BlockPos stairHop(final BlockPos current, final BlockPos target) {
		if (!this.isInsidePit(current) && !this.isInsidePit(target)) {
			return null;
		}

		int depth = Mth.clamp(this.topY - current.getY(), 0, this.stairsBuilt);
		int targetDepth = Mth.clamp(this.topY - target.getY(), 0, this.stairsBuilt);
		if (Math.abs(depth - targetDepth) <= 1) {
			return null;
		}

		return this.stairStand(depth > targetDepth ? Math.max(targetDepth, depth - STAIR_HOP) : Math.min(targetDepth, depth + STAIR_HOP));
	}

	/**
	 * The next cell of the current layer to walk into from {@code cell}, or null when the layer is done.
	 * Cells the golem cannot enter (unbreakable blocks, chests, a stair step) are skipped and walked around.
	 */
	public @Nullable BlockPos nextStep(final Level level, final BlockPos cell) {
		int y = this.layerFeetY();
		int fromX = cell.getX() - this.minX;
		int fromZ = cell.getZ() - this.minZ;
		for (; this.routeIndex < this.waypoints.size(); this.routeIndex++) {
			int[] target = this.waypoints.get(this.routeIndex);
			if (target[0] == fromX && target[1] == fromZ || this.isBlocked(level, target[0], target[1], y)) {
				continue;
			}

			int[] step = this.firstStepTowards(level, fromX, fromZ, target, y, false);
			if (step == null) {
				step = this.firstStepTowards(level, fromX, fromZ, target, y, true);
			}

			if (step != null) {
				return new BlockPos(this.minX + step[0], y, this.minZ + step[1]);
			}
		}

		return null;
	}

	private boolean isBlocked(final Level level, final int x, final int z, final int y) {
		BlockPos pos = new BlockPos(this.minX + x, y, this.minZ + z);
		if (this.isProtected(pos)) {
			return true;
		}

		BlockState state = level.getBlockState(pos);
		return !state.getCollisionShape(level, pos).isEmpty() && (state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F);
	}

	/** Breadth-first search over the layer. Unmined cells count as walkable, since the golem digs its way in. */
	private int @Nullable [] firstStepTowards(final Level level, final int fromX, final int fromZ, final int[] target, final int y, final boolean allowRing) {
		int[] previous = new int[SIZE * SIZE];
		java.util.Arrays.fill(previous, -1);
		int start = fromX + fromZ * SIZE;
		int goal = target[0] + target[1] * SIZE;
		previous[start] = start;
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		queue.add(start);
		while (!queue.isEmpty() && previous[goal] < 0) {
			int current = queue.poll();
			for (int[] offset : NEIGHBOURS) {
				int x = current % SIZE + offset[0];
				int z = current / SIZE + offset[1];
				if (!isInside(x, z) || previous[x + z * SIZE] >= 0 || !allowRing && !isInner(x, z) || this.isBlocked(level, x, z, y)) {
					continue;
				}

				previous[x + z * SIZE] = current;
				queue.add(x + z * SIZE);
			}
		}

		if (previous[goal] < 0) {
			return null;
		}

		int step = goal;
		while (previous[step] != start) {
			step = previous[step];
		}

		return new int[]{step % SIZE, step / SIZE};
	}

	/**
	 * Everything the golem digs to step from {@code cell} into the adjacent {@code next}, nearest first: its
	 * usual slice across the direction of travel, whatever else is within its width of the cell, and the
	 * ring cells next to those, which it never walks on.
	 */
	public List<BlockPos> slice(final BlockPos cell, final BlockPos next) {
		int nextX = next.getX() - this.minX;
		int nextZ = next.getZ() - this.minZ;
		boolean alongX = next.getX() != cell.getX();
		List<int[]> cells = new ArrayList<>();
		cells.add(new int[]{nextX, nextZ});
		for (int offset = 1; offset <= this.radius; offset++) {
			for (int side = -1; side <= 1; side += 2) {
				int x = alongX ? nextX : nextX + side * offset;
				int z = alongX ? nextZ + side * offset : nextZ;
				if (isInner(x, z)) {
					cells.add(new int[]{x, z});
				}
			}
		}

		// The rest of the square around the cell. The cross-section above already covers it along a lane; this
		// catches what a turn would otherwise leave behind.
		for (int dx = -this.radius; dx <= this.radius; dx++) {
			for (int dz = -this.radius; dz <= this.radius; dz++) {
				if ((alongX ? dx : dz) != 0 && isInner(nextX + dx, nextZ + dz)) {
					cells.add(new int[]{nextX + dx, nextZ + dz});
				}
			}
		}

		for (int i = 0, inner = cells.size(); i < inner; i++) {
			int x = cells.get(i)[0];
			int z = cells.get(i)[1];
			if (!isInner(x, z)) {
				continue;
			}

			int ringX = x == 1 ? 0 : x == 14 ? 15 : -1;
			int ringZ = z == 1 ? 0 : z == 14 ? 15 : -1;
			if (ringX >= 0) {
				cells.add(new int[]{ringX, z});
			}

			if (ringZ >= 0) {
				cells.add(new int[]{x, ringZ});
			}

			if (ringX >= 0 && ringZ >= 0) {
				cells.add(new int[]{ringX, ringZ});
			}
		}

		List<BlockPos> blocks = new ArrayList<>();
		int y = this.layerFeetY();
		for (int[] column : cells) {
			for (int up = this.height - 1; up >= 0; up--) {
				BlockPos pos = new BlockPos(this.minX + column[0], y + up, this.minZ + column[1]);
				if (!this.isProtected(pos)) {
					blocks.add(pos);
				}
			}
		}

		return blocks;
	}
}
