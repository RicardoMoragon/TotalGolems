package com.totalgolems.entity;

import com.totalgolems.block.ChestGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A golem that works inside the fenced pen it was switched on in (see {@link Enclosure}) and never
 * leaves it: chests are used from the nearest spot inside, so they can stand in the fence line.
 * <p>
 * Each of them works one errand at a time: {@link #plan} picks it, and the golem is idle, eyes
 * off, whenever there is none.
 */
public abstract class PenGolem extends WorkerGolem {
	/** How far from a spot in the pen a chest can be and still be used. */
	protected static final double CONTAINER_REACH = 3.5;
	protected static final double WORK_REACH = 2.5;
	protected static final int CHEST_SEARCH_RADIUS = 16;
	private static final int PEN_REFRESH_INTERVAL = 100;
	private static final int PLAN_INTERVAL = 20;
	private static final int ACT_INTERVAL = 8;

	protected @Nullable Enclosure pen;
	private @Nullable BlockPos penOrigin;
	private boolean hasErrand;
	/** On its way to the chest for a tool. */
	private boolean fetchingTool;

	protected PenGolem(final EntityType<? extends AbstractGolem> type, final Level level) {
		super(type, level);
	}

	/** Chooses the next errand; returns false when there is nothing to do. Only called with a pen. */
	protected abstract boolean plan(ServerLevel level);

	/** Works on the current errand. Returns true once it is finished or has to be given up. */
	protected abstract boolean work(ServerLevel level);

	/** Drops the current errand, when the flower is taken or the pen is lost. */
	protected abstract void clearErrand();

	/** Whether there is nothing at all it can do without the tool of its trade. */
	protected boolean isIdleWithoutTool() {
		return true;
	}

	/** It never leaves its pen, not even by teleporting: whatever it cannot walk to it leaves alone for a while. */
	@Override
	protected boolean mayTeleport() {
		return false;
	}

	/** Called when it gives up on its errand because it found no way to where it had to go. */
	protected void onGaveUp() {
	}

	@Override
	protected boolean isBusy() {
		return this.hasErrand || this.fetchingTool;
	}

	/** The pen it works, or failing that the one it is standing in right now. */
	protected @Nullable Enclosure penOrSurroundings(final ServerLevel level) {
		return this.pen != null ? this.pen : Enclosure.find(level, this.feetPos());
	}

	@Override
	protected void onActivated(final ServerLevel level, final Player player) {
		this.penOrigin = this.feetPos();
		this.pen = null;
		this.stop();
		if (this.findChest(level) == null) {
			this.chestPos = this.findNearbyChest(level);
		}
	}

	@Override
	protected void onDeactivated(final ServerLevel level) {
		this.stop();
		this.getNavigation().stop();
	}

	private void stop() {
		this.hasErrand = false;
		this.fetchingTool = false;
		this.setWaitingForTool(false);
		this.clearErrand();
		this.setSwingingArm(false);
		this.resetTravel();
	}

	@Override
	protected void serverStep(final ServerLevel level) {
		if (!this.hasFlower() || this.penOrigin == null) {
			return;
		}

		if (this.pen == null || this.tickCount % PEN_REFRESH_INTERVAL == 0) {
			this.pen = Enclosure.find(level, this.penOrigin);
		}

		if (this.pen == null) {
			// Not fenced in: nothing it may touch.
			if (this.hasErrand) {
				this.stop();
			}

			return;
		}

		if (!this.hasTool() && this.isIdleWithoutTool()) {
			// Nothing gets done without the tool of its trade: to the chest for one.
			if (this.hasErrand) {
				this.stop();
			}

			this.fetchToolFromPen(level);
			return;
		}

		this.fetchingTool = false;
		this.setWaitingForTool(false);
		if (!this.hasErrand) {
			if (this.tickCount % PLAN_INTERVAL != 0) {
				return;
			}

			this.hasErrand = this.plan(level);
			this.resetTravel();
		}

		boolean done = this.hasErrand && this.work(level);
		if (this.hasErrand && !done && this.hasGivenUp()) {
			// No way there: the errand is dropped, and the next one will be somewhere else.
			this.onGaveUp();
			done = true;
		}

		if (done) {
			this.setSwingingArm(false);
			this.clearErrand();
			// Straight on to the next thing, if there is one.
			this.hasErrand = this.plan(level);
			this.resetTravel();
		}
	}

	/**
	 * Goes to the spot in the pen nearest its chest and takes a tool out of it. With none there, or
	 * with the chest out of reach, it waits at that spot with its eyes off until one turns up.
	 */
	private void fetchToolFromPen(final ServerLevel level) {
		BlockPos stand = this.chestPos == null || this.isUnreachable(this.chestPos) ? null : this.pen.standNear(this.chestPos, CONTAINER_REACH);
		if (stand == null) {
			this.fetchingTool = false;
			this.setWaitingForTool(true);
			return;
		}

		if (!this.travelTo(level, stand, 0.8)) {
			if (this.hasGivenUp()) {
				this.markUnreachable(this.chestPos);
			}

			this.fetchingTool = !this.hasGivenUp();
			this.setWaitingForTool(this.hasGivenUp());
			return;
		}

		this.fetchingTool = false;
		this.getLookControl().setLookAt(Vec3.atCenterOf(this.chestPos));
		if (this.isActTick()) {
			Container chest = this.findChest(level);
			this.setWaitingForTool(chest == null || !this.takeTool(chest));
			this.resetTravel();
		}
	}

	/** Whether this tick is one of those the golem acts on, so that jobs take a moment each. */
	protected boolean isActTick() {
		return this.tickCount % ACT_INTERVAL == 0;
	}

	/** Walks to within arm's reach of a spot in the pen. Returns true once there. */
	protected boolean walkTo(final ServerLevel level, final BlockPos spot) {
		return this.travelTo(level, spot, WORK_REACH);
	}

	/** Whether the container at {@code pos} can be used from inside the pen, and it has not lately found it out of its way. */
	protected boolean canReach(final @Nullable BlockPos pos) {
		return pos != null && !this.isUnreachable(pos) && this.pen != null && this.pen.standNear(pos, CONTAINER_REACH) != null;
	}

	/**
	 * Walks to the spot in the pen nearest the container at {@code pos}. Returns the container once the
	 * golem is there and it is a tick to act on, and null until then.
	 */
	protected @Nullable Container reachContainer(final ServerLevel level, final BlockPos pos) {
		BlockPos stand = this.pen.standNear(pos, CONTAINER_REACH);
		if (stand == null || !this.travelTo(level, stand, 0.8)) {
			if (this.hasGivenUp()) {
				this.markUnreachable(pos);
			}

			return null;
		}

		this.getLookControl().setLookAt(Vec3.atCenterOf(pos));
		if (!this.isActTick()) {
			return null;
		}

		// A golem chest comes with the chests joined to it.
		Container group = ChestGroup.container(level, pos);
		return group != null ? group : HopperBlockEntity.getContainerAt(level, pos);
	}

	/** The nearest block of the given kind that can be used from the pen, such as a chest to deliver to. */
	protected @Nullable BlockPos findContainer(final ServerLevel level, final Predicate<BlockState> wanted) {
		BlockPos centre = this.blockPosition();
		ChunkPos min = ChunkPos.containing(centre.offset(-CHEST_SEARCH_RADIUS, 0, -CHEST_SEARCH_RADIUS));
		ChunkPos max = ChunkPos.containing(centre.offset(CHEST_SEARCH_RADIUS, 0, CHEST_SEARCH_RADIUS));
		List<BlockPos> found = new ArrayList<>();
		for (int chunkX = min.x(); chunkX <= max.x(); chunkX++) {
			for (int chunkZ = min.z(); chunkZ <= max.z(); chunkZ++) {
				if (!level.hasChunk(chunkX, chunkZ)) {
					continue;
				}

				for (BlockEntity blockEntity : level.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
					if (wanted.test(blockEntity.getBlockState()) && this.canReach(blockEntity.getBlockPos())) {
						found.add(blockEntity.getBlockPos());
					}
				}
			}
		}

		return found.stream().min(java.util.Comparator.comparingDouble(pos -> pos.distSqr(centre))).orElse(null);
	}

	/** Moves everything in {@code from} into {@code chest}. Returns whether anything went. */
	protected static boolean unload(final SimpleContainer from, final Container chest) {
		boolean moved = false;
		for (int slot = 0; slot < from.getContainerSize(); slot++) {
			ItemStack stack = from.getItem(slot);
			if (!stack.isEmpty()) {
				int before = stack.getCount();
				ItemStack left = HopperBlockEntity.addItem(null, chest, stack, null);
				from.setItem(slot, left);
				moved |= left.getCount() < before;
			}
		}

		chest.setChanged();
		return moved;
	}

	protected static boolean isFull(final SimpleContainer container) {
		return container.getItems().stream().noneMatch(ItemStack::isEmpty);
	}

	@Override
	public void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("pen_origin", BlockPos.CODEC, this.penOrigin);
	}

	@Override
	public void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.penOrigin = input.read("pen_origin", BlockPos.CODEC).orElse(null);
	}
}
