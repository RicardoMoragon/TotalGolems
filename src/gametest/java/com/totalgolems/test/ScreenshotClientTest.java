package com.totalgolems.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Lays out the golems and chests in front of the player and saves screenshots to check how they look in game. */
public class ScreenshotClientTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ -90 12");
			context.waitTicks(5);
			String at = "execute at @p run ";
			server.runCommand(at + "fill ~1 ~ ~-4 ~6 ~3 ~4 air");
			server.runCommand(at + "setblock ~5 ~ ~-3 totalgolems:iron_chest[facing=west]");
			server.runCommand(at + "setblock ~5 ~ ~-2 totalgolems:diamond_chest[facing=west]");
			server.runCommand(at + "setblock ~5 ~ ~-1 totalgolems:netherite_chest[facing=west]");
			server.runCommand(at + "setblock ~5 ~ ~2 totalgolems:netherite_chest[facing=west,type=left]");
			server.runCommand(at + "setblock ~5 ~ ~1 totalgolems:netherite_chest[facing=west,type=right]");
			// One of each mining golem with its pickaxe, and a netherite one with neither flower nor tool.
			summon(server, "iron", -1.6, 70, "poppy", "iron_pickaxe");
			summon(server, "diamond", -0.5, 80, "dandelion", "diamond_pickaxe");
			summon(server, "netherite", 0.6, 100, "poppy", "netherite_pickaxe");
			summon(server, "netherite", 1.7, 110, null, null);
			server.runCommand(at + "summon totalgolems:coal_golem ~1.6 ~ ~-0.6 {NoAI:1b,Rotation:[80f,0f],equipment:{saddle:{id:\"minecraft:oxeye_daisy\",count:1}}}");
			// Upgraded with a spyglass, to see its other eyes.
			server.runCommand(at + "summon totalgolems:amethyst_golem ~1.6 ~ ~0.6 {NoAI:1b,upgraded:1b,Rotation:[100f,0f],equipment:{mainhand:{id:\"minecraft:iron_axe\",count:1},saddle:{id:\"minecraft:lily_of_the_valley\",count:1}}}");
			server.runCommand(at + "setblock ~5 ~1 ~-2 totalgolems:amethyst_chest[facing=west]");
			server.runCommand("give @p totalgolems:iron_chest");
			server.runCommand("give @p totalgolems:diamond_chest");
			server.runCommand("give @p totalgolems:netherite_chest");
			context.waitTicks(60);
			context.takeScreenshot("totalgolems_scene");
			// A second scene further along for the hay and bone golems and their chests.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~24 -90 12");
			context.waitTicks(5);
			server.runCommand(at + "fill ~1 ~ ~-4 ~6 ~3 ~4 air");
			String[] kinds = {"hay", "bone"};
			String[] flowers = {"cornflower", "white_tulip"};
			for (int i = 0; i < kinds.length; i++) {
				double z = -1.6 + 1.6 * i;
				server.runCommand(at + "summon totalgolems:" + kinds[i] + "_golem ~3 ~ ~" + z + " {NoAI:1b,Rotation:[" + (75 + 15 * i) + "f,0f],equipment:{saddle:{id:\"minecraft:" + flowers[i] + "\",count:1}}}");
				server.runCommand(at + "setblock ~5 ~ ~" + (2 * i - 2) + " totalgolems:" + kinds[i] + "_chest[facing=west]");
			}

			context.waitTicks(60);
			context.takeScreenshot("totalgolems_farm");
			// Copper golems with Efficiency I, III, IV and V, close up and from behind: the antenna glint and the dots on the back.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~24 -90 20");
			context.waitTicks(5);
			server.runCommand(at + "fill ~1 ~ ~-4 ~6 ~3 ~4 air");
			int[] levels = {1, 3, 4, 5};
			for (int i = 0; i < levels.length; i++) {
				server.runCommand(
					at + "summon minecraft:copper_golem ~2 ~ ~" + (-1.2 + 0.8 * i) + " {NoAI:1b,Rotation:[-90f,0f],\"fabric:attachments\":{\"totalgolems:copper_golem_efficiency\":" + levels[i] + "}}"
				);
			}

			context.waitTicks(60);
			context.takeScreenshot("totalgolems_copper");
			// Every chest, single and double, from the front and from above.
			server.runCommand("execute as @p at @s run tp @s ~-5 ~4 ~24 -90 35");
			context.waitTicks(5);
			server.runCommand(at + "fill ~1 ~-4 ~-8 ~14 ~3 ~8 air");
			server.runCommand(at + "fill ~1 ~-5 ~-8 ~14 ~-5 ~8 grass_block");
			String[] chests = {"iron", "diamond", "netherite", "amethyst", "hay", "bone"};
			for (int i = 0; i < chests.length; i++) {
				int z = -7 + 2 * i;
				server.runCommand(at + "setblock ~8 ~-4 ~" + z + " totalgolems:" + chests[i] + "_chest[facing=west]");
				server.runCommand(at + "setblock ~11 ~-4 ~" + z + " totalgolems:" + chests[i] + "_chest[facing=west,type=right]");
				server.runCommand(at + "setblock ~11 ~-4 ~" + (z + 1) + " totalgolems:" + chests[i] + "_chest[facing=west,type=left]");
			}

			context.waitTicks(60);
			context.takeScreenshot("totalgolems_chests");
			// Each golem at its work, one frame every two ticks for a whole blow.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~24 -90 12");
			context.waitTicks(5);
			server.runCommand(at + "fill ~1 ~-4 ~-8 ~14 ~3 ~8 air");
			server.runCommand(at + "fill ~1 ~-5 ~-8 ~14 ~-5 ~8 grass_block");
			server.runCommand(at + "fill ~1 ~-1 ~-8 ~14 ~-1 ~8 grass_block");
			server.runCommand(at + "fill ~1 ~-4 ~-8 ~14 ~-2 ~8 dirt");
			String[][] workers = {
				{"iron_mining_golem", "iron_pickaxe", "poppy"},
				{"diamond_mining_golem", "diamond_pickaxe", "poppy"},
				{"netherite_mining_golem", "netherite_pickaxe", "poppy"},
				{"amethyst_golem", "iron_axe", "lily_of_the_valley"},
				{"hay_golem", "iron_hoe", "cornflower"},
				{"bone_golem", "iron_sword", "white_tulip"},
			};
			for (int i = 0; i < workers.length; i++) {
				server.runCommand(at + "summon totalgolems:" + workers[i][0] + " ~4 ~ ~" + (-3.75 + 1.5 * i)
					+ " {NoAI:1b,unlocked:1b,Rotation:[0f,0f],equipment:{mainhand:{id:\"minecraft:" + workers[i][1] + "\",count:1},saddle:{id:\"minecraft:" + workers[i][2] + "\",count:1}}}");
			}

			context.waitTicks(5);
			// The golems are not at work (NoAI), so they are told by hand that they are swinging their tools.
			server.runOnServer(minecraft -> {
				try {
					startSwinging(minecraft.overworld());
				} catch (ReflectiveOperationException exception) {
					throw new IllegalStateException(exception);
				}
			});
			for (int frame = 0; frame < 10; frame++) {
				context.waitTicks(2);
				context.takeScreenshot("totalgolems_work_" + frame);
			}
		}
	}

	private static void startSwinging(final net.minecraft.server.level.ServerLevel level) throws ReflectiveOperationException {
		java.lang.reflect.Method mining = com.totalgolems.entity.MiningGolem.class.getDeclaredMethod("setMining", boolean.class);
		java.lang.reflect.Method swinging = com.totalgolems.entity.WorkerGolem.class.getDeclaredMethod("setSwingingArm", boolean.class);
		mining.setAccessible(true);
		swinging.setAccessible(true);
		for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
			if (entity instanceof com.totalgolems.entity.MiningGolem miner) {
				mining.invoke(miner, true);
			} else if (entity instanceof com.totalgolems.entity.WorkerGolem worker) {
				swinging.invoke(worker, true);
			}
		}
	}

	private static void summon(final TestServerContext server, final String tier, final double z, final int yaw, final String flower, final String tool) {
		String equipment = flower == null ? "" : ",equipment:{mainhand:{id:\"minecraft:" + tool + "\",count:1},saddle:{id:\"minecraft:" + flower + "\",count:1}}";
		server.runCommand("execute at @p run summon totalgolems:" + tier + "_mining_golem ~2.6 ~ ~" + z + " {NoAI:1b,unlocked:1b,Rotation:[" + yaw + "f,0f]" + equipment + "}");
	}
}
