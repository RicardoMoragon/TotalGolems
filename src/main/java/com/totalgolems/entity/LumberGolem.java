package com.totalgolems.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The lumberjack. While it wears a lily of the valley it fells the trees of the chunk it was
 * switched on in with the axe from its chest, replants each one with a sapling of its own and
 * takes the wood to its chest.
 * <p>
 * Given a spyglass it sees further: from then on it works the 3x3 chunks around that one, outwards
 * in a spiral, and its eyes change colour to show it.
 * <p>
 * It reaches as far as a player does. For the logs higher up it builds itself a pillar beside the
 * trunk with blocks from its chest, the same kind the miners build with, and takes it down again
 * when it is done, keeping the blocks. It never teleports: a tree, a sapling or a chest it finds no
 * way to on foot is left alone for a while.
 */
public class LumberGolem extends WorkerGolem {
	private static final EntityDataAccessor<Boolean> DATA_UPGRADED = SynchedEntityData.defineId(LumberGolem.class, EntityDataSerializers.BOOLEAN);
	private static final int WOOD_SLOTS = 10;
	private static final int MAX_SAPLINGS_PER_KIND = 64;
	/** Centre chunk first, then the eight around it, clockwise from the north. */
	private static final int[][] SPIRAL = {{0, 0}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
	private static final int MAX_LOGS_PER_TREE = 2048;
	private static final int MAX_TREE_SPREAD = 10;
	private static final double TREE_REACH = 3.0;
	private static final int SEARCH_INTERVAL = 5;
	private static final int PICKUP_INTERVAL = 5;
	private static final double SAPLING_SEARCH_RANGE = 10.0;
	/** How long it waits at its chest after a whole round without finding a tree, before looking again. */
	private static final int REST_TICKS = 600;
	/** How far from its eyes it can chop a log: as far as a player reaches. */
	private static final double LOG_REACH = 4.5;
	/** Blocks it keeps on it to climb with. */
	private static final int CLIMBING_BLOCKS = 16;
	/** No pillar taller than this. */
	private static final int MAX_PILLAR = 24;
	private static final int CLIMB_INTERVAL = 8;

	private final SimpleContainer wood = new SimpleContainer(WOOD_SLOTS);
	/** Wood that did not fit. Anything here means the golem is full and must unload. */
	private final List<ItemStack> overflow = new ArrayList<>();
	/** One stock per kind of sapling, each capped at a stack. */
	private final Map<Item, Integer> saplings = new LinkedHashMap<>();
	/** The blocks it climbs with. */
	private final SimpleContainer climbingBlocks = new SimpleContainer(2);
	/** The blocks of the pillar it is standing on, from the bottom up. */
	private final List<BlockPos> pillar = new ArrayList<>();
	/** Where it builds its pillar for the tree it is felling. */
	private @Nullable BlockPos pillarSpot;
	/** Where it walks to, beside the foot of the tree it is felling. */
	private @Nullable BlockPos treeStand;
	/** It has been to the chest for blocks for this tree already: if there were none, it does without. */
	private boolean fetchedBlocks;
	private boolean hasArea;
	private int centerChunkX;
	private int centerChunkZ;
	private int spiralIndex;
	private boolean felledThisRound;
	private boolean depositing;
	private boolean chestRefused;
	private int restTicks;
	private @Nullable Tree tree;
	private float breakProgress;
	private int breakStage = -1;

	public LumberGolem(final EntityType<? extends AbstractGolem> type, final Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createWorkerAttributes(1.0, 40.0);
	}

	@Override
	public String getTextureId() {
		return "amethyst";
	}

	@Override
	public String getEyesTextureId() {
		return this.isUpgraded() ? "amethyst_upgraded" : "amethyst";
	}

	/** Whether it has been given a spyglass, and so works nine chunks instead of one. */
	public boolean isUpgraded() {
		return this.entityData.get(DATA_UPGRADED);
	}

	/** How many chunks of the spiral it works: just the middle one until it is upgraded. */
	private int chunksWorked() {
		return this.isUpgraded() ? SPIRAL.length : 1;
	}

	@Override
	protected TagKey<Item> toolTag() {
		return ItemTags.AXES;
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_UPGRADED, false);
	}

