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
			// One of each golem: plain, Fortune (red antenna) and Silk Touch (blue antenna).
			summon(server, "iron", -1.6, 70, "poppy", 0);
			summon(server, "diamond", -0.5, 80, "dandelion", 3);
			summon(server, "netherite", 0.6, 100, "poppy", 4);
			summon(server, "netherite", 1.7, 110, null, 0);
			server.runCommand("give @p totalgolems:iron_chest");
			server.runCommand("give @p totalgolems:diamond_chest");
			server.runCommand("give @p totalgolems:netherite_chest");
			context.waitTicks(60);
			context.takeScreenshot("totalgolems_scene");
		}
	}

	private static void summon(final TestServerContext server, final String tier, final double z, final int yaw, final String flower, final int enchantment) {
		String equipment = flower == null ? "" : ",equipment:{saddle:{id:\"minecraft:" + flower + "\",count:1}}";
		server.runCommand(
			"execute at @p run summon totalgolems:" + tier + "_mining_golem ~2.6 ~ ~" + z + " {NoAI:1b,Rotation:[" + yaw + "f,0f],enchantment:" + enchantment + equipment + "}"
		);
	}
}
