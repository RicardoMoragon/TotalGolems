package com.totalgolems.entity;

import com.totalgolems.GolemTier;
import com.totalgolems.block.ChestGroup;
import com.totalgolems.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The drainer. While it wears a blue orchid it takes dry sponges from its sponge chest to the
 * nearest water around it and soaks the water up with them, one sponge at a time, bringing the wet
 * ones back.
 * <p>
 * Each sponge is really placed and taken away again, so it drinks exactly what a sponge placed by
 * hand would.
 * <p>
 * With its chest set against a mining golem's chest it works for that golem instead: it waits by
 * the chest until the miner comes home out of the water, then dries out the miner's diggings and
 * tells it when it is done. A lit campfire against the sponge chest dries the sponges it carries,
 * for a piece of coal out of the chest each time.
 * <p>
 * It floats, and so sinks with the water it soaks up. Where it can it works from dry land instead,
 * and from the bottom of what it has drained it is allowed to jump home however far that is.
 * <p>
 * It drains from the top down: sponges only go in the highest layer of water in its area, and it
 * moves down a layer only once there is no water left in that one. Water that keeps coming in from
 * outside the area is a job it cannot finish: when three sponges in a row leave as much water in
 * the layer as there was, it gives up, waits by its chest and tries again thirty seconds later.
 */
public class SpongeGolem extends WorkerGolem {
	private static final int SLOTS = 6;
	private static final int WORK_RADIUS = 16;
	private static final int WORK_HEIGHT = 8;
	/** A sponge soaks up water as far as six blocks away, sixty-four blocks of it at most. */
	private static final int SPONGE_RANGE = 6;
	private static final int SPONGE_CAPACITY = 64;
	private static final int MAX_WATER_BODY = 4096;
	/** Sponges are put down from the water's surface: this far to the side, and as far down as a sponge reaches. */
	private static final double PLACE_REACH = 3.5;
	private static final int PLACE_DEPTH = 7;
	/** How close to the middle of a block of dry land it stands to work from it. */
	private static final double STAND_REACH = 0.6;
	/** How much nearer water has to be, per block of it, to be worth more than a fuller spot further off. */
	private static final double DISTANCE_COST = 0.25;
	private static final int PLAN_INTERVAL = 20;
	/** Thirty seconds by the fire dries every sponge it is carrying. */
	public static final int DRYING_TICKS = 600;
	/** How much coal the coal golem keeps in a sponge chest that has a fire to feed. */
	public static final int CAMPFIRE_COAL_KEPT = 8;
	private static final double CAMPFIRE_REACH = 2.0;
	/** How long the water is given to settle before a layer that looks dry is taken to be dry. */
	private static final int SETTLE_TICKS = 60;
	/** Sponges in a row that leave the layer no drier before it decides the water is coming in from outside. */
	public static final int FRUITLESS_SPONGES_BEFORE_GIVING_UP = 3;
	/** How long it waits by its chest after giving up before it tries again. */
	public static final int RETRY_TICKS = 600;
	private static final double LINK_SEARCH_RANGE = 48.0;
	private static final double MINER_SEARCH_RANGE = 160.0;

	private final SimpleContainer dry = new SimpleContainer(SLOTS);
	private final SimpleContainer wet = new SimpleContainer(SLOTS);
	private @Nullable BlockPos home;
	/** Where the next sponge goes. */
	private @Nullable BlockPos spot;
	/** Dry land the next sponge can be put down from, when there is any within reach of the spot. */
	private @Nullable BlockPos stand;
	private boolean goingHome;
	/** The fire it is drying its sponges at. */
	private @Nullable BlockPos campfire;
	private int dryingTicks;
	/** What a mining golem asked to have dried, and where that golem met the water. */
	private @Nullable BoundingBox drainArea;
	private @Nullable BlockPos drainSpot;
	private boolean drainStarted;
	/** The layer it is draining, and the least water it has seen in it just before putting a sponge down. */
	private @Nullable Integer layerY;
	private int leastWaterInLayer;
	private int fruitlessSponges;
	/** When the area looked dry: it waits a moment to see whether the water comes back before calling it done. */
	private int dryLookingSince = -1;
	private boolean dryConfirmed;
	/** It has given up on water that keeps coming back, and waits until this tick to try again. */
	private int restUntil;
	private int spongesPlaced;
	private @Nullable BlockPos lastSponge;

