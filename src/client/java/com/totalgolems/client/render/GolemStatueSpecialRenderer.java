package com.totalgolems.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.CopperGolemStatueBlock;
import org.joml.Vector3fc;

/** A golem statue as an item, as the copper golem statue's: {@code totalgolems:golem_statue} in the item model. */
public class GolemStatueSpecialRenderer implements NoDataSpecialModelRenderer {
	private final GolemStatueModel model;
	private final CopperGolemStatueBlock.Pose pose;
	private final Identifier texture;

	public GolemStatueSpecialRenderer(final GolemStatueModel model, final CopperGolemStatueBlock.Pose pose, final Identifier texture) {
		this.model = model;
		this.pose = pose;
		this.texture = texture;
	}

	@Override
	public void submit(
		final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final int lightCoords, final int overlayCoords, final boolean hasFoil, final int outlineColor
	) {
		submitNodeCollector.submitModel(this.model, this.pose, poseStack, this.texture, lightCoords, overlayCoords, outlineColor);
	}

	@Override
	public void getExtents(final Consumer<Vector3fc> output) {
		this.model.setupAnim(this.pose);
		this.model.root().getExtentsForGui(new PoseStack(), output);
	}

	/** Which golem's statue, in which pose, and how worn (0 to 3). */
	public record Unbaked(String golem, CopperGolemStatueBlock.Pose pose, int age) implements NoDataSpecialModelRenderer.Unbaked {
		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(
					Codec.STRING.fieldOf("golem").forGetter(Unbaked::golem),
					CopperGolemStatueBlock.Pose.CODEC.fieldOf("pose").forGetter(Unbaked::pose),
					Codec.intRange(0, 3).optionalFieldOf("age", 0).forGetter(Unbaked::age)
				)
				.apply(i, Unbaked::new)
		);

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public GolemStatueSpecialRenderer bake(final SpecialModelRenderer.BakingContext context) {
			GolemStatueModel model = new GolemStatueModel(context.entityModelSet().bakeLayer(GolemModelLayers.of(this.golem)));
			return new GolemStatueSpecialRenderer(model, this.pose, GolemTextures.body(this.golem, this.age));
		}
	}
}
