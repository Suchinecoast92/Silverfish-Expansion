package com.silverfish_expansion.entity;

import com.silverfish_expansion.SilverfishExpansion;
import com.silverfish_expansion.colony.ColonyPresence;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> ARANEO_SILVERFISH_KEY = entityKey("araneo_silverfish");
	public static final ResourceKey<EntityType<?>> MANTIS_SILVERFISH_KEY = entityKey("mantis_silverfish");
	public static final ResourceKey<EntityType<?>> FLY_SILVERFISH_KEY = entityKey("fly_silverfish");
	public static final ResourceKey<EntityType<?>> CRAB_SILVERFISH_KEY = entityKey("crab_silverfish");
	public static final ResourceKey<EntityType<?>> QUEEN_SILVERFISH_KEY = entityKey("queen_silverfish");
	public static final ResourceKey<EntityType<?>> LARVA_KEY = entityKey("larva");

	public static final EntityType<ExpandedSilverfishEntity> ARANEO_SILVERFISH =
			register(ARANEO_SILVERFISH_KEY, SilverfishVariant.ARANEO);
	public static final EntityType<ExpandedSilverfishEntity> MANTIS_SILVERFISH =
			register(MANTIS_SILVERFISH_KEY, SilverfishVariant.MANTIS);
	public static final EntityType<ExpandedSilverfishEntity> FLY_SILVERFISH =
			register(FLY_SILVERFISH_KEY, SilverfishVariant.FLY);
	public static final EntityType<ExpandedSilverfishEntity> CRAB_SILVERFISH =
			register(CRAB_SILVERFISH_KEY, SilverfishVariant.CRAB);

	public static final EntityType<SilverfishQueenEntity> QUEEN_SILVERFISH = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			QUEEN_SILVERFISH_KEY,
			EntityType.Builder.of(SilverfishQueenEntity::new, MobCategory.MONSTER)
					.sized(1.275F, 0.825F)
					.clientTrackingRange(10)
					.build(QUEEN_SILVERFISH_KEY)
	);

	public static final EntityType<SilverfishLarvaEntity> LARVA = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			LARVA_KEY,
			EntityType.Builder.of(SilverfishLarvaEntity::new, MobCategory.MONSTER)
					.sized(0.4F, 0.3F)
					.clientTrackingRange(8)
					.build(LARVA_KEY)
	);

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
		FabricDefaultAttributeRegistry.register(CRAB_SILVERFISH, ExpandedSilverfishEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(QUEEN_SILVERFISH, SilverfishQueenEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(LARVA, SilverfishLarvaEntity.createAttributes());
	}

	/**
	 * Chamber fauna refill only (structure spawn overrides). Initial 3–6 adults are placed
	 * during worldgen; this gate keeps ambient refill from flooding the chamber.
	 */
	public static void registerSpawnPlacements() {
		SpawnPlacements.register(
				ARANEO_SILVERFISH,
				SpawnPlacementTypes.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				ModEntities::checkColonyCombatantSpawn
		);
		SpawnPlacements.register(
				MANTIS_SILVERFISH,
				SpawnPlacementTypes.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				ModEntities::checkColonyCombatantSpawn
		);
		SpawnPlacements.register(
				FLY_SILVERFISH,
				SpawnPlacementTypes.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				ModEntities::checkColonyCombatantSpawn
		);
		SpawnPlacements.register(
				CRAB_SILVERFISH,
				SpawnPlacementTypes.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				ModEntities::checkColonyCombatantSpawn
		);
	}

	private static boolean checkColonyCombatantSpawn(
			EntityType<? extends Monster> type,
			net.minecraft.world.level.ServerLevelAccessor level,
			net.minecraft.world.entity.EntitySpawnReason reason,
			net.minecraft.core.BlockPos pos,
			net.minecraft.util.RandomSource random
	) {
		if (!Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) {
			return false;
		}
		return ColonyPresence.canSpawnCombatant(level, pos);
	}

	public static EntityType<ExpandedSilverfishEntity> typeFor(SilverfishVariant variant) {
		return switch (variant) {
			case ARANEO -> ARANEO_SILVERFISH;
			case MANTIS -> MANTIS_SILVERFISH;
			case FLY -> FLY_SILVERFISH;
			case CRAB -> CRAB_SILVERFISH;
		};
	}
}
