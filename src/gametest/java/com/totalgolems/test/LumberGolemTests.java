package com.totalgolems.test;

import static com.totalgolems.test.MiningGolemTests.chest;
import static com.totalgolems.test.MiningGolemTests.give;
import static com.totalgolems.test.MiningGolemTests.setUp;

import com.totalgolems.entity.CoalGolem;
import com.totalgolems.entity.LumberGolem;
import com.totalgolems.registry.ModBlocks;
import com.totalgolems.registry.ModEntities;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The amethyst golem on real trees: a big oak with its branches and a giant two by two spruce, grown as
 * the world grows them, which it fells whole from pillars, one after another where one will not do; and it
 * always replants, from its chest or with saplings from the leaves.
 */
public class LumberGolemTests {
	/** Where the trees go: away from the golem's chest, with room all round. */
	private static final BlockPos TREE = new BlockPos(12, 1, 12);

	@GameTest(maxTicks = 12000, structure = "totalgolems:forest", skyAccess = true)
	public void fellsABigOakWholeFromPillars(final GameTestHelper helper) {
		LumberGolem golem = lumberjack(helper);
		chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		chest(helper).setItem(1, new ItemStack(Items.OAK_SAPLING, 4));
		int height = grow(helper, TreeFeatures.FANCY_OAK, 7L);
		felledWhole(helper, golem, height, Blocks.OAK_SAPLING, 1);
	}

	@GameTest(maxTicks = 24000, structure = "totalgolems:forest", skyAccess = true)
	public void fellsAGiantSpruceWholeAndReplantsAllFourCorners(final GameTestHelper helper) {
		LumberGolem golem = lumberjack(helper);
		chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		chest(helper).setItem(1, new ItemStack(Items.SPRUCE_SAPLING, 8));
		int height = grow(helper, TreeFeatures.MEGA_SPRUCE, 3L);
		felledWhole(helper, golem, height, Blocks.SPRUCE_SAPLING, 4);
	}

	/**
	 * A trunk with a long branch out to one side: no one pillar reaches both the top of the trunk and the end
	 * of the branch, so it comes down and builds another under the branch.
	 */
	@GameTest(maxTicks = 6000, structure = "totalgolems:forest", skyAccess = true)
	public void buildsASecondPillarForALongBranch(final GameTestHelper helper) {
		LumberGolem golem = lumberjack(helper);
		chest(helper).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		chest(helper).setItem(1, new ItemStack(Items.OAK_SAPLING, 1));
		helper.setBlock(TREE.below(), Blocks.DIRT);
		for (int y = 0; y < 9; y++) {
			helper.setBlock(TREE.above(y), Blocks.OAK_LOG);
		}

		for (int x = 1; x <= 7; x++) {
			helper.setBlock(TREE.above(8).east(x), Blocks.OAK_LOG);
		}

		for (BlockPos leaf : List.of(TREE.above(9), TREE.above(8).east(8), TREE.above(9).east(7))) {
			helper.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
		}

		Set<BlockPos> pillars = new HashSet<>();
		helper.onEachTick(() -> {
			if (golem.getPillarBase() != null) {
				pillars.add(golem.getPillarBase());
			}
		});
		helper.succeedWhen(() -> {
			assertNoLogs(helper);
			helper.assertTrue(pillars.size() >= 2, "it should have built a second pillar for the branch, built " + pillars.size());
			helper.assertTrue(golem.getPillarHeight() == 0, "and taken them down");
			helper.assertBlockPresent(Blocks.OAK_SAPLING, TREE);
			assertNoneLeft(helper, Blocks.COBBLESTONE);
		});
	}

	/** No blocks at all: it climbs with the logs of the tree itself, and gets them all back. */
	@GameTest(maxTicks = 4000, structure = "totalgolems:forest", skyAccess = true)
	public void climbsWithTheTreesOwnLogsWhenItHasNoBlocks(final GameTestHelper helper) {
		LumberGolem golem = lumberjack(helper);
		chest(helper).setItem(1, new ItemStack(Items.OAK_SAPLING, 1));
		int height = 12;
		trunk(helper, height);
		int[] tallest = {0};
		helper.onEachTick(() -> tallest[0] = Math.max(tallest[0], golem.getPillarHeight()));
		helper.succeedWhen(() -> {
			assertNoLogs(helper);
			helper.assertTrue(tallest[0] >= 4, "it should have climbed, not " + tallest[0]);
			helper.assertTrue(golem.isResting(), "and gone home with the wood");
			helper.assertTrue(chest(helper).countItem(Items.OAK_LOG) == height, "every log in the chest, those it climbed with too");
			helper.assertBlockPresent(Blocks.OAK_SAPLING, TREE);
		});
	}

