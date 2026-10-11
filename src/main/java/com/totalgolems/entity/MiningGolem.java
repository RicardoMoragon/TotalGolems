package com.totalgolems.entity;

import com.totalgolems.GolemTier;
import com.totalgolems.block.MetalChestBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A golem that digs while it wears a flower, carrying what it mines back to its chest. The flower
 * picks the pattern: a poppy digs a level tunnel in a straight line, a dandelion quarries the chunk
 * the golem stands in (see {@link QuarryPlan}).
 * <p>
 * Either way the golem works at a fixed height. It only ever moves one cell at a time into a cell it
 * has cleared and that has a floor, laying a block from its inventory where the floor is missing.
 */
public class MiningGolem extends AbstractGolem {
	/** Same slot the copper golem wears its poppy in, so the vanilla antenna layer can draw it. */
	public static final EquipmentSlot FLOWER_SLOT = EquipmentSlot.SADDLE;
	/** Value of the enchantment data for Silk Touch; 1 to 3 are Fortune levels and 0 is none. */
	public static final int SILK_TOUCH = 4;
	private static final EntityDataAccessor<Boolean> DATA_MINING = SynchedEntityData.defineId(MiningGolem.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_ENCHANTMENT = SynchedEntityData.defineId(MiningGolem.class, EntityDataSerializers.INT);
	private static final double CHEST_REACH = 2.3;
	private static final int CHEST_SEARCH_RADIUS = 8;
	private static final int TRANSFER_INTERVAL = 10;
	private static final int REPATH_INTERVAL = 10;
	/** Seconds without moving while walking somewhere before the golem gives up and teleports there. */
	private static final int STALLED_SECONDS_BEFORE_TELEPORT = 10;
	private static final int STEP_TICKS_BEFORE_TELEPORT = 100;
	private static final int BLOCKS_FETCHED_PER_TRIP = 64;

	private final GolemTier tier;
	private final SimpleContainer inventory;
	/** Drops that did not fit in the inventory. Anything here means the golem is full and must unload. */
	private final List<ItemStack> overflow = new ArrayList<>();
	private final Map<BlockPos, BreakState> breaking = new HashMap<>();
	private @Nullable List<ItemStack> tools;
	private @Nullable BlockPos chestPos;
	private @Nullable Direction miningDirection;
	private @Nullable BlockPos tunnelOrigin;
	private @Nullable QuarryPlan quarry;
	/** The last cell the golem worked from: where it goes back to after a trip or after being knocked away. */
	private @Nullable BlockPos anchor;
	private boolean returning;
	private boolean depositing;
	/** It ran out of blocks to build floor with and must bring some from the chest. */
	private boolean needsBlocks;
	/** It cannot go on (unbreakable block, bottom of the world). It waits at its chest for a new flower. */
	private boolean halted;
	private int travelTicks;
	private int stalledSeconds;
	private @Nullable Vec3 lastTravelPosition;
	private int stepTicks;
	private @Nullable BlockPos stepTarget;

	public MiningGolem(final EntityType<? extends AbstractGolem> type, final Level level, final GolemTier tier) {
		super(type, level);
		this.tier = tier;
		this.inventory = new SimpleContainer(tier.inventorySlots());
		this.setPersistenceRequired();
		// The flower is handed back in dropEquipment, never rolled as random loot.
		this.setDropChance(FLOWER_SLOT, 0.0F);
	}

	public static AttributeSupplier.Builder createAttributes(final GolemTier tier) {
		return Mob.createMobAttributes()
			.add(Attributes.MOVEMENT_SPEED, 0.25 * tier.speedMultiplier())
			.add(Attributes.STEP_HEIGHT, 1.0)
			.add(Attributes.MAX_HEALTH, tier.maxHealth())
			.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	public GolemTier getTier() {
		return this.tier;
	}

	public boolean isMining() {
		return this.entityData.get(DATA_MINING);
	}

	private void setMining(final boolean mining) {
		if (this.isMining() != mining) {
			this.entityData.set(DATA_MINING, mining);
		}
	}

	/** 0 for none, 1 to 3 for that level of Fortune, {@link #SILK_TOUCH} for Silk Touch. */
	public int getEnchantment() {
		return this.entityData.get(DATA_ENCHANTMENT);
	}

	private void setEnchantment(final int enchantment) {
		this.entityData.set(DATA_ENCHANTMENT, enchantment);
		this.tools = null;
	}

	public boolean hasFlower() {
		return !this.getItemBySlot(FLOWER_SLOT).isEmpty();
	}

	public void setChestPos(final @Nullable BlockPos chestPos) {
		this.chestPos = chestPos;
	}

	public boolean isHalted() {
		return this.halted;
	}

	private boolean isWorking() {
		if (this.halted) {
			return false;
		}

		ItemStack flower = this.getItemBySlot(FLOWER_SLOT);
		return flower.is(Items.DANDELION) ? this.quarry != null : flower.is(Items.POPPY) && this.miningDirection != null && this.tunnelOrigin != null;
	}

	private boolean isBusy() {
		return this.depositing || this.isWorking();
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_MINING, false);
		entityData.define(DATA_ENCHANTMENT, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 6.0F) {
			@Override
			public boolean canUse() {
				return !MiningGolem.this.isBusy() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !MiningGolem.this.isBusy() && super.canContinueToUse();
			}
		});
		this.goalSelector.addGoal(2, new RandomLookAroundGoal(this) {
			@Override
			public boolean canUse() {
				return !MiningGolem.this.isBusy() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !MiningGolem.this.isBusy() && super.canContinueToUse();
			}
		});
	}

