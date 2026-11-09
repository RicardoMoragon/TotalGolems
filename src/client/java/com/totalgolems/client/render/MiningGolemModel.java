package com.totalgolems.client.render;

import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.totalgolems.entity.WorkerGolem;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.animation.definitions.CopperGolemAnimation;
import net.minecraft.client.model.animal.golem.CopperGolemModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.jspecify.annotations.Nullable;

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
 * short the work, so even a moment's work shows one whole blow. The farmer's straw hat hops at each stroke.
 * <p>
 * The rancher holds out its free hand to feed an animal or pick something up, the smelter rummages in chests as
 * the copper golem does, any golem off the ground spreads its legs and arms in a jump, and the netherite golem's
 * crest stays sunk in its head until a wither rose unlocks it.
 * <p>
 * Each golem has a shape of its own (see {@link GolemModelLayers}), none with the copper golem's antenna, so
 * its flower sits on its head instead: tucked in on top, or in the farmer's hat band.
 */
public class MiningGolemModel extends CopperGolemModel {
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	private final @Nullable ModelPart hat;
	private final @Nullable ModelPart crest;
	private final String golem;
	private final KeyframeAnimation walk;
	private final KeyframeAnimation idle;
	private final KeyframeAnimation chestGet;
	private final KeyframeAnimation chestDrop;

	public MiningGolemModel(final ModelPart root, final String golem) {
		super(root);
		this.golem = golem;
		this.body = root.getChild("body");
		this.head = this.body.getChild("head");
		this.rightArm = this.body.getChild("right_arm");
		this.leftArm = this.body.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
		this.hat = this.head.hasChild("hat") ? this.head.getChild("hat") : null;
		this.crest = this.head.hasChild("crest") ? this.head.getChild("crest") : null;
		this.walk = CopperGolemAnimation.COPPER_GOLEM_WALK.bake(root);
		this.idle = CopperGolemAnimation.COPPER_GOLEM_IDLE.bake(root);
		this.chestGet = CopperGolemAnimation.COPPER_GOLEM_CHEST_INTERACTION_NOITEM_GET.bake(root);
		this.chestDrop = CopperGolemAnimation.COPPER_GOLEM_CHEST_INTERACTION_ITEM_DROP.bake(root);
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
			this.chestGet.apply(state.interactionGetItem, state.ageInTicks);
			this.chestDrop.apply(state.interactionDropItem, state.ageInTicks);
			this.rightArm.xRot -= 0.35F;
		}

		float cycle = golem.swingCycle;
		float swing = golem.swingTicks;
		boolean finishing = golem.swingLength >= 0.0F && swing < Math.max(1.0F, Mth.ceil(golem.swingLength / cycle)) * cycle;
		if (cycle > 0.0F && (golem.mining || finishing)) {
			float t = swing / cycle - Mth.floor(swing / cycle);
			switch (golem.textureId) {
				case "amethyst" -> this.axe(t);
				case "hay" -> {
					this.hoe(t);
					this.hopHat(t);
				}
				case "bone" -> this.sword(t);
				default -> this.pickaxe(t);
			}
		} else if (golem.textureId.equals("coal") && golem.active && state.walkAnimationSpeed > 0.05F) {
			this.carry(state);
		}

		if (golem.reachTicks >= 0.0F) {
			this.reach(golem.reachTicks / WorkerGolem.REACH_TICKS);
		}

		if (golem.jump > 0.0F) {
			this.jump(golem.jump, !golem.mining && !finishing && golem.reachTicks < 0.0F);
		}

		this.root().y -= golem.hopLift;

