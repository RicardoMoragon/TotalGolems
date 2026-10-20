package com.totalgolems.entity;

import com.totalgolems.GolemTier;
import com.totalgolems.block.ChestGroup;
import com.totalgolems.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The smelter, and the one that clears out the other golems' chests. While it wears an oxeye daisy
 * it empties the mining and bone golems' chests around the spot it was switched on at.
 * What can be smelted goes to the furnaces, of which it keeps up to five fed with coal from the
 * same chests; everything else, and what the furnaces produce, goes to a copper chest.
 * <p>
 * It leaves each chest what its owner needs: the pickaxes, shovels and swords, a stack of blocks for
 * a miner to lay as floor, and the feed for the animals in a bone golem's pen.
 */
public class CoalGolem extends WorkerGolem {
	private static final int WORK_RADIUS = 16;
	private static final int WORK_HEIGHT = 6;
	private static final int MAX_FURNACES = 5;
	private static final int SLOTS = 9;
	private static final int FURNACE_INPUT = 0;
	private static final int FURNACE_FUEL = 1;
	private static final int FURNACE_RESULT = 2;
	/** How many items one piece of coal or charcoal smelts, in a furnace or a blast furnace alike. */
	private static final int ITEMS_PER_FUEL = 8;
	private static final int PLAN_INTERVAL = 20;
	private static final double OWNER_SEARCH_RANGE = 64.0;

	/** What it is carrying to the furnaces: ores and raw food, nine stacks at most. */
	private final SimpleContainer ores = new SimpleContainer(SLOTS);
	private final SimpleContainer fuel = new SimpleContainer(SLOTS);
	/** What is on its way to a chest: what it has taken out of furnaces, and what was never going into one. */
	private final SimpleContainer products = new SimpleContainer(SLOTS);
	private final Map<Item, Boolean> smeltable = new HashMap<>();
	private final List<BlockPos> furnaces = new ArrayList<>();
	private final List<BlockPos> sources = new ArrayList<>();
	private final List<BlockPos> sinks = new ArrayList<>();
	private @Nullable BlockPos home;
	private @Nullable BlockPos target;

	public CoalGolem(final EntityType<? extends AbstractGolem> type, final Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createWorkerAttributes(1.0, 30.0);
	}

	@Override
	public String getTextureId() {
		return "coal";
	}

	@Override
	protected boolean isBusy() {
		return this.target != null;
	}

	@Override
	protected boolean isActivatingFlower(final ItemStack stack) {
		return stack.is(Items.OXEYE_DAISY);
	}

	@Override
	protected void onActivated(final ServerLevel level, final Player player) {
		this.home = this.feetPos();
		this.target = null;
	}

	@Override
	protected void onDeactivated(final ServerLevel level) {
		this.target = null;
		this.getNavigation().stop();
	}

	@Override
	protected void serverStep(final ServerLevel level) {
		if (!this.hasFlower() || this.home == null) {
			this.target = null;
			return;
		}

		if (this.target == null) {
			if (this.tickCount % PLAN_INTERVAL == 0) {
				this.plan(level);
				this.resetTravel();
			}

			return;
		}

		if (!this.travelTo(level, this.target, CHEST_REACH)) {
			return;
		}

		this.getLookControl().setLookAt(Vec3.atCenterOf(this.target));
		if (this.tickCount % TRANSFER_INTERVAL != 0) {
			return;
		}

		BlockPos reached = this.target;
		BlockState reachedState = level.getBlockState(reached);
		if (level.getBlockEntity(reached) instanceof AbstractFurnaceBlockEntity furnace) {
			this.serveFurnace(level, furnace);
		} else if (reachedState.is(BlockTags.COPPER_CHESTS)) {
			this.storeProducts(level, reached);
		} else if (this.isSource(reachedState)) {
			this.takeFrom(level, reached);
		}

		this.playSound(SoundEvents.ITEM_PICKUP, 0.3F, 0.8F);
		// Straight on to the next errand, if there is one.
		this.plan(level);
		this.resetTravel();
	}

	/** Picks where to go next, nearest first within each kind of errand, or nowhere if there is nothing to do. */
	private void plan(final ServerLevel level) {
		this.scan(level);
		this.target = null;
		boolean productsWaiting = this.furnaces.stream().anyMatch(pos -> this.hasCollectableResult(level, pos));
		BlockPos sink = this.nearest(this.sinks, pos -> this.canStoreProducts(level, pos));
		if (sink != null && !productsWaiting) {
			this.target = sink;
			return;
		}

		this.target = this.nearest(this.furnaces, pos -> this.needsVisit(level, pos));

		if (this.target == null) {
			this.target = this.nearest(this.sources, pos -> this.hasSomethingToTake(level, pos));
		}

		if (this.target == null) {
			this.target = sink;
		}
	}

