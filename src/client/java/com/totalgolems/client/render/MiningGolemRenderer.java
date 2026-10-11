package com.totalgolems.client.render;

import com.totalgolems.GolemTier;
import com.totalgolems.TotalGolems;
import com.totalgolems.entity.MiningGolem;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.CopperGolemRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.BlockDecorationLayer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.state.BlockState;

public class MiningGolemRenderer extends MobRenderer<MiningGolem, CopperGolemRenderState, MiningGolemModel> {
	private final BlockModelResolver blockModelResolver;

	public MiningGolemRenderer(final EntityRendererProvider.Context context) {
		super(context, new MiningGolemModel(context.bakeLayer(ModelLayers.COPPER_GOLEM)), 0.5F);
		this.blockModelResolver = context.getBlockModelResolver();
		this.addLayer(
			new LivingEntityEmissiveLayer<>(
				this,
				state -> texture(state, "_eyes"),
				(state, ageInTicks) -> 1.0F,
				new MiningGolemModel(context.bakeLayer(ModelLayers.COPPER_GOLEM)),
				RenderTypes::eyes,
				false
			)
		);
		this.addLayer(new BlockDecorationLayer<>(this, state -> state.blockOnAntenna, this.model::applyBlockOnAntennaTransform));
	}

	// The state type has to be the copper golem's for its model and layers to fit; it is always our subclass.
	private static Identifier texture(final CopperGolemRenderState state, final String suffix) {
		GolemTier tier = ((MiningGolemRenderState)state).tier;
		return TotalGolems.id("textures/entity/mining_golem/" + tier.id() + "_golem" + suffix + ".png");
	}

	@Override
	public Identifier getTextureLocation(final CopperGolemRenderState state) {
		return texture(state, "");
	}

	@Override
	public CopperGolemRenderState createRenderState() {
		return new MiningGolemRenderState();
	}

	@Override
	public void extractRenderState(final MiningGolem entity, final CopperGolemRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		MiningGolemRenderState miningState = (MiningGolemRenderState)state;
		miningState.tier = entity.getTier();
		miningState.mining = entity.isMining();
		ItemStack flower = entity.getItemBySlot(MiningGolem.FLOWER_SLOT);
		if (flower.getItem() instanceof BlockItem blockItem) {
			BlockItemStateProperties blockItemState = flower.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
			BlockState blockState = blockItemState.apply(blockItem.getBlock().defaultBlockState());
			this.blockModelResolver.update(state.blockOnAntenna, blockState, CopperGolemRenderer.BLOCK_DISPLAY_CONTEXT);
		} else {
			state.blockOnAntenna.clear();
		}
	}
}
