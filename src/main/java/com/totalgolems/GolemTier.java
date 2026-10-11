package com.totalgolems;

import com.totalgolems.entity.MiningGolem;
import com.totalgolems.registry.ModBlocks;
import com.totalgolems.registry.ModEntities;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Everything that differs between the golems: what they are built from, how big a tunnel they dig,
 * how much they carry and how fast they work.
 */
public enum GolemTier {
	IRON("iron", 5, 0, 2, 0, 0, false, 30.0),
	DIAMOND("diamond", 15, 1, 3, 2, 0, false, 60.0),
	NETHERITE("netherite", 27, 1, 3, 2, 2, true, 100.0);

	private final String id;
	private final int inventorySlots;
	private final int tunnelRadius;
	private final int tunnelHeight;
	private final int hasteLevel;
	private final int speedLevel;
	private final boolean breaksSliceAtOnce;
	private final double maxHealth;
	private @Nullable List<ItemStack> tools;

	GolemTier(
		final String id,
		final int inventorySlots,
		final int tunnelRadius,
		final int tunnelHeight,
		final int hasteLevel,
		final int speedLevel,
		final boolean breaksSliceAtOnce,
		final double maxHealth
	) {
		this.id = id;
		this.inventorySlots = inventorySlots;
		this.tunnelRadius = tunnelRadius;
		this.tunnelHeight = tunnelHeight;
		this.hasteLevel = hasteLevel;
		this.speedLevel = speedLevel;
		this.breaksSliceAtOnce = breaksSliceAtOnce;
		this.maxHealth = maxHealth;
	}

	public String id() {
		return this.id;
	}

	public int inventorySlots() {
		return this.inventorySlots;
	}

	/** Blocks dug to each side of the tunnel axis: 0 is one block wide, 1 is three blocks wide. */
	public int tunnelRadius() {
		return this.tunnelRadius;
	}

	public int tunnelHeight() {
		return this.tunnelHeight;
	}

	/** How many blocks the golem works on at the same time. */
	public int blocksAtOnce() {
		return this.breaksSliceAtOnce ? (2 * this.tunnelRadius + 1) * this.tunnelHeight : 1;
	}

	public double maxHealth() {
		return this.maxHealth;
	}

	/** Same multiplier the Haste effect gives a player. */
	public float hasteMultiplier() {
		return 1.0F + this.hasteLevel * 0.2F;
	}

	/** Same multiplier the Speed effect gives. */
	public double speedMultiplier() {
		return 1.0 + this.speedLevel * 0.2;
	}

	/** The golem digs as if it carried a pickaxe, shovel and axe of its material, using whichever suits the block. */
	public List<ItemStack> tools() {
		if (this.tools == null) {
			Stream<Item> items = switch (this) {
				case IRON -> Stream.of(Items.IRON_PICKAXE, Items.IRON_SHOVEL, Items.IRON_AXE);
				case DIAMOND -> Stream.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_AXE);
				case NETHERITE -> Stream.of(Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_AXE);
			};
			this.tools = items.map(ItemStack::new).toList();
		}

		return this.tools;
	}

	public EntityType<MiningGolem> entityType() {
		return switch (this) {
			case IRON -> ModEntities.IRON_MINING_GOLEM;
			case DIAMOND -> ModEntities.DIAMOND_MINING_GOLEM;
			case NETHERITE -> ModEntities.NETHERITE_MINING_GOLEM;
		};
	}

	public Block chestBlock() {
		return switch (this) {
			case IRON -> ModBlocks.IRON_CHEST;
			case DIAMOND -> ModBlocks.DIAMOND_CHEST;
			case NETHERITE -> ModBlocks.NETHERITE_CHEST;
		};
	}

	public static @Nullable GolemTier fromBaseBlock(final BlockState state) {
		if (state.is(Blocks.IRON_BLOCK)) {
			return IRON;
		} else if (state.is(Blocks.DIAMOND_BLOCK)) {
			return DIAMOND;
		} else {
			return state.is(Blocks.NETHERITE_BLOCK) ? NETHERITE : null;
		}
	}
}
