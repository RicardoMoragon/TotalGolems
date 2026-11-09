package com.totalgolems.entity;

import com.totalgolems.Compat;
import com.totalgolems.GolemTier;
import com.totalgolems.block.MetalChestBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
 * enchantments, and uses it on everything.
 */
public class MiningGolem extends WorkerGolem {
	private static final EntityDataAccessor<Boolean> DATA_MINING = SynchedEntityData.defineId(MiningGolem.class, EntityDataSerializers.BOOLEAN);
	/** A netherite golem takes no pattern flower until a wither rose has unlocked it, and shows it with the crest on its head. */
	private static final EntityDataAccessor<Boolean> DATA_UNLOCKED = SynchedEntityData.defineId(MiningGolem.class, EntityDataSerializers.BOOLEAN);
	/** How many slots of blocks it always keeps for walling off water and laying floor, instead of unloading them. */
	private static final int RESERVE_SLOTS = 2;
	/** A chest keeps this many blocks for the golem, as many as it fills its reserve with; see {@link CoalGolem}. */
	public static final int BLOCKS_KEPT_FOR_FLOOR = RESERVE_SLOTS * Item.DEFAULT_MAX_STACK_SIZE;
	/** Nothing harder than this to dig counts as a building block: obsidian is worth keeping, and slow to take out again. */
	private static final float BUILDING_BLOCK_MAX_HARDNESS = 5.0F;
	/** The same for every tier. */
	public static final double MAX_HEALTH = 60.0;
	/** What unlocks a netherite golem. */
	public static final Item UNLOCK_FLOWER = Items.WITHER_ROSE;
	private static final int STEP_TICKS_BEFORE_TELEPORT = 100;
	/** How far away another golem can be and still be asked which chunk it is quarrying. */
	private static final double CLAIM_SEARCH_RANGE = 96.0;
	/** How many chunks away a golem will go to find one that no other golem is quarrying. */
	private static final int CLAIM_CHUNK_RANGE = 2;
	private static final int CHEST_PROTECTION_INTERVAL = 200;
	/** How many blocks of water or lava it walls off in one go. */
	private static final int SEALS_AT_ONCE = 4;
	/** How often a quarrying golem looks over its layer for water and lava to wall off. */
	private static final int SEAL_SCAN_INTERVAL = 40;
	/** How far away a strip-mining golem notices ore that it can see. */
	private static final double VEIN_REACH = 5.0;
	/** The most blocks of ore it follows a single vein through. */
	private static final int VEIN_LIMIT = 256;
	/** How often it looks for ore in the walls while there is none in sight. */
	private static final int VEIN_SCAN_INTERVAL = 10;
	/** Far enough to cross the whole area of a strip mine. */
	private static final double STRIP_TELEPORT_DISTANCE = 72.0;
	/** How far away it can lay a missing floor block under the place it is heading for. */
	private static final double FLOOR_REACH = 4.0;
	/** How far away a quarrying golem walls off water and lava, or digs out a block to do it with. */
	private static final double SEAL_REACH = 4.5;
	/** Twenty seconds at work without breaking a block or getting to a new cell, and it stops trying where it is. */
	private static final int TICKS_WITHOUT_PROGRESS = 400;
	/** After this many times stuck in a row with nothing achieved in between, it gives up and goes home. */
	private static final int STUCK_TIMES_BEFORE_HALT = 3;
	/** How many of the cells it was last in count as somewhere it has already been. */
	private static final int RECENT_CELLS = 8;

	private final GolemTier tier;
	private final SimpleContainer inventory;
	/** Drops that did not fit in the inventory. Anything here means the golem is full and must unload. */
	/** How much faster than a player it digs, with the same tool. */
	private static final float DIGGING_SPEEDUP = 2.0F;
	/** The longest breather it takes after a block, however long that took. */
	private static final int MAX_REST_TICKS = 40;
	private final List<ItemStack> overflow = new ArrayList<>();
	private static final int CREST_RISE_TICKS = 12;
	/** Client side: when its crest started coming up. */
	private int crestRiseTick = -1000;
	/** Ticks spent digging since the last block came out. */
	private int ticksDigging;
	private int restUntilTick;
	private final Map<BlockPos, BreakState> breaking = new HashMap<>();
	private @Nullable Direction miningDirection;
	private @Nullable BlockPos tunnelOrigin;
	private @Nullable QuarryPlan quarry;
	/** The poppy's strip mine; see {@link StripPlan}. Set together with the mining direction and the tunnel origin. */
	private @Nullable StripPlan strip;
	/** When it next looks around for ore to mine out of the walls, while it has found none. */
	private int nextVeinScan;
	/** The rest of the veins of ore it has seen, connected block to block, which it mines out to the last one. */
	private final Set<BlockPos> vein = new LinkedHashSet<>();
	/** Ore it has gone after in the floor of its tunnels, to fill in once dug so that nothing falls in there. */
	private final Set<BlockPos> veinFloor = new HashSet<>();
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
	/** Plain stone only wears the pickaxe every other block. */
	private boolean stoneWearsNext = true;
	/** Water and lava around the layer it is quarrying, still to be walled off; see {@link QuarryPlan#fluidsToSeal}. */
	private List<BlockPos> layerSeals = List.of();
	private int nextSealScan;
	/** Blocks it put into water inside the pit, the first it digs out again when it needs one to wall off more. */
	private final Set<BlockPos> fillers = new HashSet<>();
	/** The block it is getting out of its layer to wall off water with, having none left. */
	private @Nullable BlockPos blockSource;
	/** How many times it has gone back over a finished layer because water was still showing in it. */
	private int dryPasses;
	/** The last tick it broke a block or got to a new cell; see {@link #checkProgress}. */
	private int lastProgressTick;
	/** The last few cells it has been in, to tell getting somewhere from going round in circles. */
	private final java.util.ArrayDeque<BlockPos> recentCells = new java.util.ArrayDeque<>();
	private int timesStuck;
	/** Standing at its chest with nothing it can do: no pickaxe, say. */
	private boolean waiting;

