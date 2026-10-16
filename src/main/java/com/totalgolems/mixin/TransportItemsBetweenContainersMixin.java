package com.totalgolems.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.totalgolems.CopperGolemEfficiency;
import com.totalgolems.ItemFrameFilter;
import com.totalgolems.MinecartStations;
import java.util.Comparator;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The copper golem's errand of carrying items from copper chests to other chests, and to minecarts. */
@Mixin(TransportItemsBetweenContainers.class)
public abstract class TransportItemsBetweenContainersMixin {
	/** How far away the golem looks for somewhere to take what it carries: vanilla's own distances for chests. */
	@Unique
	private static final int SEARCH_HORIZONTAL = 32;
	@Unique
	private static final int SEARCH_VERTICAL = 8;
	/** How often it looks for a minecart to load while it is not loading one. */
	@Unique
	private static final int MINECART_SEARCH_INTERVAL = 20;

	@Shadow
	private TransportItemsBetweenContainers.@Nullable TransportItemTarget target;

	@Shadow
	private TransportItemsBetweenContainers.TransportItemState state;

	/** The minecart the golem is on its way to, or loading. */
	@Unique
	private @Nullable AbstractMinecartContainer totalgolems$minecart;
	@Unique
	private int totalgolems$ticksAtMinecart;
	@Unique
	private long totalgolems$nextMinecartSearch;

	@Shadow
	protected abstract void clearMemoriesAfterMatchingTargetFound(PathfinderMob body);

	@Shadow
	protected abstract void stopTargetingCurrentTarget(PathfinderMob body);

