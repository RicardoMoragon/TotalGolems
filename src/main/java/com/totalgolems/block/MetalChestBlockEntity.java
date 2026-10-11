package com.totalgolems.block;

import com.totalgolems.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MetalChestBlockEntity extends ChestBlockEntity {
	public MetalChestBlockEntity(final BlockPos worldPosition, final BlockState blockState) {
		super(ModBlockEntities.METAL_CHEST, worldPosition, blockState);
	}

	@Override
	protected Component getDefaultName() {
		return this.getBlockState().getBlock().getName();
	}
}
