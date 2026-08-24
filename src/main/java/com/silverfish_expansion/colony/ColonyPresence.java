package com.silverfish_expansion.colony;

import com.silverfish_expansion.block.ModBlocks;
import com.silverfish_expansion.entity.ExpandedSilverfishEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Soft population pressure for chamber fauna and colony renewal.
 * Ambient structure spawns also pass a ~40% gate in {@code ModEntities}.
 */
public final class ColonyPresence {
	/** Soft cap on adults + vanilla silverfish near a colony focus (seed adults are 3–12). */
	public static final int COMBATANT_CAP = 14;
	public static final double COMBATANT_RADIUS = 48.0;
	/** Max egg clusters near the queen before she stops laying. */
	public static final int EGG_CLUSTER_CAP = 10;
	public static final double EGG_SCAN_RADIUS = 12.0;

	private ColonyPresence() {
	}

	public static int countCombatants(Level level, BlockPos center) {
		AABB box = new AABB(center).inflate(COMBATANT_RADIUS);
		int count = 0;
		for (ExpandedSilverfishEntity ignored : level.getEntitiesOfClass(ExpandedSilverfishEntity.class, box)) {
			count++;
		}
		for (Silverfish ignored : level.getEntitiesOfClass(Silverfish.class, box)) {
			count++;
		}
		return count;
	}

	public static boolean canSpawnCombatant(LevelAccessor level, BlockPos pos) {
		if (!(level instanceof Level realLevel)) {
			return true;
		}
		return countCombatants(realLevel, pos) < COMBATANT_CAP;
	}

	/** True when hatching another larva is unlikely to immediately overflow the colony. */
	public static boolean canHatchLarva(Level level, BlockPos pos) {
		return canSpawnCombatant(level, pos);
	}

	public static int countEggClusters(Level level, BlockPos center) {
		int radius = (int) Math.ceil(EGG_SCAN_RADIUS);
		int count = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dy = -radius; dy <= radius; dy++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dx = -radius; dx <= radius; dx++) {
					if (dx * dx + dy * dy + dz * dz > radius * radius) {
						continue;
					}
					cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					BlockState state = level.getBlockState(cursor);
					if (state.is(ModBlocks.SILVERFISH_EGGS)) {
						count++;
					}
				}
			}
		}
		return count;
	}

	public static boolean canLayMoreEggs(Level level, BlockPos center) {
		return countEggClusters(level, center) < EGG_CLUSTER_CAP;
	}
}