	/**
	 * A golem carrying something takes it to a minecart waiting at a station (see {@link MinecartStations})
	 * when that is nearer than the chest it would take it to, or there is no such chest. It loads the minecart
	 * the way it fills a chest, and if that leaves the minecart full, or something it carries does not fit,
	 * it presses the station's button to send the minecart off. Meanwhile vanilla's errand waits.
	 */
	@Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/PathfinderMob;J)V", at = @At("HEAD"), cancellable = true)
	private void totalgolems$loadMinecarts(final ServerLevel level, final PathfinderMob body, final long timestamp, final CallbackInfo ci) {
		if (!(body instanceof CopperGolem golem)) {
			return;
		}

		ItemStack carried = body.getMainHandItem();
		AbstractMinecartContainer minecart = this.totalgolems$minecart;
		if (minecart != null && (carried.isEmpty() || !MinecartStations.isWaiting(level, minecart) || !MinecartStations.accepts(level, minecart, carried))) {
			this.totalgolems$leaveMinecart(golem);
			minecart = null;
		}

		boolean travelling = this.state == TransportItemsBetweenContainers.TransportItemState.TRAVELLING;
		if (minecart == null && !carried.isEmpty() && travelling && level.getGameTime() >= this.totalgolems$nextMinecartSearch) {
			this.totalgolems$nextMinecartSearch = level.getGameTime() + MINECART_SEARCH_INTERVAL;
			AABB area = new AABB(body.blockPosition()).inflate(SEARCH_HORIZONTAL, SEARCH_VERTICAL, SEARCH_HORIZONTAL);
			minecart = MinecartStations.waitingWithin(level, area)
				.stream()
				.filter(cart -> MinecartStations.accepts(level, cart, carried))
				.min(Comparator.comparingDouble(cart -> cart.distanceToSqr(body)))
				.orElse(null);
			// A chest it is already heading for that is nearer wins.
			if (minecart != null && this.target != null && this.target.pos().distToCenterSqr(body.position()) < minecart.distanceToSqr(body)) {
				minecart = null;
			}

			if (minecart != null) {
				this.stopTargetingCurrentTarget(body);
				this.totalgolems$minecart = minecart;
				this.totalgolems$ticksAtMinecart = 0;
			}
		}

		if (minecart == null) {
			return;
		}

		ci.cancel();
		if (body.distanceTo(minecart) > MinecartStations.REACH) {
			this.totalgolems$ticksAtMinecart = 0;
			BehaviorUtils.setWalkAndLookTargetMemories(body, minecart, 1.0F, 1);
			return;
		}

		body.getNavigation().stop();
		body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		body.getLookControl().setLookAt(minecart);
		int ticks = ++this.totalgolems$ticksAtMinecart;
		if (ticks == 1) {
			golem.setState(CopperGolemState.DROPPING_ITEM);
		} else if (ticks == 9) {
			golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_DROP);
		}

		if (ticks < CopperGolemEfficiency.interactionTicks(CopperGolemEfficiency.level(golem))) {
			return;
		}

		ItemStack left = MinecartStations.load(minecart, carried);
		body.setItemSlot(EquipmentSlot.MAINHAND, left);
		BlockPos button = MinecartStations.button(level, minecart);
		if (button != null && (!left.isEmpty() || MinecartStations.isFull(minecart))) {
			MinecartStations.send(level, button);
		}

		this.totalgolems$leaveMinecart(golem);
		if (left.isEmpty()) {
			this.clearMemoriesAfterMatchingTargetFound(body);
		}
	}

	@Unique
	private void totalgolems$leaveMinecart(final CopperGolem golem) {
		this.totalgolems$minecart = null;
		this.totalgolems$ticksAtMinecart = 0;
		golem.setState(CopperGolemState.IDLE);
	}

	// A chest whose item frames do not show what the golem is carrying is never picked as somewhere to go,
	// so it neither walks up to it nor opens it.
	@Inject(method = "isTargetValidToPick", at = @At("RETURN"), cancellable = true)
	private void totalgolems$filterByItemFrames(
		final PathfinderMob body,
		final Level level,
		final BlockEntity blockEntity,
		final Set<GlobalPos> visitedPositions,
		final Set<GlobalPos> unreachablePositions,
		final AABB targetBlockSearchArea,
		final CallbackInfoReturnable<TransportItemsBetweenContainers.TransportItemTarget> cir
	) {
		ItemStack carried = body.getMainHandItem();
		if (cir.getReturnValue() != null && !carried.isEmpty() && !ItemFrameFilter.accepts(level, blockEntity.getBlockPos(), blockEntity.getBlockState(), carried)) {
			cir.setReturnValue(null);
		}
	}

	// Vanilla only leaves an item in a chest that is empty or already holds it. In a marked chest the frames
	// have the say instead: what matches them goes in wherever there is room, whatever else is inside.
	@WrapOperation(
		method = "doReachedTargetInteraction",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers;matchesLeavingItemsRequirement(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/world/Container;)Z"
		)
	)
	private boolean totalgolems$framesOverrideVanillaRule(final PathfinderMob body, final Container container, final Operation<Boolean> original) {
		TransportItemsBetweenContainers.TransportItemTarget chest = this.target;
		if (chest != null && ItemFrameFilter.isMarked(body.level(), chest.pos(), chest.state())) {
			ItemStack carried = body.getMainHandItem();
			return ItemFrameFilter.accepts(body.level(), chest.pos(), chest.state(), carried) && ItemFrameFilter.hasRoomFor(container, carried);
		}

		return original.call(body, container);
	}

	// Efficiency I to III: more to a trip than vanilla's sixteen, though never more than the item stacks to.
	@Inject(method = "pickUpItems", at = @At("HEAD"), cancellable = true)
	private void totalgolems$carryMore(final PathfinderMob body, final Container container, final CallbackInfo ci) {
		int level = body instanceof CopperGolem golem ? CopperGolemEfficiency.level(golem) : 0;
		if (level <= 0) {
			return;
		}

		ItemStack taken = ItemStack.EMPTY;
		for (int slot = 0; slot < container.getContainerSize() && taken.isEmpty(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty()) {
				taken = container.removeItem(slot, Math.min(CopperGolemEfficiency.itemsPerTrip(level), Math.min(stack.getCount(), stack.getMaxStackSize())));
			}
		}

		body.setItemSlot(EquipmentSlot.MAINHAND, taken);
		body.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		container.setChanged();
		this.clearMemoriesAfterMatchingTargetFound(body);
		ci.cancel();
	}

	// Efficiency IV and V: less time spent at each chest.
	@ModifyExpressionValue(method = "onReachedTarget", at = @At(value = "CONSTANT", args = "intValue=60"))
	private int totalgolems$quickerAtChests(final int original, @Local(argsOnly = true) final PathfinderMob body) {
		return body instanceof CopperGolem golem ? CopperGolemEfficiency.interactionTicks(CopperGolemEfficiency.level(golem)) : original;
	}
}
