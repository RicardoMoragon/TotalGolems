package com.totalgolems.entity;

import com.totalgolems.GolemTier;
import com.totalgolems.block.MetalChestBlock;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Builds a mining golem from a carved pumpkin placed on an ore block, the way a copper golem is built. */
public class MiningGolemSpawner {
	public static void trySpawn(final Level level, final BlockPos pumpkinPos) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		BlockState pumpkin = level.getBlockState(pumpkinPos);
		if (!pumpkin.is(Blocks.CARVED_PUMPKIN) && !pumpkin.is(Blocks.JACK_O_LANTERN)) {
			return;
		}

		BlockPos basePos = pumpkinPos.below();
		BlockState base = level.getBlockState(basePos);
		GolemTier tier = GolemTier.fromBaseBlock(base);
		if (tier == null) {
			return;
		}

		MiningGolem golem = tier.entityType().create(level, EntitySpawnReason.TRIGGERED);
		if (golem == null) {
			return;
		}

		Direction facing = pumpkin.getValue(CarvedPumpkinBlock.FACING);
		level.setBlock(pumpkinPos, Blocks.AIR.defaultBlockState(), 2);
		level.levelEvent(2001, pumpkinPos, Block.getId(pumpkin));
		level.levelEvent(2001, basePos, Block.getId(base));
		level.setBlock(basePos, ((MetalChestBlock)tier.chestBlock()).stateForGolemSpawn(level, basePos, facing), 3);

		golem.snapTo(pumpkinPos.getX() + 0.5, pumpkinPos.getY() + 0.05, pumpkinPos.getZ() + 0.5, facing.toYRot(), 0.0F);
		golem.setChestPos(basePos);
		level.addFreshEntity(golem);
		golem.playSound(SoundEvents.COPPER_GOLEM_SPAWN);

		for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, golem.getBoundingBox().inflate(5.0))) {
			CriteriaTriggers.SUMMONED_ENTITY.trigger(player, golem);
		}

		level.updateNeighborsAt(pumpkinPos, Blocks.AIR);
	}
}
