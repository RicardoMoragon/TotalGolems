package com.totalgolems.client;

import com.totalgolems.client.render.MetalChestRenderer;
import com.totalgolems.client.render.MiningGolemRenderer;
import com.totalgolems.registry.ModBlockEntities;
import com.totalgolems.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class TotalGolemsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.IRON_MINING_GOLEM, MiningGolemRenderer::new);
		EntityRenderers.register(ModEntities.DIAMOND_MINING_GOLEM, MiningGolemRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.METAL_CHEST, MetalChestRenderer::new);
	}
}
