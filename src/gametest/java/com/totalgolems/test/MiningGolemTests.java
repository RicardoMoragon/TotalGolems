package com.totalgolems.test;

import com.totalgolems.entity.CoalGolem;
import com.totalgolems.entity.LumberGolem;
import com.totalgolems.entity.MiningGolem;
import com.totalgolems.entity.QuarryPlan;
import com.totalgolems.entity.StripPlan;
import com.totalgolems.entity.WorkerGolem;
import com.totalgolems.GolemTier;
import com.totalgolems.registry.ModBlocks;
import com.totalgolems.registry.ModEntities;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class MiningGolemTests {
	private static final BlockPos CHEST = new BlockPos(0, 1, 3);
	/** Ten blocks with ten different drops, two per tunnel slice, so a nine slot golem overflows on the last. */
	private static final List<Block> MIXED_WALL = List.of(
		Blocks.STONE,
		Blocks.DIRT,
		Blocks.GRANITE,
		Blocks.DIORITE,
		Blocks.ANDESITE,
		Blocks.SAND,
		Blocks.COBBLED_DEEPSLATE,
		Blocks.NETHERRACK,
		Blocks.OAK_PLANKS,
		Blocks.END_STONE
	);

	@GameTest
	public void pumpkinOnIronBlockBuildsGolemAndChest(final GameTestHelper helper) {
		helper.setBlock(3, 1, 3, Blocks.IRON_BLOCK);
		helper.setBlock(3, 2, 3, Blocks.CARVED_PUMPKIN);
		helper.assertBlockPresent(ModBlocks.IRON_CHEST, 3, 1, 3);
		helper.assertBlockPresent(Blocks.AIR, 3, 2, 3);
		helper.assertEntityPresent(ModEntities.IRON_MINING_GOLEM);
		helper.succeed();
	}

	@GameTest
	public void pumpkinOnDiamondBlockBuildsGolemAndChest(final GameTestHelper helper) {
		helper.setBlock(3, 1, 3, Blocks.DIAMOND_BLOCK);
		helper.setBlock(3, 2, 3, Blocks.JACK_O_LANTERN);
		helper.assertBlockPresent(ModBlocks.DIAMOND_CHEST, 3, 1, 3);
		helper.assertEntityPresent(ModEntities.DIAMOND_MINING_GOLEM);
		helper.succeed();
	}

	@GameTest
	public void pumpkinOnNetheriteBlockBuildsGolemAndChest(final GameTestHelper helper) {
		helper.setBlock(3, 1, 3, Blocks.NETHERITE_BLOCK);
		helper.setBlock(3, 2, 3, Blocks.CARVED_PUMPKIN);
		helper.assertBlockPresent(ModBlocks.NETHERITE_CHEST, 3, 1, 3);
		helper.assertEntityPresent(ModEntities.NETHERITE_MINING_GOLEM);
		helper.succeed();
	}

	@GameTest
	public void pumpkinOnAmethystBlockBuildsGolemAndChest(final GameTestHelper helper) {
		helper.setBlock(3, 1, 3, Blocks.AMETHYST_BLOCK);
		helper.setBlock(3, 2, 3, Blocks.CARVED_PUMPKIN);
		helper.assertBlockPresent(ModBlocks.AMETHYST_CHEST, 3, 1, 3);
		helper.assertEntityPresent(ModEntities.AMETHYST_GOLEM);
		helper.succeed();
	}

	@GameTest
	public void pumpkinOnCoalBlockBuildsGolemWithoutChest(final GameTestHelper helper) {
		helper.setBlock(3, 1, 3, Blocks.COAL_BLOCK);
		helper.setBlock(3, 2, 3, Blocks.CARVED_PUMPKIN);
		helper.assertBlockPresent(Blocks.AIR, 3, 1, 3);
		helper.assertBlockPresent(Blocks.AIR, 3, 2, 3);
		helper.assertEntityPresent(ModEntities.COAL_GOLEM);
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void eyesAreLitOnlyWhileWorking(final GameTestHelper helper) {
		MiningGolem golem = setUp(helper, ModEntities.IRON_MINING_GOLEM, ModBlocks.IRON_CHEST);
		helper.setBlock(2, 1, 3, Blocks.STONE);
		helper.setBlock(3, 1, 3, Blocks.STONE);
		stopper(helper, 4);
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(!golem.isActive(), "a golem without a flower has nothing to do");
			walls(helper, 3, 0, 7);
			give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		});
		boolean[] wasActive = {false};
		helper.onEachTick(() -> wasActive[0] |= golem.isActive());
		helper.succeedWhen(() -> {
			helper.assertTrue(wasActive[0], "eyes should have been lit while digging");
			helper.assertTrue(golem.isHalted() && !golem.isActive(), "eyes should go out once it is back home with nothing to do");
		});
	}

	/**
	 * A golem chest with raw iron, too little coal and some cobblestone; two furnaces; a copper chest.
	 * Thirty-two raw iron is sixteen per furnace, which wants two coal each, but there are only two in all.
	 */
	@GameTest(maxTicks = 4000, padding = 16)
	public void coalGolemFeedsFurnacesSharesCoalAndStoresIngots(final GameTestHelper helper) {
		CoalGolem golem = setUp(helper, ModEntities.COAL_GOLEM, ModBlocks.IRON_CHEST);
		Container source = chest(helper);
		source.setItem(0, new ItemStack(Items.RAW_IRON, 32));
		source.setItem(1, new ItemStack(Items.COAL, 2));
		source.setItem(2, new ItemStack(Items.COBBLESTONE, 10));
		BlockPos firstFurnace = new BlockPos(3, 1, 1);
		BlockPos secondFurnace = new BlockPos(5, 1, 1);
		BlockPos copperChest = new BlockPos(6, 1, 5);
		helper.setBlock(firstFurnace, Blocks.FURNACE);
		helper.setBlock(secondFurnace, Blocks.FURNACE);
		helper.setBlock(copperChest, Blocks.COPPER_CHEST.weathering().unaffected());
		give(helper, golem, new ItemStack(Items.OXEYE_DAISY), Direction.EAST);

		boolean[] fuelled = {false, false};
		helper.onEachTick(() -> {
			// A furnace burns its coal the moment it has something to smelt, so look at the fire rather than the slot.
			fuelled[0] |= helper.getBlockState(firstFurnace).getValue(AbstractFurnaceBlock.LIT);
			fuelled[1] |= helper.getBlockState(secondFurnace).getValue(AbstractFurnaceBlock.LIT);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(fuelled[0] && fuelled[1], "the two pieces of coal should have been split between the furnaces");
			helper.assertContainerContains(copperChest, Items.IRON_INGOT);
			// One coal smelts eight, so sixteen ingots come out in all and the rest waits for more coal.
			Container copper = (Container)helper.getLevel().getBlockEntity(helper.absolutePos(copperChest));
			helper.assertTrue(copper.countItem(Items.IRON_INGOT) == 16, "expected 16 ingots in the copper chest, found " + copper.countItem(Items.IRON_INGOT));
			helper.assertTrue(source.countItem(Items.COBBLESTONE) == 10, "stone must be left in the golem chest");
			helper.assertTrue(source.countItem(Items.RAW_IRON) == 0, "all the raw iron should have been taken");
			helper.assertTrue(!golem.isActive(), "with nothing left to do its eyes should be off");
		});
	}

	/**
	 * A ten-log oak, far taller than it can reach. It fetches blocks from its chest, builds a pillar beside
	 * the trunk to get at the top, takes it down again when the tree is felled and keeps the blocks.
	 */
	@GameTest(maxTicks = 4000)
	public void amethystGolemFellsTallTreeReplantsAndStoresWood(final GameTestHelper helper) {
		LumberGolem golem = setUp(helper, ModEntities.AMETHYST_GOLEM, ModBlocks.AMETHYST_CHEST);
		chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 20));
		int height = 10;
		helper.setBlock(4, 0, 4, Blocks.DIRT);
		for (int y = 1; y <= height; y++) {
			helper.setBlock(4, y, 4, Blocks.OAK_LOG);
		}

		// With a spyglass it works the chunks around as well, wherever the test happens to sit among them.
		give(helper, golem, new ItemStack(Items.SPYGLASS), Direction.EAST);
		// Leaves as they are on a grown tree, so the golem takes it for one and they do not decay mid-test.
		for (BlockPos leaf : List.of(new BlockPos(4, height + 1, 4), new BlockPos(3, height, 4), new BlockPos(5, height, 4))) {
			helper.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
		}

		// A log cabin wall: logs with no leaves are not a tree.
		helper.setBlock(6, 0, 6, Blocks.DIRT);
		helper.setBlock(6, 1, 6, Blocks.SPRUCE_LOG);
		// It picks saplings up off the ground, and nothing else.
		helper.spawnItem(Items.OAK_SAPLING, new BlockPos(2, 1, 3));
		helper.spawnItem(Items.DIAMOND, new BlockPos(2, 1, 4));
		give(helper, golem, new ItemStack(Items.LILY_OF_THE_VALLEY), Direction.EAST);
		int[] tallestPillar = {0};
		helper.onEachTick(() -> tallestPillar[0] = Math.max(tallestPillar[0], golem.getPillarHeight()));
		helper.succeedWhen(() -> {
			for (int y = 2; y <= height; y++) {
				helper.assertBlockPresent(Blocks.AIR, 4, y, 4);
			}

			helper.assertTrue(tallestPillar[0] >= 3, "it should have climbed a pillar to reach the top logs, not " + tallestPillar[0]);
			helper.assertTrue(golem.getPillarHeight() == 0, "the pillar should be taken down");
			for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(2, 1, 2), new BlockPos(6, height, 6))) {
				helper.assertBlockNotPresent(Blocks.COBBLESTONE, pos);
			}

			helper.assertTrue(golem.getClimbingBlockCount() + chest(helper).countItem(Items.COBBLESTONE) == 20, "it should keep every block it climbed with");
			helper.assertTrue(golem.getClimbingBlockCount() > 0, "it should have taken blocks to climb with");

			helper.assertBlockPresent(Blocks.OAK_SAPLING, 4, 1, 4);
			helper.assertBlockPresent(Blocks.SPRUCE_LOG, 6, 1, 6);
			helper.assertTrue(golem.getSaplingCount(Items.OAK_SAPLING) == 0, "the one sapling it found should have been planted");
			helper.assertItemEntityPresent(Items.DIAMOND);
			// With no trees left it goes home, unloads and rests there.
			helper.assertTrue(chest(helper).countItem(Items.OAK_LOG) == height, "all " + height + " logs should be in the chest");
			helper.assertTrue(golem.getTool().getDamageValue() == height, "the axe should be worn by one for every log, not " + golem.getTool().getDamageValue());
			helper.assertTrue(golem.isResting() && !golem.isActive(), "golem should be resting with its eyes off");
		});
	}

	/** The same tall oak with no blocks to climb with: it climbs with the logs it fells from the ground, and fells it all. */
	@GameTest(maxTicks = 3000)
	public void amethystGolemWithoutBlocksClimbsWithTheLogs(final GameTestHelper helper) {
		LumberGolem golem = setUp(helper, ModEntities.AMETHYST_GOLEM, ModBlocks.AMETHYST_CHEST);
		int height = 10;
		helper.setBlock(4, 0, 4, Blocks.DIRT);
		for (int y = 1; y <= height; y++) {
			helper.setBlock(4, y, 4, Blocks.OAK_LOG);
		}

		give(helper, golem, new ItemStack(Items.SPYGLASS), Direction.EAST);
		for (BlockPos leaf : List.of(new BlockPos(4, height + 1, 4), new BlockPos(3, height, 4), new BlockPos(5, height, 4))) {
			helper.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
		}

		give(helper, golem, new ItemStack(Items.LILY_OF_THE_VALLEY), Direction.EAST);
		double[] highest = {0.0};
		helper.onEachTick(() -> highest[0] = Math.max(highest[0], golem.getY() - helper.absolutePos(BlockPos.ZERO).getY()));
		helper.succeedWhen(() -> {
			for (int y = 1; y <= height; y++) {
				helper.assertBlockNotPresent(Blocks.OAK_LOG, 4, y, 4);
			}

			helper.assertTrue(highest[0] > 3.0, "it should have climbed on the logs");
			helper.assertTrue(golem.getPillarHeight() == 0, "and come down");
			helper.assertTrue(golem.isResting() && !golem.isActive(), "then rest");
			helper.assertTrue(chest(helper).countItem(Items.OAK_LOG) == height, "with every log in the chest");
		});
	}

	/**
	 * The coal golem's nearest copper chest is walled in where it cannot walk. Rather than teleport in, it
	 * gives that one up and takes the sticks to the other copper chest, further off.
	 */
	@GameTest(maxTicks = 2000, padding = 16)
	public void coalGolemGivesUpOnChestItCannotWalkTo(final GameTestHelper helper) {
		for (int x = 0; x <= 11; x++) {
			for (int z = 0; z <= 11; z++) {
				helper.setBlock(x, 0, z, Blocks.SMOOTH_STONE);
			}
		}

		CoalGolem golem = setUp(helper, ModEntities.COAL_GOLEM, ModBlocks.IRON_CHEST);
		chest(helper).setItem(0, new ItemStack(Items.STICK, 5));
		BlockPos walledIn = new BlockPos(6, 1, 3);
		BlockPos open = new BlockPos(2, 1, 9);
		helper.setBlock(walledIn, Blocks.COPPER_CHEST.weathering().unaffected());
		helper.setBlock(open, Blocks.COPPER_CHEST.weathering().unaffected());
		for (int x = 4; x <= 8; x++) {
			for (int z = 1; z <= 5; z++) {
				if (x == 4 || x == 8 || z == 1 || z == 5) {
					for (int y = 1; y <= 3; y++) {
						helper.setBlock(x, y, z, Blocks.GLASS);
					}
				}
			}
		}

		give(helper, golem, new ItemStack(Items.OXEYE_DAISY), Direction.EAST);
		Vec3[] last = {golem.position()};
		double[] longestStep = {0.0};
		helper.onEachTick(() -> {
			longestStep[0] = Math.max(longestStep[0], golem.position().distanceTo(last[0]));
			last[0] = golem.position();
		});
		helper.succeedWhen(() -> {
			Container openChest = (Container)helper.getLevel().getBlockEntity(helper.absolutePos(open));
			Container walledInChest = (Container)helper.getLevel().getBlockEntity(helper.absolutePos(walledIn));
			helper.assertTrue(openChest.countItem(Items.STICK) == 5, "the sticks should go to the chest it can walk to");
			helper.assertTrue(walledInChest.isEmpty(), "nothing should get into the walled-in chest");
			helper.assertTrue(golem.isUnreachable(helper.absolutePos(walledIn)), "it should remember it cannot get to the walled-in chest");
			helper.assertTrue(longestStep[0] < 1.0, "it should never teleport, but it moved " + longestStep[0] + " in one tick");
			helper.assertTrue(!golem.isActive(), "with nothing left to do its eyes should be off");
		});
	}

	@GameTest
	public void pumpkinOnOtherBlocksDoesNothing(final GameTestHelper helper) {
		helper.setBlock(3, 1, 3, Blocks.GOLD_BLOCK);
		helper.setBlock(3, 2, 3, Blocks.CARVED_PUMPKIN);
		helper.assertBlockPresent(Blocks.GOLD_BLOCK, 3, 1, 3);
		helper.assertBlockPresent(Blocks.CARVED_PUMPKIN, 3, 2, 3);
		helper.succeed();
	}

	@GameTest
	public void onlyPatternFlowersAreAccepted(final GameTestHelper helper) {
		MiningGolem golem = setUp(helper, ModEntities.IRON_MINING_GOLEM, ModBlocks.IRON_CHEST);
		give(helper, golem, new ItemStack(Items.BLUE_ORCHID), Direction.EAST);
		helper.assertTrue(!golem.hasFlower(), "an orchid has no pattern and should be refused");
		give(helper, golem, new ItemStack(Items.DANDELION), Direction.EAST);
		helper.assertTrue(golem.getItemBySlot(MiningGolem.FLOWER_SLOT).is(Items.DANDELION), "dandelion should be worn");
		Player player = give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.assertTrue(golem.getItemBySlot(MiningGolem.FLOWER_SLOT).is(Items.POPPY), "poppy should replace the dandelion");
		helper.assertTrue(player.getInventory().contains(new ItemStack(Items.DANDELION)), "the old flower should be handed back");
		golem.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void poppyDigsTwoHighUnloadsWhenFullAndGoesHomeAtBedrock(final GameTestHelper helper) {
		MiningGolem golem = setUp(helper, ModEntities.IRON_MINING_GOLEM, ModBlocks.IRON_CHEST);
		buildMixedWall(helper);
		// Blocks beside and above the tunnel must be left alone.
		helper.setBlock(2, 1, 2, Blocks.STONE);
		helper.setBlock(2, 3, 3, Blocks.STONE);
		walls(helper, 3, 0, 7);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.assertTrue(golem.hasFlower(), "golem should wear the flower");

		boolean[] flowerTaken = {false};
		helper.succeedWhen(() -> {
			for (int x = 2; x <= 6; x++) {
				helper.assertBlockPresent(Blocks.AIR, x, 1, 3);
				helper.assertBlockPresent(Blocks.AIR, x, 2, 3);
			}

			helper.assertBlockPresent(Blocks.STONE, 2, 1, 2);
			helper.assertBlockPresent(Blocks.STONE, 2, 3, 3);
			// The tenth kind of drop did not fit in nine slots, so the first ones were carried home before the end.
			helper.assertContainerContains(CHEST, Items.DIRT);
			// The bedrock ends the tunnel: the golem walks home by itself with the rest and waits there.
			helper.assertContainerContains(CHEST, Items.END_STONE);
			helper.assertTrue(golem.isHalted(), "golem should have given up at the bedrock");
			helper.assertTrue(golem.position().distanceTo(Vec3.atCenterOf(helper.absolutePos(CHEST))) < 3.0, "golem should be back at its chest");
			if (!flowerTaken[0]) {
				flowerTaken[0] = true;
				Player player = give(helper, golem, ItemStack.EMPTY, Direction.EAST);
				helper.assertTrue(!golem.hasFlower(), "empty hand should take the flower");
				helper.assertTrue(player.getMainHandItem().is(Items.POPPY), "flower should go back to the player");
			}
		});
	}

	@GameTest(maxTicks = 1200)
	public void diamondGolemDigsThreeByThree(final GameTestHelper helper) {
		MiningGolem golem = setUp(helper, ModEntities.DIAMOND_MINING_GOLEM, ModBlocks.DIAMOND_CHEST);
		for (int x = 2; x <= 3; x++) {
			for (int z = 1; z <= 5; z++) {
				for (int y = 1; y <= 4; y++) {
					helper.setBlock(x, y, z, Blocks.STONE);
				}
			}
		}

		stopper(helper, 4);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.succeedWhen(() -> {
			for (int x = 2; x <= 3; x++) {
				for (int z = 1; z <= 5; z++) {
					for (int y = 1; y <= 4; y++) {
						boolean inTunnel = z >= 2 && z <= 4 && y <= 3;
						helper.assertBlockPresent(inTunnel ? Blocks.AIR : Blocks.STONE, x, y, z);
					}
				}
			}
		});
	}

	@GameTest(maxTicks = 400)
	public void netheriteGolemBreaksThreeBlocksAtOnce(final GameTestHelper helper) {
		MiningGolem golem = setUp(helper, ModEntities.NETHERITE_MINING_GOLEM, ModBlocks.NETHERITE_CHEST);
		for (int z = 2; z <= 4; z++) {
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(2, y, z, Blocks.STONE);
			}
		}

		stopper(helper, 3);
		give(helper, golem, new ItemStack(MiningGolem.UNLOCK_FLOWER), Direction.EAST);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.onEachTick(() -> {
			int broken = 0;
			for (int z = 2; z <= 4; z++) {
				for (int y = 1; y <= 3; y++) {
					broken += helper.getBlockState(new BlockPos(2, y, z)).isAir() ? 1 : 0;
				}
			}

			helper.assertTrue(broken % 3 == 0, "the blocks should break three at a time, but " + broken + " were gone");
			if (broken == 9) {
				helper.succeed();
			}
		});
	}

	@GameTest(maxTicks = 700)
	public void golemStopsDiggingWhileChestIsFull(final GameTestHelper helper) {
		MiningGolem golem = setUp(helper, ModEntities.IRON_MINING_GOLEM, ModBlocks.IRON_CHEST);
		Container chest = chest(helper);
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			chest.setItem(slot, new ItemStack(Items.GOLD_BLOCK, 64));
		}

		buildMixedWall(helper);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.runAtTickTime(600, () -> {
			// It filled up on the last block, went home, and has been waiting there since: with room in the
			// chest it would have gone back, met the bedrock and given up.
			helper.assertBlockPresent(Blocks.AIR, 2, 1, 3);
			helper.assertTrue(!golem.isHalted(), "it should still be waiting to unload, not finished");
			helper.assertTrue(chest(helper).countItem(Items.GOLD_BLOCK) == 27 * 64, "nothing fits in a full chest");
			helper.assertTrue(golem.position().distanceTo(Vec3.atCenterOf(helper.absolutePos(CHEST))) < 3.0, "golem should wait at its chest");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 1200)
	public void poppyBridgesGapsAndFetchesBlocksFromChest(final GameTestHelper helper) {
		MiningGolem golem = setUp(helper, ModEntities.IRON_MINING_GOLEM, ModBlocks.IRON_CHEST);
		// One cobblestone to mine, then a pit two blocks long and three deep. The second block has to come from the chest.
		helper.setBlock(2, 1, 3, Blocks.STONE);
		digPit(helper, 3, 4);
		chest(helper).setItem(0, new ItemStack(Items.STONE_BRICKS, 10));
		stopper(helper, 6);
		walls(helper, 3, 0, 7);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.COBBLESTONE, 3, 0, 3);
			helper.assertBlockPresent(Blocks.STONE_BRICKS, 4, 0, 3);
			helper.assertTrue(golem.isHalted(), "golem should have crossed the bridge and reached the bedrock");
		});
	}

	@GameTest(maxTicks = 500)
	public void poppyWaitsAtChestWhenThereIsNothingToBuildWith(final GameTestHelper helper) {
		MiningGolem golem = setUp(helper, ModEntities.IRON_MINING_GOLEM, ModBlocks.IRON_CHEST);
		digPit(helper, 3, 4);
		stopper(helper, 6);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.runAtTickTime(400, () -> {
			helper.assertBlockPresent(Blocks.AIR, 3, 0, 3);
			helper.assertTrue(!golem.isHalted(), "waiting for blocks is not giving up");
			helper.assertTrue(golem.onGround() && Math.abs(golem.getY() - helper.absolutePos(CHEST).getY()) < 0.5, "golem must not drop into the pit");
			helper.assertTrue(golem.position().distanceTo(Vec3.atCenterOf(helper.absolutePos(CHEST))) < 3.0, "golem should wait at its chest");
			helper.succeed();
		});
	}

	/**
	 * A strip-mining golem that goes to its chest, here for a pickaxe, fills up on blocks to build with while
	 * it is there, even though it has not run out: two stacks at most, and nothing it would not build with.
	 */
	@GameTest(maxTicks = 400)
	public void minerFillsUpOnBlocksAtEveryVisitToItsChest(final GameTestHelper helper) {
		MiningGolem golem = setUp(helper, ModEntities.IRON_MINING_GOLEM, ModBlocks.IRON_CHEST);
		golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		Container chest = chest(helper);
		chest.setItem(0, new ItemStack(Items.IRON_PICKAXE));
		chest.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
		chest.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
		chest.setItem(3, new ItemStack(Items.COBBLESTONE, 40));
		chest.setItem(4, new ItemStack(Items.DIRT, 64));
		stopper(helper, 3);
		walls(helper, 3, 0, 7);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.succeedWhen(() -> {
			helper.assertTrue(golem.hasTool(), "it should have taken the pickaxe");
			helper.assertTrue(carried(golem).countItem(Items.COBBLESTONE) == MiningGolem.BLOCKS_KEPT_FOR_FLOOR, "it should carry two stacks of cobblestone, not " + carried(golem).countItem(Items.COBBLESTONE));
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 40 && chest.countItem(Items.DIRT) == 64, "the rest should be left in the chest");
		});
	}

	@GameTest(maxTicks = 1200)
	public void pickaxeEnchantmentsApplyToWhatIsDug(final GameTestHelper helper) {
		MiningGolem golem = setUp(helper, ModEntities.IRON_MINING_GOLEM, ModBlocks.IRON_CHEST);
		golem.setItemSlot(EquipmentSlot.MAINHAND, enchanted(helper, Items.IRON_PICKAXE, Enchantments.SILK_TOUCH, 1));
		helper.setBlock(2, 1, 3, Blocks.STONE);
		stopper(helper, 3);
		give(helper, golem, new ItemStack(Items.ENCHANTED_BOOK), Direction.EAST);
		helper.assertTrue(golem.getTool().is(Items.IRON_PICKAXE), "books are no longer anything to a golem");
		walls(helper, 3, 0, 7);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		// With Silk Touch stone drops as stone, not cobblestone. The golem keeps it, as a block to build with.
		helper.succeedWhen(() -> helper.assertTrue(chest(helper).countItem(Items.STONE) + carried(golem).countItem(Items.STONE) == 1, "the stone should have come out as stone"));
	}

	/**
	 * A golem with no pickaxe goes to its chest for one, and takes the shovel there too. Through dirt and
	 * gravel it digs with the shovel, so that is what wears, and the pickaxe is as good as new.
	 */
	@GameTest(maxTicks = 1200)
	public void minerTakesAShovelAndDigsDirtAndGravelWithIt(final GameTestHelper helper) {
		MiningGolem golem = setUp(helper, ModEntities.IRON_MINING_GOLEM, ModBlocks.IRON_CHEST);
		golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		chest(helper).setItem(0, new ItemStack(Items.IRON_PICKAXE));
		chest(helper).setItem(1, new ItemStack(Items.IRON_SHOVEL));
		for (int x = 2; x <= 5; x++) {
			helper.setBlock(x, 1, 3, Blocks.DIRT);
			helper.setBlock(x, 2, 3, Blocks.GRAVEL);
		}

		stopper(helper, 6);
		walls(helper, 3, 0, 7);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.succeedWhen(() -> {
			helper.assertTrue(golem.isHalted(), "it should have dug through to the bedrock");
			helper.assertTrue(golem.getShovel().is(Items.IRON_SHOVEL) && golem.getShovel().getDamageValue() > 0, "the shovel should have done the digging");
			helper.assertTrue(golem.getTool().is(Items.IRON_PICKAXE) && golem.getTool().getDamageValue() == 0, "the pickaxe should not have been used");
		});
	}

	/** A poppy's mine ends at the edge of its three by three chunks, but a vein of ore across that edge is still followed a little way. */
	@GameTest
	public void poppyFollowsOreALittleWayPastTheEdgeOfItsMine(final GameTestHelper helper) {
		StripPlan plan = StripPlan.create(new BlockPos(8, 40, 8), Direction.EAST, GolemTier.IRON);
		helper.assertTrue(!plan.contains(new BlockPos(32, 40, 8)), "the mine itself ends at the edge of the chunks around");
		helper.assertTrue(plan.reachesForOre(new BlockPos(32 + StripPlan.ORE_MARGIN - 1, 40, 8)), "ore just past the edge should still be dug");
		helper.assertTrue(!plan.reachesForOre(new BlockPos(32 + StripPlan.ORE_MARGIN, 40, 8)), "but not far beyond it");
		helper.succeed();
	}

	/**
	 * These quarry a real chunk, so they are padded well away from the other tests. The chunk gets a slab
	 * of stone with the chest and the golem on top, inside the chunk.
	 */
	@GameTest(maxTicks = 8000, padding = 24)
	public void dandelionQuarriesChunkLayerAndClimbsStairsHome(final GameTestHelper helper) {
		quarry(helper, GolemTier.IRON, 1);
	}

	@GameTest(maxTicks = 8000, padding = 24)
	public void dandelionQuarriesSecondLayerThreeWide(final GameTestHelper helper) {
		quarry(helper, GolemTier.DIAMOND, 2);
	}

	private static void quarry(final GameTestHelper helper, final GolemTier tier, final int layers) {
		ServerLevel level = helper.getLevel();
		// Built well above the test structure, clear of the blocks the test framework puts around it.
		BlockPos origin = helper.absolutePos(BlockPos.ZERO).above(16);
		int minX = origin.getX() & ~15;
		int minZ = origin.getZ() & ~15;
		// One block of floor under the layers that get dug.
		int depth = layers * tier.tunnelHeight();
		int top = origin.getY() + depth;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = origin.getY(); y <= top; y++) {
					level.setBlockAndUpdate(new BlockPos(minX + x, y, minZ + z), Blocks.STONE.defaultBlockState());
				}
			}
		}

		BlockPos chestPos = new BlockPos(minX + 5, top + 1, minZ + 5);
		BlockPos golemPos = chestPos.east();
		level.setBlockAndUpdate(chestPos, tier.chestBlock().defaultBlockState());
		// Placed in world coordinates: the helper's relative ones are not anchored to the chunk.
		level.setChunkForced(minX >> 4, minZ >> 4, true);
		MiningGolem golem = tier.entityType().create(level, EntitySpawnReason.COMMAND);
		golem.snapTo(Vec3.atBottomCenterOf(golemPos));
		level.addFreshEntity(golem);
		golem.setChestPos(chestPos);
		golem.setItemSlot(EquipmentSlot.MAINHAND, tirelessPickaxe(helper));
		if (tier.startsLocked()) {
			give(helper, golem, new ItemStack(MiningGolem.UNLOCK_FLOWER), Direction.EAST);
		}

		give(helper, golem, new ItemStack(Items.DANDELION), Direction.EAST);

		// The plan the golem made, to know which blocks it has to leave: the stairs and the walkway to the chest.
		QuarryPlan plan = QuarryPlan.create(golemPos, chestPos, tier);
		helper.succeedWhen(() -> {
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					for (int y = top - depth + 1; y <= top; y++) {
						BlockPos pos = new BlockPos(minX + x, y, minZ + z);
						boolean kept = plan.isProtected(pos);
						helper.assertTrue(level.getBlockState(pos).isAir() != kept, (kept ? "stair or walkway block dug at " : "block left at ") + pos);
					}
				}
			}

			// It has been up the stairs to unload.
			helper.assertTrue(((Container)level.getBlockEntity(chestPos)).hasAnyMatching(stack -> stack.is(Items.COBBLESTONE)), "no stone was taken to the chest");
			// Every stair step and the walkway from the top of the stairs to the chest are still there.
			for (int step = 0; step <= depth; step++) {
				helper.assertTrue(level.getBlockState(plan.stairStand(step).below()).is(Blocks.STONE), "stair step " + step + " is missing");
			}

			helper.assertTrue(level.getBlockState(chestPos.north().below()).is(Blocks.STONE), "walkway to the chest is missing");
			helper.assertTrue(level.getBlockState(chestPos.below()).is(Blocks.STONE), "the block the chest stands on should be left");
			golem.discard();
			level.setChunkForced(minX >> 4, minZ >> 4, false);
		});
	}

	/**
	 * A quarry with a pocket of water in the layer it digs and a pond just outside the chunk, against the
	 * side of the pit. The pocket is filled in and dug out, the pond walled off, and the pit ends up dry.
	 */
	@GameTest(maxTicks = 8000, padding = 24)
	public void quarryGolemWallsOffWaterAndLeavesThePitDry(final GameTestHelper helper) {
		quarryWater(helper, false);
	}

	/**
	 * The pond again, with nothing in the chest to build with and nothing in the golem's pockets, and the
	 * layer empty but for a little stone: it digs that out to wall the pond off. Were it to go to its chest
	 * for blocks it would wait there for good.
	 */
	@GameTest(maxTicks = 8000, padding = 24)
	public void quarryGolemDigsBlocksOutOfItsLayerToWallOffWater(final GameTestHelper helper) {
		quarryWater(helper, true);
	}

	private static void quarryWater(final GameTestHelper helper, final boolean noBlocks) {
		QuarrySite site = quarrySite(helper, GolemTier.IRON, 1);
		ServerLevel level = helper.getLevel();
		int top = site.top();
		if (noBlocks) {
			// Nothing in the layer but its stairs and walkway, and a little stone near the pond: all there is to wall it off with.
			((Container)level.getBlockEntity(site.chestPos())).clearContent();
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					for (int y = top - 1; y <= top; y++) {
						BlockPos pos = site.at(x, y, z);
						boolean stone = x >= 2 && x <= 4 && z >= 5 && z <= 6;
						if (!stone && !site.plan().isProtected(pos)) {
							level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}

			// Glass round the outside, or the water would run off the edge of the layer into the open air.
			for (int i = -1; i <= 16; i++) {
				for (int y = top - 1; y <= top; y++) {
					for (BlockPos pos : List.of(site.at(i, y, -1), site.at(i, y, 16), site.at(-1, y, i), site.at(16, y, i))) {
						level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
					}
				}
			}
		} else {
			List<BlockPos> pocket = List.of(site.at(6, top, 6), site.at(7, top, 6), site.at(6, top, 7), site.at(7, top, 7), site.at(6, top - 1, 6));
			pocket.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState()));
		}
		// The pond: two blocks deep in glass, against the west side of the chunk.
		List<BlockPos> pond = new java.util.ArrayList<>();
		for (int z = 3; z <= 5; z++) {
			for (int y = top - 1; y <= top; y++) {
				pond.add(site.at(-1, y, z));
				level.setBlockAndUpdate(site.at(-2, y, z), Blocks.GLASS.defaultBlockState());
			}

			level.setBlockAndUpdate(site.at(-1, top - 2, z), Blocks.GLASS.defaultBlockState());
		}

		for (int y = top - 1; y <= top; y++) {
			level.setBlockAndUpdate(site.at(-1, y, 2), Blocks.GLASS.defaultBlockState());
			level.setBlockAndUpdate(site.at(-1, y, 6), Blocks.GLASS.defaultBlockState());
		}

		pond.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState()));
		site.start(helper);
		helper.succeedWhen(() -> {
			site.assertDug(helper);
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					for (int y = top - 1; y <= top + 1; y++) {
						helper.assertTrue(level.getFluidState(site.at(x, y, z)).isEmpty(), "water left in the pit at " + site.at(x, y, z));
					}
				}
			}

			helper.assertTrue(pond.stream().anyMatch(pos -> level.getBlockState(pos).isSolid()), "the pond should have been walled off");
			site.finish();
		});
	}

	/**
	 * Where the ground dips under the top of the staircase there is nothing to stand on there: the golem
	 * puts a block in before it goes down, and does not fall into the hole and stay there.
	 */
	@GameTest(maxTicks = 8000, padding = 24)
	public void quarryGolemPutsAFloorUnderTheTopStep(final GameTestHelper helper) {
		QuarrySite site = quarrySite(helper, GolemTier.IRON, 1);
		ServerLevel level = helper.getLevel();
		BlockPos topStep = site.plan().stairStand(0);
		level.setBlockAndUpdate(topStep.below(), Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(topStep.below(2), Blocks.AIR.defaultBlockState());
		site.start(helper);
		helper.succeedWhen(() -> {
			site.assertDug(helper);
			helper.assertTrue(level.getBlockState(topStep.below()).isSolid(), "the top step should have a floor");
			site.finish();
		});
	}

	/**
	 * The ground under the walkway from the staircase to the chest has a hole in it. The golem fills it in
	 * on its way, instead of falling into the pit each time it tries to get to its chest.
	 */
	@GameTest(maxTicks = 8000, padding = 24)
	public void quarryGolemMendsTheWalkwayToItsChest(final GameTestHelper helper) {
		QuarrySite site = quarrySite(helper, GolemTier.IRON, 1);
		ServerLevel level = helper.getLevel();
		Container chest = (Container)level.getBlockEntity(site.chestPos());
		chest.clearContent();
		BlockPos topStep = site.plan().stairStand(0).below();
		List<BlockPos> walkway = new java.util.ArrayList<>();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				BlockPos pos = site.at(x, site.top(), z);
				if (site.plan().isProtected(pos) && !pos.equals(topStep) && !pos.equals(site.chestPos().below())) {
					walkway.add(pos);
				}
			}
		}

		helper.assertTrue(!walkway.isEmpty(), "the chest should have a walkway");
		walkway.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
		site.start(helper);
		helper.succeedWhen(() -> {
			site.assertDug(helper);
			helper.assertTrue(chest.hasAnyMatching(stack -> stack.is(Items.COBBLESTONE)), "it should have got to its chest");
			site.finish();
		});
	}

	/**
	 * Once the golem is well along the layer, cobblestone and a pocket of water turn up in the part it has
	 * already dug, and its pickaxe is taken so that it has to go to its chest for another. Back down in the
	 * pit it looks over the layer again: the water is walled off and dug out, and so is the cobblestone.
	 */
	@GameTest(maxTicks = 8000, padding = 24)
	public void quarryGolemClearsWhatTurnedUpInItsLayerWhileItWasAway(final GameTestHelper helper) {
		QuarrySite site = quarrySite(helper, GolemTier.IRON, 1);
		ServerLevel level = helper.getLevel();
		int y = site.top() - 1;
		// At the far end of the first lane, once the golem is a few lanes on and back at the other end, far
		// from the staircase: nothing takes it past there again.
		BlockPos block = site.at(14, y, 1);
		BlockPos water = site.at(12, y, 1);
		boolean[] done = {false};
		helper.onEachTick(() -> {
			if (!done[0] && level.getBlockState(block).isAir() && level.getBlockState(water).isAir() && site.golem().getZ() - site.minZ() > 4 && site.golem().getX() - site.minX() < 6) {
				done[0] = true;
				level.setBlockAndUpdate(block, Blocks.COBBLESTONE.defaultBlockState());
				level.setBlockAndUpdate(block.above(), Blocks.COBBLESTONE.defaultBlockState());
				level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
				((Container)level.getBlockEntity(site.chestPos())).setItem(1, site.golem().getMainHandItem().copy());
				site.golem().setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}
		});
		site.start(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(done[0], "the golem never got far enough along the layer");
			site.assertDug(helper);
			helper.assertTrue(level.getFluidState(water).isEmpty() && level.getFluidState(water.above()).isEmpty(), "the water should be gone");
			site.finish();
		});
	}

	/** Gravel coming down on a miner buries it until it has dug itself out, but does not choke it. */
	@GameTest
	public void minersDoNotSuffocateInWhatFallsOnThem(final GameTestHelper helper) {
		MiningGolem golem = setUp(helper, ModEntities.IRON_MINING_GOLEM, ModBlocks.IRON_CHEST);
		golem.hurtServer(helper.getLevel(), helper.getLevel().damageSources().inWall(), 10.0F);
		helper.assertTrue(golem.getHealth() == golem.getMaxHealth(), "being buried should not hurt it");
		golem.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 10.0F);
		helper.assertTrue(golem.getHealth() < golem.getMaxHealth(), "anything else still does");
		golem.discard();
		helper.succeed();
	}

	/** A tunnel through a pocket of water, with more water beside it: the tunnel ends up dry. */
	@GameTest(maxTicks = 1500)
	public void poppyTunnelsThroughWaterAndSealsItOff(final GameTestHelper helper) {
		MiningGolem golem = setUp(helper, ModEntities.IRON_MINING_GOLEM, ModBlocks.IRON_CHEST);
		chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		for (int x = 2; x <= 6; x++) {
			for (int z = 2; z <= 4; z++) {
				for (int y = 1; y <= 3; y++) {
					helper.setBlock(x, y, z, Blocks.STONE);
				}
			}
		}

		helper.setBlock(4, 1, 1, Blocks.STONE);
		helper.setBlock(4, 1, 3, Blocks.WATER);
		helper.setBlock(4, 2, 3, Blocks.WATER);
		helper.setBlock(4, 1, 2, Blocks.WATER);
		helper.setBlock(7, 1, 3, Blocks.BEDROCK);
		helper.setBlock(7, 2, 3, Blocks.BEDROCK);
		walls(helper, 3, 0, 7);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.succeedWhen(() -> {
			for (int x = 2; x <= 6; x++) {
				for (int y = 1; y <= 2; y++) {
					helper.assertBlockPresent(Blocks.AIR, x, y, 3);
				}
			}

			helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 2)).isSolid(), "the water beside the tunnel should have been walled off");
			helper.assertTrue(golem.isHalted(), "it should have stopped at the bedrock");
		});
	}

	/**
	 * Strip mining in a block of stone fifteen wide, walled in with bedrock: a main tunnel east and west
	 * through where the golem starts, branches north and south every four blocks with three blocks of
	 * stone left between them. A vein of iron ore showing in the side of the main tunnel is mined out to
	 * the last block, however far from the tunnel it runs. A lone ore it cannot see, behind the stone
	 * between two branches, is left.
	 */
	@GameTest(maxTicks = 6000, padding = 24)
	public void poppyStripMinesWithThreeBlocksBetweenBranches(final GameTestHelper helper) {
		stripMine(helper, GolemTier.IRON, 15);
	}

	/** The same with a diamond golem: tunnels three wide and three high, so its branches are six apart. */
	@GameTest(maxTicks = 8000, padding = 24)
	public void diamondGolemStripMinesThreeWideBranchesSixApart(final GameTestHelper helper) {
		stripMine(helper, GolemTier.DIAMOND, 17);
	}

	private static void stripMine(final GameTestHelper helper, final GolemTier tier, final int size) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BlockPos.ZERO).above(16);
		int height = tier.tunnelHeight();
		int radius = tier.tunnelRadius();
		int middle = size / 2;
		for (int x = -1; x <= size; x++) {
			for (int z = -1; z <= size; z++) {
				boolean edge = x < 0 || z < 0 || x >= size || z >= size;
				level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.STONE.defaultBlockState());
				for (int y = 1; y <= height + 1; y++) {
					level.setBlockAndUpdate(base.offset(x, y, z), (edge ? Blocks.BEDROCK : y <= height ? Blocks.STONE : Blocks.AIR).defaultBlockState());
				}
			}
		}

		forceChunks(level, base, size, true);
		BlockPos start = base.offset(middle, 1, middle);
		for (int y = 0; y < height; y++) {
			level.setBlockAndUpdate(start.above(y), Blocks.AIR.defaultBlockState());
		}

		// The chest against the side of the main tunnel, the ore in the stone between the first branches east.
		int gapX = middle + radius + 2;
		BlockPos chestPos = base.offset(middle - radius - 2, 1, middle + radius + 1);
		level.setBlockAndUpdate(chestPos, tier.chestBlock().defaultBlockState());
		List<BlockPos> vein = new java.util.ArrayList<>();
		for (int z = middle + radius + 1; z <= size - 3; z++) {
			vein.add(base.offset(gapX, 1, z));
		}

		BlockPos hidden = base.offset(middle - radius - 2, 1, size - 2);
		vein.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.IRON_ORE.defaultBlockState()));
		// A column of gravel standing in the way of the main tunnel and up through the roof: it keeps coming down as it is dug.
		for (int y = 1; y <= height + 6; y++) {
			level.setBlockAndUpdate(base.offset(middle + 3, y, middle), Blocks.GRAVEL.defaultBlockState());
		}

		level.setBlockAndUpdate(hidden, Blocks.IRON_ORE.defaultBlockState());

		MiningGolem golem = tier.entityType().create(level, EntitySpawnReason.COMMAND);
		golem.snapTo(Vec3.atBottomCenterOf(start));
		level.addFreshEntity(golem);
		golem.setChestPos(chestPos);
		golem.setItemSlot(EquipmentSlot.MAINHAND, tirelessPickaxe(helper));
		if (tier.startsLocked()) {
			give(helper, golem, new ItemStack(MiningGolem.UNLOCK_FLOWER), Direction.EAST);
		}

		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		int pitch = 2 * radius + 1 + StripPlan.GAP;
		helper.succeedWhen(() -> {
			helper.assertTrue(golem.isHalted(), "it should have finished the whole mine");
			for (int x = 0; x < size; x++) {
				for (int z = 0; z < size; z++) {
					BlockPos pos = base.offset(x, 1, z);
					if (pos.equals(chestPos) || vein.contains(pos) || pos.equals(hidden)) {
						continue;
					}

					int offset = Math.floorMod(x - middle, pitch);
					boolean dug = Math.abs(z - middle) <= radius || Math.min(offset, pitch - offset) <= radius;
					for (int y = 0; y < height; y++) {
						helper.assertTrue(level.getBlockState(pos.above(y)).isAir() == dug, (dug ? "left standing in a tunnel at " : "dug between the tunnels at ") + (x + ", " + (y + 1) + ", " + z));
					}
				}
			}

			vein.forEach(pos -> helper.assertTrue(level.getBlockState(pos).isAir(), "the vein of ore should have been mined out, but not at " + pos.subtract(base)));
			helper.assertTrue(level.getBlockState(hidden).is(Blocks.IRON_ORE), "ore it could not see should be left");
			helper.assertTrue(((Container)level.getBlockEntity(chestPos)).hasAnyMatching(stack -> stack.is(Items.RAW_IRON)), "the ore should be in the chest");
			golem.discard();
			forceChunks(level, base, size, false);
		});
	}

	/** Keeps every chunk of the mine loaded, from {@code base} to {@code size} blocks east and south of it and the wall around. */
	private static void forceChunks(final ServerLevel level, final BlockPos base, final int size, final boolean forced) {
		for (int chunkX = (base.getX() - 1) >> 4; chunkX <= (base.getX() + size) >> 4; chunkX++) {
			for (int chunkZ = (base.getZ() - 1) >> 4; chunkZ <= (base.getZ() + size) >> 4; chunkZ++) {
				level.setChunkForced(chunkX, chunkZ, forced);
			}
		}
	}

	/**
	 * A quarry with a big pocket of water in the layer it digs, and more in the floor under it. The golem
	 * gets the layer dug, and never goes up and down on the spot over and over.
	 */
	@GameTest(maxTicks = 8000, padding = 24)
	public void quarryGolemInWaterNeverBobsUpAndDownInPlace(final GameTestHelper helper) {
		QuarrySite site = quarrySite(helper, GolemTier.DIAMOND, 1);
		ServerLevel level = helper.getLevel();
		int top = site.top();
		int layer = top - GolemTier.DIAMOND.tunnelHeight() + 1;
		for (int x = 3; x <= 12; x++) {
			for (int z = 3; z <= 12; z++) {
				for (int y = layer; y <= top; y++) {
					level.setBlockAndUpdate(site.at(x, y, z), Blocks.WATER.defaultBlockState());
				}

				// Water under the layer too, one deep, with stone under it.
				level.setBlockAndUpdate(site.at(x, layer - 1, z), Blocks.WATER.defaultBlockState());
				level.setBlockAndUpdate(site.at(x, layer - 2, z), Blocks.STONE.defaultBlockState());
			}
		}

		watchForBobbing(helper, site, layer);
		site.start(helper);
		helper.succeedWhen(() -> {
			site.assertDug(helper);
			site.finish();
		});
	}

	/**
	 * Water it cannot do anything about: a spring on top of a bedrock pillar in the middle of the chunk, out
	 * of its reach over its layer, keeps running down into it. The golem works on in the running water, gets
	 * the layer dug, and never goes up and down on the spot over and over.
	 */
	@GameTest(maxTicks = 8000, padding = 24)
	public void quarryGolemInRunningWaterNeverBobsUpAndDownInPlace(final GameTestHelper helper) {
		QuarrySite site = quarrySite(helper, GolemTier.IRON, 1);
		ServerLevel level = helper.getLevel();
		int top = site.top();
		int layer = top - 1;
		BlockPos pillar = site.at(8, layer, 8);
		for (int y = 0; y <= 2; y++) {
			level.setBlockAndUpdate(pillar.above(y), Blocks.BEDROCK.defaultBlockState());
		}

		level.setBlockAndUpdate(pillar.above(3), Blocks.WATER.defaultBlockState());
		watchForBobbing(helper, site, layer);
		site.start(helper);
		helper.succeedWhen(() -> {
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					for (int y = layer; y <= top; y++) {
						BlockPos pos = site.at(x, y, z);
						BlockState state = level.getBlockState(pos);
						boolean kept = site.plan().isProtected(pos) || pos.getX() == pillar.getX() && pos.getZ() == pillar.getZ();
						helper.assertTrue(kept || !state.isSolid(), "block left at " + pos);
					}
				}
			}

			site.finish();
		});
	}

	/**
	 * Fails the test as soon as the golem has gone up a block and back down in the same cell of its layer
	 * more than a handful of times in a row.
	 */
	private static void watchForBobbing(final GameTestHelper helper, final QuarrySite site, final int layer) {
		MiningGolem golem = site.golem();
		BlockPos[] cell = {null};
		boolean[] up = {false};
		int[] bobs = {0};
		helper.onEachTick(() -> {
			if (golem.isRemoved() || golem.getY() < layer - 0.5 || golem.getY() > layer + 2.0) {
				return;
			}

			BlockPos here = BlockPos.containing(golem.getX(), layer, golem.getZ());
			if (!here.equals(cell[0])) {
				cell[0] = here;
				bobs[0] = 0;
				up[0] = golem.getY() > layer + 0.6;
				return;
			}

			boolean nowUp = golem.getY() > layer + 0.6;
			if (up[0] && !nowUp) {
				bobs[0]++;
				helper.assertTrue(bobs[0] <= 6, "the golem went up and down " + bobs[0] + " times in a row at " + here);
			}

			up[0] = nowUp;
		});
	}

	/**
	 * Once the golem is at work in its layer it is shut in a glass box up beside its chest, and the bottom
	 * step of its staircase, where it would head for, is blocked with bedrock. Round that step there is
	 * nothing to stand on, only water just under the layer, inside the chunk and outside it. Unable to walk
	 * there it would jump; it must not jump anywhere it would sink below its layer.
	 */
	@GameTest(maxTicks = 2400, padding = 24)
	public void quarryGolemNeverTeleportsBelowItsLayer(final GameTestHelper helper) {
		QuarrySite site = quarrySite(helper, GolemTier.IRON, 1);
		ServerLevel level = helper.getLevel();
		MiningGolem golem = site.golem();
		int top = site.top();
		int layer = top - 1;
		BlockPos bottomStep = site.plan().stairStand(2);
		helper.assertTrue(bottomStep.getY() == layer, "the staircase should reach the layer at its third step");
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos beside = bottomStep.relative(side);
			if (site.plan().isProtected(beside.below())) {
				continue;
			}

			// Outside the chunk there is only air: stone round the water to hold it in there.
			for (Direction around : Direction.Plane.HORIZONTAL) {
				BlockPos wall = beside.below().relative(around);
				if (!site.plan().contains(wall)) {
					level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
				}
			}

			level.setBlockAndUpdate(beside.below(), Blocks.WATER.defaultBlockState());
			level.setBlockAndUpdate(beside.below(2), Blocks.STONE.defaultBlockState());
		}

		BlockPos box = site.at(12, top + 1, 12);
		long[] boxedAt = {-1};
		helper.onEachTick(() -> {
			if (boxedAt[0] < 0 && Math.abs(golem.getY() - layer) < 0.3 && golem.getZ() - site.minZ() < 4.0 && golem.getX() - site.minX() < 9.0) {
				boxedAt[0] = level.getGameTime();
				level.setBlockAndUpdate(bottomStep, Blocks.BEDROCK.defaultBlockState());
				level.setBlockAndUpdate(bottomStep.above(), Blocks.BEDROCK.defaultBlockState());
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						for (int dy = 0; dy <= 2; dy++) {
							if (dx != 0 || dz != 0 || dy == 2) {
								level.setBlockAndUpdate(box.offset(dx, dy, dz), Blocks.GLASS.defaultBlockState());
							}
						}
					}
				}

				golem.teleportTo(box.getX() + 0.5, box.getY(), box.getZ() + 0.5);
			}

			if (boxedAt[0] >= 0) {
				// Floating in water is fine, so long as its feet are not in the block under the layer.
				helper.assertTrue(golem.getY() > layer - 0.01, "the golem ended up below its layer, at " + golem.position());
			}
		});
		site.start(helper);
		helper.runAtTickTime(2300, () -> {
			helper.assertTrue(boxedAt[0] >= 0, "the golem never got to work in its layer");
			site.finish();
			helper.succeed();
		});
	}

	/** An iron golem on its own, with its chest across the border in the next chunk, quarries the chunk it stands in. */
	@GameTest(padding = 24)
	public void ironQuarryGolemDigsItsOwnChunkWithItsChestNextDoor(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO).above(16);
		int minX = (origin.getX() & ~15) + 16;
		int minZ = origin.getZ() & ~15;
		BlockPos golemPos = new BlockPos(minX + 1, origin.getY() + 1, minZ + 5);
		BlockPos chestPos = new BlockPos(minX - 2, origin.getY() + 1, minZ + 5);
		for (int x = -4; x <= 4; x++) {
			level.setBlockAndUpdate(new BlockPos(minX + x, origin.getY(), minZ + 5), Blocks.STONE.defaultBlockState());
		}

		level.setBlockAndUpdate(chestPos, ModBlocks.IRON_CHEST.defaultBlockState());
		MiningGolem golem = looseMiner(level, GolemTier.IRON, golemPos, chestPos);
		give(helper, golem, new ItemStack(Items.DANDELION), Direction.WEST);
		ChunkPos own = ChunkPos.containing(golemPos);
		helper.assertTrue(own.equals(golem.getQuarryChunk()), "it should quarry " + own + ", not " + golem.getQuarryChunk());
		golem.discard();
		helper.succeed();
	}

	/**
	 * A golem that quarried this chunk once and has had its flower taken away is not working it any more:
	 * a new golem given a dandelion there quarries it, and does not go off to the next one.
	 */
	@GameTest(padding = 24)
	public void idleMinerInTheChunkDoesNotSendANewOneAway(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO).above(16);
		int minX = origin.getX() & ~15;
		int minZ = origin.getZ() & ~15;
		for (int x = 2; x <= 10; x++) {
			level.setBlockAndUpdate(new BlockPos(minX + x, origin.getY(), minZ + 5), Blocks.STONE.defaultBlockState());
		}

		BlockPos chestPos = new BlockPos(minX + 5, origin.getY() + 1, minZ + 5);
		level.setBlockAndUpdate(chestPos, ModBlocks.IRON_CHEST.defaultBlockState());
		MiningGolem idle = looseMiner(level, GolemTier.IRON, chestPos.east(), chestPos);
		give(helper, idle, new ItemStack(Items.DANDELION), Direction.EAST);
		give(helper, idle, ItemStack.EMPTY, Direction.EAST);
		helper.assertTrue(idle.getQuarryChunk() == null, "a golem without its flower should hold no chunk");
		MiningGolem fresh = looseMiner(level, GolemTier.IRON, chestPos.east(2), chestPos);
		give(helper, fresh, new ItemStack(Items.DANDELION), Direction.EAST);
		ChunkPos own = ChunkPos.containing(chestPos);
		helper.assertTrue(own.equals(fresh.getQuarryChunk()), "it should quarry " + own + ", not " + fresh.getQuarryChunk());
		idle.discard();
		fresh.discard();
		helper.succeed();
	}

	private static MiningGolem looseMiner(final ServerLevel level, final GolemTier tier, final BlockPos at, final BlockPos chestPos) {
		MiningGolem golem = tier.entityType().create(level, EntitySpawnReason.COMMAND);
		golem.snapTo(Vec3.atBottomCenterOf(at));
		level.addFreshEntity(golem);
		golem.setChestPos(chestPos);
		golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
		return golem;
	}

	/**
	 * A tunnel through stone with water sitting on top of one of the blocks it digs: the water is walled off
	 * with a block before that block is broken, and none of it gets into the tunnel.
	 */
	@GameTest(maxTicks = 1200)
	public void poppyWallsOffWaterAboveTheTunnelBeforeDigging(final GameTestHelper helper) {
		tunnelUnderFluid(helper, new BlockPos(4, 3, 3), Blocks.WATER, true);
	}

	/** The same with lava against the side of the tunnel. */
	@GameTest(maxTicks = 1200)
	public void poppyWallsOffLavaBesideTheTunnelBeforeDigging(final GameTestHelper helper) {
		tunnelUnderFluid(helper, new BlockPos(4, 1, 2), Blocks.LAVA, true);
	}

	/**
	 * Water over the second block of the tunnel, and nothing to wall it off with: no blocks on the golem
	 * and none in its chest. It goes to its chest for some and waits there, instead of letting the water in.
	 */
	@GameTest(maxTicks = 600)
	public void poppyWithNoBlocksWaitsAtItsChestInsteadOfLettingWaterIn(final GameTestHelper helper) {
		tunnelUnderFluid(helper, new BlockPos(3, 3, 3), Blocks.WATER, false);
	}

	/**
	 * A tunnel east from the golem, two blocks of dirt and then stone to the bedrock at x 7, with {@code fluid}
	 * at {@code at}, held in by stone. The dirt gives no blocks to build with; the stone does.
	 */
	private static void tunnelUnderFluid(final GameTestHelper helper, final BlockPos at, final Block fluid, final boolean blocks) {
		MiningGolem golem = setUp(helper, ModEntities.IRON_MINING_GOLEM, ModBlocks.IRON_CHEST);
		for (int x = 2; x <= 6; x++) {
			for (int z = 1; z <= 5; z++) {
				for (int y = 1; y <= 4; y++) {
					helper.setBlock(x, y, z, x <= 3 && z == 3 && y <= 2 && !blocks ? Blocks.DIRT : Blocks.STONE);
				}
			}
		}

		if (blocks) {
			helper.setBlock(2, 1, 3, Blocks.STONE);
			helper.setBlock(2, 2, 3, Blocks.STONE);
		}

		helper.setBlock(at, fluid);
		stopper(helper, 7);
		walls(helper, 3, 0, 7);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		// The block of the tunnel next to the fluid.
		BlockPos next = at.getY() > 2 ? at.below() : new BlockPos(at.getX(), at.getY(), 3);
		helper.onEachTick(() -> {
			if (helper.getBlockState(next).isAir()) {
				helper.assertTrue(helper.getBlockState(at).getFluidState().isEmpty(), "the " + fluid.getName().getString() + " should have been walled off before " + next + " was dug");
			}

			for (int x = 1; x <= 6; x++) {
				for (int y = 1; y <= 2; y++) {
					helper.assertTrue(helper.getBlockState(new BlockPos(x, y, 3)).getFluidState().isEmpty(), "fluid got into the tunnel at " + x + ", " + y + ", 3");
				}
			}
		});
		if (blocks) {
			helper.succeedWhen(() -> {
				helper.assertTrue(golem.isHalted(), "it should have dug through to the bedrock");
				helper.assertTrue(helper.getBlockState(at).isSolid(), "a block should stand where the " + fluid.getName().getString() + " was");
			});
		} else {
			helper.runAtTickTime(500, () -> {
				helper.assertBlockPresent(Blocks.DIRT, 3, 2, 3);
				helper.assertBlockPresent(Blocks.WATER, at.getX(), at.getY(), at.getZ());
				helper.assertTrue(!golem.isHalted(), "waiting for blocks is not giving up");
				helper.assertTrue(golem.position().distanceTo(Vec3.atCenterOf(helper.absolutePos(CHEST))) < 3.0, "golem should wait at its chest");
				helper.succeed();
			});
		}
	}

	/**
	 * Lava against the outside of the chunk, beside the layer: walled off before the block of the pit next
	 * to it is dug, and none of it gets into the pit.
	 */
	@GameTest(maxTicks = 8000, padding = 24)
	public void quarryGolemWallsOffLavaInTheSideOfThePitBeforeDigging(final GameTestHelper helper) {
		QuarrySite site = quarrySite(helper, GolemTier.IRON, 1);
		ServerLevel level = helper.getLevel();
		int top = site.top();
		BlockPos lava = site.at(-1, top, 9);
		BlockPos next = site.at(0, top, 9);
		for (int dz = -1; dz <= 1; dz++) {
			for (int dy = -1; dy <= 1; dy++) {
				level.setBlockAndUpdate(site.at(-2, top + dy, 9 + dz), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(site.at(-1, top + dy, 9 + dz), Blocks.STONE.defaultBlockState());
			}
		}

		level.setBlockAndUpdate(lava, Blocks.LAVA.defaultBlockState());
		helper.onEachTick(() -> {
			if (level.getBlockState(next).isAir()) {
				helper.assertTrue(level.getFluidState(lava).isEmpty(), "the lava should have been walled off before " + next + " was dug");
			}
		});
		site.start(helper);
		helper.succeedWhen(() -> {
			site.assertDug(helper);
			helper.assertTrue(level.getBlockState(lava).isSolid(), "a block should stand where the lava was");
			site.finish();
		});
	}

	/**
	 * Water running down the staircase from a spring on one of its upper steps, put there once the golem is
	 * at work in the second layer, where it is no longer the golem's to wall off. The golem's pickaxe is
	 * taken then too, so that it has to go up the stairs through the water to its chest and come back down.
	 * It finishes the quarry, and never goes up and down on the spot over and over on the way.
	 */
	@GameTest(maxTicks = 12000, padding = 24)
	public void quarryGolemGoesUpAndDownStairsWithWaterOnThem(final GameTestHelper helper) {
		QuarrySite site = quarrySite(helper, GolemTier.IRON, 2);
		ServerLevel level = helper.getLevel();
		MiningGolem golem = site.golem();
		int secondLayer = site.top() - 3;
		BlockPos spring = site.plan().stairStand(1);
		boolean[] done = {false};
		helper.onEachTick(() -> {
			if (!done[0] && Math.abs(golem.getY() - secondLayer) < 0.3 && golem.getZ() - site.minZ() > 3.0) {
				done[0] = true;
				level.setBlockAndUpdate(spring, Blocks.WATER.defaultBlockState());
				((Container)level.getBlockEntity(site.chestPos())).setItem(1, golem.getMainHandItem().copy());
				golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}
		});
		watchForClimbing(helper, golem);
		site.start(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(done[0], "the golem never got to the second layer");
			// The spring keeps running into the pit: water is left there, but nothing solid.
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					for (int y = site.top() - site.depth() + 1; y <= site.top(); y++) {
						BlockPos pos = site.at(x, y, z);
						helper.assertTrue(site.plan().isProtected(pos) || !level.getBlockState(pos).isSolid(), "block left at " + pos);
					}
				}
			}
			site.finish();
		});
	}

	/**
	 * Fails the test as soon as the golem has gone from one block's height to another and back more than a
	 * handful of times without leaving the column it is in.
	 */
	private static void watchForClimbing(final GameTestHelper helper, final MiningGolem golem) {
		long[] column = {Long.MIN_VALUE};
		int[] height = {Integer.MIN_VALUE};
		int[] changes = {0};
		helper.onEachTick(() -> {
			if (golem.isRemoved()) {
				return;
			}

			long here = BlockPos.asLong(golem.getBlockX(), 0, golem.getBlockZ());
			int level = (int)Math.floor(golem.getY() + 0.4);
			if (here != column[0]) {
				column[0] = here;
				height[0] = level;
				changes[0] = 0;
			} else if (level != height[0]) {
				height[0] = level;
				changes[0]++;
				helper.assertTrue(changes[0] <= 8, "the golem went up and down " + changes[0] + " times on the spot at " + golem.position());
			}
		});
	}

	/**
	 * Once the golem is at work in its layer, a step of its staircase loses the block it stands on, and the
	 * golem its pickaxe and every block it carries; there are none in its chest either. On its way up for a
	 * new pickaxe the next step is two blocks above the hole, more than it can climb, and it has nothing to
	 * mend the step with. It must not be stuck there: it gets to its chest and finishes the layer.
	 */
	@GameTest(maxTicks = 8000, padding = 24)
	public void quarryGolemGetsUpItsStairsPastAMissingStep(final GameTestHelper helper) {
		QuarrySite site = quarrySite(helper, GolemTier.IRON, 1);
		ServerLevel level = helper.getLevel();
		MiningGolem golem = site.golem();
		Container chest = (Container)level.getBlockEntity(site.chestPos());
		chest.clearContent();
		BlockPos hole = site.plan().stairStand(1).below();
		boolean[] done = {false};
		helper.onEachTick(() -> {
			if (!done[0] && Math.abs(golem.getY() - (site.top() - 1)) < 0.3 && golem.getZ() - site.minZ() > 6.0) {
				done[0] = true;
				level.setBlockAndUpdate(hole, Blocks.AIR.defaultBlockState());
				chest.setItem(1, golem.getMainHandItem().copy());
				golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				carried(golem).clearContent();
			}
		});
		site.start(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(done[0], "the golem never got far enough along its layer");
			helper.assertTrue(golem.hasTool(), "the golem should have got up to its chest for a new pickaxe");
			site.assertDug(helper);
			site.finish();
		});
	}

	/** What the golem carries, for tests that need to take it away. */
	static Container carried(final MiningGolem golem) {
		try {
			java.lang.reflect.Field field = MiningGolem.class.getDeclaredField("inventory");
			field.setAccessible(true);
			return (Container)field.get(golem);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}

	/** A chunk of stone in the air, with a chest and a quarrying golem on top, ready to be given its dandelion. */
	private record QuarrySite(ServerLevel level, int minX, int minZ, int top, int depth, BlockPos chestPos, MiningGolem golem, QuarryPlan plan) {
		BlockPos at(final int x, final int y, final int z) {
			return new BlockPos(this.minX + x, y, this.minZ + z);
		}

		void start(final GameTestHelper helper) {
			if (this.golem.getTier().startsLocked()) {
				give(helper, this.golem, new ItemStack(MiningGolem.UNLOCK_FLOWER), Direction.EAST);
			}

			give(helper, this.golem, new ItemStack(Items.DANDELION), Direction.EAST);
		}

		/** Everything in the layers dug out but the stairs and the walkway. */
		void assertDug(final GameTestHelper helper) {
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					for (int y = this.top - this.depth + 1; y <= this.top; y++) {
						BlockPos pos = this.at(x, y, z);
						boolean kept = this.plan.isProtected(pos);
						helper.assertTrue(this.level.getBlockState(pos).isAir() != kept, (kept ? "stair or walkway block dug at " : "block left at ") + pos);
					}
				}
			}
		}

		void finish() {
			this.golem.discard();
			this.level.setChunkForced(this.minX >> 4, this.minZ >> 4, false);
		}
	}

	private static QuarrySite quarrySite(final GameTestHelper helper, final GolemTier tier, final int layers) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO).above(16);
		int minX = origin.getX() & ~15;
		int minZ = origin.getZ() & ~15;
		int depth = layers * tier.tunnelHeight();
		int top = origin.getY() + depth;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = origin.getY(); y <= top; y++) {
					level.setBlockAndUpdate(new BlockPos(minX + x, y, minZ + z), Blocks.STONE.defaultBlockState());
				}
			}
		}

		BlockPos chestPos = new BlockPos(minX + 5, top + 1, minZ + 5);
		BlockPos golemPos = chestPos.east();
		level.setBlockAndUpdate(chestPos, tier.chestBlock().defaultBlockState());
		((Container)level.getBlockEntity(chestPos)).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		level.setChunkForced(minX >> 4, minZ >> 4, true);
		MiningGolem golem = tier.entityType().create(level, EntitySpawnReason.COMMAND);
		golem.snapTo(Vec3.atBottomCenterOf(golemPos));
		level.addFreshEntity(golem);
		golem.setChestPos(chestPos);
		golem.setItemSlot(EquipmentSlot.MAINHAND, tirelessPickaxe(helper));
		return new QuarrySite(level, minX, minZ, top, depth, chestPos, golem, QuarryPlan.create(golemPos, chestPos, tier));
	}

	static <T extends WorkerGolem> T setUp(final GameTestHelper helper, final EntityType<T> type, final Block chest) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(x, 0, z, Blocks.SMOOTH_STONE);
			}
		}

		helper.setBlock(CHEST, chest);
		T golem = helper.spawn(type, 1, 1, 3);
		golem.setChestPos(helper.absolutePos(CHEST));
		// Already holding the tool of its trade, so that the tests are about something else.
		if (golem instanceof MiningGolem) {
			golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
		} else if (golem instanceof LumberGolem) {
			golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		}

		return golem;
	}

	private static AbstractFurnaceBlockEntity furnace(final GameTestHelper helper, final BlockPos pos) {
		return (AbstractFurnaceBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(pos));
	}

	static Container chest(final GameTestHelper helper) {
		return (Container)helper.getLevel().getBlockEntity(helper.absolutePos(CHEST));
	}

	private static void buildMixedWall(final GameTestHelper helper) {
		for (int i = 0; i < MIXED_WALL.size(); i++) {
			helper.setBlock(2 + i / 2, 1 + i % 2, 3, MIXED_WALL.get(i));
		}

		stopper(helper, 7);
	}

	/** A bedrock wall the golem cannot break, so it stays inside the test area. */
	/**
	 * Bedrock two blocks to each side of the lane of a strip-mining golem whose chest is at {@code chestZ},
	 * wherever there is only air, so that its branch tunnels end there instead of running off across the
	 * other tests.
	 */
	static void walls(final GameTestHelper helper, final int chestZ, final int fromX, final int toX) {
		for (int x = fromX; x <= toX; x++) {
			for (int y = 1; y <= 4; y++) {
				for (int z : new int[]{chestZ - 2, chestZ + 2}) {
					if (helper.getBlockState(new BlockPos(x, y, z)).isAir()) {
						helper.setBlock(x, y, z, Blocks.BEDROCK);
					}
				}
			}
		}
	}

	static void stopper(final GameTestHelper helper, final int x) {
		for (int z = 0; z <= 7; z++) {
			for (int y = 1; y <= 4; y++) {
				helper.setBlock(x, y, z, Blocks.BEDROCK);
			}
		}
	}

	/** Removes the floor in the golem's lane from {@code fromX} to {@code toX}, three blocks down. */
	static void digPit(final GameTestHelper helper, final int fromX, final int toX) {
		for (int x = fromX; x <= toX; x++) {
			for (int y = 0; y >= -2; y--) {
				helper.setBlock(x, y, 3, Blocks.AIR);
			}
		}
	}

	static ItemStack enchanted(final GameTestHelper helper, final Item item, final ResourceKey<Enchantment> enchantment, final int level) {
		ItemStack stack = new ItemStack(item);
		stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), level);
		return stack;
	}

	/** A pickaxe for the tests that dig a lot: it goes through stone at a touch and never breaks. */
	static ItemStack tirelessPickaxe(final GameTestHelper helper) {
		ItemStack pickaxe = enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.EFFICIENCY, 5);
		pickaxe.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		return pickaxe;
	}

	/** Right-clicks the golem with {@code held} as a player facing {@code facing} would. */
	static Player give(final GameTestHelper helper, final WorkerGolem golem, final ItemStack held, final Direction facing) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setYRot(facing.toYRot());
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		golem.interact(player, InteractionHand.MAIN_HAND, golem.position());
		return player;
	}
}
