package com.totalgolems.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.animation.definitions.CopperGolemAnimation;
import net.minecraft.client.model.animal.golem.CopperGolemModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The vanilla copper golem model, but a golem with a tool holds it in its right hand, the way a player
 * does, and walks with its arms free instead of carrying it in front of it with both. At its trade each
 * golem has a work animation of its own:
 * <ul>
 * <li>the miners raise the pickaxe high and bring it down hard, the netherite one fastest;</li>
 * <li>the lumberjack chops sideways, twisting its body into each blow;</li>
 * <li>the farmer bends over the field and works the soil with strokes of its hoe;</li>
 * <li>the rancher draws its sword back and thrusts it forward, lunging after it;</li>
 * <li>the smelter, which has no tool, carries its loads in front of it with both arms while it walks.</li>
 * </ul>
 * A swing starts from the beginning when the golem starts working and is always played to the end, however
 * short the work, so even a moment's work shows one whole blow.
 */
public class MiningGolemModel extends CopperGolemModel {
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final KeyframeAnimation walk;
	private final KeyframeAnimation idle;

	public MiningGolemModel(final ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = this.body.getChild("head");
		this.rightArm = this.body.getChild("right_arm");
		this.leftArm = this.body.getChild("left_arm");
		this.walk = CopperGolemAnimation.COPPER_GOLEM_WALK.bake(root);
		this.idle = CopperGolemAnimation.COPPER_GOLEM_IDLE.bake(root);
	}

	@Override
	public void setupAnim(final CopperGolemRenderState state) {
		super.setupAnim(state);
		if (!(state instanceof MiningGolemRenderState golem)) {
			return;
		}

		if (!state.rightHandItemState.isEmpty()) {
			// Not the copper golem's both-hands carry: the plain walk, with the tool held in the right hand.
			this.root().getAllParts().forEach(ModelPart::resetPose);
			this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
			this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
			this.walk.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 2.0F, 2.5F);
			this.idle.apply(state.idleAnimationState, state.ageInTicks);
			this.rightArm.xRot -= 0.35F;
		}

		float cycle = cycleTicks(golem.textureId);
		float swing = golem.swingTicks;
		boolean finishing = golem.swingLength >= 0.0F && swing < Math.max(1.0F, Mth.ceil(golem.swingLength / cycle)) * cycle;
		if (cycle > 0.0F && (golem.mining || finishing)) {
			float t = swing / cycle - Mth.floor(swing / cycle);
			switch (golem.textureId) {
				case "amethyst" -> this.axe(t);
				case "hay" -> this.hoe(t);
				case "bone" -> this.sword(t);
				default -> this.pickaxe(t);
			}
		} else if (golem.textureId.equals("coal") && golem.active && state.walkAnimationSpeed > 0.05F) {
			this.carry(state);
		}
	}

	/** How many ticks one blow takes, or 0 for a golem with no tool to swing. */
	private static float cycleTicks(final String golem) {
		return switch (golem) {
			case "iron" -> 20.0F;
			case "diamond" -> 16.0F;
			case "netherite" -> 13.0F;
			case "amethyst" -> 20.0F;
			case "hay", "bone" -> 16.0F;
			default -> 0.0F;
		};
	}

	/** Eased from 0 to 1 as {@code t} goes from {@code from} to {@code to}. */
	private static float ease(final float t, final float from, final float to) {
		float x = Mth.clamp((t - from) / (to - from), 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/**
	 * The pickaxe raised high over the head through most of the blow, brought down hard in front, a moment's
	 * pause at the bottom, and back up. The body leans back to raise it and into the blow, the other arm
	 * swinging back for balance.
	 */
	private void pickaxe(final float t) {
		float raise = ease(t, 0.0F, 0.55F) - ease(t, 0.55F, 0.7F);
		float blow = ease(t, 0.55F, 0.7F) - ease(t, 0.8F, 1.0F);
		this.rightArm.xRot = Mth.lerp(raise, -0.6F, -3.0F) + 0.5F * blow;
		this.rightArm.yRot = 0.0F;
		this.rightArm.zRot = 0.0F;
		this.leftArm.xRot = 0.5F * blow - 0.3F * raise;
		this.body.xRot = -0.15F * raise + 0.35F * blow;
		this.head.xRot += 0.3F * blow;
	}

	/** A sideways chop: the axe drawn back to the right, then swung across in front, the body turning with it. */
	private void axe(final float t) {
		float back = ease(t, 0.0F, 0.6F) - ease(t, 0.6F, 0.75F);
		this.rightArm.xRot = -1.4F;
		this.rightArm.yRot = Mth.lerp(back, -0.8F, 1.3F);
		this.rightArm.zRot = 0.0F;
		this.leftArm.xRot = -0.4F;
		this.body.yRot = Mth.lerp(back, 0.35F, -0.55F);
		this.head.yRot -= this.body.yRot * 0.5F;
	}

	/** Bent over the field: the hoe lifted and struck down into the soil, the other arm hanging low. */
	private void hoe(final float t) {
		float lift = ease(t, 0.0F, 0.5F) - ease(t, 0.5F, 0.65F);
		this.body.xRot = 0.45F;
		this.head.xRot += 0.25F;
		this.rightArm.xRot = Mth.lerp(lift, -0.3F, -2.4F);
		this.rightArm.yRot = 0.0F;
		this.rightArm.zRot = 0.0F;
		this.leftArm.xRot = -0.3F;
	}

	/** The sword drawn back, then thrust straight ahead, the golem lunging after it. */
	private void sword(final float t) {
		float draw = ease(t, 0.0F, 0.5F) - ease(t, 0.5F, 0.6F);
		float thrust = ease(t, 0.5F, 0.6F) - ease(t, 0.75F, 1.0F);
		this.rightArm.xRot = -1.5F + 0.6F * draw;
		this.rightArm.yRot = 0.0F;
		this.rightArm.zRot = 0.0F;
		this.rightArm.z += 3.0F * draw - 3.0F * thrust;
		this.leftArm.xRot = 0.3F * thrust;
		this.body.xRot = 0.3F * thrust - 0.1F * draw;
		this.body.yRot = 0.3F * draw - 0.2F * thrust;
	}

	/** Both arms out in front, holding its load, which bobs a little with each step. */
	private void carry(final CopperGolemRenderState state) {
		float bob = Mth.cos(state.walkAnimationPos * 2.5F) * 0.08F * Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
		this.rightArm.xRot = -1.0F + bob;
		this.leftArm.xRot = -1.0F + bob;
		this.rightArm.yRot = 0.0F;
		this.leftArm.yRot = 0.0F;
		this.rightArm.zRot = 0.25F;
		this.leftArm.zRot = -0.25F;
	}

	/**
	 * The tool in the hand at the end of the arm, as a player holds one. The item layer places it for a
	 * player's arm, two pixels longer than this one, so it is moved up by that much.
	 */
	@Override
	public void translateToHand(final CopperGolemRenderState state, final HumanoidArm arm, final PoseStack poseStack) {
		if (!(state instanceof MiningGolemRenderState)) {
			super.translateToHand(state, arm, poseStack);
			return;
		}

		this.root().translateAndRotate(poseStack);
		this.body.translateAndRotate(poseStack);
		(arm == HumanoidArm.RIGHT ? this.rightArm : this.leftArm).translateAndRotate(poseStack);
		poseStack.translate((arm == HumanoidArm.RIGHT ? -0.5F : 0.5F) / 16.0F, -2.0F / 16.0F, 0.0F);
	}
}
