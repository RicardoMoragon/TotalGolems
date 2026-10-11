package com.totalgolems.test;

import com.totalgolems.entity.MiningGolem;
import com.totalgolems.registry.ModBlocks;
import com.totalgolems.registry.ModEntities;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class MiningGolemTests {
	private static final BlockPos CHEST = new BlockPos(0, 1, 3);
	/** Ten blocks with ten different drops, two per tunnel slice, so a five slot golem overflows on the sixth. */
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
	public void pumpkinOnOtherBlocksDoesNothing(final GameTestHelper helper) {
		helper.setBlock(3, 1, 3, Blocks.GOLD_BLOCK);
		helper.setBlock(3, 2, 3, Blocks.CARVED_PUMPKIN);
		helper.assertBlockPresent(Blocks.GOLD_BLOCK, 3, 1, 3);
		helper.assertBlockPresent(Blocks.CARVED_PUMPKIN, 3, 2, 3);
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void ironGolemDigsTwoHighUnloadsWhenFullAndWhenFlowerIsTaken(final GameTestHelper helper) {
		MiningGolem golem = setUp(helper, ModEntities.IRON_MINING_GOLEM, ModBlocks.IRON_CHEST);
		buildMixedWall(helper);
		// Blocks beside and above the tunnel must be left alone.
		helper.setBlock(2, 1, 2, Blocks.STONE);
		helper.setBlock(2, 3, 3, Blocks.STONE);
		giveFlower(helper, golem, Direction.EAST);
		helper.assertTrue(golem.hasFlower(), "golem should wear the flower");

		boolean[] flowerTaken = {false};
		helper.succeedWhen(() -> {
			for (int x = 2; x <= 6; x++) {
				helper.assertBlockPresent(Blocks.AIR, x, 1, 3);
				helper.assertBlockPresent(Blocks.AIR, x, 2, 3);
			}

			helper.assertBlockPresent(Blocks.STONE, 2, 1, 2);
			helper.assertBlockPresent(Blocks.STONE, 2, 3, 3);
			// The sixth kind of drop did not fit in five slots, so the first ones were carried home mid-tunnel.
			helper.assertContainerContains(CHEST, Items.DIRT);
			if (!flowerTaken[0]) {
				flowerTaken[0] = true;
				Player player = helper.makeMockPlayer(GameType.SURVIVAL);
				golem.interact(player, InteractionHand.MAIN_HAND, golem.position());
				helper.assertTrue(!golem.hasFlower(), "empty hand should take the flower");
				helper.assertTrue(player.getMainHandItem().is(Items.POPPY), "flower should go back to the player");
			}

			helper.assertContainerContains(CHEST, Items.END_STONE);
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
		giveFlower(helper, golem, Direction.EAST);
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

	@GameTest(maxTicks = 700)
	public void golemStopsDiggingWhileChestIsFull(final GameTestHelper helper) {
		MiningGolem golem = setUp(helper, ModEntities.IRON_MINING_GOLEM, ModBlocks.IRON_CHEST);
		Container chest = (Container)helper.getLevel().getBlockEntity(helper.absolutePos(CHEST));
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			chest.setItem(slot, new ItemStack(Items.GOLD_BLOCK, 64));
		}

		buildMixedWall(helper);
		giveFlower(helper, golem, Direction.EAST);
		helper.runAtTickTime(600, () -> {
			// It filled up after six blocks, went home, and has been waiting there since.
			helper.assertBlockPresent(Blocks.AIR, 2, 1, 3);
			helper.assertBlockPresent(Blocks.END_STONE, 6, 2, 3);
			helper.assertTrue(golem.position().distanceTo(Vec3.atCenterOf(helper.absolutePos(CHEST))) < 3.0, "golem should wait at its chest");
			helper.succeed();
		});
	}

	private static <T extends MiningGolem> T setUp(final GameTestHelper helper, final EntityType<T> type, final Block chest) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(x, 0, z, Blocks.SMOOTH_STONE);
			}
		}

		helper.setBlock(CHEST, chest);
		T golem = helper.spawn(type, 1, 1, 3);
		golem.setChestPos(helper.absolutePos(CHEST));
		return golem;
	}

	private static void buildMixedWall(final GameTestHelper helper) {
		for (int i = 0; i < MIXED_WALL.size(); i++) {
			helper.setBlock(2 + i / 2, 1 + i % 2, 3, MIXED_WALL.get(i));
		}

		stopper(helper, 7);
	}

	/** A bedrock wall the golem cannot break, so it stays inside the test area. */
	private static void stopper(final GameTestHelper helper, final int x) {
		for (int z = 0; z <= 7; z++) {
			for (int y = 1; y <= 4; y++) {
				helper.setBlock(x, y, z, Blocks.BEDROCK);
			}
		}
	}

	private static void giveFlower(final GameTestHelper helper, final MiningGolem golem, final Direction facing) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setYRot(facing.toYRot());
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.POPPY));
		golem.interact(player, InteractionHand.MAIN_HAND, golem.position());
	}
}
