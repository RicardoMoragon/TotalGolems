package com.totalgolems.mixin;

import com.totalgolems.CopperGolemEfficiency;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CopperGolem.class)
public class CopperGolemMixin {
	// Efficiency books come before anything else the golem does with what it is handed.
	@Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
	private void totalgolems$takeEfficiencyBook(final Player player, final InteractionHand hand, final CallbackInfoReturnable<InteractionResult> cir) {
		InteractionResult result = CopperGolemEfficiency.interact((CopperGolem)(Object)this, player, hand);
		if (result != null) {
			cir.setReturnValue(result);
		}
	}
}