	public SpongeGolem(final EntityType<? extends AbstractGolem> type, final Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createWorkerAttributes(1.0, 30.0);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		// A sponge floats, which keeps it from being stuck at the bottom of what it is draining.
		this.goalSelector.addGoal(0, new FloatGoal(this));
	}

	/** Currents do not carry it off. */
	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	/** Home is never too far to jump to: draining a lake can leave it at the bottom of a pit with no way up. */
	@Override
	protected boolean mayTeleportTo(final BlockPos target, final double distance) {
		return this.goingHome || this.campfire != null || super.mayTeleportTo(target, distance);
	}

	@Override
	public String getTextureId() {
		return "sponge";
	}

	public int countDrySponges() {
		return this.dry.countItem(Items.SPONGE);
	}

	public int countWetSponges() {
		return this.wet.countItem(Items.WET_SPONGE);
	}

	/** How many sponges it has put down, and where the last one went. */
	public int countSpongesPlaced() {
		return this.spongesPlaced;
	}

	public @Nullable BlockPos lastSponge() {
		return this.lastSponge;
	}

	/** Whether it gave up on water that keeps coming in and is waiting to try again. */
	public boolean isResting() {
		return this.tickCount < this.restUntil;
	}

	@Override
	protected boolean isBusy() {
		return this.spot != null || this.goingHome || this.campfire != null || this.dryLookingSince >= 0 && !this.dryConfirmed;
	}

	@Override
	protected boolean isActivatingFlower(final ItemStack stack) {
		return stack.is(Items.BLUE_ORCHID);
	}

	@Override
	protected void onActivated(final ServerLevel level, final Player player) {
		if (this.findChest(level) == null) {
			this.chestPos = this.findNearbyChest(level);
		}

		// The work area is centred on the chest, which is where every trip starts and ends.
		this.home = this.chestPos != null ? this.chestPos : this.feetPos();
		this.stop();
	}

	@Override
	protected void onDeactivated(final ServerLevel level) {
		this.stop();
		// A miner waiting for it finds it gone the next time it looks, and carries on.
		this.drainArea = null;
		this.drainSpot = null;
		this.getNavigation().stop();
	}

	private void stop() {
		this.spot = null;
		this.stand = null;
		this.goingHome = false;
		this.campfire = null;
		this.restUntil = 0;
		this.forgetLayer();
		this.resetTravel();
	}

	/** Starts afresh: no layer being drained, and no judgement yet on whether the area is dry. */
	private void forgetLayer() {
		this.layerY = null;
		this.fruitlessSponges = 0;
		this.dryLookingSince = -1;
		this.dryConfirmed = false;
	}

	// ------------------------------------------------------------------------------------ miners

	/** Whether its sponge chest is set against a mining golem's chest, which puts it at that golem's service. */
	public boolean isLinked(final ServerLevel level) {
		return this.linkedMinerChest(level) != null;
	}

	private @Nullable BlockPos linkedMinerChest(final ServerLevel level) {
		if (this.chestPos == null) {
			return null;
		}

		for (BlockPos pos : ChestGroup.neighbours(level, this.chestPos)) {
			if (GolemTier.isMinerChest(level.getBlockState(pos))) {
				return pos;
			}
		}

		return null;
	}

	/** The working sponge golem whose chest touches the mining golem chest at {@code minerChest}, if there is one. */
	public static @Nullable SpongeGolem linkedTo(final ServerLevel level, final BlockPos minerChest) {
		for (BlockPos pos : ChestGroup.neighbours(level, minerChest)) {
			if (!level.getBlockState(pos).is(ModBlocks.SPONGE_CHEST)) {
				continue;
			}

			List<SpongeGolem> golems = level.getEntitiesOfClass(
				SpongeGolem.class, new AABB(pos).inflate(LINK_SEARCH_RANGE), golem -> golem.isAlive() && golem.hasFlower() && golem.home != null && golem.ownsChest(pos)
			);
			if (!golems.isEmpty()) {
				return golems.getFirst();
			}
		}

		return null;
	}

	/**
	 * A mining golem asking to have {@code area} dried out, starting from {@code where} it met the water.
	 * Returns whether the job was taken.
	 */
	public boolean requestDrain(final BlockPos where, final BoundingBox area) {
		if (!this.hasFlower() || this.home == null) {
			return false;
		}

		this.drainArea = area;
		this.drainSpot = where;
		this.drainStarted = false;
		this.spot = null;
		this.forgetLayer();
		return true;
	}

	public boolean isDraining() {
		return this.drainArea != null;
	}

