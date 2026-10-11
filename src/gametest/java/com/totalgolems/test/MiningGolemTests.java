package com.totalgolems.test;

import com.totalgolems.entity.MiningGolem;
import com.totalgolems.entity.QuarryPlan;
import com.totalgolems.GolemTier;
import com.totalgolems.registry.ModBlocks;
import com.totalgolems.registry.ModEntities;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
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
	public void pumpkinOnNetheriteBlockBuildsGolemAndChest(final GameTestHelper helper) {
		helper.setBlock(3, 1, 3, Blocks.NETHERITE_BLOCK);
		helper.setBlock(3, 2, 3, Blocks.CARVED_PUMPKIN);
		helper.assertBlockPresent(ModBlocks.NETHERITE_CHEST, 3, 1, 3);
		helper.assertEntityPresent(ModEntities.NETHERITE_MINING_GOLEM);
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
			// The sixth kind of drop did not fit in five slots, so the first ones were carried home mid-tunnel.
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
	public void netheriteGolemBreaksTheWholeSliceAtOnce(final GameTestHelper helper) {
		MiningGolem golem = setUp(helper, ModEntities.NETHERITE_MINING_GOLEM, ModBlocks.NETHERITE_CHEST);
		for (int z = 2; z <= 4; z++) {
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(2, y, z, Blocks.STONE);
			}
		}

		stopper(helper, 3);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.onEachTick(() -> {
			int broken = 0;
			for (int z = 2; z <= 4; z++) {
				for (int y = 1; y <= 3; y++) {
					broken += helper.getBlockState(new BlockPos(2, y, z)).isAir() ? 1 : 0;
				}
			}

			helper.assertTrue(broken == 0 || broken == 9, "all nine blocks should break on the same tick, but " + broken + " were gone");
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
			// It filled up after six blocks, went home, and has been waiting there since.
			helper.assertBlockPresent(Blocks.AIR, 2, 1, 3);
			helper.assertBlockPresent(Blocks.END_STONE, 6, 2, 3);
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
		chest(helper).setItem(0, new ItemStack(Items.DIRT, 10));
		stopper(helper, 6);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.COBBLESTONE, 3, 0, 3);
			helper.assertBlockPresent(Blocks.DIRT, 4, 0, 3);
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

	@GameTest(maxTicks = 1200)
	public void enchantedBooksApplySilkTouchAndFortuneOneAtATime(final GameTestHelper helper) {
		MiningGolem golem = setUp(helper, ModEntities.IRON_MINING_GOLEM, ModBlocks.IRON_CHEST);
		helper.setBlock(2, 1, 3, Blocks.STONE);
		stopper(helper, 3);
		give(helper, golem, book(helper, Enchantments.EFFICIENCY, 1), Direction.EAST);
		helper.assertTrue(golem.getEnchantment() == 0, "only Fortune and Silk Touch are accepted");
		give(helper, golem, book(helper, Enchantments.FORTUNE, 3), Direction.EAST);
		helper.assertTrue(golem.getEnchantment() == 3, "Fortune III should be applied");
		Player player = give(helper, golem, book(helper, Enchantments.SILK_TOUCH, 1), Direction.EAST);
		helper.assertTrue(golem.getEnchantment() == MiningGolem.SILK_TOUCH, "Silk Touch should replace Fortune");
		helper.assertTrue(player.getMainHandItem().is(Items.ENCHANTED_BOOK), "the Fortune book should be handed back");
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		// With Silk Touch stone drops as stone, not cobblestone.
		helper.succeedWhen(() -> helper.assertContainerContains(CHEST, Items.STONE));
	}

	/**
	 * These quarry a real chunk, so they are padded well away from the other tests. The chunk gets a slab
	 * of dirt with the chest and the golem on top, inside the chunk.
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
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		int minX = origin.getX() & ~15;
		int minZ = origin.getZ() & ~15;
		// One block of floor under the layers that get dug.
		int depth = layers * tier.tunnelHeight();
		int top = origin.getY() + depth;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = origin.getY(); y <= top; y++) {
					level.setBlockAndUpdate(new BlockPos(minX + x, y, minZ + z), Blocks.DIRT.defaultBlockState());
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

			// More dirt than the golem can carry, so it has been up the stairs to unload.
			helper.assertTrue(((Container)level.getBlockEntity(chestPos)).hasAnyMatching(stack -> stack.is(Items.DIRT)), "no dirt was taken to the chest");
			// Every stair step and the walkway from the top of the stairs to the chest are still there.
			for (int step = 0; step <= depth; step++) {
				helper.assertTrue(level.getBlockState(plan.stairStand(step).below()).is(Blocks.DIRT), "stair step " + step + " is missing");
			}

			helper.assertTrue(level.getBlockState(chestPos.north().below()).is(Blocks.DIRT), "walkway to the chest is missing");
			golem.discard();
			level.setChunkForced(minX >> 4, minZ >> 4, false);
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

	private static Container chest(final GameTestHelper helper) {
		return (Container)helper.getLevel().getBlockEntity(helper.absolutePos(CHEST));
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

	/** Removes the floor in the golem's lane from {@code fromX} to {@code toX}, three blocks down. */
	private static void digPit(final GameTestHelper helper, final int fromX, final int toX) {
		for (int x = fromX; x <= toX; x++) {
			for (int y = 0; y >= -2; y--) {
				helper.setBlock(x, y, 3, Blocks.AIR);
			}
		}
	}

	private static ItemStack book(final GameTestHelper helper, final ResourceKey<Enchantment> enchantment, final int level) {
		return EnchantmentHelper.createBook(
			new EnchantmentInstance(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), level)
		);
	}

	/** Right-clicks the golem with {@code held} as a player facing {@code facing} would. */
	private static Player give(final GameTestHelper helper, final MiningGolem golem, final ItemStack held, final Direction facing) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setYRot(facing.toYRot());
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		golem.interact(player, InteractionHand.MAIN_HAND, golem.position());
		return player;
	}
}
