package com.totalgolems.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.totalgolems.CopperGolemEfficiency;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CopperGolemAi.class)
public class CopperGolemAiMixin {
	// The chest is closed again at the end of the golem's visit, which Efficiency IV and V make shorter.
	@ModifyExpressionValue(method = "lambda$onReachedTargetInteraction$0", at = @At(value = "CONSTANT", args = "intValue=60"))
	private static int totalgolems$closeChestSooner(final int original, @Local(argsOnly = true) final PathfinderMob body) {
		return body instanceof CopperGolem golem ? CopperGolemEfficiency.interactionTicks(CopperGolemEfficiency.level(golem)) : original;
	}
}
