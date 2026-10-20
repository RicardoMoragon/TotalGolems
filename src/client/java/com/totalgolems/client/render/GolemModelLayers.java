package com.totalgolems.client.render;

import com.totalgolems.TotalGolems;
import java.util.List;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The shape of each of the mod's golems. All are the copper golem's body without its antenna, with the
 * same parts (body, head, arms, legs) so that its animations and the golems' own work animations fit them,
 * and each with something of its own:
 * <ul>
 * <li>iron: long, heavy arms, like the iron golem's;</li>
 * <li>diamond: everything a size bigger and rougher, with lumps on its shoulders, head and back;</li>
 * <li>netherite: forged like armour, with plated shoulders, a crest along the head and a belt;</li>
 * <li>amethyst: little crystals growing out of its head and arms;</li>
 * <li>hay: a straw hat, a part of its own so that it can hop;</li>
 * <li>bone: a ribcage, see-through between the ribs, with the spine inside;</li>
 * <li>coal: a shell with holes in it, showing a glowing core in each part.</li>
 * </ul>
 * art/golem_geometry.py has the same shapes, for the Blockbench projects.
 */
public final class GolemModelLayers {
	public static final List<String> GOLEMS = List.of("iron", "diamond", "netherite", "amethyst", "hay", "bone", "coal");

	private GolemModelLayers() {
	}

	public static ModelLayerLocation of(final String textureId) {
		return new ModelLayerLocation(TotalGolems.id(textureId + "_golem"), "main");
	}

