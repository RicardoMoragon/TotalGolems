package com.totalgolems.client.render;

import com.totalgolems.GolemGreeting;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** A golem's greeting: one hand raised high and waved from side to side, the hand it is not holding anything in. */
public final class GolemWave {
	/** How long the golem has been waving, or negative if it is not, for the copper golem's render state. */
	public static final RenderStateDataKey<Float> TICKS = RenderStateDataKey.create(() -> "totalgolems:wave_ticks");
	private static final float RAISE_TICKS = 6.0F;

	private GolemWave() {
	}

	/** Poses {@code arm} {@code ticks} into the wave; does nothing when {@code ticks} is negative. */
	public static void apply(final ModelPart arm, final float ticks, final boolean left) {
		if (ticks < 0.0F) {
			return;
		}

		float raised = Mth.clamp(ticks / RAISE_TICKS, 0.0F, 1.0F) * Mth.clamp((GolemGreeting.WAVE_TICKS - ticks) / RAISE_TICKS, 0.0F, 1.0F);
		float eased = raised * raised * (3.0F - 2.0F * raised);
		// Up over the head, a little out to the side, swaying from the shoulder.
		float sway = (0.35F + 0.3F * Mth.sin(ticks * 0.9F)) * (left ? 1.0F : -1.0F);
		arm.xRot = Mth.lerp(eased, arm.xRot, -2.8F);
		arm.yRot = Mth.lerp(eased, arm.yRot, 0.0F);
		arm.zRot = Mth.lerp(eased, arm.zRot, sway);
	}
}