	private void finishDrain(final ServerLevel level) {
		this.drainArea = null;
		this.drainSpot = null;
		BlockPos minerChest = this.linkedMinerChest(level);
		if (minerChest != null) {
			level.getEntitiesOfClass(MiningGolem.class, new AABB(minerChest).inflate(MINER_SEARCH_RANGE), golem -> golem.isAlive() && golem.ownsChest(minerChest))
				.forEach(MiningGolem::drainFinished);
		}
	}

	// ------------------------------------------------------------------------------------ behaviour

	@Override
	protected void serverStep(final ServerLevel level) {
		if (!this.hasFlower() || this.home == null) {
			return;
		}

		if (this.campfire != null) {
			this.tickDrying(level, this.campfire);
		} else if (this.goingHome) {
			this.tickChest(level);
		} else if (this.spot != null) {
			this.tickAbsorb(level, this.spot);
		} else if (this.tickCount % PLAN_INTERVAL == 0 && !this.isResting()) {
			this.plan(level);
		}
	}

	private void plan(final ServerLevel level) {
		this.resetTravel();
		boolean linked = this.isLinked(level);
		if (!linked && this.drainArea != null) {
			// The chests were moved apart: back to looking for water on its own.
			this.drainArea = null;
			this.drainSpot = null;
		}

		Container chest = this.findChest(level);
		boolean awayFromChest = this.chestPos != null && this.position().distanceTo(Vec3.atBottomCenterOf(this.chestPos)) > CHEST_REACH + 1.0;
		if (linked && this.drainArea == null) {
			// At a miner's service it does nothing until called: it waits by its chest.
			this.goingHome = awayFromChest || !this.wet.isEmpty() && chest != null && this.canPutAway(level, chest);
			return;
		}

		boolean chestHasDry = chest != null && chest.hasAnyMatching(stack -> stack.is(Items.SPONGE));
		if (this.dry.isEmpty()) {
			// Out of dry sponges: back for more, or to leave the wet ones. With neither it stays where it is, by the chest.
			this.goingHome = chestHasDry || !this.wet.isEmpty();
			return;
		}

		this.spot = this.bestSpot(level);
		this.stand = this.spot == null ? null : this.dryStandFor(level, this.spot);
		if (this.spot != null) {
			this.dryLookingSince = -1;
			this.dryConfirmed = false;
			return;
		}

		// Water flowing back in takes a moment: only once it has stayed dry for a while is the job done.
		if (!this.dryConfirmed) {
			if (this.dryLookingSince < 0) {
				this.dryLookingSince = this.tickCount;
			}

			if (this.tickCount - this.dryLookingSince < SETTLE_TICKS) {
				return;
			}

			this.dryConfirmed = true;
			this.layerY = null;
		}

		// No water left: tell the miner, if this was for one, and put away what it is carrying.
		boolean wasDraining = this.drainArea != null;
		if (wasDraining) {
			this.finishDrain(level);
		}

		this.goingHome = !this.wet.isEmpty() && chest != null || wasDraining && awayFromChest;
	}

	/** Whether a trip to the chest would get rid of any wet sponge: there is room for one, or a fire to dry them at. */
	private boolean canPutAway(final ServerLevel level, final Container chest) {
		if (this.findCampfire(level) != null && chest.hasAnyMatching(stack -> stack.is(ItemTags.COALS))) {
			return true;
		}

		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack there = chest.getItem(slot);
			if (there.isEmpty() || there.is(Items.WET_SPONGE) && there.getCount() < there.getMaxStackSize()) {
				return true;
			}
		}