	/**
	 * Not a sapling anywhere: the stump waits, and when saplings come down from the leaves it picks one up
	 * and plants it.
	 */
	@GameTest(maxTicks = 4000, structure = "totalgolems:forest", skyAccess = true)
	public void replantsWithASaplingFromTheLeavesWhenItHasNone(final GameTestHelper helper) {
		LumberGolem golem = lumberjack(helper);
		trunk(helper, 4);
		boolean[] dropped = {false};
		helper.onEachTick(() -> {
			if (!dropped[0] && golem.getStumpsToPlant() == 1) {
				// As if from the leaves, which would take a while to fall.
				dropped[0] = true;
				helper.spawnItem(Items.OAK_SAPLING, TREE.east(5).above());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(dropped[0], "with no sapling it should remember the stump");
			helper.assertBlockPresent(Blocks.OAK_SAPLING, TREE);
			helper.assertTrue(golem.getStumpsToPlant() == 0, "and plant it once it has one");
		});
	}

	/** Its chest is where it keeps saplings: none on it, it takes one from there to replant. */
	@GameTest(maxTicks = 3000, structure = "totalgolems:forest", skyAccess = true)
	public void replantsWithASaplingFromItsChest(final GameTestHelper helper) {
		LumberGolem golem = lumberjack(helper);
		chest(helper).setItem(5, new ItemStack(Items.OAK_SAPLING, 2));
		trunk(helper, 4);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.OAK_SAPLING, TREE);
			helper.assertTrue(golem.getStumpsToPlant() == 0, "nothing left to plant");
			helper.assertTrue(chest(helper).countItem(Items.OAK_SAPLING) + golem.getSaplingCount(Items.OAK_SAPLING) == 1, "one of the two planted");
		});
	}

	/** The coal golem empties the other golems' chests, but leaves the lumberjack's blocks and saplings alone. */
	@GameTest(maxTicks = 400)
	public void coalGolemLeavesTheLumberjacksChestAlone(final GameTestHelper helper) {
		CoalGolem golem = setUp(helper, ModEntities.COAL_GOLEM, ModBlocks.AMETHYST_CHEST);
		Container chest = chest(helper);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		chest.setItem(1, new ItemStack(Items.OAK_SAPLING, 8));
		chest.setItem(2, new ItemStack(Items.OAK_LOG, 16));
		helper.setBlock(5, 1, 5, Blocks.COPPER_CHEST.weathering().unaffected());
		give(helper, golem, new ItemStack(Items.OXEYE_DAISY), Direction.EAST);
		helper.runAtTickTime(300, () -> {
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 32, "the blocks stay");
			helper.assertTrue(chest.countItem(Items.OAK_SAPLING) == 8, "the saplings stay");
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 16, "the wood stays");
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------------------------ helpers

	/** An amethyst golem with its axe and chest on a wide grass field, switched on over the chunks around. */
	private static LumberGolem lumberjack(final GameTestHelper helper) {
		for (int x = 0; x <= 23; x++) {
			for (int z = 0; z <= 23; z++) {
				helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
			}
		}

		LumberGolem golem = setUp(helper, ModEntities.AMETHYST_GOLEM, ModBlocks.AMETHYST_CHEST);
		give(helper, golem, new ItemStack(Items.SPYGLASS), Direction.EAST);
		helper.runAfterDelay(1, () -> give(helper, golem, new ItemStack(Items.LILY_OF_THE_VALLEY), Direction.EAST));
		return golem;
	}

	/** A plain oak trunk at {@link #TREE} with a crown of leaves on top. */
	private static void trunk(final GameTestHelper helper, final int height) {
		helper.setBlock(TREE.below(), Blocks.DIRT);
		for (int y = 0; y < height; y++) {
			helper.setBlock(TREE.above(y), Blocks.OAK_LOG);
		}

		for (BlockPos leaf : List.of(TREE.above(height), TREE.above(height - 1).east(), TREE.above(height - 1).west())) {
			helper.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
		}
	}

	/** Grows the tree {@code feature} at {@link #TREE}, the same every time. */
	private static int grow(final GameTestHelper helper, final ResourceKey<Feature> feature, final long seed) {
		ServerLevel level = helper.getLevel();
		Feature tree = level.registryAccess().lookupOrThrow(Registries.FEATURE).getOrThrow(feature).value();
		boolean grown = false;
		for (long attempt = seed; !grown && attempt < seed + 50; attempt++) {
			grown = tree.place(level, level.getChunkSource().getGenerator(), RandomSource.create(attempt), helper.absolutePos(TREE));
		}

		helper.assertTrue(grown, "the tree should grow");
		// How tall it grew: the highest log above the ground.
		int height = 0;
		for (BlockPos pos : BlockPos.betweenClosed(TREE.offset(-10, 0, -10), TREE.offset(10, 40, 10))) {
			if (helper.getBlockState(pos).is(BlockTags.LOGS)) {
				height = Math.max(height, pos.getY() - TREE.getY() + 1);
			}
		}

		return height;
	}

	private static void felledWhole(final GameTestHelper helper, final LumberGolem golem, final int height, final Block sapling, final int saplings) {
		int[] tallest = {0};
		helper.onEachTick(() -> tallest[0] = Math.max(tallest[0], golem.getPillarHeight()));
		helper.succeedWhen(() -> {
			assertNoLogs(helper);
			helper.assertTrue(tallest[0] >= height - 6, "a tree " + height + " tall needs a pillar of " + (height - 6) + " at least, not " + tallest[0]);
			helper.assertTrue(golem.getPillarHeight() == 0, "and come down");
			assertNoneLeft(helper, Blocks.COBBLESTONE);
			int planted = 0;
			for (BlockPos pos : BlockPos.betweenClosed(TREE.offset(-1, 0, -1), TREE.offset(1, 0, 1))) {
				planted += helper.getBlockState(pos).is(sapling) ? 1 : 0;
			}

			helper.assertTrue(planted == saplings, "it should have replanted " + saplings + ", planted " + planted);
		});
	}

	private static void assertNoLogs(final GameTestHelper helper) {
		for (BlockPos pos : BlockPos.betweenClosed(TREE.offset(-10, 0, -10), TREE.offset(10, 40, 10))) {
			helper.assertTrue(!helper.getBlockState(pos).is(BlockTags.LOGS), "a log is left at " + pos.immutable());
		}
	}

	private static void assertNoneLeft(final GameTestHelper helper, final Block block) {
		for (BlockPos pos : BlockPos.betweenClosed(TREE.offset(-10, 0, -10), TREE.offset(10, 40, 10))) {
			helper.assertBlockNotPresent(block, pos);
		}
	}
}
