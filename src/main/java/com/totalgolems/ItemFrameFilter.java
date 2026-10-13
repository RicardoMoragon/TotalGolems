package com.totalgolems;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

/**
 * Item frames hung on a chest say what a copper golem may put in it: with one or more frames
 * holding an item, only those items go in. A chest without frames takes anything, as always.
 */
public final class ItemFrameFilter {
	private ItemFrameFilter() {
	}

	/** Whether {@code stack} may be delivered to the chest at {@code pos}, either half of it if it is a double one. */
	public static boolean accepts(final Level level, final BlockPos pos, final BlockState state, final ItemStack stack) {
		List<BlockPos> blocks = new ArrayList<>();
		blocks.add(pos);
		if (state.getValueOrElse(ChestBlock.TYPE, ChestType.SINGLE) != ChestType.SINGLE) {
			blocks.add(ChestBlock.getConnectedBlockPos(pos, state));
		}

		AABB around = AABB.encapsulatingFullBlocks(blocks.getFirst(), blocks.getLast()).inflate(1.0);
		List<ItemFrame> frames = level.getEntitiesOfClass(
			ItemFrame.class, around, frame -> !frame.getItem().isEmpty() && blocks.contains(frame.getPos().relative(frame.getDirection().getOpposite()))
		);
		return frames.isEmpty() || frames.stream().anyMatch(frame -> ItemStack.isSameItem(frame.getItem(), stack));
	}
}
