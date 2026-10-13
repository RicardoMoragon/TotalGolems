package com.totalgolems.registry;

import com.totalgolems.TotalGolems;
import com.totalgolems.block.MetalChestBlock;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class ModBlocks {
	public static final Block IRON_CHEST = registerChest("iron_chest", MapColor.METAL);
	public static final Block DIAMOND_CHEST = registerChest("diamond_chest", MapColor.DIAMOND);
	public static final Block NETHERITE_CHEST = registerChest("netherite_chest", MapColor.COLOR_BLACK);
	public static final Block AMETHYST_CHEST = registerChest("amethyst_chest", MapColor.COLOR_PURPLE);
	public static final Block SPONGE_CHEST = registerChest("sponge_chest", MapColor.COLOR_YELLOW, SoundType.SPONGE);
	public static final Block HAY_CHEST = registerChest("hay_chest", MapColor.COLOR_YELLOW, SoundType.GRASS);
	public static final Block BONE_CHEST = registerChest("bone_chest", MapColor.SAND, SoundType.BONE_BLOCK);

	private static Block registerChest(final String name, final MapColor color) {
		return registerChest(name, color, SoundType.METAL);
	}

	private static Block registerChest(final String name, final MapColor color, final SoundType sound) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, TotalGolems.id(name));
		Block block = Blocks.register(
			blockKey, MetalChestBlock::new, BlockBehaviour.Properties.of().mapColor(color).strength(3.0F, 6.0F).sound(sound).requiresCorrectToolForDrops()
		);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, TotalGolems.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(IRON_CHEST);
			output.accept(DIAMOND_CHEST);
			output.accept(NETHERITE_CHEST);
			output.accept(AMETHYST_CHEST);
			output.accept(SPONGE_CHEST);
			output.accept(HAY_CHEST);
			output.accept(BONE_CHEST);
		});
	}
}
