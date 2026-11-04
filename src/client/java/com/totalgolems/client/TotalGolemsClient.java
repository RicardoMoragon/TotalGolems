package com.totalgolems.client;

import com.totalgolems.TotalGolems;
import com.totalgolems.client.render.CopperGolemEfficiencyLayer;
import com.totalgolems.client.render.GolemModelLayers;
import com.totalgolems.client.render.GolemStatueRenderer;
import com.totalgolems.client.render.GolemStatueSpecialRenderer;
import com.totalgolems.client.render.MetalChestRenderer;
import com.totalgolems.client.render.MiningGolemRenderer;
import com.totalgolems.entity.WorkerGolem;
import com.totalgolems.registry.ModBlockEntities;
import com.totalgolems.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
*///?}
import net.minecraft.client.model.animal.golem.CopperGolemModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.world.entity.EntityType;
//? if >=26.1 {
import net.minecraft.world.entity.EntityTypes;
//?}

public class TotalGolemsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		for (String golem : GolemModelLayers.GOLEMS) {
			//? if >=26.1 {
			ModelLayerRegistry.registerModelLayer(GolemModelLayers.of(golem), () -> GolemModelLayers.create(golem));
			//?} else {
			/*EntityModelLayerRegistry.registerModelLayer(GolemModelLayers.of(golem), () -> GolemModelLayers.create(golem));
			*///?}
		}

		for (EntityType<? extends WorkerGolem> type : ModEntities.all()) {
			// Each golem is named after what it is made of: iron_mining_golem, hay_golem...
			String golem = EntityType.getKey(type).getPath().split("_")[0];
			EntityRenderers.register(type, context -> new MiningGolemRenderer(context, golem));
		}

		BlockEntityRenderers.register(ModBlockEntities.METAL_CHEST, MetalChestRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.GOLEM_STATUE, GolemStatueRenderer::new);
		SpecialModelRenderers.ID_MAPPER.put(TotalGolems.id("golem_statue"), GolemStatueSpecialRenderer.Unbaked.MAP_CODEC);
		//? if >=26.1 {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (type == EntityTypes.COPPER_GOLEM) {
		//?} else {
		/*LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (type == EntityType.COPPER_GOLEM) {
		*///?}
				@SuppressWarnings("unchecked")
				RenderLayerParent<CopperGolemRenderState, CopperGolemModel> parent = (RenderLayerParent<CopperGolemRenderState, CopperGolemModel>)renderer;
				helper.register(new CopperGolemEfficiencyLayer(parent, context));
			}
		});
	}
}
