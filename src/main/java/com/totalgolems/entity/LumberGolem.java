package com.totalgolems.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.totalgolems.registry.ModBlocks;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The lumberjack. While it wears an allium it fells the trees of the chunk it was
 * switched on in with the axe from its chest, replants each one with a sapling of its own and
 * takes the wood to its chest.
 * <p>
 * Given a spyglass it sees further: from then on it works the 3x3 chunks around that one, outwards
 * in a spiral, and its eyes change colour to show it.
 * <p>
 * It reaches as far as a player does. For the logs higher up it builds itself a pillar with blocks
 * from its chest, the same kind the miners build with, or else with logs of the tree itself, and takes
 * it down again when it can reach no more from it, keeping the blocks. Logs out of reach of that pillar,
 * out on a branch, it gets at from another, built closer. It never teleports: a tree, a sapling or a
 * chest it finds no way to on foot is left alone for a while.
 * <p>
 * It always replants: with a sapling it carries, one from its chest, or one it picks up where the
 * leaves fall. A stump it has no sapling for yet is remembered until it gets one.
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
	private static final int CLIMBING_BLOCKS = 64;
	/** No pillar taller than this. */
	private static final int MAX_PILLAR = 48;
	/** How far from the log's column it looks for a place to build a pillar. */
	private static final int PILLAR_SEARCH = 3;
	/** Saplings of each kind it takes from its chest, so as to have one for every stump. */
	private static final int SAPLINGS_FROM_CHEST = 8;
	private static final int CLIMB_INTERVAL = 8;
	/** How much faster than a player it chops, with the same axe. */
	private static final float CHOPPING_SPEEDUP = 2.0F;
	/** The longest breather it takes after a log, however long that took. */
	private static final int MAX_REST_TICKS = 40;

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
	/** The log it is chopping, for the cracks it shows. */
	private @Nullable BlockPos chopping;
	/** Logs of the tree it could get at from nowhere, left on it. */
	private int skippedLogs;
	/** Stumps waiting for a sapling of the kind that grew there. */
	private final Map<BlockPos, Item> toPlant = new LinkedHashMap<>();
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
	/** Ticks spent chopping since the last log came down. */
	private int ticksChopping;
	private int restUntilTick;
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

	/** The bottom block of the pillar it is standing on, or null when it is on the ground. */
	public @Nullable BlockPos getPillarBase() {
		return this.pillar.isEmpty() ? null : this.pillar.getFirst();
	}

	@Override
	protected boolean isActivatingFlower(final ItemStack stack) {
		return stack.is(Items.ALLIUM);
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
		} else if (!this.plantStump(level) && !this.walkToSapling(level) && this.tickCount % SEARCH_INTERVAL == 0) {
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
			this.skippedLogs = 0;
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
		// Grass, podzol, moss and mud as well as dirt: what trees grow on, and what is left under them once grown.
		//? if >=26.1 {
		return state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
		//?} else {
		/*return state.is(BlockTags.DIRT) || state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
		*///?}
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
		tree.logs.removeIf(log -> !isLog(level, log));
		if (tree.logs.isEmpty()) {
			if (this.climbDown(level)) {
				return;
			}

			if (this.skippedLogs > 0) {
				// Some of it is still up there: that tree is left alone for a while.
				this.markUnreachable(tree.base);
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

		// Whatever it can reach from where it is first, the lowest of those; else it climbs to the lowest log.
		BlockPos pos = tree.logs.stream().filter(this::canReachLog).findFirst().orElse(null);
		if (pos == null) {
			this.setSwingingArm(false);
			this.clearCracks(level);
			this.climbTowards(level, tree, tree.logs.getFirst());
			return;
		}

		if (!pos.equals(this.chopping)) {
			this.clearCracks(level);
			this.chopping = pos;
		}

		BlockState state = level.getBlockState(pos);
		this.holdStillIfClimbing();
		this.getLookControl().setLookAt(Vec3.atCenterOf(pos));
		if (this.tickCount < this.restUntilTick) {
			// A breather after the last log, the axe down.
			this.setSwingingArm(false);
			return;
		}

		this.setSwingingArm(true);
		this.ticksChopping++;
		ItemStack axe = this.getTool();
		float hardness = state.getDestroySpeed(level, pos);
		// Twice as fast as a player with the same axe, to make up for the breather after each log.
		this.breakProgress += hardness <= 0.0F ? 1.0F : CHOPPING_SPEEDUP * this.choppingSpeed(axe, state) / hardness / 30.0F;
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
		this.clearCracks(level);
		// As long a breather as the chopping took: with the doubled speed it fells as much as it ever did.
		this.restUntilTick = this.tickCount + Math.min(this.ticksChopping, MAX_REST_TICKS);
		this.ticksChopping = 0;
		level.destroyBlock(pos, false, this);
		tree.logs.remove(pos);
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

	/** Takes the cracks off the log it was chopping, and starts the next from nothing. */
	private void clearCracks(final ServerLevel level) {
		if (this.chopping != null) {
			level.destroyBlockProgress(this.getId(), this.chopping, -1);
		}

		this.chopping = null;
		this.breakProgress = 0.0F;
		this.breakStage = -1;
	}

	/** Up on its pillar it stands still while it chops, so as not to step off. */
	private void holdStillIfClimbing() {
		if (!this.pillar.isEmpty()) {
			this.holdStill();
		}
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

	/** Plants a sapling on every stump it has one for; the others it remembers, to plant when it gets one. */
	private void replant(final ServerLevel level, final Tree tree) {
		if (tree.sapling == null) {
			return;
		}

		for (BlockPos root : tree.roots) {
			if (!this.plant(level, root, tree.sapling) && stillBare(level, root, tree.sapling)) {
				this.toPlant.put(root.immutable(), tree.sapling);
			}
		}
	}

	/** Plants {@code sapling} at {@code root} if it has one and the ground takes it. */
	private boolean plant(final ServerLevel level, final BlockPos root, final Item sapling) {
		if (this.getSaplingCount(sapling) <= 0 || !stillBare(level, root, sapling)) {
			return false;
		}

		BlockState state = ((BlockItem)sapling).getBlock().defaultBlockState();
		level.setBlockAndUpdate(root, state);
		level.playSound(null, root, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
		this.saplings.merge(sapling, -1, Integer::sum);
		return true;
	}

	/** Whether {@code root} can take a sapling: nobody has built or planted on it since the tree came down. */
	private static boolean stillBare(final ServerLevel level, final BlockPos root, final Item sapling) {
		return sapling instanceof BlockItem blockItem && level.getBlockState(root).canBeReplaced() && blockItem.getBlock().defaultBlockState().canSurvive(level, root);
	}

	/** The stumps it has no sapling for. */
	public int getStumpsToPlant() {
		return this.toPlant.size();
	}

	/**
	 * Goes to plant a stump it now has the sapling for. If it has none of the kind and its chest does, it
	 * goes for some. Returns whether it is busy at it.
	 */
	private boolean plantStump(final ServerLevel level) {
		this.toPlant.entrySet().removeIf(entry -> level.isLoaded(entry.getKey()) && !stillBare(level, entry.getKey(), entry.getValue()));
		Map.Entry<BlockPos, Item> next = this.toPlant.entrySet()
			.stream()
			.filter(entry -> this.getSaplingCount(entry.getValue()) > 0 && !this.isUnreachable(entry.getKey()))
			.min(Comparator.comparingDouble(entry -> entry.getKey().distToCenterSqr(this.position())))
			.orElse(null);
		if (next == null) {
			Container chest = this.toPlant.isEmpty() || this.chestPos == null || this.isUnreachable(this.chestPos) ? null : this.findChest(level);
			if (chest != null && chest.hasAnyMatching(stack -> this.toPlant.containsValue(stack.getItem()) && this.getSaplingCount(stack.getItem()) == 0)) {
				this.depositing = true;
				return true;
			}

			return false;
		}

		BlockPos root = next.getKey();
		if (!this.travelTo(level, root, 2.5)) {
			if (this.hasGivenUp()) {
				this.markUnreachable(root);
			}

			return true;
		}

		this.plant(level, root, next.getValue());
		this.toPlant.remove(root);
		return true;
	}

	private void stopChopping(final ServerLevel level) {
		this.clearCracks(level);
		this.tree = null;
		this.pillarSpot = null;
		this.treeStand = null;
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
	 * Gets within reach of a log it cannot chop from where it is: to the foot of a pillar near it first, and
	 * then up a block at a time, the way a player does. Leaves give way as it pushes through them. If the
	 * pillar it is on can never get it there, out on a branch, it takes it down and builds another closer.
	 * With no blocks it climbs with logs of the tree it is felling, and with too few for the height it goes
	 * to its chest for more first, once a tree. A log it can get at from nowhere is left where it is.
	 */
	private void climbTowards(final ServerLevel level, final Tree tree, final BlockPos log) {
		if (this.pillarSpot == null && !this.pillar.isEmpty()) {
			this.pillarSpot = this.pillar.getFirst();
		}

		if (!this.pillar.isEmpty()) {
			if (!this.feetPos().below().equals(this.pillar.getLast()) || !this.towerReaches(this.pillarSpot, log)) {
				// Knocked off its pillar, or done with it: it takes it down, and starts again from the ground.
				this.climbDown(level);
				return;
			}
		} else if (this.pillarSpot == null || !this.towerReaches(this.pillarSpot, log)) {
			this.pillarSpot = this.pillarSpotFor(level, log);
			this.resetTravel();
			if (this.pillarSpot == null) {
				this.skipLog(tree, log);
				return;
			}
		}

		if (this.pillar.isEmpty()) {
			if (!this.travelTo(level, this.pillarSpot, 0.3)) {
				if (this.hasGivenUp()) {
					this.markUnreachable(this.pillarSpot);
					this.pillarSpot = null;
				}

				return;
			}

			int needed = log.getY() - this.pillarSpot.getY() - 2;
			if (this.getClimbingBlockCount() < needed && !this.fetchedBlocks && this.chestHasBuildingBlocks(level)) {
				// Not enough to get up there: to the chest for more, and back to the tree.
				this.fetchedBlocks = true;
				this.depositing = true;
				return;
			}
		}

		this.holdStill();
		if (!this.onGround() || this.tickCount % CLIMB_INTERVAL != 0) {
			return;
		}

		BlockPos feet = this.pillar.isEmpty() ? this.pillarSpot : this.pillar.getLast().above();
		BlockPos head = feet.above();
		BlockState overhead = level.getBlockState(head);
		if (overhead.getBlock() instanceof LeavesBlock && !overhead.getValue(LeavesBlock.PERSISTENT)) {
			level.destroyBlock(head, true, this);
			return;
		}

		if (!isRoom(level, head) || this.pillar.size() >= MAX_PILLAR) {
			this.skipLog(tree, log);
			return;
		}

		ItemStack blocks = this.nextClimbingBlock();
		if (blocks.isEmpty()) {
			if (this.pillar.isEmpty() && !this.fetchedBlocks && this.chestPos != null && !this.isUnreachable(this.chestPos)) {
				this.fetchedBlocks = true;
				this.depositing = true;
			} else {
				this.skipLog(tree, log);
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

	/** What it climbs with next: a building block if it has one, else one of the logs it has felled. */
	private ItemStack nextClimbingBlock() {
		for (ItemStack stack : this.climbingBlocks.getItems()) {
			if (!stack.isEmpty()) {
				return stack;
			}
		}

		for (ItemStack stack : this.wood.getItems()) {
			if (stack.is(ItemTags.LOGS) && stack.getItem() instanceof BlockItem) {
				return stack;
			}
		}

		return ItemStack.EMPTY;
	}

	private boolean chestHasBuildingBlocks(final ServerLevel level) {
		if (this.chestPos == null || this.isUnreachable(this.chestPos)) {
			return false;
		}

		Container chest = this.findChest(level);
		return chest != null && chest.hasAnyMatching(MiningGolem::isBuildingBlock);
	}

	/**
	 * Whether from a pillar on {@code spot}, as tall as need be, it could reach {@code log}: near enough to its
	 * column, and not so far below where it stands at the bottom.
	 */
	private boolean towerReaches(final BlockPos spot, final BlockPos log) {
		double dx = spot.getX() - log.getX();
		double dz = spot.getZ() - log.getZ();
		double dy = Math.max(0.0, spot.getY() + this.getEyeHeight() - (log.getY() + 0.5));
		return Math.sqrt(dx * dx + dz * dz + dy * dy) <= LOG_REACH - 0.25 && log.getY() - spot.getY() <= MAX_PILLAR + 3;
	}

	/**
	 * Where to build a pillar to get at {@code log}: on firm ground, with room to stand and to climb, in a
	 * column near the log's own; the nearest to it, and of those the one nearest the golem.
	 */
	private @Nullable BlockPos pillarSpotFor(final ServerLevel level, final BlockPos log) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		int ground = this.tree != null ? this.tree.base.getY() : this.blockPosition().getY();
		for (int dx = -PILLAR_SEARCH; dx <= PILLAR_SEARCH; dx++) {
			for (int dz = -PILLAR_SEARCH; dz <= PILLAR_SEARCH; dz++) {
				// From below, so as to stand on the ground and not on the leaves of a low crown.
				for (int y = ground - 3; y <= Math.min(log.getY(), ground + 3); y++) {
					BlockPos spot = new BlockPos(log.getX() + dx, y, log.getZ() + dz);
					if (!isRoom(level, spot) || !(isRoom(level, spot.above()) || level.getBlockState(spot.above()).getBlock() instanceof LeavesBlock)
						|| !level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP)) {
						continue;
					}

					if (this.towerReaches(spot, log) && !this.isUnreachable(spot)) {
						double distance = dx * dx + dz * dz + spot.distToCenterSqr(this.position()) / 10000.0;
						if (distance < bestDistance) {
							best = spot;
							bestDistance = distance;
						}
					}

					break;
				}
			}
		}

		return best;
	}

	/**
	 * Where to stand to get at the foot of the tree: beside it, on firm ground about its height, with room
	 * to stand. The one nearest the golem.
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

	/** Leaves {@code log} on the tree, there being no way to get at it. */
	private void skipLog(final Tree tree, final BlockPos log) {
		tree.logs.remove(log);
		this.skippedLogs++;
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
			// Unless somebody has put something else there. Logs it climbed with go back with the wood.
			boolean log = block.is(ItemTags.LOGS);
			if (log || MiningGolem.isBuildingBlock(block)) {
				level.removeBlock(top, false);
				level.playSound(null, top, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
				ItemStack left = (log ? this.wood : this.climbingBlocks).addItem(block);
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

	/**
	 * Takes saplings from {@code chest}, where it keeps its store of them, so as to have a few of each kind on
	 * it; of a kind it has stumps waiting for, as many as there are stumps.
	 */
	private void takeSaplings(final Container chest) {
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack stack = chest.getItem(slot);
			if (!stack.is(ItemTags.SAPLINGS)) {
				continue;
			}

			long stumps = this.toPlant.values().stream().filter(stack.getItem()::equals).count();
			int wanted = (int)Math.max(SAPLINGS_FROM_CHEST, stumps) - this.getSaplingCount(stack.getItem());
			if (wanted > 0) {
				ItemStack taken = chest.removeItem(slot, Math.min(wanted, stack.getCount()));
				this.saplings.merge(taken.getItem(), taken.getCount(), Integer::sum);
			}
		}

		chest.setChanged();
	}

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
		List<ItemEntity> items = new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(SAPLING_SEARCH_RANGE, 4.0, SAPLING_SEARCH_RANGE), this::canPickUp));
		// Also where the leaves of the trees it has felled come down, for the stumps it has no sapling for.
		for (BlockPos stump : this.toPlant.keySet()) {
			items.addAll(level.getEntitiesOfClass(ItemEntity.class, new AABB(stump).inflate(SAPLING_SEARCH_RANGE, 8.0, SAPLING_SEARCH_RANGE), this::canPickUp));
		}

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
		if (this.chestPos == null && this.tickCount % TRANSFER_INTERVAL == 0) {
			// An amethyst chest put down near it since will do; not another golem's.
			BlockPos found = this.findNearbyChest(level);
			if (found != null && level.getBlockState(found).is(ModBlocks.AMETHYST_CHEST)) {
				this.chestPos = found;
			}
		}

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
			this.takeSaplings(chest);
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
		if (this.chestPos == null || this.isUnreachable(this.chestPos) || this.isNearChest() || this.travelTo(level, this.chestPos, CHEST_REACH)) {
			this.restTicks--;
		}
	}

	/** Near enough its chest to potter about there. */
	private boolean isNearChest() {
		return this.chestPos != null && this.chestPos.closerToCenterThan(this.position(), WANDER_RADIUS + 2);
	}

	/** Resting by its chest, it potters about there. */
	@Override
	protected boolean mayWander() {
		return this.isIdleHere()
			&& (!this.hasFlower() || !this.hasArea || this.restTicks > 0 && this.pillar.isEmpty() && !this.depositing && (this.chestPos == null || this.isNearChest()));
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
		ValueOutput.TypedOutputList<Stump> stumpList = output.list("to_plant", Stump.CODEC);
		this.toPlant.forEach((pos, item) -> stumpList.add(new Stump(pos, item)));
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
		this.toPlant.clear();
		input.listOrEmpty("to_plant", Stump.CODEC).forEach(stump -> this.toPlant.put(stump.pos(), stump.sapling()));
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

	/** A stump waiting for a sapling, as saved. */
	private record Stump(BlockPos pos, Item sapling) {
		static final Codec<Stump> CODEC = RecordCodecBuilder.create(
			i -> i.group(BlockPos.CODEC.fieldOf("pos").forGetter(Stump::pos), BuiltInRegistries.ITEM.byNameCodec().fieldOf("sapling").forGetter(Stump::sapling))
				.apply(i, Stump::new)
		);
	}
}
