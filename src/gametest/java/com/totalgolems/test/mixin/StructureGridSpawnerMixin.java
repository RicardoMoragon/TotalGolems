package com.totalgolems.test.mixin;

import net.minecraft.gametest.framework.StructureGridSpawner;
import org.spongepowered.asm.mixin.Mixin;
//? if <26.1 {
/*import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * Before 26.1 there is no padding round a test, so the tests that dig or fell beyond their structure each run in a
 * batch of their own (see the alone_N test environments). Each batch would start again at the first test's place,
 * cleared only within the structures, and find the last one's pits and stumps around it. This starts each batch on
 * a row of its own, well clear of the rows before.
 */
@Mixin(StructureGridSpawner.class)
public class StructureGridSpawnerMixin {
	//? if <26.1 {
	/*/^* Between one batch's row and the next: more than a quarry's reach, two chunks round its own. ^/
	private static final int BATCH_GAP = 128;

	@Shadow private int currentRowCount;
	@Shadow private AABB rowBounds;
	@Shadow @Final private BlockPos.MutableBlockPos nextTestNorthWestCorner;
	@Shadow @Final private BlockPos firstTestNorthWestCorner;

	@Inject(method = "onBatchStart", at = @At("HEAD"), cancellable = true)
	private void totalgolems$newRow(final ServerLevel level, final CallbackInfo ci) {
		this.nextTestNorthWestCorner.set(
			this.firstTestNorthWestCorner.getX(), this.nextTestNorthWestCorner.getY(), (int)Math.max(this.rowBounds.maxZ, this.nextTestNorthWestCorner.getZ()) + BATCH_GAP
		);
		this.rowBounds = new AABB(this.nextTestNorthWestCorner);
		this.currentRowCount = 0;
		ci.cancel();
	}
	*///?}
}
