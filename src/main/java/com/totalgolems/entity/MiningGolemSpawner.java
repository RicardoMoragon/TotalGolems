package com.totalgolems.entity;

import com.totalgolems.GolemTier;
import com.totalgolems.block.MetalChestBlock;
import com.totalgolems.registry.ModBlocks;
import com.totalgolems.registry.ModEntities;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Builds a golem from a carved pumpkin placed on the right block, the way a copper golem is built. */
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
		EntityType<? extends WorkerGolem> type = golemFor(base);
		WorkerGolem golem = type == null ? null : type.create(level, EntitySpawnReason.TRIGGERED);
		if (golem == null) {
			return;
		}

		Direction facing = pumpkin.getValue(CarvedPumpkinBlock.FACING);
		level.setBlock(pumpkinPos, Blocks.AIR.defaultBlockState(), 2);
		level.levelEvent(2001, pumpkinPos, Block.getId(pumpkin));
		level.levelEvent(2001, basePos, Block.getId(base));
		BlockPos spawnPos;
		if (chestFor(base) instanceof MetalChestBlock chest) {
			level.setBlock(basePos, chest.stateForGolemSpawn(level, basePos, facing), 3);
			golem.setChestPos(basePos);
			// Golems work at the height they are standing at, so put it on the ground in front of its chest when there is room.
			BlockPos front = basePos.relative(facing);
			boolean roomInFront = level.getBlockState(front).getCollisionShape(level, front).isEmpty()
				&& !level.getBlockState(front.below()).getCollisionShape(level, front.below()).isEmpty();
			spawnPos = roomInFront ? front : pumpkinPos;
		} else {
			// A golem without a chest of its own simply takes the place of the block.
			level.setBlock(basePos, Blocks.AIR.defaultBlockState(), 3);
			spawnPos = basePos;
		}

		golem.snapTo(spawnPos.getX() + 0.5, spawnPos.getY() + 0.05, spawnPos.getZ() + 0.5, facing.toYRot(), 0.0F);
		level.addFreshEntity(golem);
		golem.playSound(SoundEvents.COPPER_GOLEM_SPAWN);

		for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, golem.getBoundingBox().inflate(5.0))) {
			CriteriaTriggers.SUMMONED_ENTITY.trigger(player, golem);
		}

		level.updateNeighborsAt(pumpkinPos, Blocks.AIR);
	}

	private static @Nullable EntityType<? extends WorkerGolem> golemFor(final BlockState base) {
		GolemTier tier = GolemTier.fromBaseBlock(base);
		if (tier != null) {
			return tier.entityType();
		} else if (base.is(Blocks.COAL_BLOCK)) {
			return ModEntities.COAL_GOLEM;
		} else if (base.is(Blocks.AMETHYST_BLOCK)) {
			return ModEntities.AMETHYST_GOLEM;
		} else if (base.is(Blocks.HAY_BLOCK)) {
			return ModEntities.HAY_GOLEM;
		} else {
			return base.is(Blocks.BONE_BLOCK) ? ModEntities.BONE_GOLEM : null;
		}
	}

	/** The chest the base block turns into, or null for golems that do not get one. */
	private static @Nullable Block chestFor(final BlockState base) {
		GolemTier tier = GolemTier.fromBaseBlock(base);
		if (tier != null) {
			return tier.chestBlock();
		}

		if (base.is(Blocks.AMETHYST_BLOCK)) {
			return ModBlocks.AMETHYST_CHEST;
		} else if (base.is(Blocks.HAY_BLOCK)) {
			return ModBlocks.HAY_CHEST;
		} else {
			return base.is(Blocks.BONE_BLOCK) ? ModBlocks.BONE_CHEST : null;
		}
	}
}
