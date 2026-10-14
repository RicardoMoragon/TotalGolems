package com.totalgolems.test;

import static com.totalgolems.test.FarmGolemTests.container;
import static com.totalgolems.test.FarmGolemTests.floor;
import static com.totalgolems.test.FarmGolemTests.pen;
import static com.totalgolems.test.FarmGolemTests.spawn;
import static com.totalgolems.test.MiningGolemTests.give;
import static com.totalgolems.test.MiningGolemTests.stopper;

import com.totalgolems.GolemTier;
import com.totalgolems.block.ChestGroup;
import com.totalgolems.entity.BoneGolem;
import com.totalgolems.entity.CoalGolem;
import com.totalgolems.entity.LumberGolem;
import com.totalgolems.entity.MiningGolem;
import com.totalgolems.entity.SpongeGolem;
import com.totalgolems.registry.ModBlocks;
import com.totalgolems.registry.ModEntities;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Tools that wear out, the netherite golem's lock, chests joined into one, the miner and the sponge
 * golem working together, the coal golem clearing out chests, item frames as a filter for the copper
 * golem, and the amethyst golem's spyglass.
 */
public class GolemUpgradeTests {
	private static final BlockPos CHEST = new BlockPos(0, 1, 3);

	// ------------------------------------------------------------------------------------ tools

