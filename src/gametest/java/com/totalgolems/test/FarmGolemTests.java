package com.totalgolems.test;

import com.totalgolems.entity.BoneGolem;
import com.totalgolems.entity.CoalGolem;
import com.totalgolems.entity.Enclosure;
import com.totalgolems.entity.HayGolem;
import com.totalgolems.entity.MiningGolem;
import com.totalgolems.entity.SpongeGolem;
import com.totalgolems.entity.WorkerGolem;
import com.totalgolems.registry.ModBlocks;
import com.totalgolems.registry.ModEntities;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The sponge, hay and bone golems, the coal golem's dealings with their chests, and golems sharing out chunks. */
public class FarmGolemTests {
	private static final BlockPos OWN_CHEST = new BlockPos(0, 1, 3);
	private static final BlockPos OTHER_CHEST = new BlockPos(7, 1, 3);

	@GameTest
	public void pumpkinsBuildTheNewGolemsAndTheirChests(final GameTestHelper helper) {
		List<Block> bodies = List.of(Blocks.SPONGE, Blocks.HAY_BLOCK, Blocks.BONE_BLOCK);
		List<Block> chests = List.of(ModBlocks.SPONGE_CHEST, ModBlocks.HAY_CHEST, ModBlocks.BONE_CHEST);
		List<EntityType<? extends WorkerGolem>> golems = List.of(ModEntities.SPONGE_GOLEM, ModEntities.HAY_GOLEM, ModEntities.BONE_GOLEM);
		for (int i = 0; i < bodies.size(); i++) {
			helper.setBlock(1 + 2 * i, 1, 3, bodies.get(i));
			helper.setBlock(1 + 2 * i, 2, 3, Blocks.CARVED_PUMPKIN);
			helper.assertBlockPresent(chests.get(i), 1 + 2 * i, 1, 3);
			helper.assertEntityPresent(golems.get(i));
		}

		helper.succeed();
	}

	// ------------------------------------------------------------------------------------ sponge

	/**
	 * A pool five by five and two deep is fifty blocks of water: one sponge's worth, but only from the
	 * middle. From a corner the far side is out of a sponge's reach, and one sponge is all it gets.
	 */
	@GameTest(maxTicks = 1200)
	public void spongeGolemDrainsPoolWithAsFewSpongesAsPossible(final GameTestHelper helper) {
		floor(helper, Blocks.SMOOTH_STONE);
		for (int x = 1; x <= 7; x++) {
			for (int z = 0; z <= 6; z++) {
				for (int y = -2; y <= 0; y++) {
					boolean pool = x >= 2 && x <= 6 && z >= 1 && z <= 5 && y >= -1;
					helper.setBlock(x, y, z, pool ? Blocks.WATER : Blocks.SMOOTH_STONE);
				}
			}
		}

		helper.setBlock(OWN_CHEST, ModBlocks.SPONGE_CHEST);
		// Just the one sponge: with more it would go on to dry out whatever water the neighbouring tests have.
		container(helper, OWN_CHEST).setItem(0, new ItemStack(Items.SPONGE, 1));
		SpongeGolem golem = spawn(helper, ModEntities.SPONGE_GOLEM, new BlockPos(1, 1, 7));
		golem.setChestPos(helper.absolutePos(OWN_CHEST));
		give(helper, golem, Items.BLUE_ORCHID);
		helper.succeedWhen(() -> {
			for (int x = 2; x <= 6; x++) {
				for (int z = 1; z <= 5; z++) {
					helper.assertTrue(helper.getBlockState(new BlockPos(x, 0, z)).getFluidState().isEmpty(), "water left in the pool");
					helper.assertTrue(helper.getBlockState(new BlockPos(x, -1, z)).getFluidState().isEmpty(), "water left at the bottom of the pool");
				}
			}

			Container chest = container(helper, OWN_CHEST);
			helper.assertTrue(chest.countItem(Items.WET_SPONGE) == 1, "the sponge should be back in the chest, wet");
			helper.assertTrue(!golem.isActive(), "with no water left it should be idle");
		});
	}

