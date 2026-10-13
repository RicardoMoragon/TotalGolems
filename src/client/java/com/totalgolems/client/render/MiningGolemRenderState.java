package com.totalgolems.client.render;

import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;

/** Extends the copper golem's state so the vanilla copper golem model can be driven unchanged. */
public class MiningGolemRenderState extends CopperGolemRenderState {
	/** Which golem this is, as it appears in its texture file names. */
	public String textureId = "iron";
	/** Names the texture of the lit eyes, which an upgraded golem swaps for another. */
	public String eyesTextureId = "iron";
	/** Lights the eyes. */
	public boolean active;
	public boolean mining;
}