		return false;
	}

	private void tickChest(final ServerLevel level) {
		if (this.chestPos == null || !this.travelTo(level, this.chestPos, CHEST_REACH)) {
			this.goingHome = this.chestPos != null;
			return;
		}

		this.getLookControl().setLookAt(Vec3.atCenterOf(this.chestPos));
		if (this.tickCount % TRANSFER_INTERVAL != 0) {
			return;
		}

		// With a fire by the chest, and a piece of coal in it to feed the fire, the wet sponges are dried there
		// instead of being left for the coal golem.
		Container chest = this.findChest(level);
		BlockPos fire = this.wet.isEmpty() || chest == null ? null : this.findCampfire(level);
		if (fire != null && takeCoal(chest)) {
			this.campfire = fire;
			this.dryingTicks = 0;
			this.goingHome = false;
			this.resetTravel();
			return;
		}

		if (chest != null) {
			for (int slot = 0; slot < this.wet.getContainerSize(); slot++) {
				this.wet.setItem(slot, HopperBlockEntity.addItem(null, chest, this.wet.getItem(slot), null));
			}

			for (int slot = 0; slot < chest.getContainerSize(); slot++) {
				if (chest.getItem(slot).is(Items.SPONGE)) {
					chest.setItem(slot, this.dry.addItem(chest.getItem(slot)));
				}
			}

			chest.setChanged();
			this.playSound(SoundEvents.ITEM_PICKUP, 0.3F, 0.8F);
		}

		this.goingHome = false;
		this.resetTravel();
	}

	private @Nullable BlockPos findCampfire(final ServerLevel level) {
		return this.chestPos == null ? null : campfireBy(level, this.chestPos);
	}

	/** A lit campfire set against the sponge chest at {@code chestPos}, or one of the chests joined to it. */
	public static @Nullable BlockPos campfireBy(final ServerLevel level, final BlockPos chestPos) {
		for (BlockPos pos : ChestGroup.neighbours(level, chestPos)) {
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.CAMPFIRES) && state.getValue(CampfireBlock.LIT)) {
				return pos;
			}
		}

		return null;
	}

	/** Takes one piece of coal or charcoal out of {@code chest}. Returns whether there was one. */
	private static boolean takeCoal(final Container chest) {
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			if (chest.getItem(slot).is(ItemTags.COALS)) {
				chest.removeItem(slot, 1);
				chest.setChanged();
				return true;
			}
		}

		return false;
	}

	private void tickDrying(final ServerLevel level, final BlockPos fire) {
		BlockState state = level.getBlockState(fire);
		if (this.wet.isEmpty() || !state.is(BlockTags.CAMPFIRES) || !state.getValue(CampfireBlock.LIT)) {
			// The fire went out: the chest it is, then.
			this.campfire = null;
			this.goingHome = !this.wet.isEmpty();
			return;
		}

		if (!this.travelTo(level, fire, CAMPFIRE_REACH)) {
			return;
		}

		this.getLookControl().setLookAt(Vec3.atCenterOf(fire));
		if (++this.dryingTicks < DRYING_TICKS) {
			return;
		}

		// All of them at once.
		for (int slot = 0; slot < this.wet.getContainerSize(); slot++) {
			ItemStack soaked = this.wet.getItem(slot);
			if (soaked.is(Items.WET_SPONGE)) {
				// Any there is no room for stays wet until there is.
				ItemStack left = this.dry.addItem(new ItemStack(Items.SPONGE, soaked.getCount()));
				soaked.setCount(left.getCount());
			}
		}

		this.playSound(SoundEvents.FIRE_EXTINGUISH, 0.4F, 1.4F);
		this.campfire = null;
		this.resetTravel();
	}

	private void tickAbsorb(final ServerLevel level, final BlockPos pos) {
		if (this.stand != null) {
			// From dry land when it can, which keeps it out of the hole it is making.
			if (!this.travelTo(level, this.stand, STAND_REACH)) {
				return;
			} else if (!this.canPlaceAt(pos)) {
				this.stand = null;
				return;
			}
		} else if (!this.canPlaceAt(pos) && !this.travelTo(level, this.surfaceAbove(level, pos), PLACE_REACH)) {
			// Otherwise it floats, so it heads for the surface above the spot and drops the sponge from there.
			return;
		}

		this.getNavigation().stop();
		this.getLookControl().setLookAt(Vec3.atCenterOf(pos));
		if (this.tickCount % TRANSFER_INTERVAL != 0) {
			return;
		}

		this.spot = null;
		if (!level.getFluidState(pos).is(FluidTags.WATER) || !level.getBlockState(pos).canBeReplaced()) {
			return;
		}

		if (!this.worthAnotherSponge(level, pos.getY())) {
			this.giveUp(level);
			return;
		}

		// Put a sponge down, let it drink as any sponge does, and pick it up again.
		level.setBlockAndUpdate(pos, Blocks.SPONGE.defaultBlockState());
		boolean soaked = level.getBlockState(pos).is(Blocks.WET_SPONGE);
		level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		if (soaked) {
			this.dry.removeItemType(Items.SPONGE, 1);
			this.wet.addItem(new ItemStack(Items.WET_SPONGE));
		}

		this.drainStarted = true;
		this.spongesPlaced++;
		this.lastSponge = pos;
		this.plan(level);
	}

	/**
	 * Keeps count of how much water there is in the layer each time a sponge is about to go down in it.
	 * Returns false when the last three sponges have not brought it below the least seen: the water is
	 * coming in from outside as fast as it is taken away.
	 */
	private boolean worthAnotherSponge(final ServerLevel level, final int y) {
		int water = this.waterInLayer(level, y);
		if (this.layerY == null || this.layerY != y) {
			this.layerY = y;
			this.leastWaterInLayer = water;
			this.fruitlessSponges = 0;
		} else if (water < this.leastWaterInLayer) {
			this.leastWaterInLayer = water;
			this.fruitlessSponges = 0;
		} else if (++this.fruitlessSponges >= FRUITLESS_SPONGES_BEFORE_GIVING_UP) {
			return false;
		}

		return true;
	}

	/** The job cannot be finished: home, eyes off, and another go in thirty seconds. A miner waiting for it is told. */
	private void giveUp(final ServerLevel level) {
		if (this.drainArea != null) {
			this.finishDrain(level);
		}

		this.spot = null;
		this.stand = null;
		this.forgetLayer();
		this.restUntil = this.tickCount + RETRY_TICKS;
		this.goingHome = this.chestPos != null;
		this.resetTravel();
	}

	private int waterInLayer(final ServerLevel level, final int y) {
		BoundingBox area = this.workArea();
		int water = 0;
		for (BlockPos pos : BlockPos.betweenClosed(area.minX(), y, area.minZ(), area.maxX(), y, area.maxZ())) {
			if (level.isLoaded(pos) && this.isWater(level, pos)) {
				water++;
			}
		}

		return water;
	}

	private boolean canPlaceAt(final BlockPos pos) {
		double dx = pos.getX() + 0.5 - this.getX();
		double dz = pos.getZ() + 0.5 - this.getZ();
		double dy = pos.getY() - this.getY();
		return dx * dx + dz * dz <= PLACE_REACH * PLACE_REACH && dy >= -PLACE_DEPTH && dy <= PLACE_REACH;
	}

	/**
	 * The nearest block of dry ground from which a sponge can be put down at {@code pos}, or null if the
	 * spot can only be reached from the water.
	 */
	private @Nullable BlockPos dryStandFor(final ServerLevel level, final BlockPos pos) {
		int reach = (int)PLACE_REACH;
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos cell : BlockPos.betweenClosed(pos.offset(-reach, -reach, -reach), pos.offset(reach, PLACE_DEPTH, reach))) {
			double dx = cell.getX() - pos.getX();
			double dz = cell.getZ() - pos.getZ();
			// Short of the full reach, since it stops anywhere near the middle of the block.
			if (dx * dx + dz * dz > (PLACE_REACH - STAND_REACH) * (PLACE_REACH - STAND_REACH)) {
				continue;
			}

			double distance = cell.distToCenterSqr(this.position());
			if (distance < bestDistance && level.isLoaded(cell) && this.isDryGround(level, cell)) {
				best = cell.immutable();
				bestDistance = distance;
			}
		}

		return best;
	}

	/** Solid underfoot, with room to stand and no water in it. */
	private boolean isDryGround(final ServerLevel level, final BlockPos cell) {
		if (!level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)) {
			return false;
		}

		for (BlockPos body : List.of(cell, cell.above())) {
			BlockState state = level.getBlockState(body);
			if (!state.getCollisionShape(level, body).isEmpty() || !state.getFluidState().isEmpty()) {
				return false;
			}
		}

		return true;
	}

	private BlockPos surfaceAbove(final ServerLevel level, final BlockPos pos) {
		BlockPos top = pos;
		while (top.getY() - pos.getY() < PLACE_DEPTH && this.isWater(level, top.above())) {
			top = top.above();
		}

		return top;
	}

	// ------------------------------------------------------------------------------------ water

	/** A miner's diggings while it is drying them; otherwise the surroundings of its chest. */
	private BoundingBox workArea() {
		return this.drainArea != null
			? this.drainArea
			: BoundingBox.fromCorners(this.home.offset(-WORK_RADIUS, -WORK_HEIGHT, -WORK_RADIUS), this.home.offset(WORK_RADIUS, WORK_HEIGHT, WORK_RADIUS));
	}

	/** Still water and running water alike. */
	private boolean isWater(final ServerLevel level, final BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getFluidState().is(FluidTags.WATER) && state.canBeReplaced();
	}

	/**
	 * Where to put the next sponge: always in the highest layer of water in the area, and there in the
	 * body of water nearest the golem, the place from which a sponge would soak up the most, giving a
	 * little for being closer. Spots with little water left around them lose to fuller ones, so they are
	 * mopped up last.
	 */
	private @Nullable BlockPos bestSpot(final ServerLevel level) {
		BoundingBox area = this.workArea();
		// A miner's job starts where the miner met the water.
		Vec3 from = this.drainSpot != null && !this.drainStarted ? Vec3.atCenterOf(this.drainSpot) : this.position();
		BlockPos nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
			if (nearest != null && pos.getY() < nearest.getY() || !level.isLoaded(pos) || !this.isWater(level, pos)) {
				continue;
			}

			// A higher layer wins whatever the distance; within the layer, the nearest.
			double distance = pos.distToCenterSqr(from);
			if (nearest == null || pos.getY() > nearest.getY() || distance < nearestDistance) {
				nearest = pos.immutable();
				nearestDistance = distance;
			}
		}

		if (nearest == null) {
			return null;
		}

		Set<BlockPos> body = this.waterBody(level, area, nearest);
		BlockPos best = null;
		double bestValue = -Double.MAX_VALUE;
		int top = nearest.getY();
		for (BlockPos pos : body) {
			// Nothing below the top layer until that is dry. In a big pool a sample is plenty: neighbouring
			// spots soak up nearly the same water.
			if (pos.getY() != top || body.size() > 8 * SPONGE_CAPACITY && ((pos.getX() | pos.getZ()) & 1) != 0) {
				continue;
			}

			double value = soakedFrom(body, pos) - DISTANCE_COST * Math.sqrt(pos.distToCenterSqr(from));
			if (value > bestValue) {
				best = pos;
				bestValue = value;
			}
		}

		return best;
	}

	/** The water connected to {@code start}, within the work area. */
	private Set<BlockPos> waterBody(final ServerLevel level, final BoundingBox area, final BlockPos start) {
		Set<BlockPos> body = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		body.add(start);
		queue.add(start);
		while (!queue.isEmpty() && body.size() < MAX_WATER_BODY) {
			BlockPos pos = queue.poll();
			for (Direction direction : Direction.values()) {
				BlockPos next = pos.relative(direction);
				if (area.isInside(next) && !body.contains(next) && this.isWater(level, next)) {
					body.add(next);
					queue.add(next);
				}
			}
		}

		return body;
	}

	/** How many blocks of water a sponge placed at {@code start} would take, spreading through the water as a sponge does. */
	private static int soakedFrom(final Set<BlockPos> water, final BlockPos start) {
		Map<BlockPos, Integer> depth = new HashMap<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		depth.put(start, 0);
		queue.add(start);
		while (!queue.isEmpty() && depth.size() <= SPONGE_CAPACITY) {
			BlockPos pos = queue.poll();
			int next = depth.get(pos) + 1;
			if (next > SPONGE_RANGE) {
				continue;
			}

			for (Direction direction : Direction.values()) {
				BlockPos neighbour = pos.relative(direction);
				if (water.contains(neighbour) && !depth.containsKey(neighbour) && depth.size() <= SPONGE_CAPACITY) {
					depth.put(neighbour, next);
					queue.add(neighbour);
				}
			}
		}

		return depth.size();
	}

	// ------------------------------------------------------------------------------------ persistence

	@Override
	public void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("home", BlockPos.CODEC, this.home);
		output.storeNullable("drain_area", BoundingBox.CODEC, this.drainArea);
		output.storeNullable("drain_spot", BlockPos.CODEC, this.drainSpot);
		output.putBoolean("drain_started", this.drainStarted);
		this.dry.storeAsItemList(output.list("dry", ItemStack.CODEC));
		this.wet.storeAsItemList(output.list("wet", ItemStack.CODEC));
	}

	@Override
	public void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.home = input.read("home", BlockPos.CODEC).orElse(null);
		this.drainArea = input.read("drain_area", BoundingBox.CODEC).orElse(null);
		this.drainSpot = input.read("drain_spot", BlockPos.CODEC).orElse(null);
		this.drainStarted = input.getBooleanOr("drain_started", false);
		this.dry.fromItemList(input.listOrEmpty("dry", ItemStack.CODEC));
		this.wet.fromItemList(input.listOrEmpty("wet", ItemStack.CODEC));
	}

	@Override
	protected void dropEquipment(final ServerLevel level) {
		super.dropEquipment(level);
		this.dry.removeAllItems().forEach(stack -> this.spawnAtLocation(level, stack));
		this.wet.removeAllItems().forEach(stack -> this.spawnAtLocation(level, stack));
	}
}
