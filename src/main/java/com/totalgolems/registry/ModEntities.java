package com.totalgolems.registry;

import com.totalgolems.GolemTier;
import com.totalgolems.TotalGolems;
import com.totalgolems.entity.BoneGolem;
import com.totalgolems.entity.CoalGolem;
import com.totalgolems.entity.HayGolem;
import com.totalgolems.entity.LumberGolem;
import com.totalgolems.entity.MiningGolem;
import com.totalgolems.entity.SpongeGolem;
import com.totalgolems.entity.WorkerGolem;
import java.util.List;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {
	public static final EntityType<MiningGolem> IRON_MINING_GOLEM = registerMiner("iron_mining_golem", GolemTier.IRON);
	public static final EntityType<MiningGolem> DIAMOND_MINING_GOLEM = registerMiner("diamond_mining_golem", GolemTier.DIAMOND);
	public static final EntityType<MiningGolem> NETHERITE_MINING_GOLEM = registerMiner("netherite_mining_golem", GolemTier.NETHERITE);
	public static final EntityType<CoalGolem> COAL_GOLEM = register("coal_golem", CoalGolem::new);
	public static final EntityType<LumberGolem> AMETHYST_GOLEM = register("amethyst_golem", LumberGolem::new);
	public static final EntityType<SpongeGolem> SPONGE_GOLEM = register("sponge_golem", SpongeGolem::new);
	public static final EntityType<HayGolem> HAY_GOLEM = register("hay_golem", HayGolem::new);
	public static final EntityType<BoneGolem> BONE_GOLEM = register("bone_golem", BoneGolem::new);

	private static EntityType<MiningGolem> registerMiner(final String name, final GolemTier tier) {
		return register(name, (entityType, level) -> new MiningGolem(entityType, level, tier));
	}

	private static <T extends WorkerGolem> EntityType<T> register(final String name, final EntityType.EntityFactory<T> factory) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, TotalGolems.id(name));
		// Same hitbox as the copper golem, whose model these golems share.
		EntityType<T> type = EntityType.Builder.of(factory, MobCategory.MISC).fireImmune().sized(0.49F, 0.98F).eyeHeight(0.8125F).clientTrackingRange(10).build(key);
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}

	/** Every golem of the mod, for the things they all share, such as their renderer. */
	public static List<EntityType<? extends WorkerGolem>> all() {
		return List.of(IRON_MINING_GOLEM, DIAMOND_MINING_GOLEM, NETHERITE_MINING_GOLEM, COAL_GOLEM, AMETHYST_GOLEM, SPONGE_GOLEM, HAY_GOLEM, BONE_GOLEM);
	}

	public static void init() {
		for (GolemTier tier : GolemTier.values()) {
			FabricDefaultAttributeRegistry.register(tier.entityType(), MiningGolem.createAttributes(tier));
		}

		FabricDefaultAttributeRegistry.register(COAL_GOLEM, CoalGolem.createAttributes());
		FabricDefaultAttributeRegistry.register(AMETHYST_GOLEM, LumberGolem.createAttributes());
		FabricDefaultAttributeRegistry.register(SPONGE_GOLEM, SpongeGolem.createAttributes());
		FabricDefaultAttributeRegistry.register(HAY_GOLEM, HayGolem.createAttributes());
		FabricDefaultAttributeRegistry.register(BONE_GOLEM, BoneGolem.createAttributes());
	}
}
