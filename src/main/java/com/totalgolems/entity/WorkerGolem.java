package com.totalgolems.entity;

import com.totalgolems.block.ChestGroup;
import com.totalgolems.block.MetalChestBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What every golem of the mod shares: it is switched on by wearing a flower and off by taking it
 * away, it walks to places on its own and gets itself unstuck, and its eyes are lit only while it
 * has something to do.
 * <p>
 * Those that work with a tool carry a real one in their hand, taken from their own chest. It wears
 * out as it would for a player, and when it breaks they go back to the chest for another.
 */
public abstract class WorkerGolem extends AbstractGolem {
	/** Same slot the copper golem wears its poppy in, so the vanilla antenna layer can draw it. */
	public static final EquipmentSlot FLOWER_SLOT = EquipmentSlot.SADDLE;
	private static final EntityDataAccessor<Boolean> DATA_ACTIVE = SynchedEntityData.defineId(WorkerGolem.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_SWINGING = SynchedEntityData.defineId(WorkerGolem.class, EntityDataSerializers.BOOLEAN);
	protected static final double CHEST_REACH = 2.3;
	protected static final int TRANSFER_INTERVAL = 10;
	private static final int CHEST_SEARCH_RADIUS = 8;
	private static final int REPATH_INTERVAL = 10;
	/** Seconds without moving while walking somewhere before the golem gives up and teleports there. */
	private static final int STALLED_SECONDS_BEFORE_TELEPORT = 10;
	/** Seconds of moving about without getting any closer before it does the same. */
	private static final int SECONDS_WITHOUT_PROGRESS_BEFORE_TELEPORT = 20;
	/** Further away than this and being stuck is not cured by teleporting. */
	protected static final double MAX_TELEPORT_DISTANCE = 16.0;

	protected @Nullable BlockPos chestPos;
	/** At its chest without a tool, and none in there either. */
	private boolean waitingForTool;
	private int travelTicks;
	private int stalledSeconds;
	private @Nullable Vec3 lastTravelPosition;
	private @Nullable BlockPos travelTarget;
	private double closestTravelDistance;
	private int secondsWithoutProgress;

	protected WorkerGolem(final EntityType<? extends AbstractGolem> type, final Level level) {
		super(type, level);
		this.setPersistenceRequired();
		// The flower is handed back in dropEquipment, never rolled as random loot.
		this.setDropChance(FLOWER_SLOT, 0.0F);
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	protected static AttributeSupplier.Builder createWorkerAttributes(final double speedMultiplier, final double maxHealth) {
		return Mob.createMobAttributes()
			.add(Attributes.MOVEMENT_SPEED, 0.25 * speedMultiplier)
			.add(Attributes.STEP_HEIGHT, 1.0)
			.add(Attributes.MAX_HEALTH, maxHealth)
			.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	/** Names the golem's textures: {@code textures/entity/mining_golem/<id>_golem.png} and {@code <id>_golem_eyes.png}. */
	public abstract String getTextureId();

	/** Whether the golem has something to do right now. Its eyes are lit while it does. */
	protected abstract boolean isBusy();

	/** One tick of the golem's job, on the server. */
	protected abstract void serverStep(ServerLevel level);

	protected abstract boolean isActivatingFlower(ItemStack stack);

	/** Called when the golem is handed a flower it accepts, including the one it already wears. */
	protected abstract void onActivated(ServerLevel level, Player player);

	protected abstract void onDeactivated(ServerLevel level);

	public boolean isActive() {
		return this.entityData.get(DATA_ACTIVE);
	}

	/** Whether the golem is hitting something, for the arm swing. */
	public boolean isSwingingArm() {
		return this.entityData.get(DATA_SWINGING);
	}

	protected void setSwingingArm(final boolean swinging) {
		if (this.isSwingingArm() != swinging) {
			this.entityData.set(DATA_SWINGING, swinging);
		}
	}

	/** Names the texture of the lit eyes, when it is not the same as the golem's. */
	public String getEyesTextureId() {
		return this.getTextureId();
	}

	// ------------------------------------------------------------------------------------ tool

	/** The kind of tool this golem cannot work without, or null if it needs none. */
	protected @Nullable TagKey<Item> toolTag() {
		return null;
	}

	public ItemStack getTool() {
		return this.getMainHandItem();
	}

	/** Whether it has what it needs to work: the tool of its trade in its hand, if it has a trade that needs one. */
	public boolean hasTool() {
		TagKey<Item> tag = this.toolTag();
		return tag == null || this.getTool().is(tag);
	}

	public boolean isWaitingForTool() {
		return this.waitingForTool;
	}

	protected void setWaitingForTool(final boolean waiting) {
		this.waitingForTool = waiting;
	}

	/** Takes the first tool of its trade out of {@code chest}. Returns whether it now has one. */
	protected boolean takeTool(final Container chest) {
		TagKey<Item> tag = this.toolTag();
		for (int slot = 0; tag != null && !this.hasTool() && slot < chest.getContainerSize(); slot++) {
			if (chest.getItem(slot).is(tag)) {
				this.setItemSlot(EquipmentSlot.MAINHAND, chest.removeItem(slot, 1));
				chest.setChanged();
				this.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.2F);
			}
		}

		return this.hasTool();
	}

	/** Wears the tool as one use by a player would, enchantments included. It may break. */
	protected void wearTool(final int amount) {
		ItemStack tool = this.getTool();
		if (amount > 0 && !tool.isEmpty()) {
			tool.hurtAndBreak(amount, this, EquipmentSlot.MAINHAND);
			if (tool.isEmpty()) {
				this.playSound(SoundEvents.ITEM_BREAK.value(), 0.8F, 1.0F);
			}
		}
	}

	/**
	 * Walks to the chest and takes a tool from it. With none there it stays beside the chest, and
	 * {@link #isWaitingForTool()} is true until one turns up.
	 */
	protected void fetchTool(final ServerLevel level) {
		this.setSwingingArm(false);
		if (this.chestPos == null) {
			this.waitingForTool = true;
			return;
		}

		if (!this.travelTo(level, this.chestPos, CHEST_REACH)) {
			this.waitingForTool = false;
			return;
		}

		this.getLookControl().setLookAt(Vec3.atCenterOf(this.chestPos));
		if (this.tickCount % TRANSFER_INTERVAL == 0) {
			Container chest = this.findChest(level);
			this.waitingForTool = chest == null || !this.takeTool(chest);
			if (!this.waitingForTool) {
				this.resetTravel();
			}
		}
	}

	// ------------------------------------------------------------------------------------ chest

	public boolean hasFlower() {
		return !this.getItemBySlot(FLOWER_SLOT).isEmpty();
	}

	public void setChestPos(final @Nullable BlockPos chestPos) {
		this.chestPos = chestPos;
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_ACTIVE, false);
		entityData.define(DATA_SWINGING, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 6.0F) {
			@Override
			public boolean canUse() {
				return !WorkerGolem.this.isBusy() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !WorkerGolem.this.isBusy() && super.canContinueToUse();
			}
		});
		this.goalSelector.addGoal(2, new RandomLookAroundGoal(this) {
			@Override
			public boolean canUse() {
				return !WorkerGolem.this.isBusy() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !WorkerGolem.this.isBusy() && super.canContinueToUse();
			}
		});
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		this.serverStep(level);
		boolean active = this.isBusy();
		if (active != this.isActive()) {
			this.entityData.set(DATA_ACTIVE, active);
		}
	}

