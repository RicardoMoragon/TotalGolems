package com.totalgolems.client.render;

import net.minecraft.client.model.animal.golem.CopperGolemModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.util.Mth;

/** The vanilla copper golem model and animations, plus a pickaxe-style arm swing while digging. */
public class MiningGolemModel extends CopperGolemModel {
	private final ModelPart rightArm;

	public MiningGolemModel(final ModelPart root) {
		super(root);
		this.rightArm = root.getChild("body").getChild("right_arm");
	}

	@Override
	public void setupAnim(final CopperGolemRenderState state) {
		super.setupAnim(state);
		if (state instanceof MiningGolemRenderState miningState && miningState.mining) {
			this.rightArm.xRot = -1.5F + Mth.sin(state.ageInTicks * 0.8F) * 0.7F;
		}
	}
}