	// ------------------------------------------------------------------------------------ interaction

	public static boolean isPatternFlower(final ItemStack stack) {
		return stack.is(Items.POPPY) || stack.is(Items.DANDELION);
	}

	@Override
	public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.is(Items.ENCHANTED_BOOK)) {
			int enchantment = this.enchantmentOf(held);
			if (enchantment == 0) {
				return InteractionResult.PASS;
			}

			if (this.level() instanceof ServerLevel level) {
				// Only one enchantment at a time: the previous book comes back.
				ItemStack previous = this.enchantmentBook();
				held.consume(1, player);
				this.setEnchantment(enchantment);
				if (!previous.isEmpty() && !player.addItem(previous)) {
					this.spawnAtLocation(level, previous, 1.0F);
				}

				level.playSound(null, this, SoundEvents.ENCHANTMENT_TABLE_USE, this.getSoundSource(), 1.0F, 1.0F);
			}

			return InteractionResult.SUCCESS;
		}

		if (isPatternFlower(held)) {
			if (this.level() instanceof ServerLevel level) {
				ItemStack worn = this.getItemBySlot(FLOWER_SLOT);
				if (!worn.is(held.getItem())) {
					this.setItemSlot(FLOWER_SLOT, held.copyWithCount(1));
					held.consume(1, player);
					if (!worn.isEmpty() && !player.addItem(worn)) {
						this.spawnAtLocation(level, worn, 1.0F);
					}
				}

				// Giving the flower it already wears just starts the pattern again from here.
				this.startMining(level, player.getDirection());
				this.playSound(SoundEvents.COPPER_GOLEM_SPAWN, 0.6F, 1.4F);
			}

			return InteractionResult.SUCCESS;
		}

		if (held.isEmpty() && hand == InteractionHand.MAIN_HAND && this.hasFlower()) {
			if (this.level() instanceof ServerLevel level) {
				ItemStack flower = this.getItemBySlot(FLOWER_SLOT);
				this.setItemSlot(FLOWER_SLOT, ItemStack.EMPTY);
				if (!player.addItem(flower)) {
					this.spawnAtLocation(level, flower, 1.0F);
				}

				this.stopMining(level);
				level.playSound(null, this, SoundEvents.COPPER_GOLEM_SHEAR, this.getSoundSource(), 1.0F, 1.0F);
			}

			return InteractionResult.SUCCESS;
		}

		return super.mobInteract(player, hand);
	}

	private void startMining(final ServerLevel level, final Direction direction) {
		this.clearBreaking(level);
		this.halted = false;
		this.needsBlocks = false;
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
				this.quarry = QuarryPlan.create(cell, this.chestPos, this.tier);
			}

			this.miningDirection = null;
			this.tunnelOrigin = null;
		} else {
			this.quarry = null;
			this.miningDirection = direction;
			this.tunnelOrigin = cell;
		}
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
		this.resetTravel();
		this.getNavigation().stop();
	}

	/** Gives up on the current job and walks home, where it waits until it is given a flower again. */
	private void halt(final ServerLevel level) {
		this.clearBreaking(level);
		this.setMining(false);
		this.halted = true;
		this.needsBlocks = false;
		this.depositing = this.chestPos != null;
		this.resetTravel();
	}

	// ------------------------------------------------------------------------------------ enchantments

	private @Nullable Holder<Enchantment> enchantmentHolder(final int enchantment) {
		if (enchantment == 0) {
			return null;
		}

		HolderLookup.RegistryLookup<Enchantment> enchantments = this.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		return enchantments.get(enchantment == SILK_TOUCH ? Enchantments.SILK_TOUCH : Enchantments.FORTUNE).orElse(null);
	}

	/** Reads Fortune or Silk Touch from an enchanted book, in the form {@link #getEnchantment()} uses. */
	private int enchantmentOf(final ItemStack book) {
		ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		for (Holder<Enchantment> enchantment : stored.keySet()) {
			if (enchantment.is(Enchantments.SILK_TOUCH)) {
				return SILK_TOUCH;
			} else if (enchantment.is(Enchantments.FORTUNE)) {
				return Mth.clamp(stored.getLevel(enchantment), 1, 3);
			}
		}

		return 0;
	}

	private ItemStack enchantmentBook() {
		int enchantment = this.getEnchantment();
		Holder<Enchantment> holder = this.enchantmentHolder(enchantment);
		return holder == null ? ItemStack.EMPTY : EnchantmentHelper.createBook(new EnchantmentInstance(holder, enchantment == SILK_TOUCH ? 1 : enchantment));
	}

	/** The tier's tools carrying the golem's enchantment, so block loot tables apply it as they would for a player. */
	private List<ItemStack> tools() {
		if (this.tools == null) {
			int enchantment = this.getEnchantment();
			Holder<Enchantment> holder = this.enchantmentHolder(enchantment);
			this.tools = this.tier.tools().stream().map(ItemStack::copy).toList();
			if (holder != null) {
				this.tools.forEach(tool -> tool.enchant(holder, enchantment == SILK_TOUCH ? 1 : enchantment));
			}
		}

		return this.tools;
	}

	// ------------------------------------------------------------------------------------ behaviour

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		// Without a flower the golem still takes home whatever it was carrying.
		if (!this.depositing && (!this.overflow.isEmpty() || !this.isWorking() && !this.inventory.isEmpty() && this.chestPos != null)) {
			this.beginDeposit(level);
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
			return;
		}

		BlockPos hop = this.quarry != null ? this.quarry.stairHop(this.feetPos(), this.chestPos) : null;
		if (hop != null) {
			this.travelTo(level, hop, 1.0);
			return;
		}

		if (!this.travelTo(level, this.chestPos, CHEST_REACH)) {
			return;
		}

		this.getLookControl().setLookAt(Vec3.atCenterOf(this.chestPos));
		if (this.tickCount % TRANSFER_INTERVAL != 0) {
			return;
		}

		// A missing or full chest, or one without blocks when blocks are needed, leaves the golem waiting here.
		Container chest = this.findChest(level);
		if (chest == null || !this.unloadInto(chest) || this.needsBlocks && !this.takeBuildingBlocks(chest)) {
			return;
		}

		this.needsBlocks = false;
		this.depositing = false;
		this.returning = true;
		this.resetTravel();
		this.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 0.8F);
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
			if (this.isBuildingBlock(stack)) {
				ItemStack taken = chest.removeItem(slot, Math.min(wanted, stack.getCount()));
				wanted -= taken.getCount();
				this.inventory.addItem(taken);
			}
		}

		chest.setChanged();
		return wanted < BLOCKS_FETCHED_PER_TRIP;
	}

	/** Plain full blocks the golem may lay as floor: nothing that falls, holds data or is worth keeping. */
	private boolean isBuildingBlock(final ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem blockItem) || stack.is(ConventionalItemTags.ORES) || stack.is(ConventionalItemTags.STORAGE_BLOCKS)) {
			return false;
		}

		BlockState state = blockItem.getBlock().defaultBlockState();
		return !(state.getBlock() instanceof FallingBlock) && !state.hasBlockEntity() && state.isCollisionShapeFullBlock(this.level(), BlockPos.ZERO);
	}

	private @Nullable Container findChest(final ServerLevel level) {
		return this.chestPos != null && level.isLoaded(this.chestPos) ? HopperBlockEntity.getContainerAt(level, this.chestPos) : null;
	}

	/** Golems that were not built on a block (summoned, for instance) adopt the closest golem chest. */
	private @Nullable BlockPos findNearbyChest(final ServerLevel level) {
		for (BlockPos pos : BlockPos.withinManhattan(this.blockPosition(), CHEST_SEARCH_RADIUS)) {
			if (level.getBlockState(pos).getBlock() instanceof MetalChestBlock) {
				return pos.immutable();
			}
		}

		return null;
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
		BlockPos hop = this.quarry != null ? this.quarry.stairHop(this.feetPos(), this.anchor) : null;
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
		if (!this.isOnLevel(y) || !plan.contains(cell)) {
			if (this.anchor == null || this.anchor.getY() != y || !plan.contains(this.anchor)) {
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
	 * One step of work towards the adjacent cell {@code next}: dig out {@code slice}, make sure there is
	 * a floor to stand on, then walk in.
	 */
	private void advance(final ServerLevel level, final BlockPos next, final List<BlockPos> slice, final boolean haltOnObstacle) {
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

		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			ItemStack stack = this.inventory.getItem(slot);
			if (this.isBuildingBlock(stack)) {
				BlockState block = ((BlockItem)stack.getItem()).getBlock().defaultBlockState();
				stack.shrink(1);
				level.setBlockAndUpdate(floor, block);
				level.playSound(null, floor, block.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
				return false;
			}
		}

		// Nothing to build with: unload and bring blocks from the chest.
		this.needsBlocks = true;
		this.beginDeposit(level);
		return false;
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

			ItemStack tool = this.bestTool(state);
			boolean dropsLoot = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
			float hardness = state.getDestroySpeed(level, pos);
			// Same formula a player digs with: tool speed over hardness, far slower with the wrong tool.
			progress.progress += hardness <= 0.0F ? 1.0F : tool.getDestroySpeed(state) * this.tier.hasteMultiplier() / hardness / (dropsLoot ? 30.0F : 100.0F);
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
		}
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

	private ItemStack bestTool(final BlockState state) {
		List<ItemStack> tools = this.tools();
		ItemStack best = tools.getFirst();
		for (ItemStack tool : tools) {
			if (tool.getDestroySpeed(state) > best.getDestroySpeed(state)) {
				best = tool;
			}
		}

		return best;
	}

	private void clearBreaking(final ServerLevel level) {
		this.breaking.forEach((pos, state) -> level.destroyBlockProgress(state.crackId, pos, -1));
		this.breaking.clear();
	}

	// ------------------------------------------------------------------------------------ movement

	/** The block the golem stands in. Sampled slightly above its feet so sunken blocks such as soul sand do not count as one lower. */
	private BlockPos feetPos() {
		return BlockPos.containing(this.getX(), this.getY() + 0.2, this.getZ());
	}

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

	/** Walks towards the bottom centre of {@code target}; returns true once within {@code reach} of it. */
	private boolean travelTo(final ServerLevel level, final BlockPos target, final double reach) {
		Vec3 spot = Vec3.atBottomCenterOf(target);
		double distance = this.position().distanceTo(spot);
		if (distance <= reach) {
			this.getNavigation().stop();
			this.resetTravel();
			return true;
		}

		if (distance < 1.5 && Math.abs(this.getY() - spot.y) < 0.6) {
			// Path nodes are a whole block apart, too coarse for the last stretch.
			this.getNavigation().stop();
			this.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.0);
		} else if (this.travelTicks % REPATH_INTERVAL == 0) {
			this.getNavigation().moveTo(spot.x, spot.y, spot.z, 1, 1.0);
		}

		if (++this.travelTicks % 20 == 0) {
			boolean moved = this.lastTravelPosition == null || this.lastTravelPosition.distanceTo(this.position()) > 0.5;
			this.stalledSeconds = moved ? 0 : this.stalledSeconds + 1;
			this.lastTravelPosition = this.position();
			if (this.stalledSeconds >= STALLED_SECONDS_BEFORE_TELEPORT) {
				this.resetTravel();
				this.teleportNextTo(level, target);
			}
		}

		return false;
	}

	private void resetTravel() {
		this.travelTicks = 0;
		this.stalledSeconds = 0;
		this.lastTravelPosition = null;
	}

	private void teleportNextTo(final ServerLevel level, final BlockPos pos) {
		List<BlockPos> candidates = List.of(pos, pos.above(), pos.north(), pos.south(), pos.east(), pos.west());
		for (BlockPos candidate : candidates) {
			Vec3 spot = Vec3.atBottomCenterOf(candidate);
			if (level.noCollision(this, this.getType().getDimensions().makeBoundingBox(spot))) {
				this.getNavigation().stop();
				this.teleportTo(spot.x, spot.y, spot.z);
				return;
			}
		}
	}

	// ------------------------------------------------------------------------------------ persistence

	@Override
	public void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("chest_pos", BlockPos.CODEC, this.chestPos);
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
		output.putInt("enchantment", this.getEnchantment());
		this.inventory.storeAsItemList(output.list("inventory", ItemStack.CODEC));
		ValueOutput.TypedOutputList<ItemStack> overflowList = output.list("overflow", ItemStack.CODEC);
		this.overflow.forEach(overflowList::add);
	}

	@Override
	public void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.chestPos = input.read("chest_pos", BlockPos.CODEC).orElse(null);
		this.miningDirection = input.read("mining_direction", Direction.CODEC).filter(direction -> direction.getAxis().isHorizontal()).orElse(null);
		this.tunnelOrigin = input.read("tunnel_origin", BlockPos.CODEC).orElse(null);
		this.anchor = input.read("anchor", BlockPos.CODEC).orElse(null);
		this.quarry = input.getIntArray("quarry").map(data -> QuarryPlan.load(data, this.tier, this.chestPos)).orElse(null);
		this.returning = input.getBooleanOr("returning", false);
		this.depositing = input.getBooleanOr("depositing", false);
		this.needsBlocks = input.getBooleanOr("needs_blocks", false);
		this.halted = input.getBooleanOr("halted", false);
		this.setEnchantment(Mth.clamp(input.getIntOr("enchantment", 0), 0, SILK_TOUCH));
		this.inventory.fromItemList(input.listOrEmpty("inventory", ItemStack.CODEC));
		this.overflow.clear();
		input.listOrEmpty("overflow", ItemStack.CODEC).forEach(this.overflow::add);
	}

	@Override
	protected void dropEquipment(final ServerLevel level) {
		super.dropEquipment(level);
		this.spawnAtLocation(level, this.getItemBySlot(FLOWER_SLOT));
		this.setItemSlot(FLOWER_SLOT, ItemStack.EMPTY);
		this.spawnAtLocation(level, this.enchantmentBook());
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

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	protected void playStepSound(final BlockPos pos, final BlockState blockState) {
		this.playSound(SoundEvents.COPPER_GOLEM_STEP, 1.0F, 1.0F);
	}

	@Override
	public Vec3 getLeashOffset() {
		return new Vec3(0.0, 0.75F * this.getEyeHeight(), 0.0);
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
