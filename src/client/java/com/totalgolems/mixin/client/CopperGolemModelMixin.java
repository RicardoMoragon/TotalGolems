package com.totalgolems.mixin.client;

import com.totalgolems.client.render.GolemWave;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.animal.golem.CopperGolemModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CopperGolemModel.class)
public class CopperGolemModelMixin {
	@Shadow
	@Final
	private ModelPart rightArm;

	// The copper golem waves with its right hand. The mod's golems, whose model extends this one, pose their own wave.
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/CopperGolemRenderState;)V", at = @At("TAIL"))
	private void totalgolems$wave(final CopperGolemRenderState state, final CallbackInfo ci) {
		if ((Object)this.getClass() == CopperGolemModel.class) {
			GolemWave.apply(this.rightArm, ((FabricRenderState)state).getDataOrDefault(GolemWave.TICKS, -1.0F), false);
		}
	}
}
