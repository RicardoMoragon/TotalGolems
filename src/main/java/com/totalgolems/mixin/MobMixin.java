package com.totalgolems.mixin;

import com.totalgolems.GolemGreeting;
import com.totalgolems.entity.WorkerGolem;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public class MobMixin {
	// A golem's wave, sent from the server when it first sees a player in the day.
	@Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
	private void totalgolems$wave(final byte id, final CallbackInfo ci) {
		if (id == GolemGreeting.WAVE_EVENT && ((Object)this instanceof CopperGolem || (Object)this instanceof WorkerGolem)) {
			GolemGreeting.startWave((Mob)(Object)this);
			ci.cancel();
		}
	}
}
