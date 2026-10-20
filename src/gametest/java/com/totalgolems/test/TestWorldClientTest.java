package com.totalgolems.test;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Not a test: builds a world to try every golem in by hand. It only runs with the environment variable
 * {@code TOTALGOLEMS_TEST_WORLD} set, and prints where the world was saved, to be copied into a launcher's saves.
 * <p>
 * Everything stands on a cleared stretch of a normal world: grass at {@value #GROUND}, three blocks of dirt
 * and four of stone under it, and the world's own rock, ores, caves and water below that, for the miners.
 */
public class TestWorldClientTest implements FabricClientGameTest {
	private static final int GROUND = 70;
	private static final int FEET = GROUND + 1;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (System.getenv("TOTALGOLEMS_TEST_WORLD") == null) {
			return;
		}

		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(settings -> {
				settings.setName("TotalGolems Pruebas");
				settings.setSeed("totalgolems");
				settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				settings.setAllowCommands(true);
				settings.setDifficulty(Difficulty.PEACEFUL);
				settings.setGenerateStructures(false);
			})
			.create()) {
			TestServerContext server = singleplayer.getServer();
			server.runOnServer(minecraft -> clear(minecraft.overworld()));
			server.runCommand("kill @e[type=!player]");
			server.runCommand("time set noon");
			server.runCommand("setworldspawn 56 " + FEET + " -24");
			server.runCommand("tp @p 56 " + FEET + " -24 0 20");
			context.waitTicks(20);
			kit(server);
			miners(server);
			smelting(server);
			lumber(server);
			farm(server);
			context.waitTicks(20);
			server.runOnServer(minecraft -> framesAndCarts(minecraft.overworld()));
			server.runCommand("kill @e[type=item]");
			context.waitTicks(100);
			// What there is to see, to check the commands all did what they should.
			server.runOnServer(minecraft -> {
				ServerLevel level = minecraft.overworld();
				java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
				level.getAllEntities().forEach(entity -> counts.merge(net.minecraft.world.entity.EntityType.getKey(entity.getType()).toString(), 1, Integer::sum));
				System.out.println("TOTALGOLEMS_TEST_WORLD entities " + counts);
				for (BlockPos pos : List.of(new BlockPos(55, FEET, -22), new BlockPos(56, FEET, -22), new BlockPos(40, FEET, -1), new BlockPos(57, FEET, -1), new BlockPos(90, FEET, -35), new BlockPos(53, 20, 52))) {
					System.out.println("TOTALGOLEMS_TEST_WORLD " + pos.toShortString() + " " + level.getBlockState(pos) + " "
						+ (level.getBlockEntity(pos) instanceof net.minecraft.world.Container chest ? chest.getItem(0) + " .. " + chest.getItem(17) : ""));
				}

				System.out.println("TOTALGOLEMS_TEST_WORLD sign " + (level.getBlockEntity(new BlockPos(58, FEET, -26)) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign ? sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false).get(3).getString() : "none"));
			});
			server.runCommand("tp @p 56 " + (FEET + 12) + " -34 0 35");
			context.waitTicks(60);
			context.takeScreenshot("test_world_miners");
			server.runCommand("tp @p 70 " + (FEET + 8) + " -6 180 30");
			context.waitTicks(40);
			context.takeScreenshot("test_world_copper");
			server.runCommand("tp @p 92 " + (FEET + 14) + " -16 180 40");
			context.waitTicks(40);
			context.takeScreenshot("test_world_farm");
			server.runCommand("tp @p 8 " + (FEET + 10) + " -8 180 25");
			context.waitTicks(40);
			context.takeScreenshot("test_world_lumber");
			server.runCommand("tp @p 56 " + FEET + " -24 0 20");
			context.waitTicks(20);
			System.out.println("TOTALGOLEMS_TEST_WORLD saved at " + singleplayer.getWorldSave().getSaveDirectory().toAbsolutePath());
		}
	}

	// ------------------------------------------------------------------------------------ ground

	/** Flattens the whole area: air above the grass, dirt and stone under it. */
	private static void clear(final ServerLevel level) {
		for (int x = -16; x < 128; x++) {
			for (int z = -48; z < 64; z++) {
				level.getChunk(x >> 4, z >> 4);
				for (int y = GROUND - 6; y < 160; y++) {
					BlockState state = y > GROUND ? Blocks.AIR.defaultBlockState()
						: y == GROUND ? Blocks.GRASS_BLOCK.defaultBlockState()
						: y >= GROUND - 3 ? Blocks.DIRT.defaultBlockState()
						: Blocks.STONE.defaultBlockState();
					level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------ helpers

	private static void set(final TestServerContext server, final int x, final int y, final int z, final String block) {
		server.runCommand("setblock " + x + " " + y + " " + z + " " + block);
	}

	private static void fill(final TestServerContext server, final int x1, final int y1, final int z1, final int x2, final int y2, final int z2, final String block) {
		server.runCommand("fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + block);
	}

	/** Builds a golem the way a player does: its block, and a carved pumpkin on top facing where it should stand. */
	private static void golem(final TestServerContext server, final int x, final int y, final int z, final String block, final String facing) {
		set(server, x, y, z, block);
		set(server, x, y + 1, z, "carved_pumpkin[facing=" + facing + "]");
	}

	/** Puts {@code items} ("id count" each) in the container at x y z, from the first slot on. */
	private static void stock(final TestServerContext server, final int x, final int y, final int z, final String... items) {
		for (int slot = 0; slot < items.length; slot++) {
			server.runCommand("item replace block " + x + " " + y + " " + z + " container." + slot + " with " + items[slot]);
		}
	}

	/** A sign facing north, the way the player comes from the spawn. */
	private static void sign(final TestServerContext server, final int x, final int y, final int z, final String... lines) {
		StringBuilder messages = new StringBuilder();
		for (int i = 0; i < 4; i++) {
			messages.append(i == 0 ? "" : ",").append('"').append(i < lines.length ? lines[i] : "").append('"');
		}

		set(server, x, y, z, "oak_sign[rotation=8]{front_text:{messages:[" + messages + "]}}");
	}

	// ------------------------------------------------------------------------------------ zones

	/** By the spawn: a double chest of everything needed to build and switch golems on. */
	private static void kit(final TestServerContext server) {
		set(server, 55, FEET, -22, "chest[facing=north,type=left]");
		set(server, 56, FEET, -22, "chest[facing=north,type=right]");
		stock(server, 55, FEET, -22,
			"poppy 8", "dandelion 8", "wither_rose 2", "oxeye_daisy 4", "lily_of_the_valley 4", "blue_orchid 4", "cornflower 4", "white_tulip 4",
			"spyglass 2", "carved_pumpkin 16", "iron_block 4", "diamond_block 2", "netherite_block 2", "coal_block 2", "amethyst_block 2",
			"hay_block 2", "bone_block 2", "iron_pickaxe 4", "iron_shovel 4", "iron_axe 2", "iron_hoe 2", "iron_sword 2", "cobblestone 64");
		stock(server, 56, FEET, -22,
			"item_frame 16", "stone_button 8", "powered_rail 16", "rail 64", "chest_minecart 4", "copper_chest 4", "cow_spawn_egg 16", "sheep_spawn_egg 16",
			"pig_spawn_egg 4", "wheat 64", "carrot 64", "wheat_seeds 64");
		for (int level = 1; level <= 5; level++) {
			server.runCommand("item replace block 56 " + FEET + " -22 container." + (12 + level) + " with enchanted_book[stored_enchantments={\"minecraft:efficiency\":" + level + "}]");
		}

		sign(server, 54, FEET, -22, "Kit:", "flores, bloques,", "herramientas, libros");
		sign(server, 58, FEET, -26, "Sur: mineros", "Este: granja", "Oeste: le\u00f1ador", "Aqu\u00ed: fundidor/cobre");
	}

	/**
	 * Three miners in a row of chunks south of the spawn, chests just north of them: iron and diamond for
	 * quarries (the diamond one's chunk has a lake in it, for walling off water), and
	 * a locked netherite one. A fourth, iron, in a room down a shaft, for a strip mine.
	 */
	private static void miners(final TestServerContext server) {
		// Iron: chunk 2,0. Coal in its chest for the coal golem to take to the furnaces.
		golem(server, 40, FEET, -1, "iron_block", "south");
		stock(server, 40, FEET, -1, "iron_pickaxe 1", "iron_pickaxe 1", "iron_shovel 1", "cobblestone 64", "cobblestone 64", "coal 32");
		sign(server, 41, FEET, -3, "Minero hierro", "cantera:", "diente de le\u00f3n");
		// Diamond: chunk 3,0, with a lake in its chunk.
		golem(server, 56, FEET, -1, "diamond_block", "south");
		stock(server, 56, FEET, -1, "diamond_pickaxe 1", "diamond_shovel 1", "cobblestone 64", "cobblestone 64");
		fill(server, 50, GROUND - 4, 5, 61, GROUND, 13, "water");
		sign(server, 55, FEET, -3, "Minero diamante", "cantera con pala", "chunk con lago");
		// Netherite: chunk 4,0, locked until it gets a wither rose.
		golem(server, 72, FEET, -1, "netherite_block", "south");
		stock(server, 72, FEET, -1, "netherite_pickaxe 1", "cobblestone 64", "cobblestone 64");
		sign(server, 73, FEET, -3, "Minero netherita", "antes rosa wither", "luego una flor");
		// Strip mine: a shaft with a ladder from 56,?,56 down to a room at y 20.
		int bottom = 20;
		fill(server, 56, bottom, 56, 56, GROUND, 56, "air");
		fill(server, 56, bottom, 57, 56, GROUND, 57, "stone");
		fill(server, 56, bottom, 56, 56, GROUND, 56, "ladder[facing=north]");
		fill(server, 51, bottom - 1, 49, 61, bottom - 1, 55, "stone");
		fill(server, 51, bottom, 49, 61, bottom + 3, 55, "air");
		for (int[] corner : new int[][]{{51, 49}, {61, 49}, {51, 55}, {61, 55}}) {
			set(server, corner[0], bottom, corner[1], "torch");
		}

		golem(server, 53, bottom, 52, "iron_block", "east");
		stock(server, 53, bottom, 52, "iron_pickaxe 1", "iron_pickaxe 1", "iron_shovel 1", "cobblestone 64", "cobblestone 64");
		sign(server, 55, FEET, 55, "Bajando: minero", "amapola (tiras)", "a la altura " + bottom);
	}

	/**
	 * North of the miners: the coal golem's furnaces, the copper chest it fills, and a copper golem that takes
	 * that to chests with item frames on them and to a minecart station.
	 */
	private static void smelting(final TestServerContext server) {
		golem(server, 56, FEET, -9, "coal_block", "north");
		set(server, 50, FEET, -12, "furnace[facing=south]");
		set(server, 52, FEET, -12, "furnace[facing=south]");
		set(server, 54, FEET, -12, "blast_furnace[facing=south]");
		set(server, 58, FEET, -12, "smoker[facing=south]");
		set(server, 62, FEET, -12, "copper_chest[facing=south]");
		sign(server, 56, FEET, -13, "Fundidor:", "margarita aqu\u00ed", "");
		server.runCommand("summon minecraft:copper_golem 63 " + FEET + " -14");
		// Where the copper golem takes things: frames go on top of these in framesAndCarts.
		for (int x : List.of(66, 68, 70, 72)) {
			set(server, x, FEET, -16, "chest[facing=south]");
		}

		sign(server, 64, FEET, -17, "Cobre: marcos", "hierro, adoqu\u00edn,", "vac\u00edo = el resto", "vagoneta: tierra");
		// A minecart station: a block behind, a powered rail under the button, rails on to the east.
		set(server, 73, FEET, -20, "stone");
		set(server, 74, FEET, -20, "powered_rail[shape=east_west]");
		fill(server, 75, FEET, -20, 90, FEET, -20, "rail[shape=east_west]");
		set(server, 91, FEET, -20, "stone");
		fill(server, 73, FEET + 1, -19, 74, FEET + 1, -19, "stone");
		set(server, 74, FEET + 1, -20, "stone_button[face=wall,facing=north]");
	}

	/** West: a chunk of trees for the amethyst golem. */
	private static void lumber(final TestServerContext server) {
		String[] trees = {"oak", "birch", "spruce", "oak", "birch", "spruce", "fancy_oak", "oak"};
		int[][] spots = {{3, -29}, {8, -29}, {12, -29}, {3, -24}, {12, -24}, {3, -20}, {8, -24}, {12, -20}};
		for (int i = 0; i < trees.length; i++) {
			server.runCommand("place feature minecraft:" + trees[i] + " " + spots[i][0] + " " + FEET + " " + spots[i][1]);
		}

		golem(server, 8, FEET, -18, "amethyst_block", "north");
		stock(server, 8, FEET, -18, "iron_axe 1", "iron_axe 1", "oak_sapling 16", "birch_sapling 16", "spruce_sapling 16");
		sign(server, 9, FEET, -17, "Le\u00f1ador:", "lirio de los valles", "catalejo = 3x3");
	}

	/**
	 * East: two pens sharing a fence, a field for the hay golem and a herd for the bone golem, with the bone
	 * chest in the shared fence. Twenty-two animals, so the bone golem culls as soon as it has a sword, and one
	 * pig on its own, which it never feeds.
	 */
	private static void farm(final TestServerContext server) {
		int z1 = -42;
		int z2 = -28;
		fill(server, 80, FEET, z1, 104, FEET, z1, "oak_fence");
		fill(server, 80, FEET, z2, 104, FEET, z2, "oak_fence");
		fill(server, 80, FEET, z1, 80, FEET, z2, "oak_fence");
		fill(server, 90, FEET, z1, 90, FEET, z2, "oak_fence");
		fill(server, 104, FEET, z1, 104, FEET, z2, "oak_fence");
		set(server, 85, GROUND, -35, "water");
		golem(server, 80, FEET, -35, "hay_block", "east");
		stock(server, 80, FEET, -35, "iron_hoe 1", "iron_hoe 1", "wheat_seeds 32", "carrot 16", "potato 16");
		golem(server, 90, FEET, -35, "bone_block", "east");
		stock(server, 90, FEET, -35, "iron_sword 1", "wheat 32", "carrot 8");
		sign(server, 85, FEET, -27, "Agricultor:", "aciano dentro", "");
		sign(server, 97, FEET, -27, "Ganadero:", "tulip\u00e1n blanco", "22 animales", "cerdo solo: no come");
		for (int i = 0; i < 12; i++) {
			server.runCommand("summon cow " + (92 + i % 6 * 2) + " " + FEET + " " + (-40 + i / 6 * 3));
		}

		for (int i = 0; i < 9; i++) {
			server.runCommand("summon sheep " + (92 + i % 5 * 2) + " " + FEET + " " + (-33 + i / 5 * 2));
		}

		server.runCommand("summon pig 102 " + FEET + " -30");
	}

	/** The item frames and the minecart, which take a few lines of Java rather than one command each. */
	private static void framesAndCarts(final ServerLevel level) {
		frame(level, new BlockPos(66, FEET + 1, -16), Direction.UP, Items.IRON_INGOT);
		frame(level, new BlockPos(68, FEET + 1, -16), Direction.UP, Items.COBBLESTONE);
		frame(level, new BlockPos(70, FEET + 1, -16), Direction.UP, null);
		frame(level, new BlockPos(73, FEET + 1, -20), Direction.NORTH, Items.DIRT);
		MinecartChest cart = EntityTypes.CHEST_MINECART.create(level, EntitySpawnReason.COMMAND);
		cart.setPos(74.5, FEET, -19.5);
		level.addFreshEntity(cart);
	}

	private static void frame(final ServerLevel level, final BlockPos pos, final Direction facing, final @Nullable Item item) {
		ItemFrame frame = new ItemFrame(level, pos, facing);
		if (item != null) {
			frame.setItem(new ItemStack(item), false);
		}

		level.addFreshEntity(frame);
	}
}
