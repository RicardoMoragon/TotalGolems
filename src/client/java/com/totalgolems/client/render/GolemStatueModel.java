package com.totalgolems.client.render;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.level.block.CopperGolemStatueBlock;

/**
 * A golem of the mod standing still as a statue, in the copper golem statue's poses. The copper golem's statue has a
 * model of its own for each pose; these golems each have their own body, so the poses are put on it instead.
 */
public class GolemStatueModel extends Model<CopperGolemStatueBlock.Pose> {
	private final ModelPart body;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public GolemStatueModel(final ModelPart root) {
		// Without culling, so what shows through the bone golem's ribs and the coal golem's holes has its far side.
		super(root, RenderTypes::entityCutout);
		this.body = root.getChild("body");
		this.rightArm = this.body.getChild("right_arm");
		this.leftArm = this.body.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
	}

	@Override
	public void setupAnim(final CopperGolemStatueBlock.Pose pose) {
		super.setupAnim(pose);
		switch (pose) {
			case RUNNING -> {
				this.body.xRot = 0.12F;
				this.body.zRot = -0.08F;
				this.rightArm.xRot = 1.0F;
				this.leftArm.xRot = -0.87F;
				this.rightLeg.xRot = -0.87F;
				this.leftLeg.xRot = 0.79F;
			}
			case SITTING -> {
				// Down on the ground, legs straight out in front and hands on its knees.
				this.body.y += 2.0F;
				this.rightLeg.y += 3.0F;
				this.leftLeg.y += 3.0F;
				this.rightLeg.xRot = -1.5708F;
				this.leftLeg.xRot = -1.5708F;
				this.rightArm.xRot = -0.61F;
				this.leftArm.xRot = -0.61F;
			}
			case STAR -> {
				this.rightArm.zRot = 1.92F;
				this.leftArm.zRot = -1.92F;
				this.rightLeg.zRot = 0.26F;
				this.leftLeg.zRot = -0.26F;
			}
			default -> {
			}
		}

		// As the copper golem's statue: upright on the block's floor, the model being drawn upside down.
		this.root().y = 0.0F;
		this.root().zRot = (float)Math.PI;
	}
}
