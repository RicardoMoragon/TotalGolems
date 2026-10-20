package com.totalgolems.block;

import com.totalgolems.registry.ModBlockEntities;
import com.totalgolems.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class MetalChestBlock extends ChestBlock {
	public MetalChestBlock(final BlockBehaviour.Properties properties) {
		super(() -> ModBlockEntities.METAL_CHEST, SoundEvents.COPPER_CHEST_OPEN, SoundEvents.COPPER_CHEST_CLOSE, properties);
	}

	@Override
	public BlockEntity newBlockEntity(final BlockPos worldPosition, final BlockState blockState) {
		return new MetalChestBlockEntity(worldPosition, blockState);
	}

	/** Now and then a wisp of straw comes loose from a hay chest and drifts down its side. */
	@Override
	public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
		super.animateTick(state, level, pos, random);
		if (this == ModBlocks.HAY_CHEST && random.nextInt(8) == 0) {
			dropStraw(level, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 0.45, random);
		}
	}

	/** One to three bits of straw falling from around {@code x, y, z}, up to {@code spread} blocks to the side. */
	public static void dropStraw(final Level level, final double x, final double y, final double z, final double spread, final RandomSource random) {
		BlockParticleOption straw = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.HAY_BLOCK.defaultBlockState());
		for (int i = 1 + random.nextInt(3); i > 0; i--) {
			level.addParticle(straw, x + (random.nextDouble() * 2.0 - 1.0) * spread, y + random.nextDouble() * 0.3, z + (random.nextDouble() * 2.0 - 1.0) * spread, 0.0, 0.0, 0.0);
		}
	}

	/** State for the chest a golem leaves behind when it is built, joining a neighbouring chest of the same kind if there is one. */
	public BlockState stateForGolemSpawn(final Level level, final BlockPos pos, final Direction facing) {
		return this.defaultBlockState().setValue(FACING, facing).setValue(TYPE, this.getChestType(level, pos, facing));
	}
}