	public MiningGolem(final EntityType<? extends AbstractGolem> type, final Level level, final GolemTier tier) {
		super(type, level);
		this.tier = tier;
		this.inventory = new SimpleContainer(tier.inventorySlots());
		this.entityData.set(DATA_UNLOCKED, !tier.startsLocked());
		this.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
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

	/** Client side: how far its crest has come up out of its head, from 0 (hidden, locked) to 1. */
	public float getCrestRise(final float partialTicks) {
		return this.isUnlocked() ? Mth.clamp((this.tickCount - this.crestRiseTick + partialTicks) / (float)CREST_RISE_TICKS, 0.0F, 1.0F) : 0.0F;
	}

	@Override
	public void onSyncedDataUpdated(final EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		// Unlocked in front of the player, the crest comes up; one that already was unlocked when it came into view has it up.
		if (DATA_UNLOCKED.equals(accessor) && this.level().isClientSide() && this.isUnlocked()) {
			this.crestRiseTick = this.tickCount > 0 ? this.tickCount : -CREST_RISE_TICKS;
		}
	}

	public boolean isUnlocked() {
		return this.entityData.get(DATA_UNLOCKED);
	}

	@Override
	protected TagKey<Item> toolTag() {
		return ItemTags.PICKAXES;
	}

	/**
	 * Buried by gravel or sand coming down in its diggings it digs itself out, which with a pickaxe can take
	 * a while: it does not choke in there meanwhile.
	 */
	@Override
	public boolean isInvulnerableTo(final ServerLevel level, final DamageSource source) {
		return source.is(DamageTypes.IN_WALL) || super.isInvulnerableTo(level, source);
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
		return flower.is(Items.DANDELION) ? this.quarry != null : flower.is(Items.POPPY) && this.strip != null;
	}

	@Override
	protected boolean isBusy() {
		return (this.depositing || this.isWorking()) && !this.waiting;
	}

	/** Neither at work nor on its way home with a load, it potters about by its chest. */
	@Override
	protected boolean mayWander() {
		return this.isIdleHere() && !this.isWorking() && !this.depositing;
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_MINING, false);
		entityData.define(DATA_UNLOCKED, false);
	}

	// ------------------------------------------------------------------------------------ interaction

	public static boolean isPatternFlower(final ItemStack stack) {
		return stack.is(Items.POPPY) || stack.is(Items.DANDELION);
	}

