package com.totalgolems.entity;

import com.totalgolems.block.MetalChestBlock;
import com.totalgolems.registry.ModBlocks;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The farmer. While it wears a cornflower it works the fenced field it stands in: with a hoe from
 * its hay chest it tills the soil, sows it, harvests what is ripe and replants it, and takes the
 * harvest to the nearest bone chest. Every block tilled and every crop reaped wears the hoe.
 */
public class HayGolem extends PenGolem {
	/** About every ten seconds a little straw comes loose from it. */
	private static final int STRAW_CHANCE = 200;
	private static final int SEED_SLOTS = 5;
	private static final int HARVEST_SLOTS = 9;
	/** It keeps a stack of each kind of seed for replanting; the rest of what it harvests is delivered. */
	private static final int SEEDS_KEPT_PER_KIND = 64;
	/** After a delivery that could not be made (no room in the chest), how long before trying again. */
	private static final int RETRY_TICKS = 200;

	private final SimpleContainer seeds = new SimpleContainer(SEED_SLOTS);
	private final SimpleContainer harvest = new SimpleContainer(HARVEST_SLOTS);
	private @Nullable Errand errand;
	private @Nullable BlockPos target;
	private int deliveryRetryTick;

	private enum Errand {
		TILL,
		FETCH_SEEDS,
		SOW,
		HARVEST,
		DELIVER
	}

	public HayGolem(final EntityType<? extends AbstractGolem> type, final Level level) {
		super(type, level);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide() && this.random.nextInt(STRAW_CHANCE) == 0) {
			MetalChestBlock.dropStraw(this.level(), this.getX(), this.getY() + 0.5 + this.random.nextDouble() * 0.4, this.getZ(), 0.3, this.random);
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createWorkerAttributes(1.0, 30.0);
	}

	@Override
	public String getTextureId() {
		return "hay";
	}

	public boolean hasHoe() {
		return this.hasTool();
	}

	@Override
	protected TagKey<Item> toolTag() {
		return ItemTags.HOES;
	}

	@Override
	protected boolean isActivatingFlower(final ItemStack stack) {
		return stack.is(Items.CORNFLOWER);
	}

	@Override
	protected void clearErrand() {
		this.errand = null;
		this.target = null;
	}

	/** Anything that grows into a crop when planted on farmland: seeds, and carrots and potatoes themselves. */
	private static boolean isSeed(final ItemStack stack) {
		return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof CropBlock;
	}

