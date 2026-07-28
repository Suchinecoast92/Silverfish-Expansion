package com.silverfish_expansion.entity;

import com.silverfish_expansion.SilverfishExpansion;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> ARANEO_SILVERFISH_KEY = entityKey("araneo_silverfish");
	public static final ResourceKey<EntityType<?>> MANTIS_SILVERFISH_KEY = entityKey("mantis_silverfish");
	public static final ResourceKey<EntityType<?>> FLY_SILVERFISH_KEY = entityKey("fly_silverfish");

	public static final EntityType<ExpandedSilverfishEntity> ARANEO_SILVERFISH =
			register(ARANEO_SILVERFISH_KEY, SilverfishVariant.ARANEO);
	public static final EntityType<ExpandedSilverfishEntity> MANTIS_SILVERFISH =
			register(MANTIS_SILVERFISH_KEY, SilverfishVariant.MANTIS);
	public static final EntityType<ExpandedSilverfishEntity> FLY_SILVERFISH =
			register(FLY_SILVERFISH_KEY, SilverfishVariant.FLY);

	private ModEntities() {
	}

	private static ResourceKey<EntityType<?>> entityKey(String path) {
		return ResourceKey.create(Registries.ENTITY_TYPE,
				Identifier.fromNamespaceAndPath(SilverfishExpansion.MOD_ID, path));
	}

	private static EntityType<ExpandedSilverfishEntity> register(ResourceKey<EntityType<?>> key, SilverfishVariant variant) {
		return Registry.register(
				BuiltInRegistries.ENTITY_TYPE,
				key,
				EntityType.Builder.<ExpandedSilverfishEntity>of(
								(type, level) -> new ExpandedSilverfishEntity(variant, type, level),
								MobCategory.MONSTER)
						.sized(0.7F, 0.5F)
						.clientTrackingRange(8)
						.build(key)
		);
	}

	public static void registerAttributes() {
		FabricDefaultAttributeRegistry.register(ARANEO_SILVERFISH, ExpandedSilverfishEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(MANTIS_SILVERFISH, ExpandedSilverfishEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(FLY_SILVERFISH, ExpandedSilverfishEntity.createAttributes());
	}
}