	@Override
	public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (this.isActivatingFlower(held)) {
			if (this.level() instanceof ServerLevel level) {
				ItemStack worn = this.getItemBySlot(FLOWER_SLOT);
				if (!worn.is(held.getItem())) {
					this.setItemSlot(FLOWER_SLOT, held.copyWithCount(1));
					held.consume(1, player);
					if (!worn.isEmpty() && !player.addItem(worn)) {
						this.spawnAtLocation(level, worn, 1.0F);
					}
				}

				// Giving the flower it already wears just starts the job again from here.
				this.onActivated(level, player);
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

				this.onDeactivated(level);
				level.playSound(null, this, SoundEvents.COPPER_GOLEM_SHEAR, this.getSoundSource(), 1.0F, 1.0F);
			}

			return InteractionResult.SUCCESS;
		}

		return super.mobInteract(player, hand);
	}

	/** The golem's chest together with the chests of the same kind touching it, as one inventory. */
	protected @Nullable Container findChest(final ServerLevel level) {
		if (this.chestPos == null || !level.isLoaded(this.chestPos)) {
			return null;
		}

		Container group = ChestGroup.container(level, this.chestPos);
		return group != null ? group : HopperBlockEntity.getContainerAt(level, this.chestPos);
	}

	/** Whether the chest at {@code pos} is this golem's, or one joined to it. */
	public boolean ownsChest(final BlockPos pos) {
		return this.chestPos != null && (this.chestPos.equals(pos) || ChestGroup.contains(this.level(), this.chestPos, pos));
	}

	public @Nullable BlockPos getChestPos() {
		return this.chestPos;
	}

	/** Golems that were not built on a block (summoned, for instance) adopt the closest golem chest. */
	protected @Nullable BlockPos findNearbyChest(final ServerLevel level) {
		for (BlockPos pos : BlockPos.withinManhattan(this.blockPosition(), CHEST_SEARCH_RADIUS)) {
			if (level.getBlockState(pos).getBlock() instanceof MetalChestBlock) {
				return pos.immutable();
			}
		}

		return null;
	}

	/** The block the golem stands in. Sampled slightly above its feet so sunken blocks such as soul sand do not count as one lower. */
	protected BlockPos feetPos() {
		return BlockPos.containing(this.getX(), this.getY() + 0.2, this.getZ());
	}

	/** Walks towards the bottom centre of {@code target}; returns true once within {@code reach} of it. */
	protected boolean travelTo(final ServerLevel level, final BlockPos target, final double reach) {
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
			// A chest is solid, so stopping next to it will do. Anywhere else the path has to end on the spot itself:
			// a neighbouring cell could be outside the tunnel or the quarry.
			this.getNavigation().moveTo(spot.x, spot.y, spot.z, reach > 1.0 ? 1 : 0, 1.0);
		}

		if (!target.equals(this.travelTarget)) {
			this.travelTarget = target;
			this.closestTravelDistance = Double.MAX_VALUE;
			this.secondsWithoutProgress = 0;
		}

		if (++this.travelTicks % 20 == 0) {
			boolean moved = this.lastTravelPosition == null || this.lastTravelPosition.distanceTo(this.position()) > 0.5;
			this.stalledSeconds = moved ? 0 : this.stalledSeconds + 1;
			this.lastTravelPosition = this.position();
			// Standing still is one way of being stuck; pacing about at the bottom of a hole without getting any nearer is the other.
			if (distance < this.closestTravelDistance - 1.0) {
				this.closestTravelDistance = distance;
				this.secondsWithoutProgress = 0;
			} else {
				this.secondsWithoutProgress++;
			}

			if (this.stalledSeconds >= STALLED_SECONDS_BEFORE_TELEPORT || this.secondsWithoutProgress >= SECONDS_WITHOUT_PROGRESS_BEFORE_TELEPORT) {
				this.resetTravel();
				if (this.mayTeleportTo(target, distance)) {
					this.teleportNextTo(level, target);
				}
			}
		}

		return false;
	}

	/** Whether a golem that cannot get to {@code target}, {@code distance} blocks away, may jump there. */
	protected boolean mayTeleportTo(final BlockPos target, final double distance) {
		return distance <= MAX_TELEPORT_DISTANCE;
	}

	protected void resetTravel() {
		this.travelTicks = 0;
		this.stalledSeconds = 0;
		this.lastTravelPosition = null;
		this.travelTarget = null;
	}

	/** Whether a golem that teleports may land at {@code spot}, which it fits into. Any such place will do by default. */
	protected boolean mayLandAt(final ServerLevel level, final BlockPos spot) {
		return true;
	}

	/** Teleports to {@code pos} or a place next to it, the first it fits into and may land at; to none if there is none. */
	private void teleportNextTo(final ServerLevel level, final BlockPos pos) {
		List<BlockPos> candidates = List.of(pos, pos.above(), pos.north(), pos.south(), pos.east(), pos.west());
		for (BlockPos candidate : candidates) {
			Vec3 spot = Vec3.atBottomCenterOf(candidate);
			if (level.noCollision(this, this.getType().getDimensions().makeBoundingBox(spot)) && this.mayLandAt(level, candidate)) {
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
	}

	@Override
	public void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.chestPos = input.read("chest_pos", BlockPos.CODEC).orElse(null);
	}

	@Override
	protected void dropEquipment(final ServerLevel level) {
		super.dropEquipment(level);
		this.spawnAtLocation(level, this.getItemBySlot(FLOWER_SLOT));
		this.setItemSlot(FLOWER_SLOT, ItemStack.EMPTY);
		this.spawnAtLocation(level, this.getTool());
		this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
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
