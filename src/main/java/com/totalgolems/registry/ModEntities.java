package com.totalgolems.registry;

import com.totalgolems.GolemTier;
import com.totalgolems.TotalGolems;
import com.totalgolems.entity.MiningGolem;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {
	public static final EntityType<MiningGolem> IRON_MINING_GOLEM = register("iron_mining_golem", GolemTier.IRON);
	public static final EntityType<MiningGolem> DIAMOND_MINING_GOLEM = register("diamond_mining_golem", GolemTier.DIAMOND);
	public static final EntityType<MiningGolem> NETHERITE_MINING_GOLEM = register("netherite_mining_golem", GolemTier.NETHERITE);

	private static EntityType<MiningGolem> register(final String name, final GolemTier tier) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, TotalGolems.id(name));
		// Same hitbox as the copper golem, whose model these golems share.
		EntityType<MiningGolem> type = EntityType.Builder.<MiningGolem>of((entityType, level) -> new MiningGolem(entityType, level, tier), MobCategory.MISC)
			.fireImmune()
			.sized(0.49F, 0.98F)
			.eyeHeight(0.8125F)
			.clientTrackingRange(10)
			.build(key);
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}

	public static void init() {
		for (GolemTier tier : GolemTier.values()) {
			FabricDefaultAttributeRegistry.register(tier.entityType(), MiningGolem.createAttributes(tier));
		}
	}
}
