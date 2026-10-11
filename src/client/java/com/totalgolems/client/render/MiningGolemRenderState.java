package com.totalgolems.client.render;

import com.totalgolems.GolemTier;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;

/** Extends the copper golem's state so the vanilla copper golem model can be driven unchanged. */
public class MiningGolemRenderState extends CopperGolemRenderState {
	public GolemTier tier = GolemTier.IRON;
	public boolean mining;
	/** As MiningGolem#getEnchantment: 0 none, 1 to 3 Fortune, 4 Silk Touch. */
	public int enchantment;
}
