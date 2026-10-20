package com.totalgolems.test;

import static com.totalgolems.test.FarmGolemTests.floor;
import static com.totalgolems.test.FarmGolemTests.give;
import static com.totalgolems.test.FarmGolemTests.spawn;

import com.totalgolems.GolemGreeting;
import com.totalgolems.block.GolemStatueBlock;
import com.totalgolems.entity.HayGolem;
import com.totalgolems.entity.WorkerGolem;
import com.totalgolems.registry.ModBlocks;
import com.totalgolems.registry.ModEntities;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The golems wear with time as the copper golem does, take wax and the axe, and in the end turn into statues; and they
 * greet the player once a day.
 */
public class GolemAgingTests {
	private static final BlockPos CHEST = new BlockPos(0, 1, 3);
	private static final BlockPos STATUE = new BlockPos(3, 1, 3);

	@GameTest
	public void honeycombWaxesAndTheAxeTakesOffWaxThenWear(final GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		WorkerGolem golem = spawn(helper, ModEntities.IRON_MINING_GOLEM, STATUE);
		golem.setWeatherState(WeatheringCopper.WeatherState.WEATHERED);
		give(helper, golem, Items.HONEYCOMB);
		helper.assertTrue(golem.isWaxed(), "honeycomb waxes it");
		Player player = give(helper, golem, Items.IRON_AXE);
		helper.assertTrue(!golem.isWaxed(), "the axe takes the wax off first");
		helper.assertTrue(golem.getWeatherState() == WeatheringCopper.WeatherState.WEATHERED, "without scraping any wear");
		helper.assertTrue(player.getMainHandItem().getDamageValue() == 1, "and that is one use of the axe");
		give(helper, golem, Items.IRON_AXE);
		helper.assertTrue(golem.getWeatherState() == WeatheringCopper.WeatherState.EXPOSED, "then it scrapes off a stage");
		helper.succeed();
	}

	@GameTest
	public void wornOutGolemTurnsToStatueAndTheAxeBringsItBack(final GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		helper.setBlock(CHEST, ModBlocks.HAY_CHEST);
		HayGolem golem = spawn(helper, ModEntities.HAY_GOLEM, STATUE);
		golem.setChestPos(helper.absolutePos(CHEST));
		golem.setItemSlot(WorkerGolem.FLOWER_SLOT, new ItemStack(Items.CORNFLOWER));
		golem.setWeatherState(WeatheringCopper.WeatherState.OXIDIZED);
		golem.turnToStatue(helper.getLevel());
		helper.assertBlockPresent(ModBlocks.HAY_GOLEM_STATUE, STATUE);
		helper.assertBlockProperty(STATUE, GolemStatueBlock.AGE, 3);
		helper.assertTrue(golem.isRemoved(), "the golem is gone, the statue stands in its place");

		// Three scrapes take the wear off, the fourth wakes it.
		for (int age = 2; age >= 0; age--) {
			useAxe(helper);
			helper.assertBlockProperty(STATUE, GolemStatueBlock.AGE, age);
		}

		useAxe(helper);
		helper.assertBlockPresent(Blocks.AIR, STATUE);
		List<HayGolem> golems = helper.getLevel().getEntitiesOfClass(HayGolem.class, helper.getBounds());
		helper.assertTrue(golems.size() == 1, "one golem comes back");
		HayGolem back = golems.getFirst();
		helper.assertTrue(back.getWeatherState() == WeatheringCopper.WeatherState.UNAFFECTED, "as new");
		helper.assertTrue(back.getItemBySlot(WorkerGolem.FLOWER_SLOT).is(Items.CORNFLOWER), "still wearing its flower");
		helper.assertTrue(helper.absolutePos(CHEST).equals(back.getChestPos()), "and knowing its chest");
		helper.succeed();
	}

	@GameTest
	public void waxedStatueDoesNotWearAndAnythingElseChangesItsPose(final GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		helper.setBlock(STATUE, ModBlocks.BONE_GOLEM_STATUE.defaultBlockState().setValue(GolemStatueBlock.AGE, 1));
		use(helper, new ItemStack(Items.HONEYCOMB));
		helper.assertBlockProperty(STATUE, GolemStatueBlock.WAXED, true);
		BlockState waxed = helper.getBlockState(STATUE);
		helper.assertTrue(!waxed.isRandomlyTicking(), "a waxed statue does not wear any further");
		use(helper, ItemStack.EMPTY);
		helper.assertTrue(helper.getBlockState(STATUE).getValue(GolemStatueBlock.POSE) == waxed.getValue(GolemStatueBlock.POSE).getNextPose(), "its pose changes");
		useAxe(helper);
		helper.assertBlockProperty(STATUE, GolemStatueBlock.WAXED, false);
		helper.assertBlockProperty(STATUE, GolemStatueBlock.AGE, 1);
		helper.succeed();
	}

	@GameTest
	public void golemsWaveAtAPlayerTheFirstTimeTheySeeThemInTheDay(final GameTestHelper helper) {
		floor(helper, Blocks.STONE);
		WorkerGolem golem = spawn(helper, ModEntities.BONE_GOLEM, STATUE);
		CopperGolem copper = helper.spawn(EntityTypes.COPPER_GOLEM, 5, 1, 3);
		Player player = helper.makeMockServerPlayerInLevel();
		player.snapTo(helper.absoluteVec(new Vec3(3.5, 1.0, 6.5)));
		helper.succeedWhen(() -> {
			helper.assertTrue(GolemGreeting.greetedToday(golem, player), "the golem should have waved at the player");
			helper.assertTrue(GolemGreeting.greetedToday(copper, player), "and so should the copper golem");
		});
	}

	private static void useAxe(final GameTestHelper helper) {
		use(helper, new ItemStack(Items.IRON_AXE));
	}

	private static void use(final GameTestHelper helper, final ItemStack held) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		BlockPos pos = helper.absolutePos(STATUE);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		helper.getBlockState(STATUE).useItemOn(held, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
	}
}
