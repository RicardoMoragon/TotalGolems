package com.totalgolems.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.totalgolems.ItemFrameFilter;
import java.util.Set;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The copper golem's errand of carrying items from copper chests to other chests. */
@Mixin(TransportItemsBetweenContainers.class)
public class TransportItemsBetweenContainersMixin {
	@Shadow
	private TransportItemsBetweenContainers.@Nullable TransportItemTarget target;

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
}
