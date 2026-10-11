package com.totalgolems.registry;

import com.totalgolems.TotalGolems;
import com.totalgolems.block.MetalChestBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlockEntities {
	public static final BlockEntityType<MetalChestBlockEntity> METAL_CHEST = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		TotalGolems.id("metal_chest"),
		FabricBlockEntityTypeBuilder.create(MetalChestBlockEntity::new, ModBlocks.IRON_CHEST, ModBlocks.DIAMOND_CHEST, ModBlocks.NETHERITE_CHEST).build()
	);

	public static void init() {
	}
}
