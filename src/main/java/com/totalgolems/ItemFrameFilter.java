package com.totalgolems;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
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
		Set<GlobalPos> unreachable = golem.getBrain().getMemory(MemoryModuleType.UNREACHABLE_TRANSPORT_BLOCK_POSITIONS).orElse(Set.of());
		for (MarkedChest chest : markedChests(level, golem)) {
			if (chest.marks().shows(stack) && !unreachable.contains(new GlobalPos(level.dimension(), chest.pos()))) {
				return true;
			}
		}

		AABB area = new AABB(golem.blockPosition()).inflate(SEARCH_HORIZONTAL, SEARCH_VERTICAL, SEARCH_HORIZONTAL);
		return MinecartStations.waitingWithin(level, area).stream().anyMatch(cart -> MinecartStations.marks(level, cart).shows(stack));
	}

	/** How long a look round for marked chests holds good, in ticks: frames are seldom moved, and a second late is no harm. */
	private static final int SCAN_TICKS = 20;
	/** How far the golem may walk before its last look round no longer holds. */
	private static final int SCAN_MOVE = 2;
	/**
	 * Each golem's last look round. Without it every chest it weighs up, one after the other, would have it go through
	 * every chest within reach again. Weak, so a golem that is gone takes its own with it.
	 */
	private static final Map<PathfinderMob, Scan> SCANS = new WeakHashMap<>();

	/** A chest with a frame showing an item. */
	private record MarkedChest(BlockPos pos, Marks marks) {
	}

	private record Scan(long until, BlockPos from, List<MarkedChest> chests) {
	}

	/** The chests within reach of {@code golem} with a frame showing an item, looked for at most once a second. */
	private static List<MarkedChest> markedChests(final Level level, final PathfinderMob golem) {
		long now = level.getGameTime();
		BlockPos from = golem.blockPosition();
		Scan scan = SCANS.get(golem);
		if (scan != null && now < scan.until() && scan.from().closerThan(from, SCAN_MOVE)) {
			return scan.chests();
		}

		AABB area = new AABB(from).inflate(SEARCH_HORIZONTAL, SEARCH_VERTICAL, SEARCH_HORIZONTAL);
		List<MarkedChest> chests = new ArrayList<>();
		int chunks = Math.floorDiv(SEARCH_HORIZONTAL, 16) + 1;
		for (ChunkPos chunkPos : ChunkPos.rangeClosed(Compat.chunkAt(from), chunks).toList()) {
			LevelChunk chunk = level.getChunkSource().getChunkNow(Compat.chunkX(chunkPos), Compat.chunkZ(chunkPos));
			if (chunk == null) {
				continue;
			}

			for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
				BlockPos pos = blockEntity.getBlockPos();
				BlockState state = blockEntity.getBlockState();
				if (blockEntity instanceof ChestBlockEntity && (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) && area.contains(pos.getX(), pos.getY(), pos.getZ())) {
					Marks marks = marksOn(level, pos, state);
					if (!marks.shown().isEmpty()) {
						chests.add(new MarkedChest(pos.immutable(), marks));
					}
				}
			}
		}

		SCANS.put(golem, new Scan(now + SCAN_TICKS, from.immutable(), chests));
		return chests;
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
