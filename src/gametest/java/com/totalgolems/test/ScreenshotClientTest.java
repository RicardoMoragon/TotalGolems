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
			server.runCommand(at + "setblock ~4 ~ ~-2 totalgolems:iron_chest[facing=west]");
			server.runCommand(at + "setblock ~4 ~ ~-1 totalgolems:diamond_chest[facing=west]");
			server.runCommand(at + "setblock ~4 ~ ~2 totalgolems:iron_chest[facing=west,type=left]");
			server.runCommand(at + "setblock ~4 ~ ~1 totalgolems:iron_chest[facing=west,type=right]");
			server.runCommand(at + "setblock ~4 ~1 ~2 totalgolems:diamond_chest[facing=west,type=left]");
			server.runCommand(at + "setblock ~4 ~1 ~1 totalgolems:diamond_chest[facing=west,type=right]");
			server.runCommand(
				at + "summon totalgolems:iron_mining_golem ~2.5 ~ ~-0.5 {NoAI:1b,Rotation:[70f,0f],equipment:{saddle:{id:\"minecraft:poppy\",count:1}}}"
			);
			server.runCommand(
				at + "summon totalgolems:diamond_mining_golem ~2.5 ~ ~0.7 {NoAI:1b,Rotation:[110f,0f],equipment:{saddle:{id:\"minecraft:blue_orchid\",count:1}}}"
			);
			server.runCommand(at + "summon totalgolems:iron_mining_golem ~2.5 ~ ~-1.7 {NoAI:1b,Rotation:[270f,0f]}");
			server.runCommand("give @p totalgolems:iron_chest");
			server.runCommand("give @p totalgolems:diamond_chest");
			context.waitTicks(60);
			context.takeScreenshot("totalgolems_scene");
		}
	}
}
