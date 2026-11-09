package com.totalgolems.client.render;

import com.totalgolems.TotalGolems;
import net.minecraft.resources.Identifier;

/** Where the golems' textures are, worn or not (see art/make_aging.py, which draws the worn ones). */
public final class GolemTextures {
	private GolemTextures() {
	}

	/** The golem's skin at {@code age} stages of wear, from 0 (as new) to 3. */
	public static Identifier body(final String golem, final int age) {
		return TotalGolems.id("textures/entity/mining_golem/" + golem + "_golem" + (age > 0 ? "_aged_" + age : "") + ".png");
	}
}
