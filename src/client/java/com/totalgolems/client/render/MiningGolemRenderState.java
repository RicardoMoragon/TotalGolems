package com.totalgolems.client.render;

import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;

/** Extends the copper golem's state so the vanilla copper golem model can be driven unchanged. */
public class MiningGolemRenderState extends CopperGolemRenderState {
	/** Which golem this is, as it appears in its texture file names. */
	public String textureId = "iron";
	/** Names the texture of the lit eyes, which an upgraded golem swaps for another. */
	public String eyesTextureId = "iron";
	/** Has something to do. */
	public boolean active;
	public boolean mining;
	/** Shifts its animated textures along, so that golems side by side do not glint in step. */
	public int effectOffset;
	/** Ticks since it started swinging its tool. */
	public float swingTicks;
	/** How long it swung for, once it has stopped; negative while it is still swinging. */
	public float swingLength = -1.0F;
	/** How many ticks one blow of its tool takes; 0 for a golem with no tool to swing. */
	public float swingCycle;
	/** Ticks since it started waving at a player, or negative if it is not. */
	public float waveTicks = -1.0F;
	/** Stages of wear, from 0 (as new) to 3. */
	public int age;
	/** Ticks since it reached out with its free hand, or negative if it is not. */
	public float reachTicks = -1.0F;
	/** How far into a jump it is, from 0 (feet on the ground) to 1 (in the air). */
	public float jump;
	/** How high off the ground a little hop on the spot has taken it, in pixels. */
	public float hopLift;
	/** How far the netherite golem's crest is up out of its head, from 0 (hidden) to 1. */
	public float crest = 1.0F;
}