	@GameTest(maxTicks = 600)
	public void minerWithoutPickaxeWaitsAtChestUntilGivenOne(final GameTestHelper helper) {
		MiningGolem golem = miner(helper, GolemTier.IRON, CHEST, ItemStack.EMPTY);
		helper.setBlock(2, 1, 3, Blocks.STONE);
		stopper(helper, 4);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.runAfterDelay(100, () -> {
			helper.assertBlockPresent(Blocks.STONE, 2, 1, 3);
			helper.assertTrue(golem.isWaitingForTool() && !golem.isActive(), "without a pickaxe it waits with its eyes off");
			helper.assertTrue(golem.position().distanceTo(Vec3.atCenterOf(helper.absolutePos(CHEST))) < 3.0, "and it does so by its chest");
			container(helper, CHEST).setItem(3, new ItemStack(Items.STONE_PICKAXE));
		});
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.AIR, 2, 1, 3);
			helper.assertTrue(golem.getTool().is(Items.STONE_PICKAXE), "it should be holding the pickaxe from the chest");
			helper.assertTrue(golem.getTool().getDamageValue() == 1, "one block dug, one use of the pickaxe");
		});
	}

	@GameTest(maxTicks = 1200)
	public void pickaxeWearsOutBreaksAndIsReplacedFromChest(final GameTestHelper helper) {
		ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
		worn.setDamageValue(worn.getMaxDamage() - 2);
		MiningGolem golem = miner(helper, GolemTier.IRON, CHEST, worn);
		container(helper, CHEST).setItem(0, new ItemStack(Items.IRON_PICKAXE));
		// Cobblestone, which wears a pickaxe with every block: natural stone only does every other one.
		for (int x = 2; x <= 5; x++) {
			helper.setBlock(x, 1, 3, Blocks.COBBLESTONE);
		}

		stopper(helper, 6);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.succeedWhen(() -> {
			for (int x = 2; x <= 5; x++) {
				helper.assertBlockPresent(Blocks.AIR, x, 1, 3);
			}

			// The old one lasted two blocks; the new one has done the other two.
			helper.assertTrue(golem.getTool().is(Items.IRON_PICKAXE) && golem.getTool().getDamageValue() == 2, "it should be on its second pickaxe, used twice");
			helper.assertTrue(container(helper, CHEST).countItem(Items.IRON_PICKAXE) == 0, "the spare pickaxe should have been taken");
			helper.assertTrue(container(helper, CHEST).countItem(Items.COBBLESTONE) == 4, "all four blocks should be in the chest");
		});
	}

	/** Natural stone and deepslate cost half: of every two blocks only the first wears the pickaxe. */
	@GameTest(maxTicks = 1200)
	public void stoneWearsThePickaxeHalfAsMuch(final GameTestHelper helper) {
		MiningGolem golem = miner(helper, GolemTier.IRON, CHEST, new ItemStack(Items.IRON_PICKAXE));
		helper.setBlock(2, 1, 3, Blocks.STONE);
		helper.setBlock(3, 1, 3, Blocks.DEEPSLATE);
		helper.setBlock(4, 1, 3, Blocks.STONE);
		helper.setBlock(5, 1, 3, Blocks.DEEPSLATE);
		helper.setBlock(6, 1, 3, Blocks.COBBLESTONE);
		stopper(helper, 7);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.AIR, 6, 1, 3);
			helper.assertTrue(golem.isHalted(), "it should have finished its tunnel");
			// Two for the four blocks of stone and deepslate, one for the cobblestone.
			helper.assertTrue(golem.getTool().getDamageValue() == 3, "the pickaxe should be worn by 3, not " + golem.getTool().getDamageValue());
		});
	}

	/** Two golems, two lanes of deepslate: a wooden pickaxe against a diamond one with Efficiency V. */
	@GameTest(maxTicks = 1500)
	public void golemDigsAtTheSpeedOfItsPickaxe(final GameTestHelper helper) {
		BlockPos slowChest = new BlockPos(0, 1, 2);
		BlockPos fastChest = new BlockPos(0, 1, 5);
		MiningGolem slow = miner(helper, GolemTier.IRON, slowChest, new ItemStack(Items.WOODEN_PICKAXE));
		MiningGolem fast = miner(helper, GolemTier.IRON, fastChest, MiningGolemTests.enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.EFFICIENCY, 5));
		for (int x = 2; x <= 4; x++) {
			for (int y = 1; y <= 2; y++) {
				helper.setBlock(x, y, 2, Blocks.DEEPSLATE);
				helper.setBlock(x, y, 5, Blocks.DEEPSLATE);
			}
		}

		stopper(helper, 5);
		give(helper, slow, new ItemStack(Items.POPPY), Direction.EAST);
		give(helper, fast, new ItemStack(Items.POPPY), Direction.EAST);
		long[] done = {-1, -1};
		helper.onEachTick(() -> {
			for (int lane = 0; lane < 2; lane++) {
				if (done[lane] < 0 && helper.getBlockState(new BlockPos(4, 1, lane == 0 ? 2 : 5)).isAir()) {
					done[lane] = helper.getTick();
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(done[0] >= 0 && done[1] >= 0, "both lanes should be dug");
			helper.assertTrue(done[1] * 4 < done[0], "the good pickaxe took " + done[1] + " ticks and the wooden one " + done[0]);
		});
	}

	@GameTest(maxTicks = 800)
	public void pickaxeTooPoorForTheBlockGetsNothingOutOfIt(final GameTestHelper helper) {
		MiningGolem golem = miner(helper, GolemTier.IRON, CHEST, new ItemStack(Items.WOODEN_PICKAXE));
		helper.setBlock(2, 1, 3, Blocks.IRON_ORE);
		stopper(helper, 3);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.AIR, 2, 1, 3);
			helper.assertTrue(golem.isHalted() && !golem.isActive(), "it should be done and back home");
			helper.assertTrue(container(helper, CHEST).isEmpty(), "iron ore dug with a wooden pickaxe drops nothing");
		});
	}

	@GameTest
	public void netheriteGolemTakesNoPatternUntilGivenWitherRose(final GameTestHelper helper) {
		helper.assertTrue(GolemTier.IRON.inventorySlots() == 9, "the iron golem carries nine stacks");
		helper.assertTrue(GolemTier.IRON.blocksAtOnce() == 1 && GolemTier.DIAMOND.blocksAtOnce() == 2 && GolemTier.NETHERITE.blocksAtOnce() == 3, "one, two and three blocks at once");
		helper.assertTrue(GolemTier.NETHERITE.speedMultiplier() > 1.3, "the netherite golem keeps its speed");
		MiningGolem golem = miner(helper, GolemTier.NETHERITE, CHEST, new ItemStack(Items.NETHERITE_PICKAXE));
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		give(helper, golem, new ItemStack(Items.DANDELION), Direction.EAST);
		give(helper, golem, new ItemStack(Items.CORNFLOWER), Direction.EAST);
		helper.assertTrue(!golem.hasFlower() && !golem.isUnlocked(), "a new netherite golem takes neither pattern flower, and a cornflower no longer unlocks it");
		Player player = give(helper, golem, new ItemStack(Items.WITHER_ROSE), Direction.EAST);
		helper.assertTrue(golem.isUnlocked() && !golem.hasFlower(), "the wither rose unlocks it without being worn");
		helper.assertTrue(player.getMainHandItem().isEmpty(), "the wither rose is used up");
		give(helper, golem, new ItemStack(Items.DANDELION), Direction.EAST);
		helper.assertTrue(golem.getItemBySlot(MiningGolem.FLOWER_SLOT).is(Items.DANDELION), "once unlocked it takes a pattern like the others");
		// Other tiers were never locked, and they are all as tough as each other.
		MiningGolem iron = spawn(helper, ModEntities.IRON_MINING_GOLEM, new BlockPos(1, 1, 5));
		helper.assertTrue(iron.isUnlocked(), "an iron golem needs no unlocking");
		helper.assertTrue(iron.getMaxHealth() == MiningGolem.MAX_HEALTH && golem.getMaxHealth() == MiningGolem.MAX_HEALTH, "every tier has the same health");
		golem.discard();
		iron.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void amethystGolemWaitsAtChestUntilGivenAxe(final GameTestHelper helper) {
		LumberGolem golem = MiningGolemTests.setUp(helper, ModEntities.AMETHYST_GOLEM, ModBlocks.AMETHYST_CHEST);
		golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		// Tall enough to stand clear of the roof the test framework puts over the plot: trees are found from above.
		int height = 10;
		tree(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 4)), height);
		helper.setBlock(4, 0, 4, Blocks.DIRT);
		give(helper, golem, new ItemStack(Items.SPYGLASS), Direction.EAST);
		give(helper, golem, new ItemStack(Items.LILY_OF_THE_VALLEY), Direction.EAST);
		helper.runAfterDelay(100, () -> {
			helper.assertBlockPresent(Blocks.OAK_LOG, 4, 1, 4);
			helper.assertTrue(golem.isWaitingForTool() && !golem.isActive(), "without an axe it waits with its eyes off");
			container(helper, CHEST).setItem(0, new ItemStack(Items.STONE_AXE));
		});
		helper.succeedWhen(() -> {
			for (int y = 1; y <= height; y++) {
				helper.assertBlockPresent(Blocks.AIR, 4, y, 4);
			}

			helper.assertTrue(golem.getTool().is(Items.STONE_AXE) && golem.getTool().getDamageValue() == height, "one use of the axe for every log");
		});
	}

	/**
	 * Twenty-two cows and no sword. It feeds them and picks up what is lying about, and nothing dies
	 * until a sword turns up in its chest; then it takes it and thins the herd.
	 */
	@GameTest(maxTicks = 8000)
	public void boneGolemWithoutSwordFeedsAndTidiesButKillsNothing(final GameTestHelper helper) {
		BoneGolem golem = pen(helper, ModEntities.BONE_GOLEM, ModBlocks.BONE_CHEST);
		container(helper, CHEST).setItem(0, new ItemStack(Items.WHEAT, 8));
		FarmGolemTests.herd(helper, 22, 0);
		helper.spawnItem(Items.FEATHER, new BlockPos(5, 1, 5));
		FarmGolemTests.give(helper, golem, Items.WHITE_TULIP);
		boolean[] armed = {false};
		int[] fewestUnarmed = {Integer.MAX_VALUE};
		helper.onEachTick(() -> {
			if (!armed[0]) {
				fewestUnarmed[0] = Math.min(fewestUnarmed[0], FarmGolemTests.animals(helper).size());
			}
		});
		helper.succeedWhen(() -> {
			if (!armed[0]) {
				helper.assertTrue(FarmGolemTests.animals(helper).stream().anyMatch(animal -> animal.isInLove() || animal.isBaby()), "it should feed the cows without a sword");
				helper.assertItemEntityNotPresent(Items.FEATHER);
				helper.assertTrue(!golem.isWaitingForTool(), "lacking a sword does not leave it waiting for one");
				helper.assertTrue(fewestUnarmed[0] >= 22, "nothing dies while it has no sword");
				container(helper, CHEST).setItem(5, new ItemStack(Items.IRON_SWORD));
				armed[0] = true;
			}

			helper.assertTrue(golem.getTool().is(Items.IRON_SWORD), "it should take the sword once there is one");
			int herd = FarmGolemTests.animals(helper).size();
			helper.assertTrue(herd == BoneGolem.MAX_ANIMALS, "with a sword it thins the herd to twenty, found " + herd);
		});
	}

	// ------------------------------------------------------------------------------------ chests

	/**
	 * The golem's own chest is full, but the one beside it has room, and the pickaxe: the two are one
	 * chest to it. Above, ten chests in a block, of which a group only takes eight.
	 */
	@GameTest(maxTicks = 800)
	public void chestsThatTouchWorkAsOne(final GameTestHelper helper) {
		MiningGolem golem = miner(helper, GolemTier.IRON, CHEST, ItemStack.EMPTY);
		BlockPos second = CHEST.south();
		helper.setBlock(second, ModBlocks.IRON_CHEST);
		Container own = container(helper, CHEST);
		for (int slot = 0; slot < own.getContainerSize(); slot++) {
			own.setItem(slot, new ItemStack(Items.GOLD_BLOCK, 64));
		}

		container(helper, second).setItem(0, new ItemStack(Items.IRON_PICKAXE));
		for (int x = 2; x <= 6; x++) {
			helper.setBlock(x, 4, 5, ModBlocks.AMETHYST_CHEST);
			helper.setBlock(x, 4, 6, ModBlocks.AMETHYST_CHEST);
		}

		ServerLevel level = helper.getLevel();
		helper.assertTrue(ChestGroup.positions(level, helper.absolutePos(new BlockPos(2, 4, 5))).size() == ChestGroup.MAX_CHESTS, "a group stops at eight chests");
		helper.assertTrue(ChestGroup.positions(level, helper.absolutePos(CHEST)).size() == 2, "only chests of the same kind join up");
		helper.setBlock(2, 1, 3, Blocks.STONE);
		stopper(helper, 3);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.AIR, 2, 1, 3);
			helper.assertTrue(golem.getTool().is(Items.IRON_PICKAXE), "it should have found the pickaxe in the chest next door");
			helper.assertTrue(container(helper, second).countItem(Items.COBBLESTONE) == 1, "what did not fit in its own chest goes in the one beside it");
			helper.assertTrue(golem.isHalted(), "it should have finished its tunnel");
		});
	}

	// ------------------------------------------------------------------------------------ water

	/**
	 * A tunnel in a channel one block deep, so that the water stays in it. Two blocks in the golem breaks
	 * into a pocket of water and then meets obsidian, slow enough to keep it standing there. It must go
	 * home, the sponge golem next door must dry the tunnel, and then the miner must finish it.
	 */
	@GameTest(maxTicks = 4000)
	public void minerCallsSpongeGolemToDrainFloodedTunnel(final GameTestHelper helper) {
		BlockPos spongeChest = CHEST.south();
		MiningGolem miner = miner(helper, GolemTier.IRON, CHEST, new ItemStack(Items.DIAMOND_PICKAXE));
		helper.setBlock(spongeChest, ModBlocks.SPONGE_CHEST);
		container(helper, spongeChest).setItem(0, new ItemStack(Items.SPONGE, 2));
		for (int x = 1; x <= 6; x++) {
			helper.setBlock(x, 1, 2, Blocks.STONE);
			helper.setBlock(x, 1, 4, Blocks.STONE);
		}

		helper.setBlock(2, 1, 3, Blocks.STONE);
		helper.setBlock(3, 1, 3, Blocks.STONE);
		helper.setBlock(4, 1, 3, Blocks.WATER);
		helper.setBlock(5, 1, 3, Blocks.WATER);
		helper.setBlock(6, 1, 3, Blocks.OBSIDIAN);
		helper.setBlock(6, 2, 3, Blocks.OBSIDIAN);
		stopper(helper, 7);
		SpongeGolem sponge = spawn(helper, ModEntities.SPONGE_GOLEM, new BlockPos(1, 1, 5));
		sponge.setChestPos(helper.absolutePos(spongeChest));
		FarmGolemTests.give(helper, sponge, Items.BLUE_ORCHID);
		give(helper, miner, new ItemStack(Items.POPPY), Direction.EAST);
		helper.assertTrue(sponge.isLinked(helper.getLevel()), "chests side by side should link the two golems");

		boolean[] waited = {false};
		boolean[] dugWhileWaiting = {false};
		helper.onEachTick(() -> {
			waited[0] |= miner.isWaitingForDrain();
			dugWhileWaiting[0] |= miner.isWaitingForDrain() && miner.isMining();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(waited[0], "the miner should have gone home to wait for the tunnel to be drained");
			helper.assertTrue(!dugWhileWaiting[0], "and should not have dug while it waited");
			for (int x = 1; x <= 6; x++) {
				helper.assertTrue(helper.getBlockState(new BlockPos(x, 1, 3)).getFluidState().isEmpty(), "water left in the tunnel at " + x);
			}

			// Then the miner went back and dug the obsidian that the water was in front of.
			helper.assertBlockPresent(Blocks.AIR, 6, 1, 3);
			helper.assertBlockPresent(Blocks.AIR, 6, 2, 3);
			helper.assertTrue(miner.isHalted() && !miner.isWaitingForDrain(), "the miner should have finished the tunnel");
			helper.assertContainerContains(CHEST, Items.OBSIDIAN);
			helper.assertContainerContains(spongeChest, Items.WET_SPONGE);
			helper.assertTrue(!sponge.isDraining() && !sponge.isActive(), "the sponge golem should be back at rest");
		});
	}

	/**
	 * A pool six by six and three deep, more than one sponge can take. Built in the air, clear of other
	 * tests' water. No sponge may go into a layer while there is still water above it.
	 */
	@GameTest(maxTicks = 3000, padding = 24)
	public void spongeGolemDrainsFromTheTopLayerDown(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BlockPos.ZERO).above(16);
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 8; z++) {
				for (int y = -3; y <= 0; y++) {
					boolean pool = x >= 2 && x <= 7 && z >= 2 && z <= 7 && y >= -2;
					level.setBlockAndUpdate(base.offset(x, y, z), (pool ? Blocks.WATER : Blocks.STONE).defaultBlockState());
				}
			}
		}

		BlockPos chestPos = base.offset(0, 1, 4);
		level.setBlockAndUpdate(chestPos, ModBlocks.SPONGE_CHEST.defaultBlockState());
		((Container)level.getBlockEntity(chestPos)).setItem(0, new ItemStack(Items.SPONGE, 8));
		SpongeGolem golem = ModEntities.SPONGE_GOLEM.create(level, EntitySpawnReason.COMMAND);
		golem.snapTo(Vec3.atBottomCenterOf(base.offset(1, 1, 4)));
		level.addFreshEntity(golem);
		golem.setChestPos(chestPos);
		FarmGolemTests.give(helper, golem, Items.BLUE_ORCHID);
		int[] topWater = {Integer.MIN_VALUE};
		int[] seen = {0};
		String[] tooDeep = {null};
		helper.onEachTick(() -> {
			// The water the golem saw when it chose where to put the sponge is what was there at the end of the last tick.
			if (golem.countSpongesPlaced() > seen[0]) {
				seen[0] = golem.countSpongesPlaced();
				if (golem.lastSponge().getY() < topWater[0]) {
					tooDeep[0] = "a sponge went in at " + golem.lastSponge() + " with water still at height " + topWater[0];
				}
			}

			topWater[0] = Integer.MIN_VALUE;
			for (BlockPos pos : BlockPos.betweenClosed(base.offset(2, -2, 2), base.offset(7, 0, 7))) {
				if (!level.getFluidState(pos).isEmpty()) {
					topWater[0] = Math.max(topWater[0], pos.getY());
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(tooDeep[0] == null, String.valueOf(tooDeep[0]));
			helper.assertTrue(topWater[0] == Integer.MIN_VALUE, "water left in the pool");
			helper.assertTrue(golem.countSpongesPlaced() >= 2, "a pool this size takes more than one sponge");
			helper.assertTrue(!golem.isActive(), "with no water left it should be idle");
			golem.discard();
		});
	}

	/**
	 * A sponge golem drying a miner's diggings that end in a channel of water running on beyond them,
	 * fed by a source too far down it for any sponge to reach. The water keeps coming back, so it gives up.
	 */
	@GameTest(maxTicks = 3000, padding = 24)
	public void spongeGolemGivesUpOnWaterFedFromOutsideItsArea(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BlockPos.ZERO).above(16);
		for (int x = -2; x <= 14; x++) {
			for (int z = 0; z <= 4; z++) {
				level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.STONE.defaultBlockState());
				boolean channel = z == 2 && x >= 0 && x <= 9;
				level.setBlockAndUpdate(base.offset(x, 0, z), (channel ? Blocks.WATER : Blocks.STONE).defaultBlockState());
			}
		}

		BlockPos spongeChest = base.offset(1, 1, 0);
		BlockPos minerChest = base.offset(2, 1, 0);
		level.setBlockAndUpdate(spongeChest, ModBlocks.SPONGE_CHEST.defaultBlockState());
		level.setBlockAndUpdate(minerChest, ModBlocks.IRON_CHEST.defaultBlockState());
		((Container)level.getBlockEntity(spongeChest)).setItem(0, new ItemStack(Items.SPONGE, 8));
		SpongeGolem golem = ModEntities.SPONGE_GOLEM.create(level, EntitySpawnReason.COMMAND);
		golem.snapTo(Vec3.atBottomCenterOf(base.offset(1, 1, 1)));
		level.addFreshEntity(golem);
		golem.setChestPos(spongeChest);
		FarmGolemTests.give(helper, golem, Items.BLUE_ORCHID);
		// The miner's part: the first three blocks of the channel are its tunnel.
		helper.assertTrue(golem.requestDrain(base.offset(1, 0, 2), BoundingBox.fromCorners(base.offset(0, 0, 2), base.offset(2, 0, 2))), "the job should be taken");
		helper.succeedWhen(() -> {
			helper.assertTrue(golem.isResting() && !golem.isDraining(), "it should have given up and let the miner go");
			// The first ones lower the water for good; then three in a row leave it as it was, and that is enough.
			int placed = golem.countSpongesPlaced();
			helper.assertTrue(placed > SpongeGolem.FRUITLESS_SPONGES_BEFORE_GIVING_UP && placed <= SpongeGolem.FRUITLESS_SPONGES_BEFORE_GIVING_UP + 3, "it should give up after a few sponges, not " + placed);
			helper.assertTrue(golem.position().distanceTo(Vec3.atCenterOf(spongeChest)) < 3.5 && !golem.isActive(), "it waits by its chest with its eyes off");
			helper.assertTrue(level.getBlockState(base.offset(9, 0, 2)).is(Blocks.WATER), "the source is out of its reach and still there");
			golem.discard();
		});
	}

	@GameTest(maxTicks = 300)
	public void spongeGolemLinkedToMinerWaitsToBeCalled(final GameTestHelper helper) {
		BlockPos spongeChest = CHEST.south();
		floor(helper, Blocks.SMOOTH_STONE);
		helper.setBlock(CHEST, ModBlocks.DIAMOND_CHEST);
		helper.setBlock(spongeChest, ModBlocks.SPONGE_CHEST);
		container(helper, spongeChest).setItem(0, new ItemStack(Items.SPONGE, 2));
		// Water it would go for at once if it were working on its own.
		helper.setBlock(5, 0, 5, Blocks.WATER);
		helper.setBlock(5, 0, 6, Blocks.WATER);
		SpongeGolem golem = spawn(helper, ModEntities.SPONGE_GOLEM, new BlockPos(1, 1, 5));
		golem.setChestPos(helper.absolutePos(spongeChest));
		FarmGolemTests.give(helper, golem, Items.BLUE_ORCHID);
		helper.runAtTickTime(250, () -> {
			helper.assertTrue(golem.isLinked(helper.getLevel()), "its chest touches a miner's chest");
			helper.assertBlockPresent(Blocks.WATER, 5, 0, 5);
			helper.assertBlockPresent(Blocks.WATER, 5, 0, 6);
			helper.assertTrue(!golem.isActive() && golem.countDrySponges() == 0, "it should not even have taken sponges out");
			helper.assertTrue(golem.position().distanceTo(Vec3.atCenterOf(helper.absolutePos(spongeChest))) < 3.5, "it waits by its chest");
			helper.succeed();
		});
	}

	/** Two glass tanks four blocks deep: the sponge golem comes up to the top of its own, the miner stays at the bottom. */
	@GameTest(maxTicks = 300)
	public void spongeGolemFloatsAndCurrentsPushNeitherItNorMiners(final GameTestHelper helper) {
		floor(helper, Blocks.SMOOTH_STONE);
		for (BlockPos tank : List.of(new BlockPos(2, 1, 2), new BlockPos(5, 1, 5))) {
			for (int y = 0; y < 4; y++) {
				for (Direction direction : Direction.Plane.HORIZONTAL) {
					helper.setBlock(tank.above(y).relative(direction), Blocks.GLASS);
				}

				helper.setBlock(tank.above(y), Blocks.WATER);
			}
		}

		SpongeGolem sponge = spawn(helper, ModEntities.SPONGE_GOLEM, new BlockPos(2, 1, 2));
		MiningGolem miner = spawn(helper, ModEntities.IRON_MINING_GOLEM, new BlockPos(5, 1, 5));
		CoalGolem other = spawn(helper, ModEntities.COAL_GOLEM, new BlockPos(1, 1, 6));
		helper.assertTrue(!sponge.isPushedByFluid() && !miner.isPushedByFluid(), "currents must not push the sponge golem or the miners");
		helper.assertTrue(other.isPushedByFluid(), "the other golems are carried along as before");
		int bottom = helper.absolutePos(new BlockPos(0, 1, 0)).getY();
		helper.runAtTickTime(200, () -> {
			helper.assertTrue(sponge.getY() > bottom + 2.5, "the sponge golem should have floated up, but is at " + (sponge.getY() - bottom));
			helper.assertTrue(miner.getY() < bottom + 0.5, "the miner should be standing on the bottom, but is at " + (miner.getY() - bottom));
			helper.succeed();
		});
	}

	/** One sponge, two blocks of water, a campfire against the sponge chest and two pieces of coal in it. */
	@GameTest(maxTicks = 2400)
	public void spongeGolemDriesItsSpongesAtCampfireByChest(final GameTestHelper helper) {
		floor(helper, Blocks.SMOOTH_STONE);
		helper.setBlock(CHEST, ModBlocks.SPONGE_CHEST);
		helper.setBlock(CHEST.north(), Blocks.CAMPFIRE);
		helper.setBlock(5, 0, 5, Blocks.WATER);
		helper.setBlock(5, 0, 6, Blocks.WATER);
		container(helper, CHEST).setItem(0, new ItemStack(Items.SPONGE, 1));
		container(helper, CHEST).setItem(1, new ItemStack(Items.COAL, 2));
		SpongeGolem golem = spawn(helper, ModEntities.SPONGE_GOLEM, new BlockPos(1, 1, 4));
		golem.setChestPos(helper.absolutePos(CHEST));
		FarmGolemTests.give(helper, golem, Items.BLUE_ORCHID);
		long[] soakedAt = {-1};
		helper.onEachTick(() -> {
			if (soakedAt[0] < 0 && golem.countWetSponges() > 0) {
				soakedAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(soakedAt[0] >= 0, "it should have soaked up the water");
			helper.assertTrue(golem.countDrySponges() == 1 && golem.countWetSponges() == 0, "its sponge should be dry again");
			helper.assertTrue(helper.getTick() - soakedAt[0] >= SpongeGolem.DRYING_TICKS, "drying takes thirty seconds by the fire");
			Container chest = container(helper, CHEST);
			helper.assertTrue(chest.countItem(Items.WET_SPONGE) == 0, "with a fire it does not leave wet sponges in the chest");
			helper.assertTrue(chest.countItem(Items.COAL) == 1, "one drying burns one piece of coal");
			helper.assertTrue(!golem.isActive(), "with no water left it should be idle");
		});
	}

	/** The same, but with no coal in the chest: the fire is no use, and the wet sponge is left for the coal golem. */
	@GameTest(maxTicks = 1200)
	public void spongeGolemCannotUseCampfireWithoutCoal(final GameTestHelper helper) {
		floor(helper, Blocks.SMOOTH_STONE);
		helper.setBlock(CHEST, ModBlocks.SPONGE_CHEST);
		helper.setBlock(CHEST.north(), Blocks.CAMPFIRE);
		helper.setBlock(5, 0, 5, Blocks.WATER);
		helper.setBlock(5, 0, 6, Blocks.WATER);
		container(helper, CHEST).setItem(0, new ItemStack(Items.SPONGE, 1));
		SpongeGolem golem = spawn(helper, ModEntities.SPONGE_GOLEM, new BlockPos(1, 1, 4));
		golem.setChestPos(helper.absolutePos(CHEST));
		FarmGolemTests.give(helper, golem, Items.BLUE_ORCHID);
		helper.succeedWhen(() -> {
			helper.assertTrue(container(helper, CHEST).countItem(Items.WET_SPONGE) == 1, "without coal the wet sponge goes in the chest");
			helper.assertTrue(golem.countDrySponges() == 0 && !golem.isActive(), "and it has nothing left to do");
		});
	}

	/** A sponge chest with a campfire against it is kept in coal out of the miner's chest; one without gets none. */
	@GameTest(maxTicks = 2000)
	public void coalGolemKeepsSpongeChestWithCampfireInCoal(final GameTestHelper helper) {
		floor(helper, Blocks.SMOOTH_STONE);
		BlockPos ironChest = new BlockPos(0, 1, 1);
		BlockPos withFire = new BlockPos(0, 1, 5);
		BlockPos withoutFire = new BlockPos(6, 1, 1);
		helper.setBlock(ironChest, ModBlocks.IRON_CHEST);
		helper.setBlock(withFire, ModBlocks.SPONGE_CHEST);
		helper.setBlock(withFire.south(), Blocks.CAMPFIRE);
		helper.setBlock(withoutFire, ModBlocks.SPONGE_CHEST);
		container(helper, ironChest).setItem(0, new ItemStack(Items.COAL, 20));
		CoalGolem golem = spawn(helper, ModEntities.COAL_GOLEM, new BlockPos(2, 1, 3));
		FarmGolemTests.give(helper, golem, Items.OXEYE_DAISY);
		boolean[] tookBack = {false};
		helper.onEachTick(() -> tookBack[0] |= container(helper, withFire).countItem(Items.COAL) > SpongeGolem.CAMPFIRE_COAL_KEPT);
		helper.succeedWhen(() -> {
			int coal = container(helper, withFire).countItem(Items.COAL);
			helper.assertTrue(coal == SpongeGolem.CAMPFIRE_COAL_KEPT, "the chest by the fire should hold 8 coal, not " + coal);
			helper.assertTrue(container(helper, withoutFire).isEmpty(), "a sponge chest with no fire gets no coal");
			helper.assertTrue(!tookBack[0], "it never puts in more than the fire's share");
		});
	}

	// ------------------------------------------------------------------------------------ coal

	/**
	 * A miner's chest, a sponge chest and, in the fence of a small pen with a cow and a pig, a bone
	 * chest. Everything goes to the copper chest, the potatoes by way of the furnace, except what the
	 * owner of each chest needs.
	 */
	@GameTest(maxTicks = 4000)
	public void coalGolemCartsEverythingAwayExceptWhatOwnersNeed(final GameTestHelper helper) {
		floor(helper, Blocks.SMOOTH_STONE);
		BlockPos ironChest = new BlockPos(0, 1, 1);
		BlockPos spongeChest = new BlockPos(0, 1, 3);
		BlockPos boneChest = new BlockPos(4, 1, 5);
		BlockPos copperChest = new BlockPos(0, 1, 6);
		BlockPos furnace = new BlockPos(2, 1, 0);
		for (int i = 4; i <= 7; i++) {
			for (BlockPos post : List.of(new BlockPos(i, 1, 4), new BlockPos(i, 1, 7), new BlockPos(4, 1, i), new BlockPos(7, 1, i))) {
				helper.setBlock(post, Blocks.OAK_FENCE);
			}
		}

		helper.setBlock(ironChest, ModBlocks.IRON_CHEST);
		helper.setBlock(spongeChest, ModBlocks.SPONGE_CHEST);
		helper.setBlock(boneChest, ModBlocks.BONE_CHEST);
		helper.setBlock(copperChest, Blocks.COPPER_CHEST.weathering().unaffected());
		helper.setBlock(furnace, Blocks.FURNACE);
		fill(container(helper, ironChest), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 36), new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.COAL, 1));
		fill(container(helper, spongeChest), new ItemStack(Items.SPONGE, 2));
		fill(container(helper, boneChest), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.WHEAT, 5), new ItemStack(Items.CARROT, 3), new ItemStack(Items.POTATO, 2), new ItemStack(Items.FEATHER, 4));
		// The rancher is not at work, but the chest is still its own and the animals around it are still its herd.
		BoneGolem rancher = spawn(helper, ModEntities.BONE_GOLEM, new BlockPos(6, 1, 6));
		rancher.setChestPos(helper.absolutePos(boneChest));
		helper.spawn(EntityTypes.COW, 5, 1, 5);
		helper.spawn(EntityTypes.PIG, 5, 1, 6);
		CoalGolem golem = spawn(helper, ModEntities.COAL_GOLEM, new BlockPos(2, 1, 3));
		FarmGolemTests.give(helper, golem, Items.OXEYE_DAISY);
		helper.succeedWhen(() -> {
			Container copper = container(helper, copperChest);
			helper.assertTrue(copper.countItem(Items.DIAMOND) == 3 && copper.countItem(Items.FEATHER) == 4, "what cannot be smelted goes to the copper chest");
			helper.assertTrue(copper.countItem(Items.BAKED_POTATO) == 2, "potatoes are baked on the way, even though the pig eats them");
			helper.assertTrue(copper.countItem(Items.COBBLESTONE) == 36, "expected the 36 blocks over a stack, found " + copper.countItem(Items.COBBLESTONE));
			Container miners = container(helper, ironChest);
			helper.assertTrue(miners.countItem(Items.IRON_PICKAXE) == 1, "the miner's pickaxe stays");
			helper.assertTrue(miners.countItem(Items.COBBLESTONE) == 64, "the miner keeps a stack of blocks for laying floor");
			helper.assertTrue(container(helper, spongeChest).countItem(Items.SPONGE) == 2, "dry sponges stay in the sponge chest");
			Container bones = container(helper, boneChest);
			helper.assertTrue(bones.countItem(Items.IRON_SWORD) == 1, "the rancher's sword stays");
			helper.assertTrue(bones.countItem(Items.WHEAT) == 5 && bones.countItem(Items.CARROT) == 3, "what the cow and the pig eat stays");
			helper.assertTrue(bones.countItem(Items.POTATO) == 0 && bones.countItem(Items.FEATHER) == 0, "the rest of the bone chest goes");
		});
	}

	// ------------------------------------------------------------------------------------ copper golem

	/**
	 * A vanilla copper golem with iron ingots to put away. The nearer chest has a frame with a gold ingot
	 * on it, the further one a frame with an iron ingot: only the second may take them.
	 */
	@GameTest(maxTicks = 3000)
	public void copperGolemOnlyFillsChestsWhoseItemFramesMatch(final GameTestHelper helper) {
		floor(helper, Blocks.SMOOTH_STONE);
		BlockPos source = new BlockPos(1, 1, 1);
		BlockPos wrong = new BlockPos(4, 1, 1);
		BlockPos right = new BlockPos(4, 1, 6);
		helper.setBlock(source, Blocks.COPPER_CHEST.weathering().unaffected());
		helper.setBlock(wrong, Blocks.CHEST);
		helper.setBlock(right, Blocks.CHEST);
		container(helper, source).setItem(0, new ItemStack(Items.IRON_INGOT, 4));
		frame(helper, wrong, Items.GOLD_INGOT);
		frame(helper, right, Items.IRON_INGOT);
		helper.spawn(EntityTypes.COPPER_GOLEM, 2, 1, 2);
		boolean[] misplaced = {false};
		helper.onEachTick(() -> misplaced[0] |= !container(helper, wrong).isEmpty());
		helper.succeedWhen(() -> {
			helper.assertTrue(container(helper, right).countItem(Items.IRON_INGOT) == 4, "the ingots belong in the chest with the iron ingot on it");
			helper.assertTrue(!misplaced[0], "nothing may go in the chest marked for gold");
			helper.killAllEntitiesOfClass(ItemFrame.class);
		});
	}

	/**
	 * Both chests already hold gold. The nearer one has no frame, so the vanilla rule keeps the iron out of
	 * it; the further one is marked for iron, and the frame wins over what is inside.
	 */
	@GameTest(maxTicks = 3000)
	public void copperGolemFillsMarkedChestWhateverIsAlreadyInIt(final GameTestHelper helper) {
		floor(helper, Blocks.SMOOTH_STONE);
		BlockPos source = new BlockPos(1, 1, 1);
		BlockPos unmarked = new BlockPos(4, 1, 1);
		BlockPos marked = new BlockPos(4, 1, 6);
		helper.setBlock(source, Blocks.COPPER_CHEST.weathering().unaffected());
		helper.setBlock(unmarked, Blocks.CHEST);
		helper.setBlock(marked, Blocks.CHEST);
		container(helper, source).setItem(0, new ItemStack(Items.IRON_INGOT, 4));
		container(helper, unmarked).setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
		container(helper, marked).setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
		frame(helper, marked, Items.IRON_INGOT);
		helper.spawn(EntityTypes.COPPER_GOLEM, 2, 1, 2);
		helper.succeedWhen(() -> {
			Container chest = container(helper, marked);
			helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 4, "the chest marked for iron should get the ingots even with gold in it");
			helper.assertTrue(chest.countItem(Items.GOLD_INGOT) == 5, "and the gold should still be there");
			helper.assertTrue(container(helper, unmarked).countItem(Items.IRON_INGOT) == 0, "a chest without frames keeps the vanilla rule");
			helper.killAllEntitiesOfClass(ItemFrame.class);
		});
	}

	// ------------------------------------------------------------------------------------ amethyst

	/**
	 * A slab across two chunks with a tree on each, built in the air where chunk borders are known.
	 * The golem fells the one in its own chunk and stops; with a spyglass it goes on to the other.
	 */
	@GameTest(maxTicks = 4000, padding = 40)
	public void amethystGolemWorksOneChunkUntilGivenSpyglass(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO).above(24);
		ChunkPos home = ChunkPos.containing(origin);
		int ground = origin.getY();
		for (int chunkX = home.x(); chunkX <= home.x() + 1; chunkX++) {
			level.setChunkForced(chunkX, home.z(), true);
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					level.setBlockAndUpdate(new BlockPos((chunkX << 4) + x, ground, home.getMinBlockZ() + z), Blocks.DIRT.defaultBlockState());
				}
			}
		}

		BlockPos near = new BlockPos(home.getMinBlockX() + 9, ground + 1, home.getMinBlockZ() + 8);
		BlockPos far = new BlockPos(home.getMinBlockX() + 16 + 3, ground + 1, home.getMinBlockZ() + 8);
		tree(level, near, 4);
		tree(level, far, 4);
		BlockPos chestPos = new BlockPos(home.getMinBlockX() + 5, ground + 1, home.getMinBlockZ() + 5);
		level.setBlockAndUpdate(chestPos, ModBlocks.AMETHYST_CHEST.defaultBlockState());
		LumberGolem golem = ModEntities.AMETHYST_GOLEM.create(level, EntitySpawnReason.COMMAND);
		golem.snapTo(Vec3.atBottomCenterOf(chestPos.east()));
		level.addFreshEntity(golem);
		golem.setChestPos(chestPos);
		golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		give(helper, golem, new ItemStack(Items.LILY_OF_THE_VALLEY), Direction.EAST);
		boolean[] upgraded = {false};
		helper.succeedWhen(() -> {
			helper.assertTrue(!level.getBlockState(near).is(Blocks.OAK_LOG), "the tree in its own chunk should be felled");
			if (!upgraded[0]) {
				helper.assertTrue(golem.isResting(), "with its chunk cleared it should be resting");
				helper.assertTrue(level.getBlockState(far).is(Blocks.OAK_LOG), "the tree in the next chunk is none of its business yet");
				helper.assertTrue(!golem.isUpgraded() && golem.getEyesTextureId().equals("amethyst"), "it starts with its ordinary eyes");
				Player player = give(helper, golem, new ItemStack(Items.SPYGLASS), Direction.EAST);
				helper.assertTrue(golem.isUpgraded() && player.getMainHandItem().isEmpty(), "the spyglass is used up upgrading it");
				helper.assertTrue(golem.getEyesTextureId().equals("amethyst_upgraded"), "its eyes change colour");
				upgraded[0] = true;
			}

			helper.assertTrue(!level.getBlockState(far).is(Blocks.OAK_LOG), "upgraded, it should fell the tree in the next chunk too");
			golem.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
			List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, golem.getBoundingBox().inflate(4.0), item -> item.getItem().is(Items.SPYGLASS));
			helper.assertTrue(!dropped.isEmpty(), "it should drop its spyglass when it dies");
			level.getEntitiesOfClass(ItemEntity.class, golem.getBoundingBox().inflate(8.0)).forEach(ItemEntity::discard);
			level.setChunkForced(home.x(), home.z(), false);
			level.setChunkForced(home.x() + 1, home.z(), false);
		});
	}

	// ------------------------------------------------------------------------------------ helpers

	/** A mining golem on a stone floor next to its chest at {@code chest}, holding {@code pickaxe} if there is one. */
	private static MiningGolem miner(final GameTestHelper helper, final GolemTier tier, final BlockPos chest, final ItemStack pickaxe) {
		floor(helper, Blocks.SMOOTH_STONE);
		helper.setBlock(chest, tier.chestBlock());
		MiningGolem golem = spawn(helper, tier.entityType(), chest.east());
		golem.setChestPos(helper.absolutePos(chest));
		golem.setItemSlot(EquipmentSlot.MAINHAND, pickaxe);
		return golem;
	}

	private static void fill(final Container chest, final ItemStack... stacks) {
		for (int slot = 0; slot < stacks.length; slot++) {
			chest.setItem(slot, stacks[slot]);
		}
	}

	/** An oak of {@code height} logs standing on the block below {@code base}, with leaves that grew on it. */
	private static void tree(final ServerLevel level, final BlockPos base, final int height) {
		for (int y = 0; y < height; y++) {
			level.setBlockAndUpdate(base.above(y), Blocks.OAK_LOG.defaultBlockState());
		}

		for (BlockPos leaf : List.of(base.above(height), base.above(height - 1).north(), base.above(height - 1).south())) {
			level.setBlockAndUpdate(leaf, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
		}
	}

	/** Hangs an item frame showing {@code item} on top of the chest at {@code chest}. */
	private static void frame(final GameTestHelper helper, final BlockPos chest, final Item item) {
		ItemFrame frame = new ItemFrame(helper.getLevel(), helper.absolutePos(chest.above()), Direction.UP);
		frame.setItem(new ItemStack(item), false);
		helper.getLevel().addFreshEntity(frame);
	}
}
