package com.totalgolems.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.totalgolems.TotalGolems;
import com.totalgolems.block.MetalChestBlockEntity;
import it.unimi.dsi.fastutil.floats.Float2FloatFunction;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.MultiblockChestResources;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BrightnessCombiner;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner.NeighborCombineResult;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Vanilla's ChestRenderer picks its texture from a closed enum, so the iron and diamond chests get
 * their own renderer that is otherwise the same: same chest models, same lid animation.
 */
public class MetalChestRenderer implements BlockEntityRenderer<MetalChestBlockEntity, MetalChestRenderer.State> {
	private static final MultiblockChestResources<SpriteId> IRON = sprites("iron");
	/** Each chest block's textures, found by its name: hay_chest uses hay, hay_left and hay_right. */
	private static final Map<Block, MultiblockChestResources<SpriteId>> TEXTURES = new HashMap<>();
	private final SpriteGetter sprites;
	private final MultiblockChestResources<ChestModel> models;

	public MetalChestRenderer(final BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
		this.models = ChestRenderer.LAYERS.map(layer -> new ChestModel(context.bakeLayer(layer)));
	}

	private static MultiblockChestResources<SpriteId> sprites(final String name) {
		return new MultiblockChestResources<>(TotalGolems.id(name), TotalGolems.id(name + "_left"), TotalGolems.id(name + "_right")).map(Sheets.CHEST_MAPPER::apply);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(
		final MetalChestBlockEntity blockEntity,
		final State state,
		final float partialTicks,
		final Vec3 cameraPosition,
		final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
	) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
		BlockState blockState = blockEntity.getBlockState();
		state.type = blockState.getValue(ChestBlock.TYPE);
		state.facing = blockState.getValue(ChestBlock.FACING);
		state.textures = TEXTURES.computeIfAbsent(
			blockState.getBlock(), block -> sprites(BuiltInRegistries.BLOCK.getKey(block).getPath().replace("_chest", ""))
		);
		if (blockEntity.getLevel() != null && blockState.getBlock() instanceof ChestBlock chestBlock) {
			NeighborCombineResult<? extends ChestBlockEntity> combineResult = chestBlock.combine(
				blockState, blockEntity.getLevel(), blockEntity.getBlockPos(), true
			);
			state.open = ((Float2FloatFunction)combineResult.apply(ChestBlock.opennessCombiner(blockEntity))).get(partialTicks);
			if (state.type != ChestType.SINGLE) {
				state.lightCoords = ((Int2IntFunction)combineResult.apply(new BrightnessCombiner())).applyAsInt(state.lightCoords);
			}
		} else {
			state.open = blockEntity.getOpenNess(partialTicks);
		}
	}

	@Override
	public void submit(final State state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.mulPose(ChestRenderer.modelTransformation(state.facing));
		float open = 1.0F - state.open;
		open = 1.0F - open * open * open;
		SpriteId spriteId = state.textures.select(state.type);
		ChestModel model = this.models.select(state.type);
		submitNodeCollector.submitModel(model, open, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, spriteId, this.sprites, 0);
		if (state.breakProgress != null) {
			submitNodeCollector.order(1)
				.submitCrumblingOverlay(
					model, open, poseStack, spriteId.renderType(model.renderType()), state.lightCoords, OverlayTexture.NO_OVERLAY, -1, state.breakProgress
				);
		}

		poseStack.popPose();
	}

	public static class State extends ChestRenderState {
		public MultiblockChestResources<SpriteId> textures = IRON;
	}
}
