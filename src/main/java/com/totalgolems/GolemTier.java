package com.totalgolems;

import com.totalgolems.entity.MiningGolem;
import com.totalgolems.registry.ModBlocks;
import com.totalgolems.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Everything that differs between the mining golems: what they are built from, how big a tunnel they
 * dig, how much they carry, how fast they move and how many blocks they work on at once. What they
 * dig with is the pickaxe they are given, whatever the tier.
 */
public enum GolemTier {
	IRON("iron", 9, 0, 2, 1, 0),
	DIAMOND("diamond", 15, 1, 3, 2, 0),
	NETHERITE("netherite", 27, 1, 3, 3, 2);

	private final String id;
	private final int inventorySlots;
	private final int tunnelRadius;
	private final int tunnelHeight;
	private final int blocksAtOnce;
	private final int speedLevel;

	GolemTier(final String id, final int inventorySlots, final int tunnelRadius, final int tunnelHeight, final int blocksAtOnce, final int speedLevel) {
		this.id = id;
		this.inventorySlots = inventorySlots;
		this.tunnelRadius = tunnelRadius;
		this.tunnelHeight = tunnelHeight;
		this.blocksAtOnce = blocksAtOnce;
		this.speedLevel = speedLevel;
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
		return this.blocksAtOnce;
	}

	/** Same multiplier the Speed effect gives. */
	public double speedMultiplier() {
		return 1.0 + this.speedLevel * 0.2;
	}

	/** A netherite golem has to be given a wither rose once before it will take a pattern flower. */
	public boolean startsLocked() {
		return this == NETHERITE;
	}

	public boolean isChest(final BlockState state) {
		return state.is(this.chestBlock());
	}

	/** Whether the block is the chest of a mining golem of any tier. */
	public static boolean isMinerChest(final BlockState state) {
		for (GolemTier tier : values()) {
			if (tier.isChest(state)) {
				return true;
			}
		}

		return false;
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
