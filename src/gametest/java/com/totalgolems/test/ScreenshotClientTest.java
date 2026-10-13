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
			// A second scene further along for the sponge, hay and bone golems and their chests.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~24 -90 12");
			context.waitTicks(5);
			server.runCommand(at + "fill ~1 ~ ~-4 ~6 ~3 ~4 air");
			String[] kinds = {"sponge", "hay", "bone"};
			String[] flowers = {"blue_orchid", "cornflower", "white_tulip"};
			for (int i = 0; i < kinds.length; i++) {
				double z = -1.6 + 1.6 * i;
				server.runCommand(at + "summon totalgolems:" + kinds[i] + "_golem ~3 ~ ~" + z + " {NoAI:1b,Rotation:[" + (75 + 15 * i) + "f,0f],equipment:{saddle:{id:\"minecraft:" + flowers[i] + "\",count:1}}}");
				server.runCommand(at + "setblock ~5 ~ ~" + (2 * i - 2) + " totalgolems:" + kinds[i] + "_chest[facing=west]");
			}

			context.waitTicks(60);
			context.takeScreenshot("totalgolems_farm");
		}
	}

	private static void summon(final TestServerContext server, final String tier, final double z, final int yaw, final String flower, final String tool) {
		String equipment = flower == null ? "" : ",equipment:{mainhand:{id:\"minecraft:" + tool + "\",count:1},saddle:{id:\"minecraft:" + flower + "\",count:1}}";
		server.runCommand("execute at @p run summon totalgolems:" + tier + "_mining_golem ~2.6 ~ ~" + z + " {NoAI:1b,unlocked:1b,Rotation:[" + yaw + "f,0f]" + equipment + "}");
	}
}
