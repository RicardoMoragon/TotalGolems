package com.totalgolems.entity;

import com.totalgolems.GolemTier;
import com.totalgolems.block.MetalChestBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * A golem that digs while it wears a flower, carrying what it mines back to its chest. The flower
 * picks the pattern: a poppy digs a level tunnel in a straight line, a dandelion quarries the chunk
 * the golem stands in (see {@link QuarryPlan}).
 * <p>
 * Either way the golem works at a fixed height. It only ever moves one cell at a time into a cell it
 * has cleared and that has a floor, laying a block from its inventory where the floor is missing.
 * <p>
 * It digs with the pickaxe it takes from its chest, at that pickaxe's speed and with its
 * enchantments, and uses it on everything. When five seconds in water show that the diggings are
 * flooded, it goes home and has the sponge golem next door (see {@link SpongeGolem}) dry them out.
 */
public class MiningGolem extends WorkerGolem {
	private static final EntityDataAccessor<Boolean> DATA_MINING = SynchedEntityData.defineId(MiningGolem.class, EntityDataSerializers.BOOLEAN);
	/** A chest keeps this many blocks for the golem to lay as floor; see {@link CoalGolem}. */
	public static final int BLOCKS_KEPT_FOR_FLOOR = 64;
	/** The same for every tier. */
	public static final double MAX_HEALTH = 60.0;
	/** What unlocks a netherite golem. */
	public static final Item UNLOCK_FLOWER = Items.WITHER_ROSE;
	/** Five seconds standing in water and it calls for the sponge golem. */
	private static final int WATER_TICKS_BEFORE_ALERT = 100;
	private static final int STEP_TICKS_BEFORE_TELEPORT = 100;
	/** How far away another golem can be and still be asked which chunk it is quarrying. */
	private static final double CLAIM_SEARCH_RANGE = 96.0;
	/** How many chunks away a golem will go to find one that no other golem is quarrying. */
	private static final int CLAIM_CHUNK_RANGE = 2;
	private static final int CHEST_PROTECTION_INTERVAL = 200;
	private static final int BLOCKS_FETCHED_PER_TRIP = 64;
	/** How many blocks of water or lava it walls off in one go. */
	private static final int SEALS_AT_ONCE = 4;
	/** How often a quarrying golem looks over its layer for water and lava to wall off. */
	private static final int SEAL_SCAN_INTERVAL = 40;
	/** How far away it can lay a missing floor block under the place it is heading for. */
	private static final double FLOOR_REACH = 4.0;

	private final GolemTier tier;
	private final SimpleContainer inventory;
	/** Drops that did not fit in the inventory. Anything here means the golem is full and must unload. */
	private final List<ItemStack> overflow = new ArrayList<>();
	private final Map<BlockPos, BreakState> breaking = new HashMap<>();
	private @Nullable Direction miningDirection;
	private @Nullable BlockPos tunnelOrigin;
	private @Nullable QuarryPlan quarry;
	private boolean chestsProtected;
	private @Nullable BlockPos approachCell;
	private @Nullable BlockPos approachFrom;
	/** The last cell the golem worked from: where it goes back to after a trip or after being knocked away. */
	private @Nullable BlockPos anchor;
	private boolean returning;
	private boolean depositing;
	/** It ran out of blocks to build floor with and must bring some from the chest. */
	private boolean needsBlocks;
	/** It cannot go on (unbreakable block, bottom of the world). It waits at its chest for a new flower. */
	private boolean halted;
	private int stepTicks;
	private @Nullable BlockPos stepTarget;
	/** A netherite golem takes no pattern flower until a wither rose has unlocked it. */
	private boolean unlocked;
	/** Plain stone only wears the pickaxe every other block. */
	private boolean stoneWearsNext = true;
	/** Water and lava around the layer it is quarrying, still to be walled off; see {@link QuarryPlan#fluidsToSeal}. */
	private List<BlockPos> layerSeals = List.of();
	private int nextSealScan;
	/** Standing at its chest with nothing it can do: no pickaxe, or the sponge golem still drying the diggings. */
	private boolean waiting;
	private boolean waitingForDrain;
	private int waterTicks;

	public MiningGolem(final EntityType<? extends AbstractGolem> type, final Level level, final GolemTier tier) {
		super(type, level);
		this.tier = tier;
		this.inventory = new SimpleContainer(tier.inventorySlots());
		this.unlocked = !tier.startsLocked();
	}

	public static AttributeSupplier.Builder createAttributes(final GolemTier tier) {
		return createWorkerAttributes(tier.speedMultiplier(), MAX_HEALTH);
	}

	public GolemTier getTier() {
		return this.tier;
	}

	@Override
	public String getTextureId() {
		return this.tier.id();
	}

	@Override
	public boolean isSwingingArm() {
		return this.isMining();
	}

	public boolean isMining() {
		return this.entityData.get(DATA_MINING);
	}

	private void setMining(final boolean mining) {
		if (this.isMining() != mining) {
			this.entityData.set(DATA_MINING, mining);
		}
	}

	public boolean isHalted() {
		return this.halted;
	}