	@GameTest(maxTicks = 300)
	public void spongeGolemWaitsByChestWithoutDrySponges(final GameTestHelper helper) {
		floor(helper, Blocks.SMOOTH_STONE);
		helper.setBlock(5, 1, 5, Blocks.WATER);
		helper.setBlock(OWN_CHEST, ModBlocks.SPONGE_CHEST);
		SpongeGolem golem = spawn(helper, ModEntities.SPONGE_GOLEM, new BlockPos(1, 1, 3));
		golem.setChestPos(helper.absolutePos(OWN_CHEST));
		give(helper, golem, Items.BLUE_ORCHID);
		helper.runAtTickTime(200, () -> {
			helper.assertBlockPresent(Blocks.WATER, 5, 1, 5);
			helper.assertTrue(!golem.isActive(), "without sponges it has nothing to do");
			helper.assertTrue(golem.position().distanceTo(Vec3.atCenterOf(helper.absolutePos(OWN_CHEST))) < 3.0, "it should stay by its chest");
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------------------------ hay

	/**
	 * A fenced field of grass with a pond in the middle and one ripe wheat plant. The hay chest in the
	 * fence has a hoe and four seeds; the bone chest opposite takes the harvest.
	 */
	@GameTest(maxTicks = 3000)
	public void hayGolemTillsSowsHarvestsReplantsAndDelivers(final GameTestHelper helper) {
		HayGolem golem = pen(helper, ModEntities.HAY_GOLEM, ModBlocks.HAY_CHEST);
		helper.setBlock(OTHER_CHEST, ModBlocks.BONE_CHEST);
		helper.setBlock(3, 0, 3, Blocks.WATER);
		helper.setBlock(5, 0, 5, Blocks.FARMLAND);
		helper.setBlock(new BlockPos(5, 1, 5), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
		Container chest = container(helper, OWN_CHEST);
		chest.setItem(0, new ItemStack(Items.WOODEN_HOE));
		chest.setItem(1, new ItemStack(Items.WHEAT_SEEDS, 4));
		chest.setItem(2, new ItemStack(Items.COBBLESTONE, 5));
		give(helper, golem, Items.CORNFLOWER);
		helper.succeedWhen(() -> {
			helper.assertTrue(golem.hasHoe(), "it should have taken the hoe");
			int crops = 0;
			for (int x = 1; x <= 6; x++) {
				for (int z = 1; z <= 6; z++) {
					if (x == 3 && z == 3) {
						continue;
					}

					helper.assertBlockPresent(Blocks.FARMLAND, x, 0, z);
					crops += helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.WHEAT) ? 1 : 0;
				}
			}

			// The four seeds from the chest, plus the plant it reaped and put back.
			helper.assertTrue(crops >= 5, "expected at least 5 wheat plants, found " + crops);
			BlockState replanted = helper.getBlockState(new BlockPos(5, 1, 5));
			helper.assertTrue(replanted.is(Blocks.WHEAT) && replanted.getValue(CropBlock.AGE) < CropBlock.MAX_AGE, "the ripe wheat should have been reaped and replanted");
			helper.assertContainerContains(OTHER_CHEST, Items.WHEAT);
			helper.assertTrue(container(helper, OWN_CHEST).countItem(Items.COBBLESTONE) == 5, "it takes nothing but hoes and seeds from its chest");
			// Thirty-four squares of grass tilled and one plant reaped, one use of the hoe each.
			helper.assertTrue(golem.getTool().getDamageValue() == 35, "the hoe should be worn by 35, not " + golem.getTool().getDamageValue());
		});
	}

	@GameTest(maxTicks = 300)
	public void hayGolemDoesNothingWithoutHoe(final GameTestHelper helper) {
		HayGolem golem = pen(helper, ModEntities.HAY_GOLEM, ModBlocks.HAY_CHEST);
		container(helper, OWN_CHEST).setItem(0, new ItemStack(Items.WHEAT_SEEDS, 4));
		give(helper, golem, Items.CORNFLOWER);
		helper.runAtTickTime(200, () -> {
			helper.assertBlockPresent(Blocks.GRASS_BLOCK, 4, 0, 4);
			helper.assertTrue(!golem.hasHoe() && !golem.isActive(), "without a hoe it stays switched off");
			helper.succeed();
		});
	}

	/**
	 * A pen only counts while it is closed. Built in the air in world coordinates, because the test
	 * framework walls its own area in: a fenced square with a long strip of ground leading away from it.
	 */
	@GameTest(padding = 64)
	public void penWithGapInFenceIsNotAPen(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos corner = helper.absolutePos(BlockPos.ZERO).above(24);
		for (int x = 0; x <= 60; x++) {
			for (int z = 0; z <= 5; z++) {
				boolean ground = x <= 5 || z == 2;
				boolean fence = x <= 5 && (x == 0 || x == 5 || z == 0 || z == 5);
				level.setBlockAndUpdate(corner.offset(x, 0, z), (ground ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState());
				level.setBlockAndUpdate(corner.offset(x, 1, z), (fence ? Blocks.OAK_FENCE : Blocks.AIR).defaultBlockState());
			}
		}

		BlockPos inside = corner.offset(2, 1, 2);
		Enclosure closed = Enclosure.find(level, inside);
		helper.assertTrue(closed != null && closed.cells().size() == 16, "a fenced four by four should be a pen of sixteen places");
		helper.assertTrue(closed.holds(Vec3.atBottomCenterOf(inside)) && !closed.holds(Vec3.atBottomCenterOf(corner.offset(7, 1, 2))), "inside is in, beyond the fence is out");
		// A closed gate is as good as a fence; an open one, or none, lets the pen run away down the strip.
		level.setBlockAndUpdate(corner.offset(5, 1, 2), Blocks.OAK_FENCE_GATE.defaultBlockState());
		helper.assertTrue(Enclosure.find(level, inside) != null, "a gate keeps the pen closed");
		level.setBlockAndUpdate(corner.offset(5, 1, 2), Blocks.AIR.defaultBlockState());
		helper.assertTrue(Enclosure.find(level, inside) == null, "with a gap in the fence there is no pen");
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------ bone

	@GameTest(maxTicks = 2400)
	public void boneGolemFeedsAnimalsSoTheyBreed(final GameTestHelper helper) {
		BoneGolem golem = pen(helper, ModEntities.BONE_GOLEM, ModBlocks.BONE_CHEST);
		Container chest = container(helper, OWN_CHEST);
		// It takes the sword out of the chest itself.
		chest.setItem(5, new ItemStack(Items.IRON_SWORD));
		chest.setItem(0, new ItemStack(Items.WHEAT, 8));
		// Pigs do not eat wheat, and neither does the golem take carrots when there are no pigs.
		chest.setItem(1, new ItemStack(Items.CARROT, 8));
		helper.spawn(EntityTypes.COW, 3, 1, 3);
		helper.spawn(EntityTypes.COW, 4, 1, 4);
		give(helper, golem, Items.WHITE_TULIP);
		helper.succeedWhen(() -> {
			List<Animal> animals = animals(helper);
			helper.assertTrue(animals.stream().anyMatch(Animal::isBaby), "the fed cows should have had a calf");
			helper.assertTrue(animals.size() == 3, "expected 3 animals, found " + animals.size());
			helper.assertTrue(container(helper, OWN_CHEST).countItem(Items.CARROT) == 8, "food nobody in the pen eats stays in the chest");
		});
	}

	@GameTest(maxTicks = 300)
	public void boneGolemNeverKillsWithTwentyAnimals(final GameTestHelper helper) {
		BoneGolem golem = pen(helper, ModEntities.BONE_GOLEM, ModBlocks.BONE_CHEST);
		golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		herd(helper, BoneGolem.MAX_ANIMALS, 0);
		give(helper, golem, Items.WHITE_TULIP);
		helper.runAtTickTime(250, () -> {
			helper.assertTrue(animals(helper).size() == BoneGolem.MAX_ANIMALS, "all twenty should still be alive, found " + animals(helper).size());
			helper.succeed();
		});
	}

	/**
	 * Twenty-three cows and three calves: six too many, and only grown cows may go. Generous with time:
	 * in a pen this crowded the golem is jostled about on its way to each one.
	 */
	@GameTest(maxTicks = 8000)
	public void boneGolemCullsAdultsDownToTwentyAndStoresDrops(final GameTestHelper helper) {
		BoneGolem golem = pen(helper, ModEntities.BONE_GOLEM, ModBlocks.BONE_CHEST);
		// Fire Aspect, to show that it does nothing here: the meat still comes out raw, for the coal golem to cook.
		golem.setItemSlot(EquipmentSlot.MAINHAND, MiningGolemTests.enchanted(helper, Items.IRON_SWORD, Enchantments.FIRE_ASPECT, 2));
		herd(helper, 23, 3);
		give(helper, golem, Items.WHITE_TULIP);
		int[] fewest = {Integer.MAX_VALUE};
		helper.onEachTick(() -> fewest[0] = Math.min(fewest[0], animals(helper).size()));
		helper.succeedWhen(() -> {
			List<Animal> animals = animals(helper);
			helper.assertTrue(animals.size() == BoneGolem.MAX_ANIMALS, "expected 20 animals, found " + animals.size());
			helper.assertTrue(animals.stream().filter(Animal::isBaby).count() == 3, "the three calves must all be alive");
			helper.assertContainerContains(OWN_CHEST, Items.BEEF);
			helper.assertItemEntityNotPresent(Items.BEEF);
			helper.assertTrue(container(helper, OWN_CHEST).countItem(Items.COOKED_BEEF) == 0, "Fire Aspect must not cook the meat");
			helper.assertItemEntityNotPresent(Items.COOKED_BEEF);
			helper.assertTrue(fewest[0] == BoneGolem.MAX_ANIMALS, "it must stop at twenty, but the herd got down to " + fewest[0]);
			// Six cows killed, six uses of the sword.
			helper.assertTrue(golem.getTool().getDamageValue() == 6, "the sword should be worn by 6, not " + golem.getTool().getDamageValue());
		});
	}

	// ------------------------------------------------------------------------------------ coal

	/** One of each kind of furnace, and one chest of each kind of thing to smelt. */
	@GameTest(maxTicks = 6000)
	public void coalGolemSortsOresMeatAndSpongesIntoTheRightFurnaces(final GameTestHelper helper) {
		floor(helper, Blocks.SMOOTH_STONE);
		BlockPos spongeChest = new BlockPos(0, 1, 1);
		BlockPos boneChest = new BlockPos(0, 1, 5);
		BlockPos copperChest = new BlockPos(7, 1, 5);
		BlockPos furnace = new BlockPos(2, 1, 0);
		BlockPos blastFurnace = new BlockPos(4, 1, 0);
		BlockPos smoker = new BlockPos(6, 1, 0);
		helper.setBlock(OWN_CHEST, ModBlocks.IRON_CHEST);
		helper.setBlock(spongeChest, ModBlocks.SPONGE_CHEST);
		helper.setBlock(boneChest, ModBlocks.BONE_CHEST);
		helper.setBlock(copperChest, Blocks.COPPER_CHEST.weathering().unaffected());
		helper.setBlock(furnace, Blocks.FURNACE);
		helper.setBlock(blastFurnace, Blocks.BLAST_FURNACE);
		helper.setBlock(smoker, Blocks.SMOKER);
		container(helper, OWN_CHEST).setItem(0, new ItemStack(Items.RAW_IRON, 8));
		container(helper, OWN_CHEST).setItem(1, new ItemStack(Items.COAL, 8));
		container(helper, OWN_CHEST).setItem(2, new ItemStack(Items.COBBLESTONE, 4));
		container(helper, spongeChest).setItem(0, new ItemStack(Items.WET_SPONGE, 4));
		container(helper, spongeChest).setItem(1, new ItemStack(Items.SPONGE, 2));
		container(helper, boneChest).setItem(0, new ItemStack(Items.BEEF, 8));
		container(helper, boneChest).setItem(1, new ItemStack(Items.BONE, 6));
		container(helper, boneChest).setItem(2, new ItemStack(Items.COOKED_BEEF, 1));
		CoalGolem golem = spawn(helper, ModEntities.COAL_GOLEM, new BlockPos(3, 1, 3));
		give(helper, golem, Items.OXEYE_DAISY);

		String[] misplaced = {null};
		helper.onEachTick(() -> {
			check(helper, furnace, "furnace", Items.WET_SPONGE, misplaced);
			check(helper, blastFurnace, "blast furnace", Items.RAW_IRON, misplaced);
			check(helper, smoker, "smoker", Items.BEEF, misplaced);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(misplaced[0] == null, "wrong furnace: " + misplaced[0]);
			Container copper = container(helper, copperChest);
			helper.assertTrue(copper.countItem(Items.IRON_INGOT) == 8, "expected 8 iron ingots in the copper chest, found " + copper.countItem(Items.IRON_INGOT));
			// The eight it cooked and the one that was in the bone chest already.
			helper.assertTrue(copper.countItem(Items.COOKED_BEEF) == 9, "expected 9 steaks in the copper chest, found " + copper.countItem(Items.COOKED_BEEF));
			helper.assertTrue(copper.countItem(Items.BONE) == 6, "what cannot be smelted goes straight to the copper chest");
			helper.assertTrue(copper.countItem(Items.SPONGE) == 0, "dried sponges do not go to the copper chest");
			// The two that were already dry, and the four it dried.
			Container sponges = container(helper, spongeChest);
			helper.assertTrue(sponges.countItem(Items.SPONGE) == 6 && sponges.countItem(Items.WET_SPONGE) == 0, "the dried sponges should be back in the sponge chest");
			helper.assertTrue(container(helper, OWN_CHEST).countItem(Items.COBBLESTONE) == 4, "a miner's chest keeps its blocks for laying floor");
			helper.assertTrue(container(helper, boneChest).isEmpty(), "the bone chest should have been emptied");
		});
	}

	/** Notes it if a furnace is ever given anything but what it is for. */
	private static void check(final GameTestHelper helper, final BlockPos pos, final String name, final Item expected, final String[] misplaced) {
		ItemStack input = ((AbstractFurnaceBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(pos))).getItem(0);
		if (!input.isEmpty() && !input.is(expected)) {
			misplaced[0] = input.getItem() + " in the " + name;
		}
	}

	// ------------------------------------------------------------------------------------ mining

	/**
	 * Three iron golems side by side, all told to quarry: each must take a chunk of its own instead of
	 * digging up the others' stairs and floors, and all three must get to work. Built in the air, clear
	 * of the test framework's own blocks, on a slab covering the chunk and the eight around it.
	 */
	@GameTest(maxTicks = 3000, padding = 40)
	public void quarryingGolemsTakeAChunkEach(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO).above(24);
		ChunkPos centre = ChunkPos.containing(origin);
		int top = origin.getY() + 2;
		for (int chunkX = centre.x() - 1; chunkX <= centre.x() + 1; chunkX++) {
			for (int chunkZ = centre.z() - 1; chunkZ <= centre.z() + 1; chunkZ++) {
				level.setChunkForced(chunkX, chunkZ, true);
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						for (int y = origin.getY(); y <= top; y++) {
							level.setBlockAndUpdate(new BlockPos((chunkX << 4) + x, y, (chunkZ << 4) + z), Blocks.STONE.defaultBlockState());
						}
					}
				}
			}
		}

		List<MiningGolem> golems = new java.util.ArrayList<>();
		for (int i = 0; i < 3; i++) {
			BlockPos chestPos = new BlockPos(centre.getMinBlockX() + 6 + 2 * i, top + 1, centre.getMinBlockZ() + 7);
			level.setBlockAndUpdate(chestPos, ModBlocks.IRON_CHEST.defaultBlockState());
			MiningGolem golem = ModEntities.IRON_MINING_GOLEM.create(level, EntitySpawnReason.COMMAND);
			golem.snapTo(Vec3.atBottomCenterOf(chestPos.south()));
			level.addFreshEntity(golem);
			golem.setChestPos(chestPos);
			golem.setItemSlot(EquipmentSlot.MAINHAND, MiningGolemTests.tirelessPickaxe(helper));
			golems.add(golem);
		}

		golems.forEach(golem -> give(helper, golem, Items.DANDELION));
		List<ChunkPos> chunks = golems.stream().map(MiningGolem::getQuarryChunk).toList();
		helper.assertTrue(chunks.get(0).equals(centre), "the first golem keeps the chunk it stands in");
		helper.assertTrue(chunks.stream().distinct().count() == 3, "each golem should have a chunk to itself: " + chunks);
		helper.succeedWhen(() -> {
			for (int i = 0; i < 3; i++) {
				MiningGolem golem = golems.get(i);
				ChunkPos chunk = chunks.get(i);
				helper.assertTrue(!golem.isHalted(), "golem " + i + " gave up");
				// Each has dug the first layer below the surface of its own chunk and is still inside it.
				long dug = BlockPos.betweenClosedStream(chunk.getMinBlockX(), top, chunk.getMinBlockZ(), chunk.getMaxBlockX(), top, chunk.getMaxBlockZ())
					.filter(pos -> level.getBlockState(pos).isAir())
					.count();
				helper.assertTrue(dug >= 100, "golem " + i + " has only dug " + dug + " blocks of its chunk");
			}

			// Nobody laid floor where there was already ground: there is no stone above the slab.
			for (int i = 0; i < 3; i++) {
				ChunkPos chunk = chunks.get(i);
				long extra = BlockPos.betweenClosedStream(chunk.getMinBlockX(), top + 1, chunk.getMinBlockZ(), chunk.getMaxBlockX(), top + 3, chunk.getMaxBlockZ())
					.filter(pos -> level.getBlockState(pos).is(Blocks.STONE) || level.getBlockState(pos).is(Blocks.COBBLESTONE))
					.count();
				helper.assertTrue(extra == 0, extra + " blocks were laid above the ground in chunk " + chunk);
			}

			golems.forEach(MiningGolem::discard);
			for (int chunkX = centre.x() - 1; chunkX <= centre.x() + 1; chunkX++) {
				for (int chunkZ = centre.z() - 1; chunkZ <= centre.z() + 1; chunkZ++) {
					level.setChunkForced(chunkX, chunkZ, false);
				}
			}
		});
	}

	// ------------------------------------------------------------------------------------ helpers

	static void floor(final GameTestHelper helper, final Block block) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(x, 0, z, block);
			}
		}
	}

	/** A six by six field of grass fenced all round, with the golem inside and its chest set in the fence. */
	static <T extends WorkerGolem> T pen(final GameTestHelper helper, final EntityType<T> type, final Block chest) {
		floor(helper, Blocks.GRASS_BLOCK);
		for (int i = 0; i <= 7; i++) {
			for (BlockPos post : List.of(new BlockPos(i, 1, 0), new BlockPos(i, 1, 7), new BlockPos(0, 1, i), new BlockPos(7, 1, i))) {
				helper.setBlock(post, Blocks.OAK_FENCE);
			}
		}

		helper.setBlock(OWN_CHEST, chest);
		T golem = spawn(helper, type, new BlockPos(1, 1, 3));
		golem.setChestPos(helper.absolutePos(OWN_CHEST));
		return golem;
	}

	static <T extends WorkerGolem> T spawn(final GameTestHelper helper, final EntityType<T> type, final BlockPos pos) {
		return helper.spawn(type, pos.getX(), pos.getY(), pos.getZ());
	}

	static void herd(final GameTestHelper helper, final int adults, final int babies) {
		for (int i = 0; i < adults + babies; i++) {
			Animal cow = helper.spawn(EntityTypes.COW, 2 + i % 5, 1, 2 + i / 5 % 5);
			cow.setBaby(i >= adults);
		}
	}

	static List<Animal> animals(final GameTestHelper helper) {
		BlockPos corner = helper.absolutePos(new BlockPos(0, 0, 0));
		BlockPos opposite = helper.absolutePos(new BlockPos(7, 4, 7));
		return helper.getLevel().getEntitiesOfClass(Animal.class, AABB.encapsulatingFullBlocks(corner, opposite), Animal::isAlive);
	}

	static Container container(final GameTestHelper helper, final BlockPos pos) {
		return (Container)helper.getLevel().getBlockEntity(helper.absolutePos(pos));
	}

	static Player give(final GameTestHelper helper, final WorkerGolem golem, final Item item) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setYRot(Direction.EAST.toYRot());
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
		golem.interact(player, InteractionHand.MAIN_HAND, golem.position());
		return player;
	}
}
