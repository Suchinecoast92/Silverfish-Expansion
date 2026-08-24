package com.silverfish_expansion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import org.jetbrains.annotations.Nullable;

/**
 * Shared larva hatch logic for placeable eggs, spawn eggs, and infested-stone breaks.
 */
public final class LarvaSpawner {
	private LarvaSpawner() {
	}

	@Nullable
	public static SilverfishLarvaEntity spawnAt(ServerLevel level, BlockPos pos) {
		SilverfishLarvaEntity larva = ModEntities.LARVA.create(level, EntitySpawnReason.TRIGGERED);
		if (larva == null) {
			return null;
		}
		larva.snapTo(
				pos.getX() + 0.5,
				pos.getY() + 0.1,
				pos.getZ() + 0.5,
				level.random.nextFloat() * 360.0F,
				0.0F
		);
		larva.setPersistenceRequired();
		level.addFreshEntity(larva);
		return larva;
	}
}
