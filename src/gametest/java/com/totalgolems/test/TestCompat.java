package com.totalgolems.test;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** What the tests use that is named differently on the Minecraft versions the mod is built for. See Compat. */
final class TestCompat {
	private TestCompat() {
	}

	/** A copper chest, unweathered. */
	static Block copperChest() {
		//? if >=26.1 {
		return Blocks.COPPER_CHEST.weathering().unaffected();
		//?} else {
		/*return Blocks.COPPER_CHEST;
		*///?}
	}

	/** A copper golem statue, fully oxidized. */
	static Block oxidizedCopperGolemStatue() {
		//? if >=26.1 {
		return Blocks.COPPER_GOLEM_STATUE.weathering().oxidized();
		//?} else {
		/*return Blocks.OXIDIZED_COPPER_GOLEM_STATUE;
		*///?}
	}

	/** {@code player} using {@code hand} on {@code mob}, as a right click on it does. */
	static InteractionResult interact(final Mob mob, final Player player, final InteractionHand hand) {
		//? if >=26.1 {
		return mob.interact(player, hand, mob.position());
		//?} else {
		/*return mob.interact(player, hand);
		*///?}
	}
}