	public static LayerDefinition create(final String textureId) {
		MeshDefinition mesh = new MeshDefinition().transformed(pose -> pose.translated(0.0F, 24.0F, 0.0F));
		PartDefinition root = mesh.getRoot();
		switch (textureId) {
			case "iron" -> base(
				root,
				CubeListBuilder.create().texOffs(0, 36).addBox(-4.0F, -2.0F, -2.5F, 4.0F, 12.0F, 5.0F),
				CubeListBuilder.create().texOffs(18, 36).addBox(0.0F, -2.0F, -2.5F, 4.0F, 12.0F, 5.0F)
			);
			case "diamond" -> diamond(root);
			case "netherite" -> {
				Parts parts = base(
					root,
					arm(true).texOffs(0, 36).addBox(-4.0F, -2.0F, -3.0F, 5.0F, 3.0F, 6.0F),
					arm(false).texOffs(0, 36).mirror().addBox(-1.0F, -2.0F, -3.0F, 5.0F, 3.0F, 6.0F)
				);
				parts.body.addOrReplaceChild("belt", CubeListBuilder.create().texOffs(0, 46).addBox(-4.5F, -1.5F, -3.5F, 9.0F, 2.0F, 7.0F), PartPose.ZERO);
				parts.head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(22, 36).addBox(-0.5F, -7.0F, -4.0F, 1.0F, 2.0F, 8.0F), PartPose.ZERO);
			}
			case "amethyst" -> {
				Parts parts = base(root, arm(true), arm(false));
				crystal(parts.head, "crystal_head_0", 2.0F, -5.0F, -1.0F, 0.35F, 0.45F, true);
				crystal(parts.head, "crystal_head_1", -2.5F, -5.0F, -3.0F, -0.3F, -0.4F, false);
				crystal(parts.head, "crystal_head_2", 4.0F, -3.0F, 1.5F, 0.0F, 1.1F, false);
				crystal(parts.rightArm, "crystal_right_0", -3.0F, 2.0F, 0.0F, 0.2F, -0.9F, true);
				crystal(parts.rightArm, "crystal_right_1", -3.0F, 5.0F, 1.0F, -0.3F, -1.2F, false);
				crystal(parts.leftArm, "crystal_left_0", 3.0F, 2.0F, 0.0F, 0.2F, 0.9F, true);
				crystal(parts.leftArm, "crystal_left_1", 3.0F, 5.0F, 1.0F, -0.3F, 1.2F, false);
			}
			case "hay" -> {
				Parts parts = base(root, arm(true), arm(false));
				parts.head.addOrReplaceChild(
					"hat",
					CubeListBuilder.create()
						.texOffs(0, 36).addBox(-7.0F, -1.0F, -7.0F, 14.0F, 1.0F, 14.0F)
						.texOffs(0, 51).addBox(-3.0F, -4.0F, -3.0F, 6.0F, 3.0F, 6.0F),
					PartPose.offset(0.0F, -5.0F, 0.0F)
				);
			}
			case "bone" -> {
				Parts parts = base(root, arm(true), arm(false));
				parts.body.addOrReplaceChild("spine", CubeListBuilder.create().texOffs(28, 15).addBox(-1.0F, -6.0F, 1.0F, 2.0F, 6.0F, 2.0F), PartPose.ZERO);
			}
			case "coal" -> {
				Parts parts = base(
					root,
					arm(true).texOffs(48, 36).addBox(-2.0F, 0.0F, -1.0F, 1.0F, 8.0F, 2.0F),
					arm(false).texOffs(54, 36).addBox(1.0F, 0.0F, -1.0F, 1.0F, 8.0F, 2.0F)
				);
				// The glowing cores, a pixel inside the shell.
				parts.body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 36).addBox(-3.0F, -5.0F, -2.0F, 6.0F, 4.0F, 4.0F), PartPose.ZERO);
				parts.head.addOrReplaceChild("core", CubeListBuilder.create().texOffs(20, 36).addBox(-3.0F, -4.0F, -4.0F, 6.0F, 3.0F, 8.0F), PartPose.ZERO);
				parts.rightLeg.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 47).addBox(-3.0F, 1.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.ZERO);
				parts.leftLeg.addOrReplaceChild("core", CubeListBuilder.create().texOffs(8, 47).addBox(1.0F, 1.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.ZERO);
			}
			default -> base(root, arm(true), arm(false));
		}

		return LayerDefinition.create(mesh, 64, 64);
	}

	private record Parts(PartDefinition body, PartDefinition head, PartDefinition rightArm, PartDefinition leftArm, PartDefinition rightLeg, PartDefinition leftLeg) {
	}

	/** The copper golem's arm, to add to. */
	private static CubeListBuilder arm(final boolean right) {
		return right
			? CubeListBuilder.create().texOffs(36, 16).addBox(-3.0F, -1.0F, -2.0F, 3.0F, 10.0F, 4.0F)
			: CubeListBuilder.create().texOffs(50, 16).addBox(0.0F, -1.0F, -2.0F, 3.0F, 10.0F, 4.0F);
	}

	/** The copper golem without its antenna, with the given arms. */
	private static Parts base(final PartDefinition root, final CubeListBuilder rightArm, final CubeListBuilder leftArm) {
		PartDefinition body = root.addOrReplaceChild(
			"body", CubeListBuilder.create().texOffs(0, 15).addBox(-4.0F, -6.0F, -3.0F, 8.0F, 6.0F, 6.0F), PartPose.offset(0.0F, -5.0F, 0.0F)
		);
		PartDefinition head = body.addOrReplaceChild(
			"head",
			CubeListBuilder.create()
				.texOffs(0, 0).addBox(-4.0F, -5.0F, -5.0F, 8.0F, 5.0F, 10.0F, new CubeDeformation(0.015F))
				.texOffs(56, 0).addBox(-1.0F, -2.0F, -6.0F, 2.0F, 3.0F, 2.0F),
			PartPose.offset(0.0F, -6.0F, 0.0F)
		);
		PartDefinition right = body.addOrReplaceChild("right_arm", rightArm, PartPose.offset(-4.0F, -6.0F, 0.0F));
		PartDefinition left = body.addOrReplaceChild("left_arm", leftArm, PartPose.offset(4.0F, -6.0F, 0.0F));
		PartDefinition rightLeg = root.addOrReplaceChild(
			"right_leg", CubeListBuilder.create().texOffs(0, 27).addBox(-4.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F), PartPose.offset(0.0F, -5.0F, 0.0F)
		);
		PartDefinition leftLeg = root.addOrReplaceChild(
			"left_leg", CubeListBuilder.create().texOffs(16, 27).addBox(0.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F), PartPose.offset(0.0F, -5.0F, 0.0F)
		);
		return new Parts(body, head, right, left, rightLeg, leftLeg);
	}

	private static void diamond(final PartDefinition root) {
		PartDefinition body = root.addOrReplaceChild(
			"body",
			CubeListBuilder.create()
				.texOffs(0, 16).addBox(-5.0F, -7.0F, -3.5F, 10.0F, 7.0F, 7.0F)
				.texOffs(14, 41).addBox(-3.0F, -6.0F, 3.5F, 5.0F, 4.0F, 1.0F),
			PartPose.offset(0.0F, -5.0F, 0.0F)
		);
		body.addOrReplaceChild(
			"head",
			CubeListBuilder.create()
				.texOffs(0, 0).addBox(-4.5F, -5.0F, -5.0F, 9.0F, 5.0F, 10.0F, new CubeDeformation(0.015F))
				.texOffs(56, 0).addBox(-1.0F, -2.0F, -6.0F, 2.0F, 3.0F, 2.0F)
				.texOffs(0, 41).addBox(0.5F, -6.0F, -3.0F, 3.0F, 1.0F, 4.0F),
			PartPose.offset(0.0F, -7.0F, 0.0F)
		);
		body.addOrReplaceChild(
			"right_arm",
			CubeListBuilder.create()
				.texOffs(38, 0).addBox(-4.0F, -1.0F, -2.5F, 4.0F, 10.0F, 5.0F)
				.texOffs(40, 31).addBox(-4.5F, -2.0F, -3.0F, 5.0F, 2.0F, 6.0F),
			PartPose.offset(-5.0F, -7.0F, 0.0F)
		);
		body.addOrReplaceChild(
			"left_arm",
			CubeListBuilder.create()
				.texOffs(34, 16).addBox(0.0F, -1.0F, -2.5F, 4.0F, 10.0F, 5.0F)
				.texOffs(40, 31).mirror().addBox(-0.5F, -2.0F, -3.0F, 5.0F, 2.0F, 6.0F),
			PartPose.offset(5.0F, -7.0F, 0.0F)
		);
		root.addOrReplaceChild(
			"right_leg", CubeListBuilder.create().texOffs(0, 31).addBox(-5.0F, 0.0F, -2.5F, 5.0F, 5.0F, 5.0F), PartPose.offset(0.0F, -5.0F, 0.0F)
		);
		root.addOrReplaceChild(
			"left_leg", CubeListBuilder.create().texOffs(20, 31).addBox(0.0F, 0.0F, -2.5F, 5.0F, 5.0F, 5.0F), PartPose.offset(0.0F, -5.0F, 0.0F)
		);
	}

	/** A little amethyst crystal growing out of {@code parent} at the given spot, leaning over by the given angles. */
	private static void crystal(final PartDefinition parent, final String name, final float x, final float y, final float z, final float xRot, final float zRot, final boolean big) {
		CubeListBuilder cube = big
			? CubeListBuilder.create().texOffs(0, 36).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F)
			: CubeListBuilder.create().texOffs(8, 36).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F);
		parent.addOrReplaceChild(name, cube, PartPose.offsetAndRotation(x, y, z, xRot, 0.0F, zRot));
	}
}
