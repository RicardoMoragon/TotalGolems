package com.totalgolems.registry;

import com.totalgolems.TotalGolems;
import com.totalgolems.block.GolemStatueBlock;
import com.totalgolems.block.MetalChestBlock;
import com.totalgolems.entity.WorkerGolem;
import java.util.List;
import java.util.function.Supplier;
//? if >=26.1 {
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
//?} else {
/*import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
*///?}
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.jspecify.annotations.Nullable;

public class ModBlocks {
	public static final Block IRON_CHEST = registerChest("iron_chest", MapColor.METAL);
	public static final Block DIAMOND_CHEST = registerChest("diamond_chest", MapColor.DIAMOND);
	public static final Block NETHERITE_CHEST = registerChest("netherite_chest", MapColor.COLOR_BLACK);
	public static final Block AMETHYST_CHEST = registerChest("amethyst_chest", MapColor.COLOR_PURPLE);
	public static final Block HAY_CHEST = registerChest("hay_chest", MapColor.COLOR_YELLOW, SoundType.GRASS);
	public static final Block BONE_CHEST = registerChest("bone_chest", MapColor.SAND, SoundType.BONE_BLOCK);

	public static final Block IRON_GOLEM_STATUE = registerStatue("iron", () -> ModEntities.IRON_MINING_GOLEM, MapColor.METAL, SoundType.METAL);
	public static final Block DIAMOND_GOLEM_STATUE = registerStatue("diamond", () -> ModEntities.DIAMOND_MINING_GOLEM, MapColor.DIAMOND, SoundType.METAL);
	public static final Block NETHERITE_GOLEM_STATUE = registerStatue("netherite", () -> ModEntities.NETHERITE_MINING_GOLEM, MapColor.COLOR_BLACK, SoundType.NETHERITE_BLOCK);
	public static final Block COAL_GOLEM_STATUE = registerStatue("coal", () -> ModEntities.COAL_GOLEM, MapColor.COLOR_BLACK, SoundType.STONE);
	public static final Block AMETHYST_GOLEM_STATUE = registerStatue("amethyst", () -> ModEntities.AMETHYST_GOLEM, MapColor.COLOR_PURPLE, SoundType.AMETHYST);
	public static final Block HAY_GOLEM_STATUE = registerStatue("hay", () -> ModEntities.HAY_GOLEM, MapColor.COLOR_YELLOW, SoundType.GRASS);
	public static final Block BONE_GOLEM_STATUE = registerStatue("bone", () -> ModEntities.BONE_GOLEM, MapColor.SAND, SoundType.BONE_BLOCK);

	/** Every golem statue, one for each golem of the mod. */
	public static final List<Block> STATUES = List.of(
		IRON_GOLEM_STATUE, DIAMOND_GOLEM_STATUE, NETHERITE_GOLEM_STATUE, COAL_GOLEM_STATUE, AMETHYST_GOLEM_STATUE, HAY_GOLEM_STATUE, BONE_GOLEM_STATUE
	);

	private static Block registerChest(final String name, final MapColor color) {
		return registerChest(name, color, SoundType.METAL);
	}

	private static Block registerChest(final String name, final MapColor color, final SoundType sound) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, TotalGolems.id(name));
		Block block = Blocks.register(
			blockKey, MetalChestBlock::new, BlockBehaviour.Properties.of().mapColor(color).strength(3.0F, 6.0F).sound(sound).requiresCorrectToolForDrops()
		);
		registerItem(name, block);
		return block;
	}

	/** {@code <textureId>_golem_statue}, as the copper golem's statue. */
	private static Block registerStatue(
		final String textureId, final Supplier<EntityType<? extends WorkerGolem>> golem, final MapColor color, final SoundType sound
	) {
		String name = textureId + "_golem_statue";
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, TotalGolems.id(name));
		Block block = Blocks.register(
			blockKey,
			properties -> new GolemStatueBlock(textureId, golem, properties),
			//? if >=26.1 {
			BlockBehaviour.Properties.of().mapColor(color).strength(3.0F, 6.0F).sound(sound).pushReaction(PushReaction.POPPED).noOcclusion()
			//?} else {
			/*BlockBehaviour.Properties.of().mapColor(color).strength(3.0F, 6.0F).sound(sound).pushReaction(PushReaction.DESTROY).noOcclusion()
			*///?}
		);
		registerItem(name, block);
		return block;
	}

	private static void registerItem(final String name, final Block block) {
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, TotalGolems.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
	}

	/** The statue the golems of {@code type} turn into, or null for a type with none. */
	public static @Nullable Block statue(final EntityType<?> type) {
		for (Block block : STATUES) {
			if (((GolemStatueBlock)block).getGolemType() == type) {
				return block;
			}
		}

		return null;
	}

	public static void init() {
		//? if >=26.1 {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
		//?} else {
		/*ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
		*///?}
			output.accept(IRON_CHEST);
			output.accept(DIAMOND_CHEST);
			output.accept(NETHERITE_CHEST);
			output.accept(AMETHYST_CHEST);
			output.accept(HAY_CHEST);
			output.accept(BONE_CHEST);
			STATUES.forEach(output::accept);
		});
	}
}