	@Override
	public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		// The server decides, so the client just agrees that something happens.
		if (this.tier.startsLocked() && (held.is(UNLOCK_FLOWER) || isPatternFlower(held))) {
			if (this.level() instanceof ServerLevel level && !this.isUnlocked()) {
				if (held.is(UNLOCK_FLOWER)) {
					held.consume(1, player);
					this.entityData.set(DATA_UNLOCKED, true);
					level.playSound(null, this, SoundEvents.ENCHANTMENT_TABLE_USE, this.getSoundSource(), 1.0F, 1.0F);
				} else {
					Compat.overlay(player, Component.translatable("message.totalgolems.golem_locked"));
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
		return this.isUnlocked() && isPatternFlower(stack);
	}

	@Override
	protected void onActivated(final ServerLevel level, final Player player) {
		this.startMining(level, player);
	}

	@Override
	protected void onDeactivated(final ServerLevel level) {
		this.stopMining(level);
	}

	private void startMining(final ServerLevel level, final Player player) {
		Direction direction = player.getDirection();
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
		this.vein.clear();
		this.fillers.clear();
		this.blockSource = null;
		this.dryPasses = 0;
		this.timesStuck = 0;
		this.lastProgressTick = this.tickCount;
		this.recentCells.clear();
		// A golem that is on its way to unload finishes that first, then comes back here.
		this.returning = this.depositing;
		if (this.getItemBySlot(FLOWER_SLOT).is(Items.DANDELION)) {
			// Back in a pit it already started, the golem carries on with it and keeps using its staircase.
			if (this.quarry == null || !this.quarry.isInsidePit(cell)) {
				ChunkPos chunk = this.findFreeChunk(level, cell);
				if (!chunk.equals(Compat.chunkAt(cell))) {
					Compat.overlay(player, Component.translatable("message.totalgolems.chunk_taken"));
				}

				this.quarry = QuarryPlan.create(Compat.chunkX(chunk), Compat.chunkZ(chunk), cell, this.chestPos, this.tier);
			}

			this.miningDirection = null;
			this.tunnelOrigin = null;
		} else {
			this.quarry = null;
			this.miningDirection = direction;
			this.tunnelOrigin = cell;
			this.strip = StripPlan.create(cell, direction, this.tier);
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
		ChunkPos own = Compat.chunkAt(cell);
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
				candidates.add(new ChunkPos(Compat.chunkX(own) + dx, Compat.chunkZ(own) + dz));
			}
		}

		// Nearest first: the chunk itself, then those sharing an edge with it, then the corners, and so on outwards.
		candidates.sort(Comparator.comparingInt((ChunkPos chunk) -> Math.max(Math.abs(Compat.chunkX(chunk) - Compat.chunkX(own)), Math.abs(Compat.chunkZ(chunk) - Compat.chunkZ(own))))
			.thenComparingInt(chunk -> Math.abs(Compat.chunkX(chunk) - Compat.chunkX(own)) + Math.abs(Compat.chunkZ(chunk) - Compat.chunkZ(own)))
			.thenComparingInt(Compat::chunkX)
			.thenComparingInt(Compat::chunkZ));
		return candidates.stream().filter(chunk -> !taken.contains(chunk)).findFirst().orElse(own);
	}

	private void stopMining(final ServerLevel level) {
		this.clearBreaking(level);
		this.setMining(false);
		this.miningDirection = null;
		this.tunnelOrigin = null;
		this.strip = null;
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
		this.setWaitingForTool(false);
	}

	/** Whether the golem fits into {@code cell} and has something to stand on there, out of any spring. */
	private boolean isStandable(final ServerLevel level, final BlockPos cell) {
		BlockPos floor = cell.below();
		return level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
			&& level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty()
			&& !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
			&& !level.getFluidState(cell).isSource();
	}

	/** The cell of its quarry's chunk at height {@code y} nearest to the golem that it can stand in, other than {@code not}. */
	private @Nullable BlockPos nearestStandable(final ServerLevel level, final int y, final @Nullable BlockPos not) {
		BlockPos corner = new BlockPos(this.quarry.chunkX() << 4, y, this.quarry.chunkZ() << 4);
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(corner, corner.offset(15, 0, 15))) {
			double distance = pos.distToCenterSqr(this.position());
			if (distance < bestDistance && !pos.equals(not) && !this.quarry.isProtected(pos) && this.isStandable(level, pos)) {
				best = pos.immutable();
				bestDistance = distance;
			}
		}

		return best;
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
	 * The shovel it may carry in its other hand. It is not needed, but what a shovel digs faster than the
	 * pickaxe (dirt, gravel, sand) it digs with that, at that shovel's speed and with its enchantments.
	 */
	public ItemStack getShovel() {
		return this.getOffhandItem();
	}

	/** Takes a shovel out of {@code chest} if it has none. */
	private void takeShovel(final Container chest) {
		for (int slot = 0; !this.getShovel().is(ItemTags.SHOVELS) && slot < chest.getContainerSize(); slot++) {
			if (chest.getItem(slot).is(ItemTags.SHOVELS)) {
				this.setItemSlot(EquipmentSlot.OFFHAND, chest.removeItem(slot, 1));
				chest.setChanged();
			}
		}
	}

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

	// ------------------------------------------------------------------------------------ behaviour

	@Override
	protected void serverStep(final ServerLevel level) {
		// Without a flower the golem still takes home whatever it was carrying.
		if (!this.depositing && (!this.overflow.isEmpty() || !this.isWorking() && this.hasLoot() && this.chestPos != null)) {
			this.beginDeposit(level);
		}

		// Nothing can be dug without a pickaxe: home for one.
		if (!this.depositing && this.isWorking() && !this.hasTool()) {
			this.beginDeposit(level);
		}

		if (this.depositing) {
			this.lastProgressTick = this.tickCount;
			this.tickDeposit(level);
		} else if (this.isWorking()) {
			if (!this.checkProgress(level)) {
				this.tickWork(level);
			}
		} else {
			this.setMining(false);
		}
	}

	/**
	 * The safeguard against going through the same motions for ever: at work for {@value #TICKS_WITHOUT_PROGRESS}
	 * ticks without breaking a block or getting to a cell it was not in just before, it stops trying there. A
	 * quarrying golem in its layer passes over the place it was making for; anywhere else it goes back to its
	 * chest and starts again from there. Stuck {@value #STUCK_TIMES_BEFORE_HALT} times running, it gives up
	 * and waits at its chest for a new flower. Returns true when it did any of that this tick.
	 */
	private boolean checkProgress(final ServerLevel level) {
		BlockPos cell = this.feetPos();
		if (!cell.equals(this.recentCells.peekLast())) {
			// Back to one of the cells it was in a moment ago is no headway: that is how going round in
			// circles, or up a step and down again, looks.
			if (!this.recentCells.contains(cell)) {
				this.lastProgressTick = this.tickCount;
			}

			this.recentCells.addLast(cell);
			if (this.recentCells.size() > RECENT_CELLS) {
				this.recentCells.removeFirst();
			}
		}

		if (this.tickCount - this.lastProgressTick < TICKS_WITHOUT_PROGRESS) {
			return false;
		}

		this.lastProgressTick = this.tickCount;
		this.recentCells.clear();
		this.clearBreaking(level);
		this.setMining(false);
		this.blockSource = null;
		BlockPos elsewhere = this.quarry != null && this.miningDirection == null ? this.nearestStandable(level, this.quarry.layerFeetY(), this.anchor) : null;
		if (++this.timesStuck >= STUCK_TIMES_BEFORE_HALT) {
			this.halt(level);
		} else if (this.quarry != null && this.miningDirection == null && !this.quarry.isDescending() && !this.returning) {
			this.quarry.skipTarget();
		} else if (this.returning && elsewhere != null) {
			// The place it was heading back to cannot be got at: somewhere in its layer that can.
			this.anchor = elsewhere;
			this.resetTravel();
		} else {
			this.beginDeposit(level);
		}

		return true;
	}

	/** It broke a block: that is getting something done, whatever else it is doing. */
	private void madeProgress() {
		this.lastProgressTick = this.tickCount;
		this.timesStuck = 0;
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

		// So does one without a pickaxe, with its eyes off until somebody puts one in. A shovel it takes if
		// there is one, but never waits for.
		boolean lacksTool = this.isWorking() && !this.hasTool() && !this.takeTool(chest);
		this.setWaitingForTool(lacksTool);
		if (this.isWorking()) {
			this.takeShovel(chest);
		}

		this.waiting = lacksTool;
		if (this.waiting) {
			return;
		}

		// Every visit fills its reserve of blocks up again, so that it does not run short down there; it only
		// waits for blocks if it has none at all and came for them.
		boolean hasBlocks = !this.isWorking() || this.takeBuildingBlocks(chest);
		if (this.needsBlocks && !hasBlocks) {
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

	/** Unloads everything but its reserve of blocks. Returns whether that is all it has left. */
	private boolean unloadInto(final Container chest) {
		Set<Integer> reserve = this.reserveSlots();
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			ItemStack stack = this.inventory.getItem(slot);
			if (!stack.isEmpty() && !reserve.contains(slot)) {
				this.inventory.setItem(slot, HopperBlockEntity.addItem(null, chest, stack, null));
			}
		}

		this.overflow.replaceAll(stack -> HopperBlockEntity.addItem(null, chest, stack, null));
		this.overflow.removeIf(ItemStack::isEmpty);
		chest.setChanged();
		return !this.hasLoot() && this.overflow.isEmpty();
	}

	/**
	 * The slots of blocks a golem at work keeps, the fullest {@value #RESERVE_SLOTS}, topped up from the
	 * other blocks it carries of the same kind. Without a pattern flower it keeps nothing.
	 */
	private Set<Integer> reserveSlots() {
		if (!isPatternFlower(this.getItemBySlot(FLOWER_SLOT))) {
			return Set.of();
		}

		List<Integer> slots = new ArrayList<>();
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			if (isBuildingBlock(this.inventory.getItem(slot))) {
				slots.add(slot);
			}
		}

		slots.sort(Comparator.comparingInt((Integer slot) -> this.inventory.getItem(slot).getCount()).reversed());
		List<Integer> kept = slots.subList(0, Math.min(RESERVE_SLOTS, slots.size()));
		for (int keep : kept) {
			ItemStack stack = this.inventory.getItem(keep);
			for (int other : slots) {
				ItemStack more = this.inventory.getItem(other);
				if (!kept.contains(other) && ItemStack.isSameItemSameComponents(stack, more)) {
					int moved = Math.min(more.getCount(), stack.getMaxStackSize() - stack.getCount());
					stack.grow(moved);
					more.shrink(moved);
				}
			}
		}

		return new HashSet<>(kept);
	}

	/** Whether it carries anything besides its reserve of blocks. */
	private boolean hasLoot() {
		Set<Integer> reserve = this.reserveSlots();
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			if (!this.inventory.getItem(slot).isEmpty() && !reserve.contains(slot)) {
				return true;
			}
		}

		return false;
	}

