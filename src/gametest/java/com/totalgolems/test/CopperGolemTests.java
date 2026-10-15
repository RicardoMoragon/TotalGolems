package com.totalgolems.test;

import static com.totalgolems.test.FarmGolemTests.container;
import static com.totalgolems.test.FarmGolemTests.floor;

import com.totalgolems.CopperGolemEfficiency;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CopperGolemStatueBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** Efficiency books for the vanilla copper golem. */
public class CopperGolemTests {
	@GameTest(maxTicks = 2400)
	public void efficiencyOneCarriesThirtyTwoAtATime(final GameTestHelper helper) {
		carries(helper, 1, 32);
	}

	@GameTest(maxTicks = 2400)
	public void efficiencyTwoCarriesFortyEightAtATime(final GameTestHelper helper) {
		carries(helper, 2, 48);
	}

	@GameTest(maxTicks = 2400)
	public void efficiencyThreeCarriesAWholeStack(final GameTestHelper helper) {
		carries(helper, 3, 64);
	}

	/** A copper chest with a stack of iron ingots, a chest to put them in, and a golem of the given level. */
	private static void carries(final GameTestHelper helper, final int level, final int expected) {
		floor(helper, Blocks.SMOOTH_STONE);
		BlockPos source = new BlockPos(1, 1, 1);
		BlockPos destination = new BlockPos(5, 1, 5);
		helper.setBlock(source, Blocks.COPPER_CHEST.weathering().unaffected());
		helper.setBlock(destination, Blocks.CHEST);
		container(helper, source).setItem(0, new ItemStack(Items.IRON_INGOT, 64));
		CopperGolem golem = helper.spawn(EntityTypes.COPPER_GOLEM, 2, 1, 2);
		CopperGolemEfficiency.setLevel(golem, level);
		int[] most = {0};
		helper.onEachTick(() -> most[0] = Math.max(most[0], golem.getMainHandItem().getCount()));
		helper.succeedWhen(() -> {
			helper.assertTrue(container(helper, destination).countItem(Items.IRON_INGOT) >= expected, "the first load should have been put away");
			helper.assertTrue(most[0] == expected, "with Efficiency " + level + " it should carry " + expected + " at a time, not " + most[0]);
		});
	}

	@GameTest
	public void efficiencyBooksAreTakenOnlyInOrder(final GameTestHelper helper) {
		CopperGolem golem = helper.spawn(EntityTypes.COPPER_GOLEM, 2, 1, 2);
		Player player = offer(helper, golem, 2);
		helper.assertTrue(CopperGolemEfficiency.level(golem) == 0, "a golem without Efficiency only takes a book of Efficiency I");
		helper.assertTrue(player.getMainHandItem().getCount() == 1, "the refused book is not used up");
		player = offer(helper, golem, 1);
		helper.assertTrue(CopperGolemEfficiency.level(golem) == 1 && player.getMainHandItem().isEmpty(), "Efficiency I is taken and the book used up");
		player = offer(helper, golem, 1);
		helper.assertTrue(CopperGolemEfficiency.level(golem) == 1 && player.getMainHandItem().getCount() == 1, "a second book of I is refused");
		for (int level = 2; level <= CopperGolemEfficiency.MAX_LEVEL; level++) {
			offer(helper, golem, level);
		}

		helper.assertTrue(CopperGolemEfficiency.level(golem) == CopperGolemEfficiency.MAX_LEVEL, "one after another it goes up to V");
		player = offer(helper, golem, CopperGolemEfficiency.MAX_LEVEL);
		helper.assertTrue(player.getMainHandItem().getCount() == 1, "there is nothing beyond V");
		golem.discard();
		helper.succeed();
	}

	/**
	 * Efficiency V: saved and loaded with the golem, kept in its statue and brought back to life with it,
	 * and given back as a book when it dies. At V it also walks faster.
	 */
	@GameTest
	public void efficiencyIsKeptThroughSavingStatueAndDeath(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CopperGolem golem = helper.spawn(EntityTypes.COPPER_GOLEM, 2, 1, 2);
		CopperGolemEfficiency.setLevel(golem, CopperGolemEfficiency.MAX_LEVEL);
		double baseSpeed = golem.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(golem.getAttributeValue(Attributes.MOVEMENT_SPEED) > baseSpeed * 1.19, "Efficiency V walks a fifth faster");

		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		golem.saveWithoutId(output);
		CompoundTag saved = output.buildResult();
		CopperGolem loaded = EntityTypes.COPPER_GOLEM.create(level, EntitySpawnReason.COMMAND);
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
		helper.assertTrue(CopperGolemEfficiency.level(loaded) == CopperGolemEfficiency.MAX_LEVEL, "the level should survive saving and loading");
		helper.assertTrue(loaded.getAttributeValue(Attributes.MOVEMENT_SPEED) > baseSpeed * 1.19, "and so should the speed");

		BlockPos statuePos = new BlockPos(5, 1, 5);
		Block statue = Blocks.COPPER_GOLEM_STATUE.weathering().oxidized();
		helper.setBlock(statuePos, statue);
		CopperGolemStatueBlockEntity statueEntity = (CopperGolemStatueBlockEntity)level.getBlockEntity(helper.absolutePos(statuePos));
		statueEntity.createStatue(golem);
		helper.assertTrue(CopperGolemEfficiency.level((AttachmentTarget)statueEntity) == CopperGolemEfficiency.MAX_LEVEL, "the statue keeps the level");
		CopperGolem revived = statueEntity.removeStatue(helper.getBlockState(statuePos));
		helper.assertTrue(CopperGolemEfficiency.level(revived) == CopperGolemEfficiency.MAX_LEVEL, "and the golem gets it back when revived");

		golem.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
		List<ItemEntity> books = level.getEntitiesOfClass(ItemEntity.class, golem.getBoundingBox().inflate(3.0), item -> item.getItem().is(Items.ENCHANTED_BOOK));
		helper.assertTrue(books.size() == 1, "it should drop one enchanted book when it dies, found " + books.size());
		int dropped = books.getFirst()
			.getItem()
			.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
			.getLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY));
		helper.assertTrue(dropped == CopperGolemEfficiency.MAX_LEVEL, "the book should be Efficiency V, not " + dropped);
		books.forEach(ItemEntity::discard);
		helper.succeed();
	}

	/** Right-clicks the golem with a book of Efficiency {@code level}. */
	private static Player offer(final GameTestHelper helper, final CopperGolem golem, final int level) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, CopperGolemEfficiency.book(helper.getLevel(), level));
		golem.interact(player, InteractionHand.MAIN_HAND, golem.position());
		return player;
	}
}
