package com.totalgolems;

import com.totalgolems.registry.ModBlockEntities;
import com.totalgolems.registry.ModBlocks;
import com.totalgolems.registry.ModEntities;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public class TotalGolems implements ModInitializer {
	public static final String MOD_ID = "totalgolems";

	@Override
	public void onInitialize() {
		ModBlocks.init();
		ModBlockEntities.init();
		ModEntities.init();
		CopperGolemEfficiency.init();
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
