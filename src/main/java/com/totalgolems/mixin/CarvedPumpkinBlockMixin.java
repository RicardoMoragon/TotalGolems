package com.totalgolems.mixin;

import com.totalgolems.entity.MiningGolemSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CarvedPumpkinBlock.class)
public class CarvedPumpkinBlockMixin {
	// TAIL is only reached when no snow or iron golem was built, so vanilla patterns always win.
	@Inject(method = "trySpawnGolem", at = @At("TAIL"))
	private void totalgolems$trySpawnMiningGolem(final Level level, final BlockPos topPos, final CallbackInfo ci) {
		MiningGolemSpawner.trySpawn(level, topPos);
	}
}