	public boolean isUnlocked() {
		return this.unlocked;
	}

	/** At home until the sponge golem has dried out where it was digging. */
	public boolean isWaitingForDrain() {
		return this.waitingForDrain;
	}

	@Override
	protected TagKey<Item> toolTag() {
		return ItemTags.PICKAXES;
	}

	/** Currents do not carry it off; water only slows it down. */
	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	private boolean isWorking() {
		if (this.halted) {
			return false;
		}

		ItemStack flower = this.getItemBySlot(FLOWER_SLOT);
		return flower.is(Items.DANDELION) ? this.quarry != null : flower.is(Items.POPPY) && this.miningDirection != null && this.tunnelOrigin != null;
	}

	@Override
	protected boolean isBusy() {
		return (this.depositing || this.isWorking()) && !this.waiting;
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_MINING, false);
	}

	// ------------------------------------------------------------------------------------ interaction

	public static boolean isPatternFlower(final ItemStack stack) {
		return stack.is(Items.POPPY) || stack.is(Items.DANDELION);
	}

	@Override
	public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		// Only the server knows whether it is unlocked, so the client just agrees that something happens.
		if (this.tier.startsLocked() && (held.is(UNLOCK_FLOWER) || isPatternFlower(held))) {
			if (this.level() instanceof ServerLevel level && !this.unlocked) {
				if (held.is(UNLOCK_FLOWER)) {
					held.consume(1, player);
					this.unlocked = true;
					level.playSound(null, this, SoundEvents.ENCHANTMENT_TABLE_USE, this.getSoundSource(), 1.0F, 1.0F);
				} else {
					player.sendOverlayMessage(Component.translatable("message.totalgolems.golem_locked"));
				}

				return InteractionResult.SUCCESS;
			} else if (this.level().isClientSide()) {
				return InteractionResult.SUCCESS;
			}
		}

		return super.mobInteract(player, hand);
	}

	@Override
	protected boolean isActivatingFlower(final ItemStack stack) {
		return this.unlocked && isPatternFlower(stack);
	}

	@Override
	protected void onActivated(final ServerLevel level, final Player player) {
		this.startMining(level, player.getDirection());
	}

	@Override
	protected void onDeactivated(final ServerLevel level) {
		this.stopMining(level);
	}

	private void startMining(final ServerLevel level, final Direction direction) {
		this.clearBreaking(level);
		this.halted = false;
		this.needsBlocks = false;
		this.stopWaiting();
		this.resetTravel();
		if (this.findChest(level) == null) {
			this.chestPos = this.findNearbyChest(level);
		}

		BlockPos cell = this.feetPos();
		this.anchor = cell;
		// A golem that is on its way to unload finishes that first, then comes back here.
		this.returning = this.depositing;
		if (this.getItemBySlot(FLOWER_SLOT).is(Items.DANDELION)) {
			// Back in a pit it already started, the golem carries on with it and keeps using its staircase.
			if (this.quarry == null || !this.quarry.isInsidePit(cell)) {
				ChunkPos chunk = this.findFreeChunk(level, cell);
				this.quarry = QuarryPlan.create(chunk.x(), chunk.z(), cell, this.chestPos, this.tier);
			}

			this.miningDirection = null;
			this.tunnelOrigin = null;
		} else {
			this.quarry = null;
			this.miningDirection = direction;
			this.tunnelOrigin = cell;
		}
	}

	/** The chunk this golem is quarrying or about to, or null when it is doing something else. */
	public @Nullable ChunkPos getQuarryChunk() {
		return this.quarry != null && !this.halted && this.getItemBySlot(FLOWER_SLOT).is(Items.DANDELION) ? new ChunkPos(this.quarry.chunkX(), this.quarry.chunkZ()) : null;
	}

	/**
	 * Two golems digging the same chunk would undo each other's staircases and floors, so each one takes
	 * a chunk to itself: the one it stands in if no other golem has it, otherwise the nearest free one.
	 */
	private ChunkPos findFreeChunk(final ServerLevel level, final BlockPos cell) {
		ChunkPos own = ChunkPos.containing(cell);
		Set<ChunkPos> taken = new HashSet<>();
		for (MiningGolem other : level.getEntitiesOfClass(MiningGolem.class, this.getBoundingBox().inflate(CLAIM_SEARCH_RANGE), golem -> golem != this && golem.isAlive())) {
			ChunkPos chunk = other.getQuarryChunk();
			if (chunk != null) {
				taken.add(chunk);
			}
		}

		List<ChunkPos> candidates = new ArrayList<>();
		for (int dx = -CLAIM_CHUNK_RANGE; dx <= CLAIM_CHUNK_RANGE; dx++) {
			for (int dz = -CLAIM_CHUNK_RANGE; dz <= CLAIM_CHUNK_RANGE; dz++) {
				candidates.add(new ChunkPos(own.x() + dx, own.z() + dz));
			}
		}

		// Nearest first: the chunk itself, then those sharing an edge with it, then the corners, and so on outwards.
		candidates.sort(Comparator.comparingInt((ChunkPos chunk) -> Math.max(Math.abs(chunk.x() - own.x()), Math.abs(chunk.z() - own.z())))
			.thenComparingInt(chunk -> Math.abs(chunk.x() - own.x()) + Math.abs(chunk.z() - own.z()))
			.thenComparingInt(ChunkPos::x)
			.thenComparingInt(ChunkPos::z));
		return candidates.stream().filter(chunk -> !taken.contains(chunk)).findFirst().orElse(own);
	}

	private void stopMining(final ServerLevel level) {
		this.clearBreaking(level);
		this.setMining(false);
		this.miningDirection = null;
		this.tunnelOrigin = null;
		// The quarry plan is kept: its staircase is still the way out of the pit.
		this.anchor = null;
		this.returning = false;
		this.halted = false;
		this.needsBlocks = false;
		this.stopWaiting();
		this.resetTravel();
		this.getNavigation().stop();
	}

	private void stopWaiting() {
		this.waiting = false;
		this.waitingForDrain = false;
		this.waterTicks = 0;
		this.setWaitingForTool(false);
	}

	/** Gives up on the current job and walks home, where it waits until it is given a flower again. */
	private void halt(final ServerLevel level) {
		this.clearBreaking(level);
		this.setMining(false);
		this.halted = true;
		this.needsBlocks = false;
		this.stopWaiting();
		this.depositing = this.chestPos != null;
		this.resetTravel();
	}

	// ------------------------------------------------------------------------------------ pickaxe

	/**
	 * How fast the pickaxe gets through {@code state}: what a player holding it would manage, Efficiency
	 * included, and no better than a bare hand on what a pickaxe is not meant for.
	 */
	private float diggingSpeed(final ItemStack tool, final BlockState state) {
		float speed = tool.getDestroySpeed(state);
		if (speed > 1.0F) {
			int efficiency = this.level()
				.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.get(Enchantments.EFFICIENCY)
				.map(enchantment -> EnchantmentHelper.getItemEnchantmentLevel(enchantment, tool))
				.orElse(0);
			if (efficiency > 0) {
				speed += efficiency * efficiency + 1;
			}
		}

		return speed;
	}

	// ------------------------------------------------------------------------------------ water

	/** Counts how long it has been standing in water and, at five seconds, has the sponge golem called out. */
	private void tickWater(final ServerLevel level) {
		this.waterTicks = this.isInWater() ? this.waterTicks + 1 : 0;
		if (this.waterTicks < WATER_TICKS_BEFORE_ALERT) {
			return;
		}

		// With no sponge golem to call it carries on as best it can, and looks again in another five seconds.
		this.waterTicks = 0;
		SpongeGolem sponge = this.chestPos == null ? null : SpongeGolem.linkedTo(level, this.chestPos);
		if (sponge != null && sponge.requestDrain(this.feetPos(), this.diggings())) {
			this.waitingForDrain = true;
			this.beginDeposit(level);
		}
	}

	/** Everything it is digging out: the pit of its quarry, or its tunnel as far as it has got and a little beyond. */
	private BoundingBox diggings() {
		if (this.miningDirection == null || this.tunnelOrigin == null) {
			return this.quarry.pit();
		}

		Direction side = this.miningDirection.getClockWise();
		int radius = this.tier.tunnelRadius();
		BlockPos front = this.snapToTunnel(this.cellAt(this.tunnelOrigin.getY()), this.miningDirection).relative(this.miningDirection, 2);
		return BoundingBox.fromCorners(this.tunnelOrigin.relative(side, -radius), front.relative(side, radius).above(this.tier.tunnelHeight() - 1));
	}

	/** Called by the sponge golem when no water is left. */
	public void drainFinished() {
		this.waitingForDrain = false;
	}

	// ------------------------------------------------------------------------------------ behaviour

	@Override
	protected void serverStep(final ServerLevel level) {
		// Without a flower the golem still takes home whatever it was carrying.
		if (!this.depositing && (!this.overflow.isEmpty() || !this.isWorking() && !this.inventory.isEmpty() && this.chestPos != null)) {
			this.beginDeposit(level);
		}

		// Nothing can be dug without a pickaxe: home for one.
		if (!this.depositing && this.isWorking() && !this.hasTool()) {
			this.beginDeposit(level);
		}

		if (this.isWorking() && !this.depositing) {
			this.tickWater(level);
		}

		if (this.depositing) {
			this.tickDeposit(level);
		} else if (this.isWorking()) {
			this.tickWork(level);
		} else {
			this.setMining(false);
		}
	}

	private void beginDeposit(final ServerLevel level) {
		this.clearBreaking(level);
		this.setMining(false);
		this.depositing = true;
		this.resetTravel();
	}

	private void tickDeposit(final ServerLevel level) {
		if (this.chestPos == null) {
			// Nowhere to go: a halted golem just stays put, a full one keeps waiting for a chest.
			this.depositing = !this.halted;
			this.waiting = this.depositing;
			return;
		}

		if (this.mendPath(level)) {
			return;
		}

		BlockPos hop = this.quarry != null ? this.quarry.stairHop(this.feetPos(), this.chestPos) : null;
		if (hop != null) {
			this.waiting = false;
			this.travelTo(level, hop, 1.0);
			return;
		}

		if (!this.travelTo(level, this.chestPos, CHEST_REACH)) {
			this.waiting = false;
			return;
		}

		this.getLookControl().setLookAt(Vec3.atCenterOf(this.chestPos));
		if (this.tickCount % TRANSFER_INTERVAL != 0) {
			return;
		}

		// A missing or full chest, or one without blocks when blocks are needed, leaves the golem waiting here.
		Container chest = this.findChest(level);
		if (chest == null || !this.unloadInto(chest)) {
			return;
		}

		// So does one without a pickaxe, with its eyes off until somebody puts one in.
		boolean lacksTool = this.isWorking() && !this.hasTool() && !this.takeTool(chest);
		this.setWaitingForTool(lacksTool);
		if (this.waitingForDrain) {
			// The sponge golem says when it is done; should it be gone, there is nothing to wait for either.
			SpongeGolem sponge = SpongeGolem.linkedTo(level, this.chestPos);
			this.waitingForDrain = sponge != null && sponge.isDraining();
		}

		this.waiting = lacksTool || this.waitingForDrain;
		if (this.waiting || this.needsBlocks && !this.takeBuildingBlocks(chest)) {
			return;
		}

		this.needsBlocks = false;
		this.depositing = false;
		this.returning = true;
		this.resetTravel();
		this.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 0.8F);
		if (this.quarry != null && this.miningDirection == null) {
			// Back down into the pit: first a look over all of it for new blocks and for water to wall off.
			this.quarry.rescan();
			this.nextSealScan = 0;
		}
	}

	private boolean unloadInto(final Container chest) {
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			ItemStack stack = this.inventory.getItem(slot);
			if (!stack.isEmpty()) {
				this.inventory.setItem(slot, HopperBlockEntity.addItem(null, chest, stack, null));
			}
		}

		this.overflow.replaceAll(stack -> HopperBlockEntity.addItem(null, chest, stack, null));
		this.overflow.removeIf(ItemStack::isEmpty);
		chest.setChanged();
		return this.inventory.isEmpty() && this.overflow.isEmpty();
	}

	private boolean takeBuildingBlocks(final Container chest) {
		int wanted = BLOCKS_FETCHED_PER_TRIP;
		for (int slot = 0; slot < chest.getContainerSize() && wanted > 0; slot++) {
			ItemStack stack = chest.getItem(slot);
			if (isBuildingBlock(stack)) {
				ItemStack taken = chest.removeItem(slot, Math.min(wanted, stack.getCount()));
				wanted -= taken.getCount();
				this.inventory.addItem(taken);
			}
		}

		chest.setChanged();
		return wanted < BLOCKS_FETCHED_PER_TRIP;
	}

	/** Plain full blocks the golem may lay as floor: nothing that falls, holds data or is worth keeping. */
	public static boolean isBuildingBlock(final ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem blockItem) || stack.is(ConventionalItemTags.ORES) || stack.is(ConventionalItemTags.STORAGE_BLOCKS)) {
			return false;
		}

		BlockState state = blockItem.getBlock().defaultBlockState();
		return !(state.getBlock() instanceof FallingBlock) && !state.hasBlockEntity() && state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	private void tickWork(final ServerLevel level) {
		if (this.returning) {
			if (this.anchor != null && !this.travelToAnchor(level)) {
				this.setMining(false);
				return;
			}

			this.returning = false;
		}

		this.getNavigation().stop();
		if (this.miningDirection == null) {
			this.tickQuarry(level, this.quarry);
		} else {
			this.tickTunnel(level);
		}
	}

	private boolean travelToAnchor(final ServerLevel level) {
		if (this.mendPath(level)) {
			return false;
		}

		BlockPos hop = this.quarry != null ? this.quarry.stairHop(this.feetPos(), this.anchor) : null;
		BlockPos destination = hop != null ? hop : this.anchor;
		// Where the ground dipped there may be nothing to stand on, the top step of the stairs included: put
		// a block there first, or the golem drops back down every time it gets there.
		BlockPos floor = destination.below();
		boolean near = this.position().distanceTo(Vec3.atBottomCenterOf(destination)) <= FLOOR_REACH;
		if (near && !this.getBoundingBox().intersects(new AABB(floor)) && !this.ensureFloor(level, floor)) {
			return false;
		}

		if (hop != null) {
			this.travelTo(level, hop, 1.0);
			return false;
		}

		return this.travelTo(level, this.anchor, 0.35);
	}

	/** Poppy: a straight tunnel that never leaves the height it was started at. */
	private void tickTunnel(final ServerLevel level) {
		Direction direction = this.miningDirection;
		int y = this.tunnelOrigin.getY();
		if (!this.isOnLevel(y)) {
			this.returning = true;
			return;
		}

		BlockPos cell = this.snapToTunnel(this.cellAt(y), direction);
		if (!this.alignWithTunnel(cell, direction)) {
			return;
		}

		this.anchor = cell;
		BlockPos next = cell.relative(direction);
		if (!level.isLoaded(next)) {
			this.setMining(false);
			return;
		}

		List<BlockPos> slice = new ArrayList<>();
		Direction side = direction.getClockWise();
		for (int height = this.tier.tunnelHeight() - 1; height >= 0; height--) {
			for (int offset = -this.tier.tunnelRadius(); offset <= this.tier.tunnelRadius(); offset++) {
				slice.add(next.above(height).relative(side, offset));
			}
		}

		// Anything it cannot break ends the tunnel: the golem goes home.
		this.advance(level, next, slice, true);
	}

	/** Dandelion: clear the chunk one layer at a time, building the staircase down between layers. */
	private void tickQuarry(final ServerLevel level, final QuarryPlan plan) {
		if (this.tickCount >= this.nextSealScan) {
			this.layerSeals = plan.fluidsToSeal(level);
			this.nextSealScan = this.tickCount + SEAL_SCAN_INTERVAL;
		}

		if (this.tickCount % CHEST_PROTECTION_INTERVAL == 0 || !this.chestsProtected) {
			plan.protectChests(level);
			this.chestsProtected = true;
		}

		if (plan.isDescending()) {
			BlockPos stand = plan.stairStand(plan.stairsBuilt());
			BlockPos nextStand = plan.stairStand(plan.stairsBuilt() + 1);
			if (this.isAt(nextStand)) {
				plan.stairStepDone();
				this.anchor = nextStand;
				return;
			}

			this.anchor = stand;
			// Over the next step but not yet landed on it still counts as being on the way.
			boolean dropping = this.cellAt(nextStand.getY()).equals(nextStand) && this.getY() <= stand.getY() + 0.75;
			if (!this.isAt(stand) && !dropping) {
				this.returning = true;
			} else if (this.isAt(stand) && stand.getY() - 2 >= level.getMinY() && !this.ensureFloor(level, stand.below(2))) {
				// The block under the step it stands on is part of the staircase too, so it fills it in where there was a hole.
				return;
			} else if (nextStand.getY() - 1 < level.getMinY()) {
				this.halt(level);
			} else {
				// Room to walk off this step and drop onto the next one.
				this.advance(level, nextStand, List.of(nextStand.above(), nextStand), true);
			}

			return;
		}

		int y = plan.layerFeetY();
		BlockPos cell = this.cellAt(y);
		// A golem given a neighbouring chunk first has to get there.
		boolean approaching = plan.isOnFirstLayer() && !plan.contains(cell);
		if (approaching && this.isOnLevel(y)) {
			this.anchor = cell;
			this.approach(level, plan, cell);
			return;
		}

		if (!this.isOnLevel(y) || !plan.contains(cell)) {
			if (this.anchor == null || !approaching && (this.anchor.getY() != y || !plan.contains(this.anchor))) {
				this.anchor = plan.stairStand(plan.stairsBuilt());
			}

			this.returning = true;
			return;
		}

		this.anchor = cell;
		BlockPos next = plan.nextStep(level, cell);
		if (next == null) {
			plan.startNextLayer();
		} else {
			// Chests and unbreakable blocks are simply left standing.
			this.advance(level, next, plan.slice(cell, next), false);
		}
	}

	/**
	 * Walks to the chunk to be quarried the way it does everything else: a cell at a time, digging through
	 * what is in the way and laying floor where there is none. Chests and unbreakable blocks are walked around.
	 */
	private void approach(final ServerLevel level, final QuarryPlan plan, final BlockPos cell) {
		// Remembering where it came from keeps it from stepping back and forth in front of an obstacle.
		if (!cell.equals(this.approachCell)) {
			this.approachFrom = this.approachCell;
			this.approachCell = cell;
		}

		BlockPos entry = plan.stairStand(0);
		int stepX = Integer.signum(entry.getX() - cell.getX());
		int stepZ = Integer.signum(entry.getZ() - cell.getZ());
		List<BlockPos> steps = new ArrayList<>();
		if (stepX != 0) {
			steps.add(cell.offset(stepX, 0, 0));
		}

		if (stepZ != 0) {
			steps.add(cell.offset(0, 0, stepZ));
		}

		// Sideways, to get round something that cannot be dug.
		steps.add(stepX != 0 ? cell.offset(0, 0, 1) : cell.offset(1, 0, 0));
		steps.add(stepX != 0 ? cell.offset(0, 0, -1) : cell.offset(-1, 0, 0));
		for (BlockPos next : steps) {
			BlockState state = level.getBlockState(next);
			boolean passable = state.getCollisionShape(level, next).isEmpty() || state.getDestroySpeed(level, next) >= 0.0F && !state.hasBlockEntity();
			if (passable && !next.equals(this.approachFrom)) {
				List<BlockPos> column = new ArrayList<>();
				for (int up = this.tier.tunnelHeight() - 1; up >= 0; up--) {
					column.add(next.above(up));
				}

				this.advance(level, next, column, false);
				return;
			}
		}

		this.halt(level);
	}

	/**
	 * One step of work towards the adjacent cell {@code next}: dig out {@code slice}, make sure there is
	 * a floor to stand on, then walk in.
	 */
	private void advance(final ServerLevel level, final BlockPos next, final List<BlockPos> slice, final boolean haltOnObstacle) {
		if (this.sealFluids(level, slice)) {
			return;
		}

		LinkedHashSet<BlockPos> targets = new LinkedHashSet<>();
		for (BlockPos pos : slice) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
				continue;
			}

			if (state.getDestroySpeed(level, pos) < 0.0F || state.hasBlockEntity()) {
				if (haltOnObstacle) {
					this.halt(level);
					return;
				}

				continue;
			}

			targets.add(pos);
			if (targets.size() >= this.tier.blocksAtOnce()) {
				break;
			}
		}

		if (!targets.isEmpty()) {
			this.breakBlocks(level, targets);
			return;
		}

		this.clearBreaking(level);
		this.setMining(false);
		if (!this.ensureFloor(level, next.below())) {
			return;
		}

		if (!next.equals(this.stepTarget)) {
			this.stepTarget = next;
			this.stepTicks = 0;
		}

		double x = next.getX() + 0.5;
		double z = next.getZ() + 0.5;
		if (++this.stepTicks > STEP_TICKS_BEFORE_TELEPORT) {
			this.stepTicks = 0;
			this.teleportTo(x, next.getY(), z);
		} else {
			this.getMoveControl().setWantedPosition(x, next.getY(), z, 1.0);
		}
	}

	/** Returns true when {@code floor} can be stood on; otherwise does one thing towards making it so. */
	private boolean ensureFloor(final ServerLevel level, final BlockPos floor) {
		BlockState state = level.getBlockState(floor);
		if (!state.getCollisionShape(level, floor).isEmpty()) {
			return true;
		}

		if (floor.getY() < level.getMinY()) {
			this.halt(level);
			return false;
		}

		if (!state.isAir() && !state.canBeReplaced()) {
			level.destroyBlock(floor, true, this);
			return false;
		}

		this.placeBuildingBlock(level, floor);
		return false;
	}

	/**
	 * Puts back a missing block of its staircase or of the walkway to its chest, when it is near enough and
	 * has a block to do it with. Returns true when it did, which is all it does that tick.
	 */
	private boolean mendPath(final ServerLevel level) {
		if (this.quarry == null || !this.inventory.hasAnyMatching(MiningGolem::isBuildingBlock)) {
			return false;
		}

		for (BlockPos gap : this.quarry.pathGaps(level)) {
			if (gap.distToCenterSqr(this.position()) <= FLOOR_REACH * FLOOR_REACH && level.isUnobstructed(Blocks.STONE.defaultBlockState(), gap, CollisionContext.empty())) {
				return this.placeBuildingBlock(level, gap);
			}
		}

		return false;
	}

	/**
	 * Lays one of the blocks it carries at {@code pos}. With none left it goes to unload and bring some
	 * from the chest, and returns false.
	 */
	private boolean placeBuildingBlock(final ServerLevel level, final BlockPos pos) {
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			ItemStack stack = this.inventory.getItem(slot);
			if (isBuildingBlock(stack)) {
				BlockState block = ((BlockItem)stack.getItem()).getBlock().defaultBlockState();
				stack.shrink(1);
				level.setBlockAndUpdate(pos, block);
				level.playSound(null, pos, block.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
				return true;
			}
		}

		this.needsBlocks = true;
		this.beginDeposit(level);
		return false;
	}

	/**
	 * Water and lava do what a player would do about them: a source in what it is about to dig is filled
	 * in with a block, to be dug out again like any other, and whatever flows in from beyond the diggings
	 * is walled off for good where it comes in. Without its sources the rest of the water drains away by
	 * itself. It only does so with blocks it is carrying, which it nearly always is after digging stone;
	 * with none it digs on through the water as it is. Returns true when it sealed anything this tick.
	 */
	private boolean sealFluids(final ServerLevel level, final List<BlockPos> slice) {
		if (!this.inventory.hasAnyMatching(MiningGolem::isBuildingBlock)) {
			return false;
		}

		Set<BlockPos> inSlice = new HashSet<>(slice);
		LinkedHashSet<BlockPos> seal = new LinkedHashSet<>();
		// A quarry seals its whole layer, nearest first, before it digs on.
		if (this.miningDirection == null) {
			this.layerSeals.stream()
				.filter(pos -> isFluid(level, pos, false))
				.sorted(Comparator.comparingDouble(pos -> pos.distToCenterSqr(this.position())))
				.forEach(seal::add);
		}

		for (BlockPos pos : slice) {
			if (isFluid(level, pos, true)) {
				seal.add(pos);
			}

			for (Direction direction : Direction.values()) {
				BlockPos beside = pos.relative(direction);
				// A source anywhere, or water still running in from outside.
				if (!inSlice.contains(beside) && isFluid(level, beside, !this.isBeyondDiggings(beside))) {
					seal.add(beside);
				}
			}
		}

		int placed = 0;
		for (BlockPos pos : seal) {
			// Never on top of itself, or of anyone else.
			if (!level.isUnobstructed(Blocks.STONE.defaultBlockState(), pos, CollisionContext.empty())) {
				continue;
			}

			if (!this.inventory.hasAnyMatching(MiningGolem::isBuildingBlock)) {
				break;
			}

			this.placeBuildingBlock(level, pos);
			if (this.quarry != null && this.miningDirection == null && this.quarry.contains(pos)) {
				// The block filling the source has to be dug out again, wherever in the layer it is.
				this.quarry.rescan();
			}

			if (++placed >= SEALS_AT_ONCE) {
				break;
			}
		}

		return placed > 0;
	}

	/** Water or lava in a block with nothing solid in it, a source only if {@code sourceOnly}. */
	private static boolean isFluid(final Level level, final BlockPos pos, final boolean sourceOnly) {
		FluidState fluid = level.getFluidState(pos);
		if (fluid.isEmpty() || sourceOnly && !fluid.isSource()) {
			return false;
		}

		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && !state.hasBlockEntity();
	}

	/** Whether {@code pos} is outside everything this golem will ever dig: beyond its chunk, or beside its tunnel. */
	private boolean isBeyondDiggings(final BlockPos pos) {
		if (this.miningDirection == null || this.tunnelOrigin == null) {
			return this.quarry == null || !this.quarry.contains(pos);
		}

		Direction side = this.miningDirection.getClockWise();
		BlockPos offset = pos.subtract(this.tunnelOrigin);
		int across = offset.getX() * side.getStepX() + offset.getZ() * side.getStepZ();
		int along = offset.getX() * this.miningDirection.getStepX() + offset.getZ() * this.miningDirection.getStepZ();
		return along < 0 || Math.abs(across) > this.tier.tunnelRadius() || offset.getY() < 0 || offset.getY() >= this.tier.tunnelHeight();
	}

	private void breakBlocks(final ServerLevel level, final LinkedHashSet<BlockPos> targets) {
		Iterator<Map.Entry<BlockPos, BreakState>> stale = this.breaking.entrySet().iterator();
		while (stale.hasNext()) {
			Map.Entry<BlockPos, BreakState> entry = stale.next();
			if (!targets.contains(entry.getKey())) {
				level.destroyBlockProgress(entry.getValue().crackId, entry.getKey(), -1);
				stale.remove();
			}
		}

		this.setMining(true);
		this.getLookControl().setLookAt(Vec3.atCenterOf(targets.getFirst()));
		boolean playSound = this.tickCount % 4 == 0;
		for (BlockPos pos : targets) {
			BlockState state = level.getBlockState(pos);
			BreakState progress = this.breaking.get(pos);
			if (progress == null) {
				progress = new BreakState(this.freeCrackId());
				this.breaking.put(pos, progress);
			}

			ItemStack tool = this.getTool();
			boolean dropsLoot = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
			float hardness = state.getDestroySpeed(level, pos);
			// Same formula a player digs with: tool speed over hardness, far slower with the wrong tool.
			progress.progress += hardness <= 0.0F ? 1.0F : this.diggingSpeed(tool, state) / hardness / (dropsLoot ? 30.0F : 100.0F);
			if (playSound) {
				level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.NEUTRAL, 0.5F, 0.75F);
				playSound = false;
			}

			if (progress.progress < 1.0F) {
				int stage = (int)(progress.progress * 10.0F);
				if (stage != progress.stage) {
					progress.stage = stage;
					level.destroyBlockProgress(progress.crackId, pos, stage);
				}

				continue;
			}

			List<ItemStack> drops = dropsLoot ? Block.getDrops(state, level, pos, null, this, tool) : List.of();
			level.destroyBlockProgress(progress.crackId, pos, -1);
			this.breaking.remove(pos);
			level.destroyBlock(pos, false, this);
			for (ItemStack drop : drops) {
				ItemStack leftover = this.inventory.addItem(drop);
				if (!leftover.isEmpty()) {
					this.overflow.add(leftover);
				}
			}

			// The pickaxe wears as it does in a player's hand: not at all on what breaks at a touch.
			Tool wear = tool.get(DataComponents.TOOL);
			this.wearTool(hardness == 0.0F || this.isSpared(state) ? 0 : wear != null ? wear.damagePerBlock() : 1);
			if (!this.hasTool()) {
				// It broke. Whatever else was being dug is left as it is.
				this.clearBreaking(level);
				this.setMining(false);
				return;
			}
		}
	}

	/**
	 * Stone and deepslate are most of what a quarry is, so they cost half: of every two such blocks only
	 * the first wears the pickaxe.
	 */
	private boolean isSpared(final BlockState state) {
		if (!state.is(BlockTags.BASE_STONE_OVERWORLD)) {
			return false;
		}

		this.stoneWearsNext = !this.stoneWearsNext;
		return this.stoneWearsNext;
	}

	/**
	 * Crack overlays are keyed by an id on the client, normally the breaker's entity id. A golem that
	 * breaks several blocks at once needs one per block, so it uses negative ids no entity has.
	 */
	private int freeCrackId() {
		for (int index = 0; ; index++) {
			int id = -(1 + this.getId() * 16 + index);
			if (this.breaking.values().stream().noneMatch(state -> state.crackId == id)) {
				return id;
			}
		}
	}

	private void clearBreaking(final ServerLevel level) {
		this.breaking.forEach((pos, state) -> level.destroyBlockProgress(state.crackId, pos, -1));
		this.breaking.clear();
	}

	// ------------------------------------------------------------------------------------ movement

	private BlockPos cellAt(final int y) {
		return new BlockPos(Mth.floor(this.getX()), y, Mth.floor(this.getZ()));
	}

	/** Whether the golem stands at working height {@code y}, give or take a slab. */
	private boolean isOnLevel(final int y) {
		return Math.abs(this.getY() - y) <= 0.75;
	}

	private boolean isAt(final BlockPos cell) {
		return this.isOnLevel(cell.getY()) && this.cellAt(cell.getY()).equals(cell);
	}

	private BlockPos snapToTunnel(final BlockPos pos, final Direction direction) {
		return direction.getAxis() == Direction.Axis.X
			? new BlockPos(pos.getX(), pos.getY(), this.tunnelOrigin.getZ())
			: new BlockPos(this.tunnelOrigin.getX(), pos.getY(), pos.getZ());
	}

	/** Keeps the golem on the tunnel line if something pushed it sideways. Returns whether it is on it. */
	private boolean alignWithTunnel(final BlockPos cell, final Direction direction) {
		double centerX = cell.getX() + 0.5;
		double centerZ = cell.getZ() + 0.5;
		double sideways = direction.getAxis() == Direction.Axis.X ? this.getZ() - centerZ : this.getX() - centerX;
		if (Math.abs(sideways) <= 0.3) {
			return true;
		}

		this.setMining(false);
		if (Math.abs(sideways) > 1.5) {
			// Too far off to shuffle back: walk to the last good spot instead.
			this.returning = true;
		} else {
			this.getMoveControl().setWantedPosition(centerX, this.getY(), centerZ, 1.0);
		}

		return false;
	}

	// ------------------------------------------------------------------------------------ persistence

	@Override
	public void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("mining_direction", Direction.CODEC, this.miningDirection);
		output.storeNullable("tunnel_origin", BlockPos.CODEC, this.tunnelOrigin);
		output.storeNullable("anchor", BlockPos.CODEC, this.anchor);
		if (this.quarry != null) {
			output.putIntArray("quarry", this.quarry.save());
		}

		output.putBoolean("returning", this.returning);
		output.putBoolean("depositing", this.depositing);
		output.putBoolean("needs_blocks", this.needsBlocks);
		output.putBoolean("halted", this.halted);
		output.putBoolean("unlocked", this.unlocked);
		output.putBoolean("waiting_for_drain", this.waitingForDrain);
		this.inventory.storeAsItemList(output.list("inventory", ItemStack.CODEC));
		ValueOutput.TypedOutputList<ItemStack> overflowList = output.list("overflow", ItemStack.CODEC);
		this.overflow.forEach(overflowList::add);
	}

	@Override
	public void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.miningDirection = input.read("mining_direction", Direction.CODEC).filter(direction -> direction.getAxis().isHorizontal()).orElse(null);
		this.tunnelOrigin = input.read("tunnel_origin", BlockPos.CODEC).orElse(null);
		this.anchor = input.read("anchor", BlockPos.CODEC).orElse(null);
		this.quarry = input.getIntArray("quarry").map(data -> QuarryPlan.load(data, this.tier, this.chestPos)).orElse(null);
		this.returning = input.getBooleanOr("returning", false);
		this.depositing = input.getBooleanOr("depositing", false);
		this.needsBlocks = input.getBooleanOr("needs_blocks", false);
		this.halted = input.getBooleanOr("halted", false);
		// One already wearing a pattern flower was at work before the lock existed, and stays as it was.
		this.unlocked = !this.tier.startsLocked() || input.getBooleanOr("unlocked", false) || isPatternFlower(this.getItemBySlot(FLOWER_SLOT));
		this.waitingForDrain = input.getBooleanOr("waiting_for_drain", false);
		this.inventory.fromItemList(input.listOrEmpty("inventory", ItemStack.CODEC));
		this.overflow.clear();
		input.listOrEmpty("overflow", ItemStack.CODEC).forEach(this.overflow::add);
	}

	@Override
	protected void dropEquipment(final ServerLevel level) {
		super.dropEquipment(level);
		this.inventory.removeAllItems().forEach(stack -> this.spawnAtLocation(level, stack));
		this.overflow.forEach(stack -> this.spawnAtLocation(level, stack));
		this.overflow.clear();
	}

	@Override
	public void remove(final RemovalReason reason) {
		if (this.level() instanceof ServerLevel level) {
			this.clearBreaking(level);
		}

		super.remove(reason);
	}

	private static class BreakState {
		private final int crackId;
		private float progress;
		private int stage = -1;

		private BreakState(final int crackId) {
			this.crackId = crackId;
		}
	}
}
