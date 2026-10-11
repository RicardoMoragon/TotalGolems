package com.totalgolems.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.totalgolems.TotalGolems;
import com.totalgolems.entity.MiningGolem;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * The shimmer on an enchanted golem's antenna: red for Fortune, blue for Silk Touch.
 * <p>
 * Vanilla's item glint has a fixed colour, so this draws a scrolling additive texture the way the
 * charged creeper's aura does, tinted, over a copy of the golem model that only has the antenna.
 */
public class AntennaGlintLayer extends RenderLayer<CopperGolemRenderState, MiningGolemModel> {
	public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(TotalGolems.id("mining_golem"), "antenna");
	private static final Identifier TEXTURE = TotalGolems.id("textures/entity/mining_golem/antenna_glint.png");
	private static final int FORTUNE_COLOR = 0xFFFF2A2A;
	private static final int SILK_TOUCH_COLOR = 0xFF2A6CFF;
	private final MiningGolemModel antennaModel;

	public AntennaGlintLayer(final RenderLayerParent<CopperGolemRenderState, MiningGolemModel> renderer, final MiningGolemModel antennaModel) {
		super(renderer);
		this.antennaModel = antennaModel;
	}

	/** The copper golem's part tree, so its animations still apply, with only the antenna's cubes, slightly inflated. */
	public static LayerDefinition createAntennaLayer() {
		MeshDefinition mesh = new MeshDefinition().transformed(pose -> pose.translated(0.0F, 24.0F, 0.0F));
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, -5.0F, 0.0F));
		body.addOrReplaceChild(
			"head",
			CubeListBuilder.create()
				.texOffs(0, 0)
				.addBox(-1.0F, -9.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.1F))
				.texOffs(0, 8)
				.addBox(-2.0F, -13.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.15F)),
			PartPose.offset(0.0F, -6.0F, 0.0F)
		);
		body.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-4.0F, -6.0F, 0.0F));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(4.0F, -6.0F, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(0.0F, -5.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(0.0F, -5.0F, 0.0F));
		return LayerDefinition.create(mesh, 16, 16);
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
		int enchantment = ((MiningGolemRenderState)state).enchantment;
		if (enchantment == 0 || state.isInvisible) {
			return;
		}

		float scroll = state.ageInTicks * 0.01F % 1.0F;
		submitNodeCollector.order(1)
			.submitModel(
				this.antennaModel,
				state,
				poseStack,
				RenderTypes.energySwirl(TEXTURE, scroll, scroll),
				lightCoords,
				OverlayTexture.NO_OVERLAY,
				enchantment == MiningGolem.SILK_TOUCH ? SILK_TOUCH_COLOR : FORTUNE_COLOR,
				null,
				state.outlineColor
			);
	}
}
