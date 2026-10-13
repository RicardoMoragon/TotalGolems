package com.totalgolems.client;

import com.totalgolems.client.render.MetalChestRenderer;
import com.totalgolems.client.render.MiningGolemRenderer;
import com.totalgolems.entity.WorkerGolem;
import com.totalgolems.registry.ModBlockEntities;
import com.totalgolems.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.EntityType;

public class TotalGolemsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		for (EntityType<? extends WorkerGolem> type : ModEntities.all()) {
			EntityRenderers.register(type, MiningGolemRenderer::new);
		}

		BlockEntityRenderers.register(ModBlockEntities.METAL_CHEST, MetalChestRenderer::new);
	}
}
