package com.totalgolems.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.totalgolems.TotalGolems;
import com.totalgolems.block.MetalChestBlockEntity;
import it.unimi.dsi.fastutil.floats.Float2FloatFunction;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BrightnessCombiner;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner.NeighborCombineResult;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
//?} else {
/*import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
*///?}

/**
 * Vanilla's ChestRenderer picks its texture from a closed enum, so the iron and diamond chests get
 * their own renderer that is otherwise the same: same chest models, same lid animation.
 */
public class MetalChestRenderer implements BlockEntityRenderer<MetalChestBlockEntity, MetalChestRenderer.State> {
	/** Each chest block's textures, found by its name: hay_chest uses hay, hay_left and hay_right. */
	private static final Map<Block, Parts<Sprite>> TEXTURES = new HashMap<>();
	private static final Parts<Sprite> IRON = sprites("iron");
	//? if >=26.1 {
	private final SpriteGetter sprites;
	//?} else {
	/*private final MaterialSet sprites;
	*///?}
	private final Parts<ChestModel> models;

	public MetalChestRenderer(final BlockEntityRendererProvider.Context context) {
		//? if >=26.1 {
		this.sprites = context.sprites();
		this.models = new Parts<>(ChestRenderer.LAYERS.single(), ChestRenderer.LAYERS.left(), ChestRenderer.LAYERS.right())
			.map(layer -> new ChestModel(context.bakeLayer(layer)));
		//?} else {
		/*this.sprites = context.materials();
		this.models = new Parts<>(ModelLayers.CHEST, ModelLayers.DOUBLE_CHEST_LEFT, ModelLayers.DOUBLE_CHEST_RIGHT)
			.map(layer -> new ChestModel(context.bakeLayer(layer)));
		*///?}
	}

	private static Parts<Sprite> sprites(final String name) {
		return new Parts<>(TotalGolems.id(name), TotalGolems.id(name + "_left"), TotalGolems.id(name + "_right"))
			.map(id -> new Sprite(Sheets.CHEST_MAPPER.apply(id)));
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
		state.direction = blockState.getValue(ChestBlock.FACING);
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
		float open = 1.0F - state.open;
		open = 1.0F - open * open * open;
		ChestModel model = this.models.select(state.type);
		Sprite sprite = state.textures.select(state.type);
		//? if >=26.1 {
		poseStack.mulPose(ChestRenderer.modelTransformation(state.direction));
		submitNodeCollector.submitModel(model, open, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, sprite.id(), this.sprites, 0);
		if (state.breakProgress != null) {
			submitNodeCollector.order(1)
				.submitCrumblingOverlay(
					model, open, poseStack, sprite.id().renderType(model.renderType()), state.lightCoords, OverlayTexture.NO_OVERLAY, -1, state.breakProgress
				);
		}
		//?} else {
		/*// As vanilla's chest renderer before 26.1: turned about the block's middle.
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.direction.toYRot()));
		poseStack.translate(-0.5F, -0.5F, -0.5F);
		submitNodeCollector.submitModel(
			model, open, poseStack, sprite.id().renderType(RenderTypes::entityCutout), state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
			this.sprites.get(sprite.id()), 0, state.breakProgress
		);
		*///?}

		poseStack.popPose();
	}

	/** A chest texture in the chest atlas. */
	//? if >=26.1 {
	private record Sprite(SpriteId id) {
	}
	//?} else {
	/*private record Sprite(Material id) {
	}
	*///?}

	/** One of each for a single chest and the two halves of a double chest. */
	private record Parts<T>(T single, T left, T right) {
		<U> Parts<U> map(final Function<T, U> function) {
			return new Parts<>(function.apply(this.single), function.apply(this.left), function.apply(this.right));
		}

		T select(final ChestType type) {
			return switch (type) {
				case SINGLE -> this.single;
				case LEFT -> this.left;
				case RIGHT -> this.right;
			};
		}
	}

	public static class State extends ChestRenderState {
		public Direction direction = Direction.SOUTH;
		public Parts<Sprite> textures = IRON;
	}
}
