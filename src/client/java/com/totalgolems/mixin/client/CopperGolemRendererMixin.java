package com.totalgolems.mixin.client;

import com.totalgolems.CopperGolemEfficiency;
import com.totalgolems.GolemGreeting;
import com.totalgolems.client.render.CopperGolemEfficiencyLayer;
import com.totalgolems.client.render.GolemWave;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.renderer.entity.CopperGolemRenderer;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CopperGolemRenderer.class)
public class CopperGolemRendererMixin {
	// The Efficiency level travels with the render state, for the glint and the dots on the forehead, and so does its wave.
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/animal/golem/CopperGolem;Lnet/minecraft/client/renderer/entity/state/CopperGolemRenderState;F)V",
		at = @At("TAIL")
	)
	private void totalgolems$extractEfficiency(final CopperGolem entity, final CopperGolemRenderState state, final float partialTicks, final CallbackInfo ci) {
		((FabricRenderState)state).setData(CopperGolemEfficiencyLayer.EFFICIENCY, CopperGolemEfficiency.level(entity));
		((FabricRenderState)state).setData(GolemWave.TICKS, GolemGreeting.waveTicks(entity, partialTicks));
	}
}
