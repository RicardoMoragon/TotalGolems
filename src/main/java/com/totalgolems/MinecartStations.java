package com.totalgolems;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Minecart stations for the copper golem: a minecart with a chest (or a hopper) standing still on a rail
 * with a button right above that rail. The golem loads it the way it fills a chest, and once the minecart
 * is full it presses the button to send it off.
 * <p>
 * An item frame beside the button does what one on a chest does: with an item in it, the minecart only
 * takes that item. A station without frames follows the vanilla rule for chests: it takes an item when it
 * is empty or already holds that item.
 */
public final class MinecartStations {
	/** How close the golem has to be to a minecart to load it. */
	public static final double REACH = 1.5;
	/** Slower than this along the rails counts as standing still. */
	private static final double STILL_SPEED_SQR = 1.0E-4;

	private MinecartStations() {
	}

	/** The button that sends {@code cart} off: the one right above the rail it stands on, or null if there is none. */
	public static @Nullable BlockPos button(final Level level, final AbstractMinecart cart) {
		BlockPos rail = cart.blockPosition();
		if (!BaseRailBlock.isRail(level, rail)) {
			rail = rail.below();
			if (!BaseRailBlock.isRail(level, rail)) {
				return null;
			}
		}

		BlockPos button = rail.above();
		return level.getBlockState(button).getBlock() instanceof ButtonBlock ? button : null;
	}

	/** Whether {@code cart} is at a station and standing still there, waiting to be loaded. */
	public static boolean isWaiting(final Level level, final AbstractMinecartContainer cart) {
		return cart.isAlive() && cart.getDeltaMovement().horizontalDistanceSqr() < STILL_SPEED_SQR && button(level, cart) != null;
	}

	/** The minecarts waiting at a station within {@code area}. */
	public static List<AbstractMinecartContainer> waitingWithin(final Level level, final AABB area) {
		return level.getEntitiesOfClass(AbstractMinecartContainer.class, area, cart -> isWaiting(level, cart));
	}

	/** The item frames with an item in them beside the button of {@code cart}'s station. */
	public static List<ItemFrame> frames(final Level level, final AbstractMinecartContainer cart) {
		BlockPos button = button(level, cart);
		return button == null ? List.of() : ItemFrameFilter.framesBeside(level, button);
	}

	/** Whether the station takes {@code stack}: a frame beside its button shows it, or, with no frames, the vanilla rule allows it. */
	public static boolean accepts(final Level level, final AbstractMinecartContainer cart, final ItemStack stack) {
		List<ItemFrame> frames = frames(level, cart);
		if (!frames.isEmpty()) {
			return frames.stream().anyMatch(frame -> ItemStack.isSameItem(frame.getItem(), stack));
		}

		if (cart.isEmpty()) {
			return true;
		}

		for (ItemStack there : (Container)cart) {
			if (ItemStack.isSameItem(there, stack)) {
				return true;
			}
		}

		return false;
	}

	/** Whether there is not a single empty slot left in {@code container}. */
	public static boolean isFull(final Container container) {
		for (ItemStack stack : container) {
			if (stack.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	/** Puts as much of {@code stack} as fits into {@code container}, topping up stacks of it first. Returns what is left. */
	public static ItemStack load(final Container container, final ItemStack stack) {
		ItemStack left = stack.copy();
		for (int slot = 0; slot < container.getContainerSize() && !left.isEmpty(); slot++) {
			ItemStack there = container.getItem(slot);
			if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, left)) {
				int moved = Math.min(left.getCount(), there.getMaxStackSize() - there.getCount());
				there.grow(moved);
				left.shrink(moved);
			}
		}

		for (int slot = 0; slot < container.getContainerSize() && !left.isEmpty(); slot++) {
			if (container.getItem(slot).isEmpty()) {
				container.setItem(slot, left.split(Math.min(left.getCount(), container.getMaxStackSize(left))));
			}
		}

		container.setChanged();
		return left;
	}

	/** Presses the button of a station, which sends its minecart off down the line. */
	public static void send(final Level level, final BlockPos button) {
		BlockState state = level.getBlockState(button);
		if (state.getBlock() instanceof ButtonBlock buttonBlock && !state.getValue(ButtonBlock.POWERED)) {
			buttonBlock.press(state, level, button, null);
		}
	}
}
