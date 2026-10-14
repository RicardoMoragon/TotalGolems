package com.totalgolems;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

/**
 * Item frames hung on a chest say what a copper golem puts in it. A chest with one or more frames
 * holding an item takes whatever matches one of them, as long as there is room, whatever else is
 * already inside; nothing else goes in. A chest without frames follows the vanilla rule: it takes
 * an item only when it is empty or already holds that item.
 */
public final class ItemFrameFilter {
	private ItemFrameFilter() {
	}

	/** The frames with an item in them hung on the chest at {@code pos}, either half of it if it is a double one. */
	public static List<ItemFrame> framesOn(final Level level, final BlockPos pos, final BlockState state) {
		List<BlockPos> blocks = new ArrayList<>();
		blocks.add(pos);
		if (state.getValueOrElse(ChestBlock.TYPE, ChestType.SINGLE) != ChestType.SINGLE) {
			blocks.add(ChestBlock.getConnectedBlockPos(pos, state));
		}

		AABB around = AABB.encapsulatingFullBlocks(blocks.getFirst(), blocks.getLast()).inflate(1.0);
		return level.getEntitiesOfClass(
			ItemFrame.class, around, frame -> !frame.getItem().isEmpty() && blocks.contains(frame.getPos().relative(frame.getDirection().getOpposite()))
		);
	}

	/** Whether the chest is marked at all, so that its frames, not the vanilla rule, decide what goes in. */
	public static boolean isMarked(final Level level, final BlockPos pos, final BlockState state) {
		return !framesOn(level, pos, state).isEmpty();
	}

	/** Whether {@code stack} may be delivered to the chest at {@code pos}: it has no frames, or one shows that item. */
	public static boolean accepts(final Level level, final BlockPos pos, final BlockState state, final ItemStack stack) {
		List<ItemFrame> frames = framesOn(level, pos, state);
		return frames.isEmpty() || frames.stream().anyMatch(frame -> ItemStack.isSameItem(frame.getItem(), stack));
	}

	/** Whether any of {@code stack} fits in {@code container}: an empty slot, or a stack of it that is not full. */
	public static boolean hasRoomFor(final Container container, final ItemStack stack) {
		for (ItemStack there : container) {
			if (there.isEmpty() || ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
				return true;
			}
		}

		return false;
	}
}
