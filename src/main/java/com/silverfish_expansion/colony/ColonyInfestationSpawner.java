package com.silverfish_expansion.colony;

import com.silverfish_expansion.entity.LarvaSpawner;
import com.silverfish_expansion.entity.ModEntities;
import com.silverfish_expansion.entity.SilverfishVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Silverfish;

/**
 * Spawns the occupant that should emerge from a larva-made infested stone.
 */
public final class ColonyInfestationSpawner {
	private ColonyInfestationSpawner() {
	}

	public static void spawn(ServerLevel level, BlockPos pos, ColonyInfestationData.Maturity maturity) {
		switch (maturity) {
			case LARVA -> {
				if (LarvaSpawner.spawnAt(level, pos) == null) {
					return;
				}
			}
			case JUVENILE -> {
				Silverfish silverfish = EntityType.SILVERFISH.create(level, EntitySpawnReason.TRIGGERED);
				spawnEntity(level, pos, silverfish);
			}
			case ADULT -> {
				SilverfishVariant variant = SilverfishVariant.values()[level.random.nextInt(SilverfishVariant.values().length)];
				EntityType<?> type = ModEntities.typeFor(variant);
				spawnEntity(level, pos, type.create(level, EntitySpawnReason.TRIGGERED));
			}
		}
	}

	private static void spawnEntity(ServerLevel level, BlockPos pos, Entity entity) {
		if (entity == null) {
			return;
		}
		entity.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
		level.addFreshEntity(entity);
		if (entity instanceof Silverfish silverfish) {
			silverfish.spawnAnim();
		}
	}
}