	private @Nullable BlockPos nearest(final List<BlockPos> positions, final Predicate<BlockPos> wanted) {
		return positions.stream().filter(wanted).min(Comparator.comparingDouble(pos -> pos.distToCenterSqr(this.position()))).orElse(null);
	}

	/**
	 * Finds the furnaces, golem chests and copper chests around home. Only the five nearest furnaces are
	 * used, whatever their kind.
	 */
	private void scan(final ServerLevel level) {
		this.furnaces.clear();
		this.sources.clear();
		this.sinks.clear();
		ChunkPos min = ChunkPos.containing(this.home.offset(-WORK_RADIUS, 0, -WORK_RADIUS));
		ChunkPos max = ChunkPos.containing(this.home.offset(WORK_RADIUS, 0, WORK_RADIUS));
		for (int chunkX = min.x(); chunkX <= max.x(); chunkX++) {
			for (int chunkZ = min.z(); chunkZ <= max.z(); chunkZ++) {
				if (!level.hasChunk(chunkX, chunkZ)) {
					continue;
				}

				for (BlockEntity blockEntity : level.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
					BlockPos pos = blockEntity.getBlockPos();
					if (Math.abs(pos.getX() - this.home.getX()) > WORK_RADIUS
						|| Math.abs(pos.getZ() - this.home.getZ()) > WORK_RADIUS
						|| Math.abs(pos.getY() - this.home.getY()) > WORK_HEIGHT) {
						continue;
					}

					BlockState state = blockEntity.getBlockState();
					if (blockEntity instanceof AbstractFurnaceBlockEntity) {
						this.furnaces.add(pos);
					} else if (this.isSource(state)) {
						this.sources.add(pos);
					} else if (state.is(BlockTags.COPPER_CHESTS)) {
						this.sinks.add(pos);
					}
				}
			}
		}

		this.furnaces.sort(Comparator.comparingDouble(pos -> pos.distSqr(this.home)));
		if (this.furnaces.size() > MAX_FURNACES) {
			this.furnaces.subList(MAX_FURNACES, this.furnaces.size()).clear();
		}
	}

	/** It only takes from the chests the mining and bone golems fill. */
	private boolean isSource(final BlockState state) {
		return state.is(ModBlocks.IRON_CHEST)
			|| state.is(ModBlocks.DIAMOND_CHEST)
			|| state.is(ModBlocks.NETHERITE_CHEST)
			|| state.is(ModBlocks.BONE_CHEST);
	}

	/** The two things it smelts. Each has its own kind of furnace when there is one. */
	private enum Kind {
		ORE(Blocks.BLAST_FURNACE),
		FOOD(Blocks.SMOKER);

		private final Block preferredFurnace;

		Kind(final Block preferredFurnace) {
			this.preferredFurnace = preferredFurnace;
		}
	}

	/**
	 * What {@code stack} is to the smelter, or null if it is not for the furnaces: ores and raw metals,
	 * raw meat and fish, and potatoes. No stone, sand or anything else that merely can be smelted.
	 */
	private @Nullable Kind kindOf(final ServerLevel level, final ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}

		boolean meat = stack.is(ItemTags.MEAT) || stack.is(ItemTags.FISHES) || stack.is(Items.POTATO);
		if (!meat && !stack.is(ConventionalItemTags.ORES) && !stack.is(ConventionalItemTags.RAW_MATERIALS)) {
			return null;
		}