	private static boolean isTillable(final BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.DIRT_PATH);
	}

	private static boolean isRipe(final BlockState state) {
		return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
	}

	private @Nullable BlockPos nearestCell(final Predicate<BlockPos> wanted) {
		return this.pen.cells().stream().filter(wanted).min(Comparator.comparingDouble(cell -> cell.distToCenterSqr(this.position()))).orElse(null);
	}

	@Override
	protected boolean plan(final ServerLevel level) {
		Container chest = this.canReach(this.chestPos) ? this.findChest(level) : null;
		BlockPos boneChest = this.tickCount >= this.deliveryRetryTick ? this.findContainer(level, state -> state.is(ModBlocks.BONE_CHEST)) : null;
		if (boneChest != null && isFull(this.harvest)) {
			return this.start(Errand.DELIVER, boneChest);
		}

		BlockPos untilled = this.nearestCell(cell -> isTillable(level.getBlockState(cell.below())) && level.getBlockState(cell).canBeReplaced());
		if (untilled != null) {
			return this.start(Errand.TILL, untilled);
		}

		BlockPos ripe = isFull(this.harvest) ? null : this.nearestCell(cell -> isRipe(level.getBlockState(cell)));
		BlockPos bare = this.nearestCell(cell -> level.getBlockState(cell.below()).is(Blocks.FARMLAND) && level.getBlockState(cell).isAir());
		if (bare != null && !this.seeds.isEmpty()) {
			return this.start(Errand.SOW, bare);
		} else if (bare != null && chest != null && chest.hasAnyMatching(HayGolem::isSeed)) {
			return this.start(Errand.FETCH_SEEDS, this.chestPos);
		} else if (ripe != null) {
			// Also where seeds come from when there are none anywhere else.
			return this.start(Errand.HARVEST, ripe);
		}

		return boneChest != null && !this.harvest.isEmpty() && this.start(Errand.DELIVER, boneChest);
	}

	private boolean start(final Errand errand, final BlockPos target) {
		this.errand = errand;
		this.target = target;
		return true;
	}

	@Override
	protected boolean work(final ServerLevel level) {
		BlockPos pos = this.target;
		switch (this.errand) {
			case FETCH_SEEDS, DELIVER -> {
				if (!this.canReach(pos)) {
					return true;
				}

				Container chest = this.reachContainer(level, pos);
				if (chest == null) {
					return false;
				}

				if (this.errand == Errand.FETCH_SEEDS) {
					this.takeSeeds(chest);
				} else if (!unload(this.harvest, chest)) {
					this.deliveryRetryTick = this.tickCount + RETRY_TICKS;
				}

				this.playSound(SoundEvents.ITEM_PICKUP, 0.3F, 0.8F);
				return true;
			}
			default -> {
				if (!this.walkTo(level, pos)) {
					return false;
				}

				this.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
				this.setSwingingArm(this.errand != Errand.SOW);
				if (!this.isActTick()) {
					return false;
				}

				switch (this.errand) {
					case TILL -> this.till(level, pos);
					case SOW -> this.sow(level, pos);
					default -> this.harvest(level, pos);
				}

				return true;
			}
		}
	}

	private void takeSeeds(final Container chest) {
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack stack = chest.getItem(slot);
			if (isSeed(stack)) {
				chest.setItem(slot, this.keepSeeds(stack));
			}
		}

		chest.setChanged();
	}

	/** Puts seeds in the seed pouch, up to a stack of each kind. Returns what was not taken. */
	private ItemStack keepSeeds(final ItemStack stack) {
		int wanted = Math.min(stack.getCount(), SEEDS_KEPT_PER_KIND - this.seeds.countItem(stack.getItem()));
		if (wanted <= 0) {
			return stack;
		}

		ItemStack left = this.seeds.addItem(stack.split(wanted));
		stack.grow(left.getCount());
		return stack;
	}

	private void till(final ServerLevel level, final BlockPos cell) {
		if (!isTillable(level.getBlockState(cell.below())) || !level.getBlockState(cell).canBeReplaced()) {
			return;
		}

		// Grass and flowers growing on the soil go under the hoe.
		if (!level.getBlockState(cell).isAir()) {
			level.destroyBlock(cell, false, this);
		}

		level.setBlockAndUpdate(cell.below(), Blocks.FARMLAND.defaultBlockState());
		level.playSound(null, cell, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
		this.wearTool(1);
	}

	private void sow(final ServerLevel level, final BlockPos cell) {
		if (!level.getBlockState(cell).isAir()) {
			return;
		}

		for (int slot = 0; slot < this.seeds.getContainerSize(); slot++) {
			ItemStack stack = this.seeds.getItem(slot);
			if (isSeed(stack)) {
				BlockState crop = ((BlockItem)stack.getItem()).getBlock().defaultBlockState();
				if (crop.canSurvive(level, cell)) {
					level.setBlockAndUpdate(cell, crop);
					level.playSound(null, cell, crop.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
					stack.shrink(1);
					return;
				}
			}
		}
	}

	/** Reaps a ripe crop and puts the same crop back in its place, using one of the seeds it gave. */
	private void harvest(final ServerLevel level, final BlockPos cell) {
		BlockState state = level.getBlockState(cell);
		if (!isRipe(state)) {
			return;
		}

		CropBlock crop = (CropBlock)state.getBlock();
		List<ItemStack> drops = Block.getDrops(state, level, cell, null, this, this.getTool());
		level.destroyBlock(cell, false, this);
		ItemStack seed = drops.stream().filter(drop -> drop.is(crop.asItem())).findFirst().orElse(ItemStack.EMPTY);
		if (seed.isEmpty()) {
			seed = this.seeds.getItems().stream().filter(stack -> stack.is(crop.asItem())).findFirst().orElse(ItemStack.EMPTY);
		}

		if (!seed.isEmpty() && crop.defaultBlockState().canSurvive(level, cell)) {
			seed.shrink(1);
			level.setBlockAndUpdate(cell, crop.defaultBlockState());
		}

		for (ItemStack drop : drops) {
			ItemStack left = this.harvest.addItem(isSeed(drop) ? this.keepSeeds(drop) : drop);
			if (!left.isEmpty()) {
				this.spawnAtLocation(level, left);
			}
		}

		this.wearTool(1);
	}

	@Override
	public void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		this.seeds.storeAsItemList(output.list("seeds", ItemStack.CODEC));
		this.harvest.storeAsItemList(output.list("harvest", ItemStack.CODEC));
	}

	@Override
	public void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		// Before golems held their tools in their hand the hoe was kept apart.
		input.read("hoe", ItemStack.CODEC).ifPresent(hoe -> this.setItemSlot(EquipmentSlot.MAINHAND, hoe));
		this.seeds.fromItemList(input.listOrEmpty("seeds", ItemStack.CODEC));
		this.harvest.fromItemList(input.listOrEmpty("harvest", ItemStack.CODEC));
	}

	@Override
	protected void dropEquipment(final ServerLevel level) {
		super.dropEquipment(level);
		for (SimpleContainer container : List.of(this.seeds, this.harvest)) {
			container.removeAllItems().forEach(stack -> this.spawnAtLocation(level, stack));
		}
	}
}
