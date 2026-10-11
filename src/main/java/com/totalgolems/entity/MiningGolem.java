package com.totalgolems.entity;

import com.totalgolems.GolemTier;
import com.totalgolems.block.MetalChestBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockItemTags;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A golem that digs a straight tunnel while it wears a flower, carrying what it mines back to its chest.
 * <p>
 * The tunnel is a fixed line on the horizontal plane (set when the flower is given) but follows the
 * golem's own height, so it keeps going if it drops into a cave.
 */
public class MiningGolem extends AbstractGolem {
	/** Same slot the copper golem wears its poppy in, so the vanilla antenna layer can draw it. */
	public static final EquipmentSlot FLOWER_SLOT = EquipmentSlot.SADDLE;
	private static final EntityDataAccessor<Boolean> DATA_MINING = SynchedEntityData.defineId(MiningGolem.class, EntityDataSerializers.BOOLEAN);
	private static final double CHEST_REACH = 2.3;
	private static final int CHEST_SEARCH_RADIUS = 8;
	private static final int TRANSFER_INTERVAL = 10;
	private static final int REPATH_INTERVAL = 10;
	/** Seconds without getting closer to where it is walking before the golem gives up and teleports there. */
	private static final int STALLED_SECONDS_BEFORE_TELEPORT = 10;
	private static final int ALIGN_TICKS_BEFORE_TELEPORT = 100;

	private final GolemTier tier;
	private final SimpleContainer inventory;
	/** Drops that did not fit in the inventory. Anything here means the golem is full and must unload. */
	private final List<ItemStack> overflow = new ArrayList<>();
	private @Nullable BlockPos chestPos;
	private @Nullable Direction miningDirection;
	private @Nullable BlockPos tunnelOrigin;
	/** Where to walk back to after unloading. */
	private @Nullable BlockPos resumePos;
	private boolean depositing;
	private @Nullable BlockPos breakingPos;
	private float breakProgress;
	private int breakStage = -1;
	private int travelTicks;
	private int stalledSeconds;
	private double lastTravelDistance = Double.MAX_VALUE;
	private int alignTicks;

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
			.add(Attributes.MOVEMENT_SPEED, 0.25)
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

	public boolean hasFlower() {
		return !this.getItemBySlot(FLOWER_SLOT).isEmpty();
	}

	public void setChestPos(final @Nullable BlockPos chestPos) {
		this.chestPos = chestPos;
	}

	private boolean isWorking() {
		return this.miningDirection != null && this.tunnelOrigin != null && this.hasFlower();
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_MINING, false);
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

	private boolean isBusy() {
		return this.depositing || this.hasFlower();
	}

