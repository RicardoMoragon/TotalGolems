package com.totalgolems.client;

import com.totalgolems.GolemTier;
import com.totalgolems.client.render.AntennaGlintLayer;
import com.totalgolems.client.render.MetalChestRenderer;
import com.totalgolems.client.render.MiningGolemRenderer;
import com.totalgolems.registry.ModBlockEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class TotalGolemsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(AntennaGlintLayer.MODEL_LAYER, AntennaGlintLayer::createAntennaLayer);
		for (GolemTier tier : GolemTier.values()) {
			EntityRenderers.register(tier.entityType(), MiningGolemRenderer::new);
		}

		BlockEntityRenderers.register(ModBlockEntities.METAL_CHEST, MetalChestRenderer::new);
	}
}
