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
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Tools that wear out, the netherite golem's lock, chests joined into one, miners in water, the coal
 * golem clearing out chests, item frames as a filter for the copper
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
		MiningGolemTests.walls(helper, 3, 0, 7);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.succeedWhen(() -> {
			for (int x = 2; x <= 5; x++) {
				helper.assertBlockPresent(Blocks.AIR, x, 1, 3);
			}

			// The old one lasted two blocks; the new one has done the other two.
			helper.assertTrue(golem.getTool().is(Items.IRON_PICKAXE) && golem.getTool().getDamageValue() == 2, "it should be on its second pickaxe, used twice");
			helper.assertTrue(container(helper, CHEST).countItem(Items.IRON_PICKAXE) == 0, "the spare pickaxe should have been taken");
			// Blocks it keeps to build with: they are on the golem, not in the chest.
			int blocks = container(helper, CHEST).countItem(Items.COBBLESTONE) + MiningGolemTests.carried(golem).countItem(Items.COBBLESTONE);
			helper.assertTrue(blocks == 4, "all four blocks should have been dug, found " + blocks);
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
		MiningGolemTests.walls(helper, 3, 0, 7);
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
		MiningGolemTests.walls(helper, 3, 0, 7);
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
			int herd = FarmGolemTests.penned(helper).size();
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
		// Dirt, which it does not keep to build with.
		helper.setBlock(2, 1, 3, Blocks.DIRT);
		stopper(helper, 3);
		MiningGolemTests.walls(helper, 3, 1, 7);
		give(helper, golem, new ItemStack(Items.POPPY), Direction.EAST);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.AIR, 2, 1, 3);
			helper.assertTrue(golem.getTool().is(Items.IRON_PICKAXE), "it should have found the pickaxe in the chest next door");
			helper.assertTrue(container(helper, second).countItem(Items.DIRT) == 1, "what did not fit in its own chest goes in the one beside it");
			helper.assertTrue(golem.isHalted(), "it should have finished its tunnel");
		});
	}

	// ------------------------------------------------------------------------------------ water

	/** A glass tank four blocks deep: the miner stays on the bottom, where it works, and currents push no miner along. */
	@GameTest(maxTicks = 300, padding = 16)
	public void minersStayOnTheBottomAndCurrentsDoNotPushThem(final GameTestHelper helper) {
		floor(helper, Blocks.SMOOTH_STONE);
		BlockPos tank = new BlockPos(5, 1, 5);
		for (int y = 0; y < 4; y++) {
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				helper.setBlock(tank.above(y).relative(direction), Blocks.GLASS);
			}

			helper.setBlock(tank.above(y), Blocks.WATER);
		}

		MiningGolem miner = spawn(helper, ModEntities.IRON_MINING_GOLEM, tank);
		CoalGolem other = spawn(helper, ModEntities.COAL_GOLEM, new BlockPos(1, 1, 6));
		helper.assertTrue(!miner.isPushedByFluid(), "currents must not push the miners");
		helper.assertTrue(other.isPushedByFluid(), "the other golems are carried along as before");
		int bottom = helper.absolutePos(new BlockPos(0, 1, 0)).getY();
		helper.runAtTickTime(200, () -> {
			helper.assertTrue(miner.getY() < bottom + 0.5, "the miner should be standing on the bottom, but is at " + (miner.getY() - bottom));
			helper.succeed();
		});
	}


	/**
	 * A miner's chest and, in the fence of a small pen with a cow and a pig, a bone
	 * chest. Everything goes to the copper chest, the potatoes by way of the furnace, except what the
	 * owner of each chest needs.
	 */
	@GameTest(maxTicks = 4000, padding = 16)
	public void coalGolemCartsEverythingAwayExceptWhatOwnersNeed(final GameTestHelper helper) {
		floor(helper, Blocks.SMOOTH_STONE);
		BlockPos ironChest = new BlockPos(0, 1, 1);
		BlockPos boneChest = new BlockPos(4, 1, 5);
		BlockPos copperChest = new BlockPos(0, 1, 6);
		BlockPos furnace = new BlockPos(2, 1, 0);
		for (int i = 4; i <= 7; i++) {
			for (BlockPos post : List.of(new BlockPos(i, 1, 4), new BlockPos(i, 1, 7), new BlockPos(4, 1, i), new BlockPos(7, 1, i))) {
				helper.setBlock(post, Blocks.OAK_FENCE);
			}
		}

		helper.setBlock(ironChest, ModBlocks.IRON_CHEST);
		helper.setBlock(boneChest, ModBlocks.BONE_CHEST);
		helper.setBlock(copperChest, Blocks.COPPER_CHEST.weathering().unaffected());
		helper.setBlock(furnace, Blocks.FURNACE);
		fill(container(helper, ironChest), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 36), new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.COAL, 1));
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
			helper.assertTrue(copper.countItem(Items.COBBLESTONE) == 36, "expected the 36 blocks over two stacks, found " + copper.countItem(Items.COBBLESTONE));
			Container miners = container(helper, ironChest);
			helper.assertTrue(miners.countItem(Items.IRON_PICKAXE) == 1 && miners.countItem(Items.IRON_SHOVEL) == 1, "the miner's pickaxe and shovel stay");
			helper.assertTrue(miners.countItem(Items.COBBLESTONE) == MiningGolem.BLOCKS_KEPT_FOR_FLOOR, "the miner keeps two stacks of blocks to build with");
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

	/**
	 * A chest with an empty frame on it, and gold already inside, which the vanilla rule would keep the iron
	 * out of. No other chest shows iron, so the empty frame takes it.
	 */
	@GameTest(maxTicks = 3000)
	public void copperGolemPutsAnythingInAChestWithAnEmptyFrame(final GameTestHelper helper) {
		floor(helper, Blocks.SMOOTH_STONE);
		BlockPos source = new BlockPos(1, 1, 1);
		BlockPos anything = new BlockPos(4, 1, 4);
		helper.setBlock(source, Blocks.COPPER_CHEST.weathering().unaffected());
		helper.setBlock(anything, Blocks.CHEST);
		container(helper, source).setItem(0, new ItemStack(Items.IRON_INGOT, 4));
		container(helper, anything).setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
		frame(helper, anything, null);
		helper.spawn(EntityTypes.COPPER_GOLEM, 2, 1, 2);
		helper.succeedWhen(() -> {
			helper.assertTrue(container(helper, anything).countItem(Items.IRON_INGOT) == 4, "the chest with an empty frame should take the iron");
			helper.killAllEntitiesOfClass(ItemFrame.class);
		});
	}

	/**
	 * The chest with an empty frame is the nearer one, but a further chest has a frame showing iron: the iron
	 * goes there, and none of it in the chest that takes anything.
	 */
	@GameTest(maxTicks = 3000)
	public void copperGolemPrefersAChestShowingTheItemToOneWithAnEmptyFrame(final GameTestHelper helper) {
		floor(helper, Blocks.SMOOTH_STONE);
		BlockPos source = new BlockPos(1, 1, 1);
		BlockPos anything = new BlockPos(4, 1, 1);
		BlockPos iron = new BlockPos(4, 1, 6);
		helper.setBlock(source, Blocks.COPPER_CHEST.weathering().unaffected());
		helper.setBlock(anything, Blocks.CHEST);
		helper.setBlock(iron, Blocks.CHEST);
		container(helper, source).setItem(0, new ItemStack(Items.IRON_INGOT, 4));
		frame(helper, anything, null);
		frame(helper, iron, Items.IRON_INGOT);
		helper.spawn(EntityTypes.COPPER_GOLEM, 2, 1, 2);
		boolean[] misplaced = {false};
		helper.onEachTick(() -> misplaced[0] |= !container(helper, anything).isEmpty());
		helper.succeedWhen(() -> {
			helper.assertTrue(container(helper, iron).countItem(Items.IRON_INGOT) == 4, "the iron belongs in the chest showing iron");
			helper.assertTrue(!misplaced[0], "the chest with an empty frame only gets what no other chest shows");
			helper.killAllEntitiesOfClass(ItemFrame.class);
		});
	}

	/** The same for a minecart: an empty frame beside its button takes the iron, though the minecart holds dirt. */
	@GameTest(maxTicks = 1200)
	public void copperGolemLoadsAnythingIntoAMinecartWithAnEmptyFrame(final GameTestHelper helper) {
		floor(helper, Blocks.SMOOTH_STONE);
		BlockPos source = new BlockPos(1, 1, 1);
		helper.setBlock(source, Blocks.COPPER_CHEST.weathering().unaffected());
		container(helper, source).setItem(0, new ItemStack(Items.IRON_INGOT, 8));
		MinecartChest minecart = station(helper, 3, 5, Items.AIR);
		minecart.setItem(0, new ItemStack(Items.DIRT, 10));
		helper.spawn(EntityTypes.COPPER_GOLEM, 2, 1, 2);
		helper.succeedWhen(() -> {
			helper.assertTrue(minecart.countItem(Items.IRON_INGOT) == 8, "the minecart with an empty frame by its button should take the iron");
			helper.killAllEntitiesOfClass(ItemFrame.class);
			minecart.discard();
		});
	}

	/**
	 * A minecart with a chest waits at a station, a button right above its rail, full of iron but for one
	 * slot. The golem puts its iron in, which fills it, and presses the button: the minecart goes off down
	 * the line.
	 */
	@GameTest(maxTicks = 1200)
	public void copperGolemLoadsAMinecartAndSendsItOffOnceFull(final GameTestHelper helper) {
		floor(helper, Blocks.SMOOTH_STONE);
		BlockPos source = new BlockPos(1, 1, 1);
		helper.setBlock(source, Blocks.COPPER_CHEST.weathering().unaffected());
		container(helper, source).setItem(0, new ItemStack(Items.IRON_INGOT, 16));
		MinecartChest minecart = station(helper, 3, 5, null);
		for (int slot = 0; slot < minecart.getContainerSize() - 1; slot++) {
			minecart.setItem(slot, new ItemStack(Items.IRON_INGOT, 64));
		}

		BlockPos button = new BlockPos(3, 2, 5);
		double startX = minecart.getX();
		boolean[] pressed = {false};
		helper.onEachTick(() -> pressed[0] |= helper.getBlockState(button).getValue(ButtonBlock.POWERED));
		helper.spawn(EntityTypes.COPPER_GOLEM, 2, 1, 2);
		helper.succeedWhen(() -> {
			helper.assertTrue(minecart.countItem(Items.IRON_INGOT) == (minecart.getContainerSize() - 1) * 64 + 16, "the iron should be in the minecart");
			helper.assertTrue(pressed[0], "the golem should have pressed the button once the minecart was full");
			helper.assertTrue(minecart.getX() - startX > 1.0, "the minecart should have been sent off");
			minecart.discard();
		});
	}

	/**
	 * Two stations: the nearer one with a frame showing gold beside its button, the further one a frame
	 * showing iron. The iron goes in the further minecart only; neither is full, so neither is sent off.
	 */
	@GameTest(maxTicks = 1200)
	public void copperGolemOnlyLoadsMinecartsWhoseItemFramesMatch(final GameTestHelper helper) {
		floor(helper, Blocks.SMOOTH_STONE);
		BlockPos source = new BlockPos(1, 1, 1);
		helper.setBlock(source, Blocks.COPPER_CHEST.weathering().unaffected());
		container(helper, source).setItem(0, new ItemStack(Items.IRON_INGOT, 8));
		MinecartChest gold = station(helper, 3, 2, Items.GOLD_INGOT);
		MinecartChest iron = station(helper, 3, 6, Items.IRON_INGOT);
		boolean[] misplaced = {false};
		helper.onEachTick(() -> misplaced[0] |= !gold.isEmpty());
		helper.spawn(EntityTypes.COPPER_GOLEM, 1, 1, 4);
		helper.succeedWhen(() -> {
			helper.assertTrue(iron.countItem(Items.IRON_INGOT) == 8, "the iron belongs in the minecart with the iron ingot by its button");
			helper.assertTrue(!misplaced[0], "nothing may go in the minecart marked for gold");
			helper.assertTrue(!helper.getBlockState(new BlockPos(3, 2, 6)).getValue(ButtonBlock.POWERED), "a minecart that is not full stays");
			helper.killAllEntitiesOfClass(ItemFrame.class);
			gold.discard();
			iron.discard();
		});
	}

	/**
	 * A station along x at ({@code x}, {@code z}): a powered rail with a block behind it to the west, so that
	 * a minecart started there runs off east, two more rails, a button right above the powered rail on a
	 * wall to the south, and a frame next to the button if {@code item} is given: showing it, or empty for
	 * air. Returns the minecart with a chest waiting on it.
	 */
	private static MinecartChest station(final GameTestHelper helper, final int x, final int z, final @Nullable Item item) {
		helper.setBlock(x - 1, 1, z, Blocks.STONE);
		helper.setBlock(new BlockPos(x, 1, z), Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST));
		for (int east = 1; east <= 2; east++) {
			helper.setBlock(new BlockPos(x + east, 1, z), Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST));
		}

		helper.setBlock(x - 1, 2, z + 1, Blocks.STONE);
		helper.setBlock(x, 2, z + 1, Blocks.STONE);
		helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.NORTH));
		if (item != null) {
			ItemFrame frame = new ItemFrame(helper.getLevel(), helper.absolutePos(new BlockPos(x - 1, 2, z)), Direction.NORTH);
			if (item != Items.AIR) {
				frame.setItem(new ItemStack(item), false);
			}

			helper.getLevel().addFreshEntity(frame);
		}

		MinecartChest minecart = EntityTypes.CHEST_MINECART.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		minecart.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 1, z))));
		helper.getLevel().addFreshEntity(minecart);
		return minecart;
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

	/** Hangs an item frame showing {@code item}, or an empty one, on top of the chest at {@code chest}. */
	private static void frame(final GameTestHelper helper, final BlockPos chest, final @Nullable Item item) {
		ItemFrame frame = new ItemFrame(helper.getLevel(), helper.absolutePos(chest.above()), Direction.UP);
		if (item != null) {
			frame.setItem(new ItemStack(item), false);
		}

		helper.getLevel().addFreshEntity(frame);
	}
}
