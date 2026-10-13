package com.totalgolems.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

/**
 * Golem chests of the same kind that touch each other, single or double, work as one big chest for
 * the golem that owns any of them: it looks through all of them and fills them one after another.
 */
public final class ChestGroup {
	/** A double chest counts as one. */
	public static final int MAX_CHESTS = 8;

	private ChestGroup() {
	}

	/**
	 * The blocks of the group the chest at {@code start} belongs to, that one first. Empty when there is
	 * no golem chest there.
	 */
	public static List<BlockPos> positions(final Level level, final BlockPos start) {
		if (!level.isLoaded(start) || !(level.getBlockState(start).getBlock() instanceof MetalChestBlock kind)) {
			return List.of();
		}

		Set<BlockPos> blocks = new LinkedHashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		int chests = 0;
		queue.add(start);
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			if (blocks.contains(pos) || !level.isLoaded(pos)) {
				continue;
			}

			BlockState state = level.getBlockState(pos);
			if (!state.is(kind)) {
				continue;
			}

			// The second half of a double chest already counted comes along for free.
			BlockPos otherHalf = state.getValue(ChestBlock.TYPE) == ChestType.SINGLE ? null : ChestBlock.getConnectedBlockPos(pos, state);
			if (otherHalf == null || !blocks.contains(otherHalf)) {
				if (chests >= MAX_CHESTS) {
					continue;
				}

				chests++;
			}

			blocks.add(pos);
			for (Direction direction : Direction.values()) {
				queue.add(pos.relative(direction));
			}
		}

		return new ArrayList<>(blocks);
	}

	/** Whether the chest at {@code pos} is of the same group as the one at {@code start}. */
	public static boolean contains(final Level level, final BlockPos start, final BlockPos pos) {
		return positions(level, start).contains(pos);
	}

	/** All the chests of the group as a single inventory, or null when there is no golem chest at {@code start}. */
	public static @Nullable Container container(final Level level, final BlockPos start) {
		Container all = null;
		for (BlockPos pos : positions(level, start)) {
			if (level.getBlockEntity(pos) instanceof MetalChestBlockEntity chest) {
				all = all == null ? chest : new CompoundContainer(all, chest);
			}
		}

		return all;
	}

	/** The blocks touching the group that are not part of it. */
	public static List<BlockPos> neighbours(final Level level, final BlockPos start) {
		List<BlockPos> group = positions(level, start);
		Set<BlockPos> around = new LinkedHashSet<>();
		for (BlockPos pos : group) {
			for (Direction direction : Direction.values()) {
				around.add(pos.relative(direction));
			}
		}

		around.removeAll(group);
		return new ArrayList<>(around);
	}
}
