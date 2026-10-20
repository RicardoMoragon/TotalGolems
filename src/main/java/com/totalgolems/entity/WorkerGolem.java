package com.totalgolems.entity;

import com.totalgolems.GolemGreeting;
import com.totalgolems.block.ChestGroup;
import com.totalgolems.block.GolemStatueBlock;
import com.totalgolems.block.GolemStatueBlockEntity;
import com.totalgolems.block.MetalChestBlock;
import com.totalgolems.registry.ModBlocks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CopperGolemStatueBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
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
	private static final EntityDataAccessor<WeatheringCopper.WeatherState> DATA_WEATHER_STATE = SynchedEntityData.defineId(
		WorkerGolem.class, EntityDataSerializers.WEATHERING_COPPER_STATE
	);
	/** As the copper golem's: seven to eight days a stage. */
	private static final int WEATHERING_TICK_FROM = 504000;
	private static final int WEATHERING_TICK_TO = 552000;
	private static final float TURN_TO_STATUE_CHANCE = 0.0058F;
	/** {@link #nextWeatheringTick} of a waxed golem, which never weathers; and of one whose next stage is not drawn yet. */
	private static final long WAXED = -2L;
	private static final long UNSET = -1L;
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
	/** How many paths in a row that do not get there before a golem that cannot teleport gives up on the place. */
	private static final int DEAD_END_PATHS_BEFORE_GIVING_UP = 3;
	/** How long a place or a creature it could not get to is left alone before it is tried again: a minute. */
	private static final int UNREACHABLE_TICKS = 1200;

	protected @Nullable BlockPos chestPos;
	/** At its chest without a tool, and none in there either. */
	private boolean waitingForTool;
	private int travelTicks;
	private int stalledSeconds;
	private @Nullable Vec3 lastTravelPosition;
	private @Nullable BlockPos travelTarget;
	private double closestTravelDistance;
	private int secondsWithoutProgress;
	private int deadEndPaths;
	/** Set when it last gave up on getting somewhere on foot; cleared when it sets off again. */
	private boolean gaveUp;
	/** Places and creatures it could not get to, and the game time until which they are left alone. */
	private final Map<BlockPos, Long> unreachablePlaces = new HashMap<>();
	private final Map<UUID, Long> unreachableCreatures = new HashMap<>();
	/** Client side: when it last started and stopped swinging its tool, so that each swing is shown whole. */
	private int swingStartTick = -1000;
	private int swingStopTick = -1000;
	private boolean wasSwinging;
	private long nextWeatheringTick = UNSET;
	private @Nullable UUID lastLightningBoltUUID;

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

	/** The tick it last started swinging its tool, on the client. */
	public int getSwingStartTick() {
		return this.swingStartTick;
	}

	/** The tick it last stopped swinging its tool, on the client. */
	public int getSwingStopTick() {
		return this.swingStopTick;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			boolean swinging = this.isSwingingArm();
			if (swinging && !this.wasSwinging) {
				this.swingStartTick = this.tickCount;
			} else if (!swinging && this.wasSwinging) {
				this.swingStopTick = this.tickCount;
			}

			this.wasSwinging = swinging;
		} else {
			this.updateWeathering((ServerLevel)this.level());
		}
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

		if (this.isUnreachable(this.chestPos)) {
			// It could not get to the chest: it waits where it is, and tries again in a while.
			this.waitingForTool = true;
			return;
		}

		if (!this.travelTo(level, this.chestPos, CHEST_REACH)) {
			this.waitingForTool = this.hasGivenUp();
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
		entityData.define(DATA_WEATHER_STATE, WeatheringCopper.WeatherState.UNAFFECTED);
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
		GolemGreeting.tick(this, level);
		boolean active = this.isBusy();
		if (active != this.isActive()) {
			this.entityData.set(DATA_ACTIVE, active);
		}
	}

	@Override
	public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		InteractionResult weathering = this.interactWeathering(player, hand, held);
		if (weathering != null) {
			return weathering;
		}

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

				// Giving the flower it already wears just starts the job again from here, and everything is worth a try again.
				this.forgetUnreachable();
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

		if (!target.equals(this.travelTarget)) {
			this.travelTarget = target;
			this.closestTravelDistance = Double.MAX_VALUE;
			this.secondsWithoutProgress = 0;
			this.deadEndPaths = 0;
			this.gaveUp = false;
		}

		if (distance < 1.5 && Math.abs(this.getY() - spot.y) < 0.6) {
			// Path nodes are a whole block apart, too coarse for the last stretch.
			this.getNavigation().stop();
			this.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.0);
		} else if (this.travelTicks % REPATH_INTERVAL == 0) {
			// A chest is solid, so stopping next to it will do. Anywhere else the path has to end on the spot itself:
			// a neighbouring cell could be outside the tunnel or the quarry.
			this.getNavigation().moveTo(spot.x, spot.y, spot.z, reach > 1.0 ? 1 : 0, 1.0);
			this.deadEndPaths = leadsTo(this.getNavigation().getPath(), spot, reach) ? 0 : this.deadEndPaths + 1;
		}

		if (!this.mayTeleport() && this.getNavigation().isDone() && distance < reach + 2.0) {
			// The path has run out just short of the spot: the last step straight at it. A golem that teleports
			// gets there in the end anyway; the others would stand there until they gave up.
			this.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.0);
		}

		if (!this.mayTeleport() && this.deadEndPaths >= DEAD_END_PATHS_BEFORE_GIVING_UP) {
			// No way there on foot, as far as the golem can see.
			this.giveUpOn(target);
			return false;
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
				if (!this.mayTeleport()) {
					this.giveUpOn(target);
					return false;
				}

				this.resetTravel();
				if (this.mayTeleportTo(target, distance)) {
					this.teleportNextTo(level, target);
				}
			}
		}

		return false;
	}

	/** Whether {@code path} gets the golem there: to the spot itself, or near enough to it. */
	private static boolean leadsTo(final @Nullable Path path, final Vec3 spot, final double reach) {
		if (path == null) {
			return false;
		}

		Node end = path.getEndNode();
		return path.canReach() || end != null && Vec3.atBottomCenterOf(end.asBlockPos()).distanceTo(spot) <= reach;
	}

	/**
	 * Whether the golem gets itself unstuck by teleporting. Those that cannot give up on a place they find no
	 * way to on foot: it counts as {@linkplain #isUnreachable(BlockPos) unreachable} for a while, and
	 * {@link #hasGivenUp()} tells the job to choose something else.
	 */
	protected boolean mayTeleport() {
		return true;
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
		this.deadEndPaths = 0;
		this.gaveUp = false;
	}

	private void giveUpOn(final BlockPos target) {
		this.getNavigation().stop();
		this.resetTravel();
		this.markUnreachable(target);
		this.gaveUp = true;
	}

	/** Whether the last {@link #travelTo} gave up on getting where it was going. */
	protected boolean hasGivenUp() {
		return this.gaveUp;
	}

	/** Leaves {@code pos} alone for a while: there is no getting to it. */
	protected void markUnreachable(final BlockPos pos) {
		this.unreachablePlaces.put(pos.immutable(), this.level().getGameTime() + UNREACHABLE_TICKS);
	}

	/** Leaves {@code entity} alone for a while: there is no getting to it. */
	protected void markUnreachable(final Entity entity) {
		this.unreachableCreatures.put(entity.getUUID(), this.level().getGameTime() + UNREACHABLE_TICKS);
	}

	/** Whether {@code pos} is one it recently found no way to. */
	public boolean isUnreachable(final @Nullable BlockPos pos) {
		return pos != null && isStillMarked(this.unreachablePlaces, pos, this.level().getGameTime());
	}

	/** Whether {@code entity} is one it recently found no way to. */
	public boolean isUnreachable(final Entity entity) {
		return isStillMarked(this.unreachableCreatures, entity.getUUID(), this.level().getGameTime());
	}

	private static <K> boolean isStillMarked(final Map<K, Long> marked, final K key, final long now) {
		Long until = marked.get(key);
		if (until != null && until <= now) {
			marked.remove(key);
			return false;
		}

		return until != null;
	}

	protected void forgetUnreachable() {
		this.unreachablePlaces.clear();
		this.unreachableCreatures.clear();
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

	// ------------------------------------------------------------------------------------ weathering

	/**
	 * How worn the golem is. Like the copper golem it wears a stage every seven or eight days unless it is waxed,
	 * and once fully worn it may stop for good and turn into a statue of itself.
	 */
	public WeatheringCopper.WeatherState getWeatherState() {
		return this.entityData.get(DATA_WEATHER_STATE);
	}

	public void setWeatherState(final WeatheringCopper.WeatherState state) {
		this.entityData.set(DATA_WEATHER_STATE, state);
	}

	public boolean isWaxed() {
		return this.nextWeatheringTick == WAXED;
	}

	private void updateWeathering(final ServerLevel level) {
		if (this.nextWeatheringTick == WAXED) {
			return;
		}

		long gameTime = level.getGameTime();
		if (this.nextWeatheringTick == UNSET) {
			this.nextWeatheringTick = gameTime + this.random.nextIntBetweenInclusive(WEATHERING_TICK_FROM, WEATHERING_TICK_TO);
			return;
		}

		WeatheringCopper.WeatherState state = this.getWeatherState();
		if (state == WeatheringCopper.WeatherState.OXIDIZED) {
			if (level.getBlockState(this.blockPosition()).isAir() && this.random.nextFloat() <= TURN_TO_STATUE_CHANCE) {
				this.turnToStatue(level);
			}
		} else if (gameTime >= this.nextWeatheringTick) {
			this.setWeatherState(state.next());
			this.nextWeatheringTick += this.random.nextIntBetweenInclusive(WEATHERING_TICK_FROM, WEATHERING_TICK_TO);
		}
	}

	/** Honeycomb waxes it; an axe takes the wax off, or else scrapes off a stage of wear. Null for anything else. */
	private @Nullable InteractionResult interactWeathering(final Player player, final InteractionHand hand, final ItemStack held) {
		boolean wax = held.is(Items.HONEYCOMB) && !this.isWaxed();
		boolean axe = held.is(ItemTags.AXES) && (this.isWaxed() || this.getWeatherState() != WeatheringCopper.WeatherState.UNAFFECTED);
		if (!wax && !axe) {
			return null;
		}

		if (!(this.level() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}

		if (wax) {
			level.levelEvent(this, LevelEvent.PARTICLES_WAX_ON, this.blockPosition(), 0);
			level.playSound(null, this, SoundEvents.HONEYCOMB_WAX_ON, this.getSoundSource(), 1.0F, 1.0F);
			this.nextWeatheringTick = WAXED;
			held.consume(1, player);
		} else {
			level.playSound(null, this, SoundEvents.AXE_SCRAPE, this.getSoundSource(), 1.0F, 1.0F);
			if (this.isWaxed()) {
				level.levelEvent(this, LevelEvent.PARTICLES_WAX_OFF, this.blockPosition(), 0);
			} else {
				level.levelEvent(this, LevelEvent.PARTICLES_SCRAPE, this.blockPosition(), 0);
				this.setWeatherState(this.getWeatherState().previous());
			}

			this.nextWeatheringTick = UNSET;
			held.hurtAndBreak(1, player, hand.asEquipmentSlot());
		}

		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public void thunderHit(final ServerLevel level, final LightningBolt lightningBolt) {
		super.thunderHit(level, lightningBolt);
		// As with copper, lightning takes a stage of wear off, once per bolt.
		if (!lightningBolt.getUUID().equals(this.lastLightningBoltUUID)) {
			this.lastLightningBoltUUID = lightningBolt.getUUID();
			if (this.getWeatherState() != WeatheringCopper.WeatherState.UNAFFECTED) {
				this.nextWeatheringTick = UNSET;
				this.setWeatherState(this.getWeatherState().previous());
			}
		}
	}

	/**
	 * Stops for good: a statue of the golem in a random pose takes its place, holding everything it was, its flower,
	 * tool, chest and loads included, so that scraping it back to life brings back the same golem.
	 */
	public void turnToStatue(final ServerLevel level) {
		Block statue = ModBlocks.statue(this.getType());
		if (statue == null) {
			return;
		}

		BlockPos pos = this.blockPosition();
		CopperGolemStatueBlock.Pose[] poses = CopperGolemStatueBlock.Pose.values();
		level.setBlockAndUpdate(
			pos,
			statue.defaultBlockState()
				.setValue(GolemStatueBlock.POSE, poses[this.random.nextInt(poses.length)])
				.setValue(GolemStatueBlock.FACING, Direction.fromYRot(this.getYRot()))
				.setValue(GolemStatueBlock.AGE, WeatheringCopper.WeatherState.OXIDIZED.ordinal())
				.setValue(GolemStatueBlock.WATERLOGGED, level.getFluidState(pos).is(Fluids.WATER))
		);
		if (level.getBlockEntity(pos) instanceof GolemStatueBlockEntity statueEntity) {
			statueEntity.keep(this);
			this.playSound(SoundEvents.COPPER_GOLEM_BECOME_STATUE);
			if (this.isLeashed()) {
				this.dropLeash();
			}

			this.discard();
		}
	}

	/** Brought back to life from a statue: fresh and unwaxed, whatever it was when it stopped. */
	public void wakeFromStatue() {
		this.setWeatherState(WeatheringCopper.WeatherState.UNAFFECTED);
		this.nextWeatheringTick = UNSET;
		this.resetTravel();
	}

	@Override
	public void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("chest_pos", BlockPos.CODEC, this.chestPos);
		output.putLong("next_weather_age", this.nextWeatheringTick);
		output.store("weather_state", WeatheringCopper.WeatherState.CODEC, this.getWeatherState());
	}

	@Override
	public void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.chestPos = input.read("chest_pos", BlockPos.CODEC).orElse(null);
		this.nextWeatheringTick = input.getLongOr("next_weather_age", UNSET);
		this.setWeatherState(input.read("weather_state", WeatheringCopper.WeatherState.CODEC).orElse(WeatheringCopper.WeatherState.UNAFFECTED));
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
