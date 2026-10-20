package com.totalgolems.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.totalgolems.TotalGolems;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.animal.golem.CopperGolemModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * What Efficiency shows on a copper golem: on its back four copper dots, the middle two by two pixels,
 * that light up one by one from the top left to the bottom right. At Efficiency IV all four are lit,
 * dimly; at V they shine.
 */
public class CopperGolemEfficiencyLayer extends RenderLayer<CopperGolemRenderState, CopperGolemModel> {
	public static final RenderStateDataKey<Integer> EFFICIENCY = RenderStateDataKey.create(() -> "totalgolems:copper_golem_efficiency");
	private final CopperGolemModel dots;

	public CopperGolemEfficiencyLayer(final RenderLayerParent<CopperGolemRenderState, CopperGolemModel> renderer, final EntityRendererProvider.Context context) {
		super(renderer);
		this.dots = new CopperGolemModel(context.bakeLayer(ModelLayers.COPPER_GOLEM));
	}

	private static Identifier dotsTexture(final int level) {
		return TotalGolems.id("textures/entity/copper_golem/efficiency_" + level + ".png");
	}

	@Override
	public void submit(
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final CopperGolemRenderState state,
		final float yRot,
		final float xRot
	) {
		int level = ((FabricRenderState)state).getDataOrDefault(EFFICIENCY, 0);
		if (level <= 0 || state.isInvisible) {
			return;
		}

		// Lit like the eyes, whatever the light around.
		submitNodeCollector.order(1)
			.submitModel(
				this.dots, state, poseStack, RenderTypes.eyes(dotsTexture(Math.min(level, 5))), lightCoords, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor
			);
	}
}
