package com.totalgolems.block;

import com.totalgolems.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
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

	/** State for the chest a golem leaves behind when it is built, joining a neighbouring chest of the same kind if there is one. */
	public BlockState stateForGolemSpawn(final Level level, final BlockPos pos, final Direction facing) {
		return this.defaultBlockState().setValue(FACING, facing).setValue(TYPE, this.getChestType(level, pos, facing));
	}
}