		// A greeting, with the hand that holds no tool.
		GolemWave.apply(this.leftArm, golem.waveTicks, true);
		if (this.crest != null) {
			// Sunk into the head until a wither rose unlocks the golem, then up it comes.
			this.crest.visible = golem.crest > 0.0F;
			this.crest.y += 2.0F * (1.0F - golem.crest);
		}
	}

	/**
	 * The free hand held out, to an animal with its food or down to something on the ground, the golem bowing
	 * towards it; it gets there halfway through, a moment's pause, and back.
	 */
	private void reach(final float t) {
		float out = ease(t, 0.0F, 0.5F) - ease(t, 0.65F, 1.0F);
		this.leftArm.xRot = Mth.lerp(out, this.leftArm.xRot, -1.25F);
		this.leftArm.yRot = Mth.lerp(out, this.leftArm.yRot, 0.2F);
		this.leftArm.zRot = Mth.lerp(out, this.leftArm.zRot, 0.0F);
		this.body.xRot += 0.35F * out;
		this.head.xRot += 0.2F * out;
	}

	/**
	 * In the air: legs apart, one forward and one back, leaning into it, and the arms out for balance unless
	 * they are busy with something.
	 */
	private void jump(final float amount, final boolean armsFree) {
		this.rightLeg.xRot = Mth.lerp(amount, this.rightLeg.xRot, -0.6F);
		this.leftLeg.xRot = Mth.lerp(amount, this.leftLeg.xRot, 0.45F);
		this.body.xRot += 0.12F * amount;
		if (armsFree) {
			this.rightArm.xRot = Mth.lerp(amount, this.rightArm.xRot, -0.5F);
			this.leftArm.xRot = Mth.lerp(amount, this.leftArm.xRot, -0.5F);
			this.rightArm.zRot = Mth.lerp(amount, this.rightArm.zRot, 0.55F);
			this.leftArm.zRot = Mth.lerp(amount, this.leftArm.zRot, -0.55F);
		}
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

	/** Leaning over the field: the hoe lifted and struck down into the soil, the other arm hanging low. */
	private void hoe(final float t) {
		float lift = ease(t, 0.0F, 0.5F) - ease(t, 0.5F, 0.65F);
		// Leaning in a little, the head kept up so that it looks at the soil ahead and not at its feet.
		this.body.xRot = 0.25F;
		this.head.xRot -= 0.15F;
		this.rightArm.xRot = Mth.lerp(lift, -0.3F, -2.4F);
		this.rightArm.yRot = 0.0F;
		this.rightArm.zRot = 0.0F;
		this.leftArm.xRot = -0.3F;
	}

	/** The straw hat jumps off the head as the hoe strikes the soil, and drops back on, a little askew. */
	private void hopHat(final float t) {
		if (this.hat == null) {
			return;
		}

		float hop = Mth.sin(Mth.PI * Mth.clamp((t - 0.62F) / 0.3F, 0.0F, 1.0F));
		this.hat.y -= 3.0F * hop;
		this.hat.xRot -= 0.25F * hop;
		this.hat.zRot += 0.15F * hop;
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
	 * player's arm, two pixels longer than the copper golem's, so it is moved up by that much; less for the
	 * iron golem's longer arm, and further out for the thicker arms of the iron and diamond ones.
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
		float out = switch (this.golem) {
			case "iron", "diamond" -> 1.0F;
			default -> 0.5F;
		};
		float up = "iron".equals(this.golem) ? 1.0F : 2.0F;
		poseStack.translate((arm == HumanoidArm.RIGHT ? -out : out) / 16.0F, -up / 16.0F, 0.0F);
	}

	/**
	 * Where the flower goes, for the layer that draws it with its top at the origin: somewhere of its own on
	 * each golem, from the farmer's hat band to the bone golem's ribcage.
	 */
	public void applyFlowerTransform(final PoseStack poseStack) {
		this.root().translateAndRotate(poseStack);
		this.body.translateAndRotate(poseStack);
		switch (this.golem) {
			case "hay" -> {
				this.head.translateAndRotate(poseStack);
				if (this.hat != null) {
					this.hat.translateAndRotate(poseStack);
				}

				flower(poseStack, 3.0F, -1.0F, 0.5F, 25.0F, 0.55F);
			}
			case "iron" -> {
				// Planted on top of its left shoulder.
				this.leftArm.translateAndRotate(poseStack);
				flower(poseStack, 2.0F, -2.0F, 0.0F, -10.0F, 0.6F);
			}
			case "diamond" -> {
				// Planted on top of its right shoulder.
				this.rightArm.translateAndRotate(poseStack);
				flower(poseStack, -2.0F, -2.0F, 0.0F, 10.0F, 0.6F);
			}
			case "netherite" -> {
				// Tucked into its belt, at the front.
				flower(poseStack, -2.5F, -0.5F, -3.6F, 8.0F, 0.55F);
			}
			case "bone" -> {
				// Growing inside its ribcage, seen between the ribs: small enough that a dead bush, as wide as it
				// is tall, stays within it.
				flower(poseStack, 0.0F, -0.2F, -0.4F, 0.0F, 0.32F);
			}
			case "amethyst" -> {
				// Its stem sunk into the head, so the allium's flower sits low among the crystals.
				this.head.translateAndRotate(poseStack);
				flower(poseStack, -1.5F, -1.5F, 2.5F, -10.0F, 0.7F);
			}
			case "coal" -> {
				// The fungus, smaller, its stalk sunk into the top of the head.
				this.head.translateAndRotate(poseStack);
				flower(poseStack, 1.5F, -3.5F, 1.0F, 8.0F, 0.5F);
			}
			default -> {
				this.head.translateAndRotate(poseStack);
				flower(poseStack, 2.5F, -5.0F, 1.5F, 12.0F, 0.7F);
			}
		}
	}


	/** Moves to {@code x, y, z} (in pixels), leans {@code lean} degrees to the side and shrinks to {@code scale}. */
	private static void flower(final PoseStack poseStack, final float x, final float y, final float z, final float lean, final float scale) {
		poseStack.translate(x / 16.0F, y / 16.0F, z / 16.0F);
		poseStack.mulPose(new Matrix4f().rotation(Axis.ZP.rotationDegrees(lean)));
		poseStack.scale(scale, scale, scale);
		// The layer puts the flower's top at the origin and its foot a block below it.
		//? if >=26.1 {
		poseStack.translate(0.0F, -1.0F, 0.0F);
		//?} else {
		/*// Before 26.1 the layer puts the block half a block lower down.
		poseStack.translate(0.0F, -1.5F, 0.0F);
		*///?}
	}
}
