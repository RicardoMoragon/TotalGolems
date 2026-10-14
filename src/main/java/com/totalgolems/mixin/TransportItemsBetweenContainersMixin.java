package com.totalgolems.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.totalgolems.CopperGolemEfficiency;
import com.totalgolems.ItemFrameFilter;
import java.util.Set;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The copper golem's errand of carrying items from copper chests to other chests. */
@Mixin(TransportItemsBetweenContainers.class)
public abstract class TransportItemsBetweenContainersMixin {
	@Shadow
	private TransportItemsBetweenContainers.@Nullable TransportItemTarget target;

	@Shadow
	protected abstract void clearMemoriesAfterMatchingTargetFound(PathfinderMob body);

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