	/** Fills its reserve of blocks up from the chest. Returns whether it has any blocks now. */
	private boolean takeBuildingBlocks(final Container chest) {
		int carried = 0;
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			ItemStack stack = this.inventory.getItem(slot);
			carried += isBuildingBlock(stack) ? stack.getCount() : 0;
		}

		int wanted = RESERVE_SLOTS * Item.DEFAULT_MAX_STACK_SIZE - carried;
		for (int slot = 0; slot < chest.getContainerSize() && wanted > 0; slot++) {
			ItemStack stack = chest.getItem(slot);
			if (isBuildingBlock(stack)) {
				ItemStack taken = chest.removeItem(slot, Math.min(wanted, stack.getCount()));
				wanted -= taken.getCount();
				this.inventory.addItem(taken);
			}
		}

		chest.setChanged();
		return this.inventory.hasAnyMatching(MiningGolem::isBuildingBlock);
	}

	/**
	 * Plain full blocks the golem may lay as floor or put into water: nothing that falls, holds data or is
	 * worth keeping, and only what a pickaxe digs, and not too slowly, so that it can always take them out again.
	 */
	public static boolean isBuildingBlock(final ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem blockItem) || stack.is(ConventionalItemTags.ORES) || stack.is(ConventionalItemTags.STORAGE_BLOCKS)) {
			return false;
		}

		BlockState state = blockItem.getBlock().defaultBlockState();
		return !(state.getBlock() instanceof FallingBlock)
			&& !state.hasBlockEntity()
			&& blockItem.getBlock().defaultDestroyTime() <= BUILDING_BLOCK_MAX_HARDNESS
			&& state.is(BlockTags.MINEABLE_WITH_PICKAXE)
			&& state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	private void tickWork(final ServerLevel level) {
		if (this.returning && this.anchor != null && this.cellAt(this.anchor.getY()).equals(this.anchor) && this.digDownTo(level, this.anchor.getY(), this::mayDigUnderfoot)) {
			// Right over the spot it is heading back to, with something in the way: gravel that fell in on it, say.
			this.setMining(true);
			return;
		}

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
			this.tickStrip(level);
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

	/** Poppy: strip mining at the height it was started at; see {@link StripPlan}. */
	private void tickStrip(final ServerLevel level) {
		StripPlan plan = this.strip;
		int y = plan.y();
		if (!this.isWorkingAt(y)) {
			if (!this.digDownTo(level, y, plan::isInDiggings)) {
				this.returning = true;
			}

			return;
		}

		StripPlan.Leg leg = plan.currentLeg();
		if (leg == null) {
			// The whole mine is dug: home, to wait for a new flower.
			this.halt(level);
			return;
		}

		// Gravel or sand that came down on it: dug out before anything else.
		BlockPos own = this.cellAt(y);
		for (int up = 0; up < this.tier.tunnelHeight(); up++) {
			BlockPos pos = own.above(up);
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof FallingBlock && plan.isInDiggings(pos)) {
				this.breakBlocks(level, new LinkedHashSet<>(List.of(pos)));
				return;
			}
		}

		BlockPos cell = plan.snap(own, leg);
		double sideways = leg.direction().getAxis() == Direction.Axis.X ? this.getZ() - (cell.getZ() + 0.5) : this.getX() - (cell.getX() + 0.5);
		if (!leg.main() && Math.abs(sideways) > 1.5) {
			// A branch it never got to, because its way along the main tunnel was blocked short of it.
			plan.skipBranch();
			return;
		}

		if (!this.alignWithTunnel(cell, leg.direction())) {
			return;
		}

		this.anchor = cell;
		Direction heading = plan.heading(cell, leg);
		if (heading == null) {
			plan.nextLeg();
			return;
		}

		// Ore in sight comes first, then back to the route.
		if (this.mineVein(level, plan)) {
			return;
		}

		BlockPos next = cell.relative(heading);
		if (!level.isLoaded(next)) {
			this.setMining(false);
			return;
		}

		// Something it cannot dig right in its way ends this run; a run along the main tunnel takes its branch with it.
		if (!plan.contains(next) || this.isBlockedAt(level, next)) {
			this.clearBreaking(level);
			this.setMining(false);
			if (leg.main() && plan.legIndex() > 1) {
				plan.skipBranch();
			} else {
				plan.nextLeg();
			}

			return;
		}

		this.advance(level, next, plan.slice(next, heading), false);
	}

	/**
	 * Standing a block above the height it works at, on something that has turned up in its diggings
	 * (gravel that fell in, a block put there): it digs that out from under its feet and drops back down,
	 * instead of trying to walk to where it already is. Returns true while it is doing so.
	 */
	private boolean digDownTo(final ServerLevel level, final int y, final java.util.function.Predicate<BlockPos> mayDig) {
		if (this.getY() < y + 0.75 || this.getY() > y + 1.25) {
			return false;
		}

		BlockPos below = this.cellAt(y);
		BlockState state = level.getBlockState(below);
		if (state.getCollisionShape(level, below).isEmpty() || state.getDestroySpeed(level, below) < 0.0F || state.hasBlockEntity() || !mayDig.test(below)) {
			return false;
		}

		this.breakBlocks(level, new LinkedHashSet<>(List.of(below)));
		return true;
	}

	/** Whether a block under the golem's feet is one of its own diggings, which it may clear. */
	private boolean mayDigUnderfoot(final BlockPos pos) {
		if (this.strip != null) {
			return this.strip.isInDiggings(pos);
		}

		return this.quarry != null && this.quarry.contains(pos) && !this.quarry.isProtected(pos);
	}

	/**
	 * A strip mine is a long way round, and the way home can be cut off by lava or a cave. A golem stuck
	 * anywhere in its mine may jump back to its chest or its tunnel; others only over a short distance.
	 */
	@Override
	protected boolean mayTeleportTo(final BlockPos target, final double distance) {
		return distance <= (this.strip != null ? STRIP_TELEPORT_DISTANCE : MAX_TELEPORT_DISTANCE);
	}

	/** Whether the golem cannot get into {@code cell}: a block it cannot break, or a chest, stands where it would. */
	private boolean isBlockedAt(final ServerLevel level, final BlockPos cell) {
		for (int up = 0; up < this.tier.tunnelHeight(); up++) {
			BlockPos pos = cell.above(up);
			BlockState state = level.getBlockState(pos);
			if (!state.getCollisionShape(level, pos).isEmpty() && (state.getDestroySpeed(level, pos) < 0.0F || state.hasBlockEntity())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Never anywhere below the height it works at, nor into water or lava, nor where there is nothing solid
	 * to stand on: wherever it landed it would only sink or drop further down, out of its diggings.
	 */
	@Override
	protected boolean mayLandAt(final ServerLevel level, final BlockPos spot) {
		BlockPos floor = spot.below();
		return spot.getY() >= this.lowestLanding()
			&& level.getFluidState(spot).isEmpty()
			&& level.getFluidState(floor).isEmpty()
			&& !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty();
	}

	/**
	 * The height it works at: its tunnels, the layer of its quarry or, while it goes down the staircase to
	 * one, the step it is about to step onto.
	 */
	private int lowestLanding() {
		if (this.strip != null) {
			return this.strip.y();
		}

		if (this.quarry != null && this.getItemBySlot(FLOWER_SLOT).is(Items.DANDELION)) {
			return this.quarry.isDescending() ? this.quarry.stairStand(this.quarry.stairsBuilt() + 1).getY() : this.quarry.layerFeetY();
		}

		return Integer.MIN_VALUE;
	}

	/**
	 * Mines out every vein of ore it sees from where it stands, to the last block, nearest first: ore it can
	 * see within {@value #VEIN_REACH} blocks, and all the ore connected to that, however far it goes on, as
	 * long as it is no further outside the area of the mine than {@link StripPlan#ORE_MARGIN}: ore at the edge
	 * of the area is not left half dug. Returns true while there is ore to mine.
	 */
	private boolean mineVein(final ServerLevel level, final StripPlan plan) {
		if (this.tickCount < this.nextVeinScan) {
			return false;
		}

		Vec3 eye = this.getEyePosition();
		BlockPos centre = BlockPos.containing(eye);
		int reach = Mth.ceil(VEIN_REACH);
		// Ore dug out of the floor of a tunnel leaves a hole to fall into: it fills those back in first.
		Iterator<BlockPos> holes = this.veinFloor.iterator();
		while (holes.hasNext()) {
			BlockPos pos = holes.next();
			BlockState state = level.getBlockState(pos);
			if (!state.getCollisionShape(level, pos).isEmpty() && !state.is(ConventionalBlockTags.ORES)) {
				holes.remove();
			} else if (state.getCollisionShape(level, pos).isEmpty()
				&& this.inventory.hasAnyMatching(MiningGolem::isBuildingBlock)
				&& level.isUnobstructed(Blocks.STONE.defaultBlockState(), pos, CollisionContext.empty())) {
				holes.remove();
				this.placeBuildingBlock(level, pos);
				return true;
			}
		}

		// Not what it stands on: it would fall in. That one it gets from the next cell along.
		BlockPos underfoot = this.cellAt(plan.y()).below();
		this.vein.removeIf(pos -> !isOre(level, pos));
		if (this.vein.isEmpty()) {
			for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-reach, -reach, -reach), centre.offset(reach, reach, reach))) {
				if (pos.distToCenterSqr(eye) <= VEIN_REACH * VEIN_REACH && plan.reachesForOre(pos) && !pos.equals(underfoot) && isOre(level, pos) && this.canSee(level, eye, pos)) {
					this.followVein(level, plan, pos.immutable());
				}
			}
		}

		List<BlockPos> ores = new ArrayList<>(this.vein);
		ores.remove(underfoot);
		if (ores.isEmpty()) {
			this.nextVeinScan = this.tickCount + VEIN_SCAN_INTERVAL;
			return false;
		}

		// What it has already started on first, or it would keep switching between two ores the same distance away.
		ores.sort(Comparator.comparing((BlockPos pos) -> !this.breaking.containsKey(pos)).thenComparingDouble(pos -> pos.distToCenterSqr(eye)));
		List<BlockPos> targets = ores.subList(0, Math.min(ores.size(), this.tier.blocksAtOnce()));
		// Taking the ore out opens a way in for whatever is behind it, as much as digging the tunnel does.
		if (!this.sealFluids(level, targets)) {
			this.breakBlocks(level, new LinkedHashSet<>(targets));
		}

		return true;
	}

	/**
	 * Adds the whole vein that {@code start} belongs to: every block of ore touching it, edges and corners
	 * included, and every block touching those, within reach of the area of the mine.
	 */
	private void followVein(final ServerLevel level, final StripPlan plan, final BlockPos start) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(start));
		this.vein.add(start);
		while (!queue.isEmpty() && this.vein.size() < VEIN_LIMIT) {
			BlockPos pos = queue.poll();
			if (pos.getY() == plan.y() - 1 && plan.isInDiggings(pos.above())) {
				this.veinFloor.add(pos);
			}

			for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
				if (plan.reachesForOre(near) && !this.vein.contains(near) && isOre(level, near)) {
					BlockPos found = near.immutable();
					this.vein.add(found);
					queue.add(found);
				}
			}
		}
	}

	private static boolean isOre(final Level level, final BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(ConventionalBlockTags.ORES) && state.getDestroySpeed(level, pos) >= 0.0F;
	}

	/** Whether nothing but {@code pos} itself stands between the golem's eyes and the middle of that block. */
	private boolean canSee(final ServerLevel level, final Vec3 eye, final BlockPos pos) {
		BlockHitResult hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
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

		// Once it has gone after a block to wall off water with, it gets it before anything else.
		if (this.blockSource != null && this.inventory.hasAnyMatching(MiningGolem::isBuildingBlock)) {
			this.blockSource = null;
		} else if (this.blockSource != null && this.digForBlocks(level)) {
			return;
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
		if (approaching && this.isWorkingAt(y)) {
			this.anchor = cell;
			this.approach(level, plan, cell);
			return;
		}

		if (!this.isWorkingAt(y) && plan.contains(cell) && this.digDownTo(level, y, pos -> !plan.isProtected(pos))) {
			return;
		}

		if (!this.isWorkingAt(y) || !plan.contains(cell)) {
			if (this.anchor == null || !approaching && (this.anchor.getY() != y || !plan.contains(this.anchor))) {
				this.anchor = plan.stairStand(plan.stairsBuilt());
			}

			// Somewhere it can stand: the top of the stairs may still be inside a hill nobody has dug yet.
			if (!approaching && !this.isStandable(level, this.anchor)) {
				BlockPos standable = this.nearestStandable(level, y, null);
				if (standable != null) {
					this.anchor = standable;
				}
			}

			this.returning = true;
			return;
		}

		this.anchor = cell;
		BlockPos next = plan.nextStep(level, cell);
		if (next == null && this.dryPasses < 2 && !plan.fluidsToSeal(level).isEmpty()) {
			// Water still showing in the layer: over it again to wall that off before going any deeper.
			this.dryPasses++;
			plan.rescan();
		} else if (next == null) {
			this.dryPasses = 0;
			this.fillers.clear();
			this.blockSource = null;
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
			if (this.mayLandAt(level, next)) {
				this.teleportTo(x, next.getY(), z);
			}
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
	 * Lays one of the blocks it carries at {@code pos}. With none left it returns false: a quarrying golem
	 * digs one out of its layer, and only with none to be had there does a golem go to unload and bring
	 * some from the chest.
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

		if (this.quarry != null && this.miningDirection == null && this.digForBlocks(level)) {
			return false;
		}

		this.needsBlocks = true;
		this.beginDeposit(level);
		return false;
	}

	/**
	 * Before it breaks anything it walls off the water and lava that would come in through the gap, the way
	 * a player would. In a block it is about to dig, a source is filled in with a block, to be dug out again
	 * like any other. Next to what it digs, anything running in from beyond its diggings (roof, walls, the
	 * floor of the cell it is going into) is walled off for good where it comes in; inside a quarry's layer,
	 * so are the sources. Without its sources the rest of the water drains away by itself. Water and lava
	 * already inside its tunnels are left alone. It goes from the top down, with the blocks it carries.
	 * <p>
	 * A quarrying golem only puts blocks within {@value #SEAL_REACH} blocks of itself, and short of blocks it
	 * digs one out of its layer, where there is no water left, before it goes to its chest for more. A
	 * strip-mining golem short of blocks goes to its chest for them straight away, and waits there if there
	 * are none, rather than let anything in. Returns true when it did anything about it this tick.
	 */
	private boolean sealFluids(final ServerLevel level, final List<BlockPos> slice) {
		boolean quarrying = this.quarry != null && this.miningDirection == null;
		Vec3 from = this.position().add(0.0, 1.0, 0.0);
		Set<BlockPos> inSlice = new HashSet<>(slice);
		Set<BlockPos> found = new HashSet<>();
		if (quarrying) {
			this.layerSeals.stream().filter(pos -> isFluid(level, pos, false)).forEach(found::add);
		}

		for (BlockPos pos : slice) {
			if (isFluid(level, pos, true)) {
				found.add(pos);
			}

			for (Direction direction : Direction.values()) {
				BlockPos beside = pos.relative(direction);
				if (inSlice.contains(beside)) {
					continue;
				}

				// Anything from outside; inside a quarry's layer, the sources too.
				boolean outside = this.isBeyondDiggings(beside);
				if (outside ? isFluid(level, beside, false) : quarrying && isFluid(level, beside, true)) {
					found.add(beside);
				}
			}
		}

		// Never on top of itself, or of anyone else.
		List<BlockPos> seal = found.stream()
			.filter(pos -> !quarrying || pos.distToCenterSqr(from) <= SEAL_REACH * SEAL_REACH)
			.filter(pos -> level.isUnobstructed(Blocks.STONE.defaultBlockState(), pos, CollisionContext.empty()))
			.sorted(Comparator.comparingInt((BlockPos pos) -> -pos.getY()).thenComparingDouble(pos -> pos.distToCenterSqr(from)))
			.toList();
		if (seal.isEmpty()) {
			return false;
		}

		if (!this.inventory.hasAnyMatching(MiningGolem::isBuildingBlock)) {
			// A quarrying golem digs a block out to do it with; failing that, or in a strip mine, it goes for some.
			this.placeBuildingBlock(level, seal.getFirst());
			return true;
		}

		int placed = 0;
		for (BlockPos pos : seal) {
			if (!this.inventory.hasAnyMatching(MiningGolem::isBuildingBlock)) {
				break;
			}

			this.placeBuildingBlock(level, pos);
			if (quarrying && this.quarry.contains(pos)) {
				// The block filling the source has to be dug out again, wherever in the layer it is.
				this.fillers.add(pos);
				this.quarry.rescan();
			}

			if (++placed >= SEALS_AT_ONCE) {
				break;
			}
		}

		return true;
	}

	/**
	 * A quarrying golem out of blocks digs one out of its layer, one it put into the water first, then the
	 * nearest: nothing with a source of water or lava next to it, which would only let that back in. What
	 * is left running about drains away once the sources are gone. It walks over to a block out of reach,
	 * and keeps to the one it picked until it has dug it. Returns whether there was one to be had.
	 */
	private boolean digForBlocks(final ServerLevel level) {
		if (this.blockSource == null || !this.isBlockSource(level, this.blockSource)) {
			this.blockSource = this.findBlockSource(level);
		}

		if (this.blockSource == null) {
			return false;
		}

		BlockPos pos = this.blockSource;
		if (!this.isWithinReach(pos)) {
			BlockPos spot = this.spotBeside(level, pos);
			if (spot == null) {
				this.blockSource = null;
				return false;
			}

			this.clearBreaking(level);
			this.setMining(false);
			this.anchor = spot;
			this.returning = true;
		} else {
			this.getNavigation().stop();
			this.breakBlocks(level, new LinkedHashSet<>(List.of(pos)));
		}

		return true;
	}

	private @Nullable BlockPos findBlockSource(final ServerLevel level) {
		QuarryPlan plan = this.quarry;
		int bottom = plan.layerFeetY();
		Vec3 from = this.position().add(0.0, 1.0, 0.0);
		List<BlockPos> candidates = new ArrayList<>();
		BlockPos corner = new BlockPos(plan.chunkX() << 4, bottom, plan.chunkZ() << 4);
		for (BlockPos pos : BlockPos.betweenClosed(corner, corner.offset(15, this.tier.tunnelHeight() - 1, 15))) {
			if (this.isBlockSource(level, pos)) {
				candidates.add(pos.immutable());
			}
		}

		candidates.sort(Comparator.comparing((BlockPos pos) -> !this.fillers.contains(pos)).thenComparingDouble(pos -> pos.distToCenterSqr(from)));
		ItemStack tool = this.getTool();
		for (BlockPos pos : candidates) {
			// Only what comes out as a block it can use (stone, but not ore), and that it can get at.
			if ((this.isWithinReach(pos) || this.spotBeside(level, pos) != null)
				&& Block.getDrops(level.getBlockState(pos), level, pos, null, this, tool).stream().anyMatch(MiningGolem::isBuildingBlock)) {
				return pos;
			}
		}

		return null;
	}

	private boolean isWithinReach(final BlockPos pos) {
		return pos.distToCenterSqr(this.position().add(0.0, 1.0, 0.0)) <= SEAL_REACH * SEAL_REACH;
	}

	/**
	 * A cell beside the column of {@code pos} on the floor of the layer that the golem can stand in, to dig it
	 * from, nearest to the golem; null if there is none. It never heads for the block itself: a path cannot
	 * end in stone, and it would go back and forth for ever trying to get there.
	 */
	private @Nullable BlockPos spotBeside(final ServerLevel level, final BlockPos pos) {
		BlockPos column = new BlockPos(pos.getX(), this.quarry.layerFeetY(), pos.getZ());
		return Direction.Plane.HORIZONTAL.stream()
			.map(column::relative)
			.filter(cell -> this.quarry.contains(cell) && this.isStandable(level, cell))
			.min(Comparator.comparingDouble(cell -> cell.distToCenterSqr(this.position())))
			.orElse(null);
	}

	private boolean isBlockSource(final ServerLevel level, final BlockPos pos) {
		QuarryPlan plan = this.quarry;
		int bottom = plan.layerFeetY();
		if (!plan.contains(pos) || plan.isProtected(pos) || pos.getY() < bottom || pos.getY() >= bottom + this.tier.tunnelHeight() || pos.equals(this.feetPos().below())) {
			return false;
		}

		BlockState state = level.getBlockState(pos);
		if (state.getCollisionShape(level, pos).isEmpty() || state.getDestroySpeed(level, pos) < 0.0F || state.hasBlockEntity() || !state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return false;
		}

		for (Direction direction : Direction.values()) {
			if (isFluid(level, pos.relative(direction), true)) {
				return false;
			}
		}

		return true;
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

	/** Whether {@code pos} is outside everything this golem will ever dig: beyond its chunk, or beside its tunnels. */
	private boolean isBeyondDiggings(final BlockPos pos) {
		if (this.strip != null) {
			return !this.strip.isInDiggings(pos);
		}

		return this.quarry == null || !this.quarry.contains(pos);
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

		this.getLookControl().setLookAt(Vec3.atCenterOf(targets.getFirst()));
		if (this.tickCount < this.restUntilTick) {
			// A breather after the last block: the pickaxe down, and the cracks left as they are.
			this.setMining(false);
			return;
		}

		this.setMining(true);
		this.ticksDigging++;
		boolean broke = false;
		boolean playSound = this.tickCount % 4 == 0;
		for (BlockPos pos : targets) {
			BlockState state = level.getBlockState(pos);
			BreakState progress = this.breaking.get(pos);
			if (progress == null) {
				progress = new BreakState(this.freeCrackId());
				this.breaking.put(pos, progress);
			}

			ItemStack pickaxe = this.getTool();
			ItemStack shovel = this.getShovel();
			boolean shovelling = shovel.is(ItemTags.SHOVELS) && this.diggingSpeed(shovel, state) > this.diggingSpeed(pickaxe, state);
			ItemStack tool = shovelling ? shovel : pickaxe;
			boolean dropsLoot = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
			float hardness = state.getDestroySpeed(level, pos);
			// Same formula a player digs with: tool speed over hardness, far slower with the wrong tool. Only twice as
			// fast, to make up for the breather after each block.
			progress.progress += hardness <= 0.0F ? 1.0F : DIGGING_SPEEDUP * this.diggingSpeed(tool, state) / hardness / (dropsLoot ? 30.0F : 100.0F);
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
			this.madeProgress();
			broke = true;
			for (ItemStack drop : drops) {
				ItemStack leftover = this.inventory.addItem(drop);
				if (!leftover.isEmpty()) {
					this.overflow.add(leftover);
				}
			}

			// The tool wears as it does in a player's hand: not at all on what breaks at a touch.
			Tool wear = tool.get(DataComponents.TOOL);
			int damage = wear != null ? wear.damagePerBlock() : 1;
			if (shovelling) {
				if (hardness != 0.0F) {
					shovel.hurtAndBreak(damage, this, EquipmentSlot.OFFHAND);
				}
			} else {
				this.wearTool(hardness == 0.0F || this.isSpared(state) ? 0 : damage);
			}

			if (!this.hasTool()) {
				// It broke. Whatever else was being dug is left as it is.
				this.clearBreaking(level);
				this.setMining(false);
				return;
			}
		}

		if (broke) {
			// As long a breather as the digging took, which with the doubled speed digs as much as it ever did.
			this.restUntilTick = this.tickCount + Math.min(this.ticksDigging, MAX_REST_TICKS);
			this.ticksDigging = 0;
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

	/**
	 * Whether the golem is at work at height {@code y}: standing there, or afloat in water over it, no higher
	 * than its tunnels are. Golems float, so in deep water it would otherwise never be at its working height,
	 * and would keep heading back to where it already is.
	 */
	private boolean isWorkingAt(final int y) {
		return this.isOnLevel(y) || this.isInWater() && this.getY() > y && this.getY() < y + this.tier.tunnelHeight();
	}

	private boolean isAt(final BlockPos cell) {
		return this.isOnLevel(cell.getY()) && this.cellAt(cell.getY()).equals(cell);
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
		if (this.strip != null) {
			output.putInt("strip_leg", this.strip.legIndex());
		}
		output.storeNullable("anchor", BlockPos.CODEC, this.anchor);
		if (this.quarry != null) {
			output.putIntArray("quarry", this.quarry.save());
		}

		output.putBoolean("returning", this.returning);
		output.putBoolean("depositing", this.depositing);
		output.putBoolean("needs_blocks", this.needsBlocks);
		output.putBoolean("halted", this.halted);
		output.putBoolean("unlocked", this.isUnlocked());
		this.inventory.storeAsItemList(output.list("inventory", ItemStack.CODEC));
		ValueOutput.TypedOutputList<ItemStack> overflowList = output.list("overflow", ItemStack.CODEC);
		this.overflow.forEach(overflowList::add);
	}

	@Override
	public void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.miningDirection = input.read("mining_direction", Direction.CODEC).filter(direction -> direction.getAxis().isHorizontal()).orElse(null);
		this.tunnelOrigin = input.read("tunnel_origin", BlockPos.CODEC).orElse(null);
		this.strip = this.miningDirection != null && this.tunnelOrigin != null
			? StripPlan.load(this.tunnelOrigin, this.miningDirection, this.tier, input.getIntOr("strip_leg", 0))
			: null;
		this.anchor = input.read("anchor", BlockPos.CODEC).orElse(null);
		this.quarry = input.getIntArray("quarry").map(data -> QuarryPlan.load(data, this.tier, this.chestPos)).orElse(null);
		this.returning = input.getBooleanOr("returning", false);
		this.depositing = input.getBooleanOr("depositing", false);
		this.needsBlocks = input.getBooleanOr("needs_blocks", false);
		this.halted = input.getBooleanOr("halted", false);
		// One already wearing a pattern flower was at work before the lock existed, and stays as it was.
		this.entityData.set(DATA_UNLOCKED, !this.tier.startsLocked() || input.getBooleanOr("unlocked", false) || isPatternFlower(this.getItemBySlot(FLOWER_SLOT)));
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
		this.spawnAtLocation(level, this.getShovel());
		this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
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
