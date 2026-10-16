package com.totalgolems.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.totalgolems.TotalGolems;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.animal.golem.CopperGolemModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * What Efficiency shows on a copper golem: an enchantment glint on its antenna, and on its back four
 * copper dots, the middle two by two pixels, that light up one by one from the top left to the bottom
 * right. At Efficiency IV all four are lit, dimly; at V they shine.
 */
public class CopperGolemEfficiencyLayer extends RenderLayer<CopperGolemRenderState, CopperGolemModel> {
	public static final RenderStateDataKey<Integer> EFFICIENCY = RenderStateDataKey.create(() -> "totalgolems:copper_golem_efficiency");
	public static final ModelLayerLocation ANTENNA_LAYER = new ModelLayerLocation(TotalGolems.id("copper_golem"), "antenna");
	/** The purple of an enchanted item's shimmer. */
	private static final int GLINT_COLOR = 0xFF8C5AFF;
	private final CopperGolemModel antenna;
	private final CopperGolemModel dots;

	public CopperGolemEfficiencyLayer(final RenderLayerParent<CopperGolemRenderState, CopperGolemModel> renderer, final EntityRendererProvider.Context context) {
		super(renderer);
		this.antenna = new CopperGolemModel(context.bakeLayer(ANTENNA_LAYER));
		this.dots = new CopperGolemModel(context.bakeLayer(ModelLayers.COPPER_GOLEM));
	}

	/**
	 * The copper golem's part tree, so that its animations still apply, with only the antenna's cubes, a
	 * shade larger than the real ones so that the glint sits on top of them.
	 */
	public static LayerDefinition createAntennaLayer() {
		MeshDefinition mesh = new MeshDefinition().transformed(pose -> pose.translated(0.0F, 24.0F, 0.0F));
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, -5.0F, 0.0F));
		body.addOrReplaceChild(
			"head",
			CubeListBuilder.create()
				.texOffs(37, 8)
				.addBox(-1.0F, -9.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.03F))
				.texOffs(37, 0)
				.addBox(-2.0F, -13.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.03F)),
			PartPose.offset(0.0F, -6.0F, 0.0F)
		);
		body.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-4.0F, -6.0F, 0.0F));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(4.0F, -6.0F, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(0.0F, -5.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(0.0F, -5.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 64);
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

		// The enchanted glint texture, scrolled and added on top the way a charged creeper's aura is drawn.
		float scroll = state.ageInTicks * 0.01F % 1.0F;
		submitNodeCollector.order(1)
			.submitModel(
				this.antenna,
				state,
				poseStack,
				RenderTypes.energySwirl(ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR, scroll, scroll),
				lightCoords,
				OverlayTexture.NO_OVERLAY,
				GLINT_COLOR,
				null,
				state.outlineColor
			);
		// Lit like the eyes, whatever the light around.
		submitNodeCollector.order(1)
			.submitModel(
				this.dots, state, poseStack, RenderTypes.eyes(dotsTexture(Math.min(level, 5))), lightCoords, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor
			);
	}
}
