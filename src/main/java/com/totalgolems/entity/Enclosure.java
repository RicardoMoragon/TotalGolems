package com.totalgolems.entity;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A fenced pen: every place a golem can stand that it can walk to from where it started without
 * crossing a fence, wall, gate or anything else too tall to step over. The farming golems never
 * work, or walk, outside theirs.
 */
public final class Enclosure {
	private static final int MAX_CELLS = 4096;
	private static final int MAX_SPREAD = 48;
	private static final double FENCE_MARGIN = 1.25;
	private final Set<BlockPos> cells;
	private final Set<Long> columns = new HashSet<>();
	private final AABB bounds;

	private Enclosure(final Set<BlockPos> cells) {
		this.cells = cells;
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (BlockPos cell : cells) {
			this.columns.add(column(cell.getX(), cell.getZ()));
			minX = Math.min(minX, cell.getX());
			minY = Math.min(minY, cell.getY());
			minZ = Math.min(minZ, cell.getZ());
			maxX = Math.max(maxX, cell.getX());
			maxY = Math.max(maxY, cell.getY());
			maxZ = Math.max(maxZ, cell.getZ());
		}

		// Out to the fence line all round, where {@link #holds} still counts what stands there.
		this.bounds = new AABB(minX - 1, minY - 1, minZ - 1, maxX + 2, maxY + 3, maxZ + 2);
	}

	/**
	 * The pen around {@code start}, or null when there is none: the place is not closed off, so the
	 * search runs away into the open.
	 */
	public static @Nullable Enclosure find(final ServerLevel level, final BlockPos start) {
		BlockPos origin = canStand(level, start) ? start : canStand(level, start.above()) ? start.above() : canStand(level, start.below()) ? start.below() : null;
		if (origin == null) {
			return null;
		}

		Set<BlockPos> cells = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		cells.add(origin);
		queue.add(origin);
		while (!queue.isEmpty()) {
			BlockPos cell = queue.poll();
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos next = step(level, cell.relative(direction));
				if (next == null || !cells.add(next)) {
					continue;
				}

				if (cells.size() > MAX_CELLS || Math.abs(next.getX() - origin.getX()) > MAX_SPREAD || Math.abs(next.getZ() - origin.getZ()) > MAX_SPREAD) {
					return null;
				}

				queue.add(next);
			}
		}

		return new Enclosure(cells);
	}

	/** Where walking into the column of {@code pos} ends up: level, one block down or one block up, or nowhere. */
	private static @Nullable BlockPos step(final ServerLevel level, final BlockPos pos) {
		if (canStand(level, pos)) {
			return pos;
		}

		BlockState state = level.getBlockState(pos);
		if (isFence(state)) {
			return null;
		} else if (!blocks(level, pos, state)) {
			return canStand(level, pos.below()) ? pos.below() : null;
		} else {
			return canStand(level, pos.above()) ? pos.above() : null;
		}
	}

	private static boolean isFence(final BlockState state) {
		return state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCE_GATES);
	}

	/** Whether the block is in the way of something standing at its height. Crops, carpets and the like are not. */
	private static boolean blocks(final ServerLevel level, final BlockPos pos, final BlockState state) {
		if (isFence(state)) {
			return true;
		}

		VoxelShape shape = state.getCollisionShape(level, pos);
		return !shape.isEmpty() && shape.max(Direction.Axis.Y) > 0.6;
	}

	private static boolean canStand(final ServerLevel level, final BlockPos pos) {
		if (blocks(level, pos, level.getBlockState(pos))) {
			return false;
		}

		BlockPos below = pos.below();
		BlockState floor = level.getBlockState(below);
		// Shallow water counts: a golem wades across an irrigation channel.
		return !isFence(floor) && (!floor.getCollisionShape(level, below).isEmpty() || !floor.getFluidState().isEmpty());
	}

	private static long column(final int x, final int z) {
		return (long)x << 32 | z & 0xFFFFFFFFL;
	}

	public Collection<BlockPos> cells() {
		return this.cells;
	}

	/** A box around the whole pen, for finding the entities in it. */
	public AABB bounds() {
		return this.bounds;
	}

	/**
	 * Whether something standing at {@code pos} is inside the pen, at whatever height. Small animals fit
	 * in beside the fence itself and a crowded herd pushes some up on top of it, so anything within
	 * {@value #FENCE_MARGIN} blocks of the middle of some part of the pen counts. That takes in the whole
	 * fence line and is still nearer than anything on the far side of the fence can get.
	 */
	public boolean holds(final Vec3 pos) {
		int x = (int)Math.floor(pos.x);
		int z = (int)Math.floor(pos.z);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (this.columns.contains(column(x + dx, z + dz)) && Math.abs(pos.x - (x + dx + 0.5)) <= FENCE_MARGIN && Math.abs(pos.z - (z + dz + 0.5)) <= FENCE_MARGIN) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * The place in the pen to stand at to reach the block at {@code target}, which may be in the fence
	 * line or just outside. Null if it is further than {@code reach} from every part of the pen.
	 */
	public @Nullable BlockPos standNear(final BlockPos target, final double reach) {
		Vec3 centre = Vec3.atCenterOf(target);
		return this.cells
			.stream()
			.filter(cell -> !cell.equals(target))
			.min(Comparator.comparingDouble(cell -> Vec3.atBottomCenterOf(cell).distanceToSqr(centre)))
			.filter(cell -> Vec3.atBottomCenterOf(cell).distanceTo(centre) <= reach)
			.orElse(null);
	}
}