		// Cooked meat and the like are in the same tags but no furnace does anything to them.
		boolean smelts = this.smeltable
			.computeIfAbsent(stack.getItem(), item -> level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(item)), level).isPresent());
		return !smelts ? null : meat ? Kind.FOOD : Kind.ORE;
	}

	/** Blast furnaces for ores and smokers for food when one is assigned; otherwise plain furnaces. */
	private Block furnaceFor(final ServerLevel level, final Kind kind) {
		boolean available = this.furnaces.stream().anyMatch(pos -> level.getBlockState(pos).is(kind.preferredFurnace));
		return available ? kind.preferredFurnace : Blocks.FURNACE;
	}

	/** Whether this furnace is the right kind for {@code stack} and has room for it on top. */
	private boolean canTake(final ServerLevel level, final AbstractFurnaceBlockEntity furnace, final ItemStack stack) {
		Kind kind = this.kindOf(level, stack);
		return kind != null && furnace.getBlockState().is(this.furnaceFor(level, kind)) && fits(furnace.getItem(FURNACE_INPUT), stack);
	}

	private static boolean isFuel(final ItemStack stack) {
		return stack.is(ItemTags.COALS);
	}

	private @Nullable AbstractFurnaceBlockEntity furnaceAt(final ServerLevel level, final BlockPos pos) {
		return level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace ? furnace : null;
	}

	private static int count(final Container container) {
		int total = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			total += container.getItem(slot).getCount();
		}

		return total;
	}

	/** Whether {@code stack} could go on top of what {@code slot} already holds. */
	private static boolean fits(final ItemStack slot, final ItemStack stack) {
		return slot.isEmpty() || ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize();
	}

	private boolean hasCollectableResult(final ServerLevel level, final BlockPos pos) {
		AbstractFurnaceBlockEntity furnace = this.furnaceAt(level, pos);
		return furnace != null && !furnace.getItem(FURNACE_RESULT).isEmpty() && this.products.canAddItem(furnace.getItem(FURNACE_RESULT));
	}

	private List<AbstractFurnaceBlockEntity> furnaceEntities(final ServerLevel level) {
		return this.furnaces.stream().map(pos -> this.furnaceAt(level, pos)).filter(furnace -> furnace != null).toList();
	}

	/**
	 * How much of the carried {@code ore} this furnace should get so that every furnace able to take it
	 * ends up with the same amount on top.
	 */
	private int oreShare(final ServerLevel level, final AbstractFurnaceBlockEntity furnace, final ItemStack ore) {
		ItemStack input = furnace.getItem(FURNACE_INPUT);
		if (!this.canTake(level, furnace, ore)) {
			return 0;
		}

		int total = ore.getCount();
		int takers = 0;
		for (AbstractFurnaceBlockEntity other : this.furnaceEntities(level)) {
			if (this.canTake(level, other, ore)) {
				takers++;
				total += other.getItem(FURNACE_INPUT).getCount();
			}
		}

		int even = (total + takers - 1) / Math.max(1, takers);
		return Math.max(0, Math.min(even - input.getCount(), input.getMaxStackSize() - input.getCount()));
	}

	private boolean wantsOre(final ServerLevel level, final AbstractFurnaceBlockEntity furnace) {
		return this.ores.getItems().stream().anyMatch(ore -> this.oreShare(level, furnace, ore) > 0);
	}

	/** Coal the furnace already has: what is in the slot, plus the piece it is burning. */
	private static int fuelHeld(final AbstractFurnaceBlockEntity furnace) {
		return furnace.getItem(FURNACE_FUEL).getCount() + (furnace.getBlockState().getValue(AbstractFurnaceBlock.LIT) ? 1 : 0);
	}

	/** How much more coal the furnace needs to get through what is in it. */
	private static int fuelShortage(final AbstractFurnaceBlockEntity furnace) {
		ItemStack inFurnace = furnace.getItem(FURNACE_FUEL);
		if (furnace.getItem(FURNACE_INPUT).isEmpty() || !inFurnace.isEmpty() && !isFuel(inFurnace)) {
			return 0;
		}

		int needed = (furnace.getItem(FURNACE_INPUT).getCount() + ITEMS_PER_FUEL - 1) / ITEMS_PER_FUEL;
		return Math.max(0, needed - fuelHeld(furnace));
	}

	/**
	 * How much of the carried coal this furnace should get. When there is not enough for everyone it is
	 * spread so that all the furnaces with something to smelt, or about to get it, have the same.
	 */
	private int fuelShare(final ServerLevel level, final AbstractFurnaceBlockEntity furnace) {
		int shortage = fuelShortage(furnace);
		if (shortage == 0) {
			return 0;
		}

		int total = count(this.fuel);
		int needy = 0;
		for (AbstractFurnaceBlockEntity other : this.furnaceEntities(level)) {
			if (fuelShortage(other) > 0 || this.wantsOre(level, other)) {
				needy++;
				total += fuelHeld(other);
			}
		}

		int even = (total + needy - 1) / Math.max(1, needy);
		return Math.max(0, Math.min(shortage, even - fuelHeld(furnace)));
	}

	private boolean wantsFuel(final ServerLevel level, final AbstractFurnaceBlockEntity furnace) {
		ItemStack inFurnace = furnace.getItem(FURNACE_FUEL);
		return this.fuelShare(level, furnace) > 0 && this.fuel.getItems().stream().anyMatch(carried -> !carried.isEmpty() && fits(inFurnace, carried));
	}

	private boolean needsVisit(final ServerLevel level, final BlockPos pos) {
		AbstractFurnaceBlockEntity furnace = this.furnaceAt(level, pos);
		return furnace != null && (this.hasCollectableResult(level, pos) || this.wantsOre(level, furnace) || this.wantsFuel(level, furnace));
	}

	private void serveFurnace(final ServerLevel level, final AbstractFurnaceBlockEntity furnace) {
		ItemStack result = furnace.getItem(FURNACE_RESULT);
		if (!result.isEmpty()) {
			furnace.setItem(FURNACE_RESULT, this.products.addItem(result));
		}

		// Ore on top.
		for (int slot = 0; slot < this.ores.getContainerSize(); slot++) {
			ItemStack ore = this.ores.getItem(slot);
			int share = this.oreShare(level, furnace, ore);
			if (share > 0) {
				this.move(ore, furnace, FURNACE_INPUT, share);
				break;
			}
		}

		// Coal underneath.
		int give = this.fuelShare(level, furnace);
		for (int slot = 0; slot < this.fuel.getContainerSize() && give > 0; slot++) {
			ItemStack carried = this.fuel.getItem(slot);
			if (!carried.isEmpty() && fits(furnace.getItem(FURNACE_FUEL), carried)) {
				give -= this.move(carried, furnace, FURNACE_FUEL, give);
			}
		}

		furnace.setChanged();
	}

	/** Moves up to {@code amount} of {@code from} into the furnace slot; returns how many went. */
	private int move(final ItemStack from, final AbstractFurnaceBlockEntity furnace, final int slot, final int amount) {
		ItemStack there = furnace.getItem(slot);
		int room = there.isEmpty() ? from.getMaxStackSize() : there.getMaxStackSize() - there.getCount();
		int moved = Math.min(Math.min(amount, room), from.getCount());
		if (moved <= 0) {
			return 0;
		}

		if (there.isEmpty()) {
			furnace.setItem(slot, from.split(moved));
		} else {
			there.grow(moved);
			from.shrink(moved);
		}

		return moved;
	}

	/** Something is only worth carrying if a furnace of the right kind could take it: an empty one, or one already smelting the same thing. */
	private boolean hasFurnaceFor(final ServerLevel level, final ItemStack ore) {
		return this.furnaceEntities(level).stream().anyMatch(furnace -> this.canTake(level, furnace, ore));
	}

	/** A golem chest it is taking from, with what has to be known to tell what must stay in it. */
	private record Source(BlockState state, Container chest, @Nullable BoneGolem rancher) {
	}

	/** The chest at {@code pos} with those joined to it, or null if it is gone. */
	private @Nullable Source sourceAt(final ServerLevel level, final BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		Container chest = ChestGroup.container(level, pos);
		if (chest == null || !this.isSource(state)) {
			return null;
		}

		BoneGolem rancher = null;
		if (state.is(ModBlocks.BONE_CHEST)) {
			rancher = level.getEntitiesOfClass(BoneGolem.class, new AABB(pos).inflate(OWNER_SEARCH_RANGE), golem -> golem.isAlive() && golem.ownsChest(pos))
				.stream()
				.findFirst()
				.orElse(null);
		}

		return new Source(state, chest, rancher);
	}

	/**
	 * What the owner of the chest needs and so is never taken: the tools of its trade (a miner's shovel as
	 * well as its pickaxe), and whatever the animals in a bone golem's pen eat. Potatoes are the exception,
	 * because they are worth more baked.
	 */
	private boolean isReserved(final ServerLevel level, final Source source, final ItemStack stack) {
		if (GolemTier.isMinerChest(source.state)) {
			return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS);
		} else if (source.state.is(ModBlocks.BONE_CHEST)) {
			return stack.is(ItemTags.SWORDS) || !stack.is(Items.POTATO) && source.rancher != null && source.rancher.feedsOn(level, stack);
		}

		return false;
	}

	/** How many blocks a miner's chest holds beyond the stack it keeps for the miner to lay as floor. */
	private static int spareBlocks(final Source source) {
		if (!GolemTier.isMinerChest(source.state)) {
			return Integer.MAX_VALUE;
		}

		int blocks = 0;
		for (int slot = 0; slot < source.chest.getContainerSize(); slot++) {
			ItemStack stack = source.chest.getItem(slot);
			blocks += MiningGolem.isBuildingBlock(stack) ? stack.getCount() : 0;
		}

		return Math.max(0, blocks - MiningGolem.BLOCKS_KEPT_FOR_FLOOR);
	}

	/**
	 * Which of its pouches {@code stack} would go into if taken, or null if it is to stay in the chest:
	 * coal with the fuel, what can be smelted with the load for the furnaces, and anything else with
	 * what is bound for the copper chest. It only takes what it has somewhere to put down again.
	 */
	private @Nullable SimpleContainer pouchFor(final ServerLevel level, final Source source, final ItemStack stack) {
		if (stack.isEmpty() || this.isReserved(level, source, stack)) {
			return null;
		} else if (isFuel(stack)) {
			return !this.furnaces.isEmpty() && this.fuel.canAddItem(stack) ? this.fuel : null;
		} else if (this.kindOf(level, stack) != null) {
			return this.ores.canAddItem(stack) && this.hasFurnaceFor(level, stack) ? this.ores : null;
		}

		return this.products.canAddItem(stack) && this.sinks.stream().anyMatch(sink -> hasRoomFor(level, sink, stack)) ? this.products : null;
	}

	private static boolean hasRoomFor(final ServerLevel level, final BlockPos pos, final ItemStack stack) {
		Container chest = HopperBlockEntity.getContainerAt(level, pos);
		if (chest == null) {
			return false;
		}

		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			if (fits(chest.getItem(slot), stack)) {
				return true;
			}
		}

		return false;
	}

	private boolean hasSomethingToTake(final ServerLevel level, final BlockPos pos) {
		Source source = this.sourceAt(level, pos);
		if (source == null) {
			return false;
		}

		boolean spare = spareBlocks(source) > 0;
		return source.chest.hasAnyMatching(stack -> (spare || !MiningGolem.isBuildingBlock(stack)) && this.pouchFor(level, source, stack) != null);
	}

	private void takeFrom(final ServerLevel level, final BlockPos pos) {
		Source source = this.sourceAt(level, pos);
		if (source == null) {
			return;
		}

		Container chest = source.chest;
		int spare = spareBlocks(source);
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack stack = chest.getItem(slot);
			SimpleContainer pouch = this.pouchFor(level, source, stack);
			int allowed = MiningGolem.isBuildingBlock(stack) ? Math.min(spare, stack.getCount()) : stack.getCount();
			if (pouch == null || allowed <= 0) {
				continue;
			}

			ItemStack left = pouch.addItem(chest.removeItem(slot, allowed));
			int taken = allowed - left.getCount();
			if (MiningGolem.isBuildingBlock(stack)) {
				spare -= taken;
			}

			// What did not fit goes back where it was.
			if (!left.isEmpty()) {
				ItemStack there = chest.getItem(slot);
				if (there.isEmpty()) {
					chest.setItem(slot, left);
				} else {
					there.grow(left.getCount());
				}
			}
		}

		chest.setChanged();
	}

	/** Whether the chest at {@code pos} has room for any of the carried products. */
	private boolean canStoreProducts(final ServerLevel level, final BlockPos pos) {
		Container chest = HopperBlockEntity.getContainerAt(level, pos);
		if (chest == null) {
			return false;
		}

		for (ItemStack product : this.products.getItems()) {
			for (int slot = 0; slot < chest.getContainerSize() && !product.isEmpty(); slot++) {
				if (fits(chest.getItem(slot), product)) {
					return true;
				}
			}
		}

		return false;
	}

	private void storeProducts(final ServerLevel level, final BlockPos pos) {
		Container chest = HopperBlockEntity.getContainerAt(level, pos);
		if (chest == null) {
			return;
		}

		for (int slot = 0; slot < this.products.getContainerSize(); slot++) {
			ItemStack product = this.products.getItem(slot);
			if (!product.isEmpty()) {
				this.products.setItem(slot, HopperBlockEntity.addItem(null, chest, product, null));
			}
		}

		chest.setChanged();
	}

	@Override
	public void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("home", BlockPos.CODEC, this.home);
		this.ores.storeAsItemList(output.list("ores", ItemStack.CODEC));
		this.fuel.storeAsItemList(output.list("fuel", ItemStack.CODEC));
		this.products.storeAsItemList(output.list("products", ItemStack.CODEC));
	}

	@Override
	public void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.home = input.read("home", BlockPos.CODEC).orElse(null);
		this.ores.fromItemList(input.listOrEmpty("ores", ItemStack.CODEC));
		this.fuel.fromItemList(input.listOrEmpty("fuel", ItemStack.CODEC));
		this.products.fromItemList(input.listOrEmpty("products", ItemStack.CODEC));
	}

	@Override
	protected void dropEquipment(final ServerLevel level) {
		super.dropEquipment(level);
		for (SimpleContainer container : List.of(this.ores, this.fuel, this.products)) {
			container.removeAllItems().forEach(stack -> this.spawnAtLocation(level, stack));
		}
	}
}
