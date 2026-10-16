package com.totalgolems;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * Item frames hung on a chest, or beside the button of a minecart station (see {@link MinecartStations}),
 * say what a copper golem puts in it.
 * <ul>
 * <li>A frame with an item in it takes whatever matches it, as long as there is room, whatever else is
 * already inside; nothing else goes in.</li>
 * <li>An empty frame takes anything, but only what no other chest or station within the golem's reach
 * shows in a frame of its own: that goes there instead.</li>
 * <li>With no frames at all the vanilla rule holds: an item goes in only when it is empty or already holds
 * that item.</li>
 * </ul>
 */
public final class ItemFrameFilter {
	/** How far away the copper golem looks for chests: vanilla's own search distances. */
	public static final int SEARCH_HORIZONTAL = 32;
	public static final int SEARCH_VERTICAL = 8;

	private ItemFrameFilter() {
	}

	/** What the frames at a chest or a station show, and whether one of them is empty. */
	public record Marks(List<ItemStack> shown, boolean catchAll) {
		/** Whether the frames, not the vanilla rule, decide what goes in. */
		public boolean decide() {
			return !this.shown.isEmpty() || this.catchAll;
		}

		public boolean shows(final ItemStack stack) {
			return this.shown.stream().anyMatch(item -> ItemStack.isSameItem(item, stack));
		}

		/**
		 * Whether {@code stack} may go in: a frame shows it, or an empty frame takes it because nothing else
		 * shows it, which is only looked into when it matters. Only to be asked when the frames {@link #decide()}.
		 */
		public boolean takes(final ItemStack stack, final BooleanSupplier shownElsewhere) {
			return this.shows(stack) || this.catchAll && !shownElsewhere.getAsBoolean();
		}
	}

	/** The marks of the chest at {@code pos}: the frames hung on it, on either half of it if it is a double one. */
	public static Marks marksOn(final Level level, final BlockPos pos, final BlockState state) {
		List<BlockPos> blocks = new ArrayList<>();
		blocks.add(pos);
		if (state.getValueOrElse(ChestBlock.TYPE, ChestType.SINGLE) != ChestType.SINGLE) {
			blocks.add(ChestBlock.getConnectedBlockPos(pos, state));
		}

		AABB around = AABB.encapsulatingFullBlocks(blocks.getFirst(), blocks.getLast()).inflate(1.0);
		return marks(level.getEntitiesOfClass(ItemFrame.class, around, frame -> blocks.contains(frame.getPos().relative(frame.getDirection().getOpposite()))));
	}

	/** The marks of a station: the frames hanging in a cell beside its button {@code pos}, to one side of it or above. */
	public static Marks marksBeside(final Level level, final BlockPos pos) {
		List<BlockPos> cells = List.of(pos.north(), pos.south(), pos.east(), pos.west(), pos.above());
		return marks(level.getEntitiesOfClass(ItemFrame.class, new AABB(pos).inflate(1.0), frame -> cells.contains(frame.getPos())));
	}

	private static Marks marks(final List<ItemFrame> frames) {
		List<ItemStack> shown = new ArrayList<>();
		boolean catchAll = false;
		for (ItemFrame frame : frames) {
			if (frame.getItem().isEmpty()) {
				catchAll = true;
			} else {
				shown.add(frame.getItem());
			}
		}

		return new Marks(shown, catchAll);
	}

	/**
	 * Whether some chest or minecart station within reach of {@code golem} shows {@code stack} in a frame:
	 * then that is where it goes, not into a chest or station with an empty frame. Chests the golem has found
	 * it cannot get to do not count.
	 */
	public static boolean shownElsewhere(final Level level, final PathfinderMob golem, final ItemStack stack) {
		AABB area = new AABB(golem.blockPosition()).inflate(SEARCH_HORIZONTAL, SEARCH_VERTICAL, SEARCH_HORIZONTAL);
		Set<GlobalPos> unreachable = golem.getBrain().getMemory(MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS).orElse(Set.of());
		Predicate<BlockEntity> showsIt = blockEntity -> {
			BlockPos pos = blockEntity.getBlockPos();
			BlockState state = blockEntity.getBlockState();
			return (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST))
				&& area.contains(pos.getX(), pos.getY(), pos.getZ())
				&& !unreachable.contains(new GlobalPos(level.dimension(), pos))
				&& marksOn(level, pos, state).shows(stack);
		};
		int chunks = Math.floorDiv(SEARCH_HORIZONTAL, 16) + 1;
		for (ChunkPos chunkPos : ChunkPos.rangeClosed(ChunkPos.containing(golem.blockPosition()), chunks).toList()) {
			LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());
			if (chunk != null && chunk.getBlockEntities().values().stream().anyMatch(blockEntity -> blockEntity instanceof ChestBlockEntity && showsIt.test(blockEntity))) {
				return true;
			}
		}

		return MinecartStations.waitingWithin(level, area).stream().anyMatch(cart -> MinecartStations.marks(level, cart).shows(stack));
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
