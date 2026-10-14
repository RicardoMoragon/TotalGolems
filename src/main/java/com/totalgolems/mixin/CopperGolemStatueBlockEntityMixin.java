package com.totalgolems.mixin;

import com.totalgolems.CopperGolemEfficiency;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.level.block.entity.CopperGolemStatueBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A copper golem that oxidises into a statue keeps its Efficiency in it, and has it back when scraped to life. */
@Mixin(CopperGolemStatueBlockEntity.class)
public class CopperGolemStatueBlockEntityMixin {
	@Inject(method = "createStatue", at = @At("TAIL"))
	private void totalgolems$keepEfficiency(final CopperGolem copperGolem, final CallbackInfo ci) {
		int level = CopperGolemEfficiency.level(copperGolem);
		if (level > 0) {
			((AttachmentTarget)(Object)this).setAttached(CopperGolemEfficiency.LEVEL, level);
		}
	}

	@Inject(method = "removeStatue", at = @At("RETURN"))
	private void totalgolems$restoreEfficiency(final BlockState state, final CallbackInfoReturnable<CopperGolem> cir) {
		CopperGolem golem = cir.getReturnValue();
		int level = CopperGolemEfficiency.level((AttachmentTarget)(Object)this);
		if (golem != null && level > 0) {
			CopperGolemEfficiency.setLevel(golem, level);
		}
	}
}
