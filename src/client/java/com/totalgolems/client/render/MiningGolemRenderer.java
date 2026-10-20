package com.totalgolems.client.render;

import com.totalgolems.TotalGolems;
import com.totalgolems.entity.WorkerGolem;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.CopperGolemRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.BlockDecorationLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class MiningGolemRenderer extends MobRenderer<WorkerGolem, CopperGolemRenderState, MiningGolemModel> {
	private final BlockModelResolver blockModelResolver;

	/** A renderer for one kind of golem, {@code golem} as in its texture names, with that golem's own model. */
	public MiningGolemRenderer(final EntityRendererProvider.Context context, final String golem) {
		super(context, new MiningGolemModel(context.bakeLayer(GolemModelLayers.of(golem)), golem), 0.5F);
		this.blockModelResolver = context.getBlockModelResolver();
		this.addLayer(
			new LivingEntityEmissiveLayer<>(
				this,
				state -> TotalGolems.id("textures/entity/mining_golem/" + ((MiningGolemRenderState)state).eyesTextureId + "_golem_eyes.png"),
				// The base texture has the eyes switched off; this layer lights them while the golem has work to do.
				(state, ageInTicks) -> ((MiningGolemRenderState)state).active ? 1.0F : 0.0F,
				new MiningGolemModel(context.bakeLayer(GolemModelLayers.of(golem)), golem),
				RenderTypes::eyes,
				false
			)
		);
		// Glints on the ore and amethyst golems and the coal one's glowing core, lit like the eyes.
		this.addLayer(
			new LivingEntityEmissiveLayer<>(
				this,
				MiningGolemRenderer::effectFrame,
				(state, ageInTicks) -> effect(((MiningGolemRenderState)state).textureId) != null && !"bone".equals(((MiningGolemRenderState)state).textureId) ? 1.0F : 0.0F,
				new MiningGolemModel(context.bakeLayer(GolemModelLayers.of(golem)), golem),
				RenderTypes::eyes,
				false
			)
		);
		// The bone golem's cracks, drawn over it.
		this.addLayer(
			new LivingEntityEmissiveLayer<>(
				this,
				MiningGolemRenderer::effectFrame,
				(state, ageInTicks) -> "bone".equals(((MiningGolemRenderState)state).textureId) ? 1.0F : 0.0F,
				new MiningGolemModel(context.bakeLayer(GolemModelLayers.of(golem)), golem),
				RenderTypes::entityTranslucent,
				false
			)
		);
		// The tool of its trade, held in its right hand, and the flower that switches it on.
		this.addLayer(new ItemInHandLayer<>(this));
		this.addLayer(new BlockDecorationLayer<>(this, state -> state.blockOnAntenna, this.model::applyFlowerTransform));
	}

	/**
	 * Frames in the loop and ticks per frame of each golem's animated texture (see art/make_effects.py, which
	 * draws them), or null for one without.
	 */
	private static int @Nullable [] effect(final String golem) {
		return switch (golem) {
			case "iron", "diamond", "netherite", "amethyst" -> new int[]{48, 5};
			case "bone" -> new int[]{64, 3};
			case "coal" -> new int[]{32, 5};
			default -> null;
		};
	}

	private static Identifier effectFrame(final CopperGolemRenderState state) {
		MiningGolemRenderState golem = (MiningGolemRenderState)state;
		int[] effect = effect(golem.textureId);
		int frame = effect == null ? 0 : Math.floorMod((int)(state.ageInTicks / effect[1]) + golem.effectOffset, effect[0]);
		return TotalGolems.id("textures/entity/mining_golem/effect/" + golem.textureId + "_" + frame + ".png");
	}

	// The state type has to be the copper golem's for its model and layers to fit; it is always our subclass.
	@Override
	public Identifier getTextureLocation(final CopperGolemRenderState state) {
		return TotalGolems.id("textures/entity/mining_golem/" + ((MiningGolemRenderState)state).textureId + "_golem.png");
	}

	@Override
	public CopperGolemRenderState createRenderState() {
		return new MiningGolemRenderState();
	}

	@Override
	public void extractRenderState(final WorkerGolem entity, final CopperGolemRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, this.itemModelResolver, partialTicks);
		MiningGolemRenderState miningState = (MiningGolemRenderState)state;
		miningState.textureId = entity.getTextureId();
		miningState.eyesTextureId = entity.getEyesTextureId();
		miningState.active = entity.isActive();
		miningState.mining = entity.isSwingingArm();
		miningState.effectOffset = entity.getId() * 7;
		miningState.swingTicks = entity.tickCount - entity.getSwingStartTick() + partialTicks;
		miningState.swingLength = miningState.mining ? -1.0F : entity.getSwingStopTick() - entity.getSwingStartTick();
		ItemStack flower = entity.getItemBySlot(WorkerGolem.FLOWER_SLOT);
		if (flower.getItem() instanceof BlockItem blockItem) {
			BlockItemStateProperties blockItemState = flower.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
			BlockState blockState = blockItemState.apply(blockItem.getBlock().defaultBlockState());
			this.blockModelResolver.update(state.blockOnAntenna, blockState, CopperGolemRenderer.BLOCK_DISPLAY_CONTEXT);
		} else {
			state.blockOnAntenna.clear();
		}
	}
}