	@Override
	public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.is(Items.SPYGLASS) && !this.isUpgraded()) {
			if (this.level() instanceof ServerLevel level) {
				held.consume(1, player);
				this.entityData.set(DATA_UPGRADED, true);
				// It need not wait out its rest to go and look at the chunks it can now see.
				this.restTicks = 0;
				this.spiralIndex = 0;
				level.playSound(null, this, SoundEvents.SPYGLASS_USE, this.getSoundSource(), 1.0F, 1.0F);
			}

			return InteractionResult.SUCCESS;
		}

		return super.mobInteract(player, hand);
	}

	public int getSaplingCount(final Item sapling) {
		return this.saplings.getOrDefault(sapling, 0);
	}

	/** Out of work: no trees left around, or a chest that will not take the wood. It waits by the chest. */
	public boolean isResting() {
		return this.restTicks > 0 || this.chestRefused || this.depositing && this.isUnreachable(this.chestPos);
	}

	@Override
	protected boolean isBusy() {
		return this.hasFlower() && this.hasArea && !this.isResting() && !this.isWaitingForTool();
	}

	/** It walks wherever it goes, and leaves alone for a while whatever it finds no way to. */
	@Override
	protected boolean mayTeleport() {
		return false;
	}

	public int getClimbingBlockCount() {
		return this.climbingBlocks.getItems().stream().mapToInt(ItemStack::getCount).sum();
	}

	/** How tall the pillar it is standing on is. */
	public int getPillarHeight() {
		return this.pillar.size();
	}

	@Override
	protected boolean isActivatingFlower(final ItemStack stack) {
		return stack.is(Items.LILY_OF_THE_VALLEY);
	}

	@Override
	protected void onActivated(final ServerLevel level, final Player player) {
		this.stopChopping(level);
		this.hasArea = true;
		this.centerChunkX = this.blockPosition().getX() >> 4;
		this.centerChunkZ = this.blockPosition().getZ() >> 4;
		this.spiralIndex = 0;
		this.felledThisRound = false;
		this.restTicks = 0;
		this.chestRefused = false;
		this.resetTravel();
		if (this.findChest(level) == null) {
			this.chestPos = this.findNearbyChest(level);
		}
	}

	@Override
	protected void onDeactivated(final ServerLevel level) {
		this.stopChopping(level);
		this.getNavigation().stop();
	}

	@Override
	protected void serverStep(final ServerLevel level) {
		if (!this.hasFlower() || !this.hasArea) {
			this.setSwingingArm(false);
			this.climbDown(level);
			return;
		}

		if (this.tickCount % PICKUP_INTERVAL == 0) {
			this.pickUpSaplings(level);
		}

		if (!this.overflow.isEmpty()) {
			this.depositing = true;
		}

		if (!this.pillar.isEmpty() && (this.depositing || !this.hasTool() || this.restTicks > 0 || this.tree == null)) {
			// Whatever it is off to do next, it gets down first, the way it went up.
			this.setSwingingArm(false);
			this.climbDown(level);
		} else if (this.depositing) {
			this.setSwingingArm(false);
			this.tickDeposit(level);
		} else if (!this.hasTool()) {
			// No axe, or it has just broken: to the chest for one, and there it stays until it gets it.
			this.stopChopping(level);
			this.fetchTool(level);
		} else if (this.restTicks > 0) {
			this.tickRest(level);
		} else if (this.tree != null) {
			this.tickTree(level, this.tree);
		} else if (!this.walkToSapling(level) && this.tickCount % SEARCH_INTERVAL == 0) {
			this.search(level);
		}
	}

	// ------------------------------------------------------------------------------------ trees

	/** Looks through one chunk of the spiral for a tree; moves on to the next chunk when it has none. */
	private void search(final ServerLevel level) {
		int chunkX = this.centerChunkX + SPIRAL[this.spiralIndex][0];
		int chunkZ = this.centerChunkZ + SPIRAL[this.spiralIndex][1];
		this.tree = level.hasChunk(chunkX, chunkZ) ? this.findTree(level, chunkX, chunkZ) : null;
		if (this.tree != null) {
			this.fetchedBlocks = false;
			this.resetTravel();
			return;
		}

		if (++this.spiralIndex < this.chunksWorked()) {
			return;
		}

		// A whole round done. With nothing felled there is nothing to do, and nothing to get saplings from either.
		this.spiralIndex = 0;
		if (!this.felledThisRound) {
			this.restTicks = REST_TICKS;
			this.depositing = !this.wood.isEmpty();
			this.resetTravel();
		}

		this.felledThisRound = false;
	}

	private @Nullable Tree findTree(final ServerLevel level, final int chunkX, final int chunkZ) {
		for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
			for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
				BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
				if (!isLog(level, top)) {
					continue;
				}

				BlockPos base = top;
				while (isLog(level, base.below())) {
					base = base.below();
				}

				Tree found = isSoil(level, base.below()) && !this.isUnreachable(base) ? this.traceTree(level, base) : null;
				if (found != null) {
					return found;
				}
			}
		}

		return null;
	}

	private static boolean isLog(final ServerLevel level, final BlockPos pos) {
		return level.getBlockState(pos).is(BlockTags.LOGS);
	}

	private static boolean isSoil(final ServerLevel level, final BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(BlockTags.DIRT) || state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
	}

	/**
	 * Follows the logs up from {@code base}, branches and all. Only counts as a tree if it has leaves that
	 * grew on it, which keeps the golem off log cabins.
	 */
	private @Nullable Tree traceTree(final ServerLevel level, final BlockPos base) {
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.add(base);
		queue.add(base);
		boolean hasLeaves = false;
		while (!queue.isEmpty() && seen.size() < MAX_LOGS_PER_TREE) {
			BlockPos log = queue.poll();
			for (BlockPos neighbour : BlockPos.betweenClosed(log.offset(-1, -1, -1), log.offset(1, 1, 1))) {
				if (Math.abs(neighbour.getX() - base.getX()) > MAX_TREE_SPREAD || Math.abs(neighbour.getZ() - base.getZ()) > MAX_TREE_SPREAD) {
					continue;
				}

				BlockState state = level.getBlockState(neighbour);
				if (state.is(BlockTags.LOGS)) {
					if (seen.add(neighbour.immutable())) {
						queue.add(neighbour.immutable());
					}
				} else if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
					hasLeaves = true;
				}
			}
		}

		if (!hasLeaves) {
			return null;
		}

		List<BlockPos> logs = new ArrayList<>(seen);
		logs.sort(Comparator.<BlockPos>comparingInt(pos -> pos.getY()).thenComparingDouble(pos -> pos.distSqr(base)));
		// Where to replant: the foot of the trunk, which is four blocks for the big two by two trees.
		List<BlockPos> roots = logs.stream()
			.filter(pos -> pos.getY() == base.getY() && Math.abs(pos.getX() - base.getX()) <= 1 && Math.abs(pos.getZ() - base.getZ()) <= 1 && isSoil(level, pos.below()))
			.toList();
		return new Tree(base, logs, roots, saplingFor(level.getBlockState(base).getBlock()));
	}

	/** The sapling that grows the tree a log came from, going by their names: oak_log, stripped_oak_wood -> oak_sapling. */
	private static @Nullable Item saplingFor(final Block log) {
		String name = BuiltInRegistries.BLOCK.getKey(log).getPath().replace("stripped_", "");
		String kind = name.endsWith("_log") ? name.substring(0, name.length() - 4) : name.endsWith("_wood") ? name.substring(0, name.length() - 5) : null;
		if (kind == null) {
			return null;
		}

		String namespace = BuiltInRegistries.BLOCK.getKey(log).getNamespace();
		for (String suffix : List.of("_sapling", "_propagule")) {
			Item item = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(namespace, kind + suffix)).orElse(null);
			if (item instanceof BlockItem && new ItemStack(item).is(ItemTags.SAPLINGS)) {
				return item;
			}
		}

		return null;
	}

	private void tickTree(final ServerLevel level, final Tree tree) {
		while (!tree.logs.isEmpty() && !isLog(level, tree.logs.getFirst())) {
			tree.logs.removeFirst();
		}

		if (tree.logs.isEmpty()) {
			if (this.climbDown(level)) {
				return;
			}

			this.stopChopping(level);
			this.replant(level, tree);
			this.felledThisRound = true;
			return;
		}

		if (this.pillar.isEmpty() && this.pillarSpot == null && !this.walkToTree(level, tree)) {
			this.setSwingingArm(false);
			return;
		}

		BlockPos pos = tree.logs.getFirst();
		if (!this.canReachLog(pos)) {
			this.setSwingingArm(false);
			this.climbTowards(level, tree, pos);
			return;
		}

		BlockState state = level.getBlockState(pos);
		this.setSwingingArm(true);
		this.getLookControl().setLookAt(Vec3.atCenterOf(pos));
		ItemStack axe = this.getTool();
		float hardness = state.getDestroySpeed(level, pos);
		this.breakProgress += hardness <= 0.0F ? 1.0F : this.choppingSpeed(axe, state) / hardness / 30.0F;
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

		List<ItemStack> drops = Block.getDrops(state, level, pos, null, this, axe);
		level.destroyBlockProgress(this.getId(), pos, -1);
		level.destroyBlock(pos, false, this);
		this.breakProgress = 0.0F;
		this.breakStage = -1;
		tree.logs.removeFirst();
		for (ItemStack drop : drops) {
			ItemStack leftover = this.wood.addItem(drop);
			if (!leftover.isEmpty()) {
				this.overflow.add(leftover);
			}
		}

		// The axe wears with every log, as in a player's hand. If it breaks, the rest of the tree waits.
		Tool wear = axe.get(DataComponents.TOOL);
		this.wearTool(wear != null ? wear.damagePerBlock() : 1);
	}

	/** What a player holding the axe would manage, Efficiency included. */
	private float choppingSpeed(final ItemStack axe, final BlockState state) {
		float speed = axe.getDestroySpeed(state);
		int efficiency = this.level()
			.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.get(Enchantments.EFFICIENCY)
			.map(enchantment -> EnchantmentHelper.getItemEnchantmentLevel(enchantment, axe))
			.orElse(0);
		return speed > 1.0F && efficiency > 0 ? speed + efficiency * efficiency + 1 : speed;
	}

	private void replant(final ServerLevel level, final Tree tree) {
		if (tree.sapling instanceof BlockItem blockItem) {
			BlockState sapling = blockItem.getBlock().defaultBlockState();
			for (BlockPos root : tree.roots) {
				if (this.getSaplingCount(tree.sapling) > 0 && level.getBlockState(root).canBeReplaced() && sapling.canSurvive(level, root)) {
					level.setBlockAndUpdate(root, sapling);
					level.playSound(null, root, sapling.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
					this.saplings.merge(tree.sapling, -1, Integer::sum);
				}
			}
		}
	}

	private void stopChopping(final ServerLevel level) {
		if (this.tree != null && !this.tree.logs.isEmpty()) {
			level.destroyBlockProgress(this.getId(), this.tree.logs.getFirst(), -1);
		}

		this.tree = null;
		this.pillarSpot = null;
		this.treeStand = null;
		this.breakProgress = 0.0F;
		this.breakStage = -1;
		this.setSwingingArm(false);
	}

	/**
	 * Walks to a spot beside the foot of the tree. Returns true once within reach of it. With no way there
	 * it gives the tree up: another one, and this one again in a while.
	 */
	private boolean walkToTree(final ServerLevel level, final Tree tree) {
		if (this.position().distanceTo(Vec3.atBottomCenterOf(tree.base)) <= TREE_REACH) {
			this.resetTravel();
			return true;
		}

		// Not the trunk itself: a path to a solid block ends on top of it.
		if (this.treeStand == null) {
			this.treeStand = this.spotBeside(level, tree, tree.base);
		}

		if (this.treeStand != null && this.travelTo(level, this.treeStand, 1.0)) {
			return true;
		}

		if (this.treeStand == null || this.hasGivenUp()) {
			this.markUnreachable(tree.base);
			this.stopChopping(level);
		}

		return false;
	}

	// ------------------------------------------------------------------------------------ climbing

	/** Whether {@code log} is close enough to its eyes to chop, as for a player. */
	private boolean canReachLog(final BlockPos log) {
		return this.getEyePosition().distanceTo(Vec3.atCenterOf(log)) <= LOG_REACH;
	}

	/**
	 * Gets within reach of a log too high to chop from where it stands: to the foot of its pillar first,
	 * and then up a block at a time, the way a player does. Leaves block the way only until it pushes
	 * through them. Without blocks, after one trip to the chest for some, or with something solid
	 * overhead, it leaves the logs it cannot reach where they are.
	 */
	private void climbTowards(final ServerLevel level, final Tree tree, final BlockPos log) {
		if (this.pillarSpot == null) {
			this.pillarSpot = this.spotBeside(level, tree, log);
			if (this.pillarSpot == null) {
				this.leaveOutOfReach(tree);
				return;
			}
		}

		if (this.pillar.isEmpty()) {
			if (!this.travelTo(level, this.pillarSpot, 0.3)) {
				if (this.hasGivenUp()) {
					this.leaveOutOfReach(tree);
				}

				return;
			}
		} else if (!this.feetPos().below().equals(this.pillar.getLast())) {
			// Knocked off its pillar: it takes it down and starts again.
			this.climbDown(level);
			return;
		}

		this.holdStill();
		if (!this.onGround() || this.tickCount % CLIMB_INTERVAL != 0) {
			return;
		}

		BlockPos feet = this.pillar.isEmpty() ? this.pillarSpot : this.pillar.getLast().above();
		BlockPos head = feet.above();
		BlockState overhead = level.getBlockState(head);
		if (overhead.getBlock() instanceof LeavesBlock) {
			level.destroyBlock(head, true, this);
			return;
		}

		if (!isRoom(level, head) || this.pillar.size() >= MAX_PILLAR) {
			this.leaveOutOfReach(tree);
			return;
		}

		ItemStack blocks = this.climbingBlocks.getItems().stream().filter(stack -> !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
		if (blocks.isEmpty()) {
			if (this.pillar.isEmpty() && !this.fetchedBlocks && this.chestPos != null && !this.isUnreachable(this.chestPos)) {
				// To the chest for some, and back to the tree.
				this.fetchedBlocks = true;
				this.depositing = true;
			} else {
				this.leaveOutOfReach(tree);
			}

			return;
		}

		// A jump, and the block goes in underneath.
		BlockState block = ((BlockItem)blocks.getItem()).getBlock().defaultBlockState();
		blocks.shrink(1);
		this.setPos(feet.getX() + 0.5, head.getY(), feet.getZ() + 0.5);
		level.setBlockAndUpdate(feet, block);
		level.playSound(null, feet, block.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
		this.pillar.add(feet.immutable());
		this.holdStill();
	}

	/**
	 * Where to stand to get at {@code log}, or to build a pillar under it: beside the column it is in, on
	 * firm ground about the height of the foot of the tree, with room to stand. The one nearest the golem.
	 */
	private @Nullable BlockPos spotBeside(final ServerLevel level, final Tree tree, final BlockPos log) {
		BlockPos best = null;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos column = log.relative(side);
			for (int y = tree.base.getY() + 2; y >= tree.base.getY() - 2; y--) {
				BlockPos spot = new BlockPos(column.getX(), y, column.getZ());
				if (isRoom(level, spot) && (isRoom(level, spot.above()) || level.getBlockState(spot.above()).getBlock() instanceof LeavesBlock)
					&& level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP)) {
					if (!this.isUnreachable(spot) && (best == null || spot.distToCenterSqr(this.position()) < best.distToCenterSqr(this.position()))) {
						best = spot;
					}

					break;
				}
			}
		}

		return best;
	}

	private static boolean isRoom(final ServerLevel level, final BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.canBeReplaced() && state.getFluidState().isEmpty();
	}

	/** Gives up on the logs of {@code tree} it cannot reach from where it is; the tree is left alone for a while. */
	private void leaveOutOfReach(final Tree tree) {
		tree.logs.removeIf(log -> !this.canReachLog(log));
		this.markUnreachable(tree.base);
		if (this.pillar.isEmpty()) {
			this.pillarSpot = null;
		}
	}

	/**
	 * Takes its pillar down a block at a time from the top, standing on it, and keeps the blocks. Returns
	 * whether there is any of it left.
	 */
	private boolean climbDown(final ServerLevel level) {
		if (this.pillar.isEmpty()) {
			return false;
		}

		this.holdStill();
		if (this.onGround() && this.tickCount % CLIMB_INTERVAL == 0) {
			BlockPos top = this.pillar.removeLast();
			BlockState state = level.getBlockState(top);
			ItemStack block = new ItemStack(state.getBlock().asItem());
			// Unless somebody has put something else there.
			if (MiningGolem.isBuildingBlock(block)) {
				level.removeBlock(top, false);
				level.playSound(null, top, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
				ItemStack left = this.climbingBlocks.addItem(block);
				if (!left.isEmpty()) {
					this.overflow.add(left);
				}
			}
		}

		return true;
	}

	/** Stands where it is, so as not to walk off its pillar. */
	private void holdStill() {
		this.getNavigation().stop();
		this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0);
	}

	/** Tops up the blocks it climbs with from {@code chest}. */
	private void takeClimbingBlocks(final Container chest) {
		int wanted = CLIMBING_BLOCKS - this.getClimbingBlockCount();
		for (int slot = 0; slot < chest.getContainerSize() && wanted > 0; slot++) {
			ItemStack stack = chest.getItem(slot);
			if (MiningGolem.isBuildingBlock(stack) && this.climbingBlocks.canAddItem(stack)) {
				ItemStack taken = chest.removeItem(slot, Math.min(wanted, stack.getCount()));
				ItemStack left = this.climbingBlocks.addItem(taken);
				wanted -= taken.getCount() - left.getCount();
				if (!left.isEmpty()) {
					chest.setItem(slot, HopperBlockEntity.addItem(null, chest, left, null));
				}
			}
		}

		chest.setChanged();
	}

	// ------------------------------------------------------------------------------------ saplings

	private boolean wantsSapling(final ItemStack stack) {
		return stack.is(ItemTags.SAPLINGS) && this.getSaplingCount(stack.getItem()) < MAX_SAPLINGS_PER_KIND;
	}

	/** Saplings are the only thing it picks up off the ground. */
	private void pickUpSaplings(final ServerLevel level) {
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(1.5, 1.0, 1.5), this::canPickUp)) {
			ItemStack stack = item.getItem();
			int taken = Math.min(stack.getCount(), MAX_SAPLINGS_PER_KIND - this.getSaplingCount(stack.getItem()));
			this.saplings.merge(stack.getItem(), taken, Integer::sum);
			this.take(item, taken);
			stack.shrink(taken);
			if (stack.isEmpty()) {
				item.discard();
			}
		}
	}

	private boolean canPickUp(final ItemEntity item) {
		return item.isAlive() && !item.hasPickUpDelay() && this.wantsSapling(item.getItem()) && !this.isUnreachable(item);
	}

	/** Goes for a sapling lying nearby, if there is one it has room for. Returns whether it is on its way to one. */
	private boolean walkToSapling(final ServerLevel level) {
		List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(SAPLING_SEARCH_RANGE, 4.0, SAPLING_SEARCH_RANGE), this::canPickUp);
		ItemEntity closest = items.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
		if (closest == null) {
			return false;
		}

		this.travelTo(level, closest.blockPosition(), 1.0);
		if (this.hasGivenUp()) {
			this.markUnreachable(closest);
		}

		return true;
	}

	// ------------------------------------------------------------------------------------ chest

	private void tickDeposit(final ServerLevel level) {
		if (this.chestPos == null) {
			// No chest to take the wood to: the same as a full one.
			this.chestRefused = true;
			return;
		}

		// A chest it could not get to it waits for where it is, eyes off, until it is worth another try.
		if (this.isUnreachable(this.chestPos) || !this.travelTo(level, this.chestPos, CHEST_REACH)) {
			return;
		}

		this.getLookControl().setLookAt(Vec3.atCenterOf(this.chestPos));
		if (this.tickCount % TRANSFER_INTERVAL != 0) {
			return;
		}

		Container chest = this.findChest(level);
		this.chestRefused = chest == null || !this.unloadInto(chest);
		if (chest != null) {
			this.takeClimbingBlocks(chest);
		}

		if (!this.chestRefused) {
			this.depositing = false;
			this.resetTravel();
			this.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 0.8F);
		}
	}

	private boolean unloadInto(final Container chest) {
		for (int slot = 0; slot < this.wood.getContainerSize(); slot++) {
			ItemStack stack = this.wood.getItem(slot);
			if (!stack.isEmpty()) {
				this.wood.setItem(slot, HopperBlockEntity.addItem(null, chest, stack, null));
			}
		}

		this.overflow.replaceAll(stack -> HopperBlockEntity.addItem(null, chest, stack, null));
		this.overflow.removeIf(ItemStack::isEmpty);
		chest.setChanged();
		return this.wood.isEmpty() && this.overflow.isEmpty();
	}

	private void tickRest(final ServerLevel level) {
		this.setSwingingArm(false);
		if (this.chestPos == null || this.isUnreachable(this.chestPos) || this.travelTo(level, this.chestPos, CHEST_REACH)) {
			this.restTicks--;
		}
	}

	// ------------------------------------------------------------------------------------ persistence

	@Override
	public void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("has_area", this.hasArea);
		output.putInt("center_chunk_x", this.centerChunkX);
		output.putInt("center_chunk_z", this.centerChunkZ);
		output.putInt("spiral_index", this.spiralIndex);
		output.putBoolean("upgraded", this.isUpgraded());
		output.putBoolean("depositing", this.depositing);
		this.wood.storeAsItemList(output.list("wood", ItemStack.CODEC));
		this.climbingBlocks.storeAsItemList(output.list("climbing_blocks", ItemStack.CODEC));
		ValueOutput.TypedOutputList<BlockPos> pillarList = output.list("pillar", BlockPos.CODEC);
		this.pillar.forEach(pillarList::add);
		ValueOutput.TypedOutputList<ItemStack> overflowList = output.list("overflow", ItemStack.CODEC);
		this.overflow.forEach(overflowList::add);
		ValueOutput.TypedOutputList<ItemStack> saplingList = output.list("saplings", ItemStack.CODEC);
		this.saplings.forEach((item, count) -> {
			if (count > 0) {
				saplingList.add(new ItemStack(item, count));
			}
		});
	}

	@Override
	public void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.hasArea = input.getBooleanOr("has_area", false);
		this.centerChunkX = input.getIntOr("center_chunk_x", 0);
		this.centerChunkZ = input.getIntOr("center_chunk_z", 0);
		this.entityData.set(DATA_UPGRADED, input.getBooleanOr("upgraded", false));
		this.spiralIndex = Math.floorMod(input.getIntOr("spiral_index", 0), this.chunksWorked());
		this.depositing = input.getBooleanOr("depositing", false);
		this.wood.fromItemList(input.listOrEmpty("wood", ItemStack.CODEC));
		this.climbingBlocks.fromItemList(input.listOrEmpty("climbing_blocks", ItemStack.CODEC));
		this.pillar.clear();
		input.listOrEmpty("pillar", BlockPos.CODEC).forEach(this.pillar::add);
		this.overflow.clear();
		input.listOrEmpty("overflow", ItemStack.CODEC).forEach(this.overflow::add);
		this.saplings.clear();
		input.listOrEmpty("saplings", ItemStack.CODEC).forEach(stack -> this.saplings.put(stack.getItem(), Math.min(stack.getCount(), MAX_SAPLINGS_PER_KIND)));
	}

	@Override
	protected void dropEquipment(final ServerLevel level) {
		super.dropEquipment(level);
		this.wood.removeAllItems().forEach(stack -> this.spawnAtLocation(level, stack));
		this.climbingBlocks.removeAllItems().forEach(stack -> this.spawnAtLocation(level, stack));
		this.overflow.forEach(stack -> this.spawnAtLocation(level, stack));
		this.overflow.clear();
		this.saplings.forEach((item, count) -> this.spawnAtLocation(level, new ItemStack(item, count)));
		this.saplings.clear();
		if (this.isUpgraded()) {
			this.spawnAtLocation(level, new ItemStack(Items.SPYGLASS));
		}
	}

	@Override
	public void remove(final RemovalReason reason) {
		if (this.level() instanceof ServerLevel level) {
			this.stopChopping(level);
		}

		super.remove(reason);
	}

	/** A tree being felled: its logs from the ground up, and where and what to replant. */
	private record Tree(BlockPos base, List<BlockPos> logs, List<BlockPos> roots, @Nullable Item sapling) {
	}
}
