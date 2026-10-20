package com.totalgolems.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.totalgolems.block.GolemStatueBlock;
import com.totalgolems.block.GolemStatueBlockEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.CopperGolemStatueBlockRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.CopperGolemStatueBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A golem statue: the golem's own model in the statue's pose, in its skin as worn as the statue is. */
public class GolemStatueRenderer implements BlockEntityRenderer<GolemStatueBlockEntity, GolemStatueRenderer.State> {
	private final BlockEntityRendererProvider.Context context;
	private final Map<String, GolemStatueModel> models = new HashMap<>();

	public GolemStatueRenderer(final BlockEntityRendererProvider.Context context) {
		this.context = context;
	}

	private GolemStatueModel model(final String golem) {
		return this.models.computeIfAbsent(golem, id -> new GolemStatueModel(this.context.entityModelSet().bakeLayer(GolemModelLayers.of(id))));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(
		final GolemStatueBlockEntity blockEntity,
		final State state,
		final float partialTicks,
		final Vec3 cameraPosition,
		final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
	) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
		BlockState blockState = blockEntity.getBlockState();
		state.direction = blockState.getValue(GolemStatueBlock.FACING);
		state.pose = blockState.getValue(GolemStatueBlock.POSE);
		state.age = blockState.getValue(GolemStatueBlock.AGE);
		state.golem = blockState.getBlock() instanceof GolemStatueBlock statue ? statue.getTextureId() : "iron";
	}

	@Override
	public void submit(final State state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.mulPose(CopperGolemStatueBlockRenderer.modelTransformation(state.direction));
		GolemStatueModel model = this.model(state.golem);
		Identifier texture = GolemTextures.body(state.golem, state.age);
		submitNodeCollector.submitModel(model, state.pose, poseStack, texture, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		if (state.breakProgress != null) {
			submitNodeCollector.order(1)
				.submitCrumblingOverlay(model, state.pose, poseStack, model.renderType(texture), state.lightCoords, OverlayTexture.NO_OVERLAY, -1, state.breakProgress);
		}

		poseStack.popPose();
	}

	public static class State extends BlockEntityRenderState {
		public Direction direction = Direction.NORTH;
		public CopperGolemStatueBlock.Pose pose = CopperGolemStatueBlock.Pose.STANDING;
		public int age;
		public String golem = "iron";
	}
}
