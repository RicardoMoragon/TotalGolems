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

	private final SimpleContainer wood = new SimpleContainer(WOOD_SLOTS);
	/** Wood that did not fit. Anything here means the golem is full and must unload. */
	private final List<ItemStack> overflow = new ArrayList<>();
	/** One stock per kind of sapling, each capped at a stack. */
	private final Map<Item, Integer> saplings = new LinkedHashMap<>();
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
		return this.restTicks > 0 || this.chestRefused;
	}

	@Override
	protected boolean isBusy() {
		return this.hasFlower() && this.hasArea && !this.isResting() && !this.isWaitingForTool();
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
			return;
		}

		if (this.tickCount % PICKUP_INTERVAL == 0) {
			this.pickUpSaplings(level);
		}

		if (!this.overflow.isEmpty()) {
			this.depositing = true;
		}

		if (this.depositing) {
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

				Tree found = isSoil(level, base.below()) ? this.traceTree(level, base) : null;
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
			this.stopChopping(level);
			this.replant(level, tree);
			this.felledThisRound = true;
			return;
		}

		if (!this.travelTo(level, tree.base, TREE_REACH)) {
			this.setSwingingArm(false);
			return;
		}

		// Stood at the foot of the tree it brings down every log, however high.
		BlockPos pos = tree.logs.getFirst();
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
		this.breakProgress = 0.0F;
		this.breakStage = -1;
		this.setSwingingArm(false);
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
		return item.isAlive() && !item.hasPickUpDelay() && this.wantsSapling(item.getItem());
	}

	/** Goes for a sapling lying nearby, if there is one it has room for. Returns whether it is on its way to one. */
	private boolean walkToSapling(final ServerLevel level) {
		List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(SAPLING_SEARCH_RANGE, 4.0, SAPLING_SEARCH_RANGE), this::canPickUp);
		ItemEntity closest = items.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
		if (closest == null) {
			return false;
		}

		this.travelTo(level, closest.blockPosition(), 1.0);
		return true;
	}

	// ------------------------------------------------------------------------------------ chest

	private void tickDeposit(final ServerLevel level) {
		if (this.chestPos == null) {
			// No chest to take the wood to: the same as a full one.
			this.chestRefused = true;
			return;
		}

		if (!this.travelTo(level, this.chestPos, CHEST_REACH)) {
			return;
		}

		this.getLookControl().setLookAt(Vec3.atCenterOf(this.chestPos));
		if (this.tickCount % TRANSFER_INTERVAL != 0) {
			return;
		}

		Container chest = this.findChest(level);
		this.chestRefused = chest == null || !this.unloadInto(chest);
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
		if (this.chestPos == null || this.travelTo(level, this.chestPos, CHEST_REACH)) {
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
		this.overflow.clear();
		input.listOrEmpty("overflow", ItemStack.CODEC).forEach(this.overflow::add);
		this.saplings.clear();
		input.listOrEmpty("saplings", ItemStack.CODEC).forEach(stack -> this.saplings.put(stack.getItem(), Math.min(stack.getCount(), MAX_SAPLINGS_PER_KIND)));
	}

	@Override
	protected void dropEquipment(final ServerLevel level) {
		super.dropEquipment(level);
		this.wood.removeAllItems().forEach(stack -> this.spawnAtLocation(level, stack));
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