	@Override
	public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.is(BlockItemTags.SMALL_FLOWERS.item())) {
			if (this.level() instanceof ServerLevel level) {
				if (!this.hasFlower()) {
					this.setItemSlot(FLOWER_SLOT, held.copyWithCount(1));
					held.consume(1, player);
				}

				// Giving another flower to a golem that already has one just points it somewhere else.
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
		this.miningDirection = direction;
		this.tunnelOrigin = this.feetPos();
		// A golem that is on its way to unload finishes that first, then comes back here.
		this.resumePos = this.depositing ? this.tunnelOrigin : null;
		this.resetTravel();
		if (this.findChest(level) == null) {
			this.chestPos = this.findNearbyChest(level);
		}
	}

	private void stopMining(final ServerLevel level) {
		this.clearBreaking(level);
		this.setMining(false);
		this.miningDirection = null;
		this.tunnelOrigin = null;
		this.resumePos = null;
		this.resetTravel();
		this.getNavigation().stop();
	}

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
			this.tickMining(level);
		} else {
			this.setMining(false);
		}
	}

	private void beginDeposit(final ServerLevel level) {
		this.clearBreaking(level);
		this.setMining(false);
		this.depositing = true;
		this.resumePos = this.isWorking() ? this.feetPos() : null;
		this.resetTravel();
	}

	private void tickDeposit(final ServerLevel level) {
		if (this.chestPos == null) {
			return;
		}

		if (!this.travelTo(level, this.chestPos, Vec3.atCenterOf(this.chestPos), CHEST_REACH)) {
			return;
		}

		this.getLookControl().setLookAt(Vec3.atCenterOf(this.chestPos));
		if (this.tickCount % TRANSFER_INTERVAL != 0) {
			return;
		}

		// A missing or full chest leaves the golem waiting here, which is what stops the digging.
		Container chest = this.findChest(level);
		if (chest != null && this.unloadInto(chest)) {
			this.depositing = false;
			this.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 0.8F);
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

	private @Nullable Container findChest(final ServerLevel level) {
		return this.chestPos != null && level.isLoaded(this.chestPos) ? HopperBlockEntity.getContainerAt(level, this.chestPos) : null;
	}

	/** Golems that were not built on a block (summoned, for instance) adopt the closest iron or diamond chest. */
	private @Nullable BlockPos findNearbyChest(final ServerLevel level) {
		for (BlockPos pos : BlockPos.withinManhattan(this.blockPosition(), CHEST_SEARCH_RADIUS)) {
			if (level.getBlockState(pos).getBlock() instanceof MetalChestBlock) {
				return pos.immutable();
			}
		}

		return null;
	}

	private void tickMining(final ServerLevel level) {
		Direction direction = this.miningDirection;
		if (this.resumePos != null) {
			if (!this.travelTo(level, this.resumePos, Vec3.atBottomCenterOf(this.resumePos), 1.0)) {
				return;
			}

			this.resumePos = null;
		}

		this.getNavigation().stop();
		BlockPos feet = this.snapToTunnel(this.feetPos(), direction);
		if (!this.alignWithTunnel(feet, direction)) {
			return;
		}

		BlockPos front = feet.relative(direction);
		if (!level.isLoaded(front)) {
			this.setMining(false);
			return;
		}

		Direction side = direction.getClockWise();
		for (int height = this.tier.tunnelHeight() - 1; height >= 0; height--) {
			for (int offset = -this.tier.tunnelRadius(); offset <= this.tier.tunnelRadius(); offset++) {
				BlockPos pos = front.above(height).relative(side, offset);
				BlockState state = level.getBlockState(pos);
				if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
					continue;
				}

				// Bedrock and anything holding data (chests, spawners...) is left alone; the golem waits for the way to be cleared.
				if (state.getDestroySpeed(level, pos) < 0.0F || state.hasBlockEntity()) {
					this.clearBreaking(level);
					this.setMining(false);
					return;
				}

				this.breakBlock(level, pos, state);
				return;
			}
		}

		this.clearBreaking(level);
		this.setMining(false);
		this.getMoveControl().setWantedPosition(front.getX() + 0.5, this.getY(), front.getZ() + 0.5, 1.0);
	}

	private void breakBlock(final ServerLevel level, final BlockPos pos, final BlockState state) {
		if (!pos.equals(this.breakingPos)) {
			this.clearBreaking(level);
			this.breakingPos = pos;
		}

		this.setMining(true);
		this.getLookControl().setLookAt(Vec3.atCenterOf(pos));
		ItemStack tool = this.bestTool(state);
		boolean dropsLoot = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
		float hardness = state.getDestroySpeed(level, pos);
		// Same formula a player digs with: tool speed over hardness, far slower with the wrong tool.
		this.breakProgress += hardness <= 0.0F ? 1.0F : tool.getDestroySpeed(state) * this.tier.hasteMultiplier() / hardness / (dropsLoot ? 30.0F : 100.0F);
		if (this.tickCount % 4 == 0) {
			level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.NEUTRAL, 0.5F, 0.75F);
		}

		if (this.breakProgress < 1.0F) {
			int stage = (int)(this.breakProgress * 10.0F);
			if (stage != this.breakStage) {
				this.breakStage = stage;
				level.destroyBlockProgress(this.getId(), pos, stage);
			}

			return;
		}

		List<ItemStack> drops = dropsLoot ? Block.getDrops(state, level, pos, null, this, tool) : List.of();
		this.clearBreaking(level);
		level.destroyBlock(pos, false, this);
		for (ItemStack drop : drops) {
			ItemStack leftover = this.inventory.addItem(drop);
			if (!leftover.isEmpty()) {
				this.overflow.add(leftover);
			}
		}
	}

	private ItemStack bestTool(final BlockState state) {
		ItemStack best = this.tier.tools().getFirst();
		for (ItemStack tool : this.tier.tools()) {
			if (tool.getDestroySpeed(state) > best.getDestroySpeed(state)) {
				best = tool;
			}
		}

		return best;
	}

	private void clearBreaking(final ServerLevel level) {
		if (this.breakingPos != null) {
			level.destroyBlockProgress(this.getId(), this.breakingPos, -1);
		}

		this.breakingPos = null;
		this.breakProgress = 0.0F;
		this.breakStage = -1;
	}

	/** The block the golem stands in. Sampled slightly above its feet so sunken blocks such as soul sand do not count as one lower. */
	private BlockPos feetPos() {
		return BlockPos.containing(this.getX(), this.getY() + 0.2, this.getZ());
	}

	private BlockPos snapToTunnel(final BlockPos pos, final Direction direction) {
		return direction.getAxis() == Direction.Axis.X
			? new BlockPos(pos.getX(), pos.getY(), this.tunnelOrigin.getZ())
			: new BlockPos(this.tunnelOrigin.getX(), pos.getY(), pos.getZ());
	}

	/** Keeps the golem on the tunnel line if something pushed it sideways. Returns whether it is on it. */
	private boolean alignWithTunnel(final BlockPos feet, final Direction direction) {
		double centerX = feet.getX() + 0.5;
		double centerZ = feet.getZ() + 0.5;
		double sideways = direction.getAxis() == Direction.Axis.X ? this.getZ() - centerZ : this.getX() - centerX;
		if (Math.abs(sideways) <= 0.3) {
			this.alignTicks = 0;
			return true;
		}

		this.setMining(false);
		if (++this.alignTicks > ALIGN_TICKS_BEFORE_TELEPORT) {
			this.alignTicks = 0;
			this.teleportTo(centerX, this.getY(), centerZ);
		} else {
			this.getMoveControl().setWantedPosition(centerX, this.getY(), centerZ, 1.0);
		}

		return false;
	}

	/** Walks towards {@code target}; returns true once within {@code reach} of it. */
	private boolean travelTo(final ServerLevel level, final BlockPos targetPos, final Vec3 target, final double reach) {
		double distance = this.position().distanceTo(target);
		if (distance <= reach) {
			this.getNavigation().stop();
			this.resetTravel();
			return true;
		}

		if (this.travelTicks % REPATH_INTERVAL == 0) {
			this.getNavigation().moveTo(target.x, target.y, target.z, 1, 1.0);
		}

		if (++this.travelTicks % 20 == 0) {
			this.stalledSeconds = this.lastTravelDistance - distance < 0.5 ? this.stalledSeconds + 1 : 0;
			this.lastTravelDistance = distance;
			if (this.stalledSeconds >= STALLED_SECONDS_BEFORE_TELEPORT) {
				this.resetTravel();
				this.teleportNextTo(level, targetPos);
			}
		}

		return false;
	}

	private void resetTravel() {
		this.travelTicks = 0;
		this.stalledSeconds = 0;
		this.lastTravelDistance = Double.MAX_VALUE;
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

	@Override
	public void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("chest_pos", BlockPos.CODEC, this.chestPos);
		output.storeNullable("mining_direction", Direction.CODEC, this.miningDirection);
		output.storeNullable("tunnel_origin", BlockPos.CODEC, this.tunnelOrigin);
		output.storeNullable("resume_pos", BlockPos.CODEC, this.resumePos);
		output.putBoolean("depositing", this.depositing);
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
		this.resumePos = input.read("resume_pos", BlockPos.CODEC).orElse(null);
		this.depositing = input.getBooleanOr("depositing", false);
		this.inventory.fromItemList(input.listOrEmpty("inventory", ItemStack.CODEC));
		this.overflow.clear();
		input.listOrEmpty("overflow", ItemStack.CODEC).forEach(this.overflow::add);
	}

	@Override
	protected void dropEquipment(final ServerLevel level) {
		super.dropEquipment(level);
		this.spawnAtLocation(level, this.getItemBySlot(FLOWER_SLOT));
		this.setItemSlot(FLOWER_SLOT, ItemStack.EMPTY);
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
}
