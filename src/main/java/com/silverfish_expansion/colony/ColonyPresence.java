package com.silverfish_expansion.colony;

import com.silverfish_expansion.block.ModBlocks;
import com.silverfish_expansion.entity.ExpandedSilverfishEntity;
import com.silverfish_expansion.entity.SilverfishLarvaEntity;
import com.silverfish_expansion.entity.SilverfishQueenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Soft population pressure for chamber fauna, wild hint spawns, and colony alerts.
 */
public final class ColonyPresence {
	/** Soft cap on adults + vanilla silverfish near a colony focus (seed adults are 3–12). */
	public static final int COMBATANT_CAP = 14;
	public static final double COMBATANT_RADIUS = 48.0;
	/** Wild cave scouts stay scarce so they feel like a trail, not a second nest. */
	public static final int WILD_HINT_CAP = 3;
	public static final double WILD_HINT_RADIUS = 40.0;
	public static final int WILD_HINT_MIN_Y = -54;
	public static final int WILD_HINT_MAX_Y = 24;
	/** Max egg clusters near the queen before she stops laying. */
	public static final int EGG_CLUSTER_CAP = 10;
	public static final double EGG_SCAN_RADIUS = 12.0;
	public static final double ALERT_RANGE = 20.0;

	private ColonyPresence() {
	}

	public static boolean isColonyMember(Entity entity) {
		return entity instanceof ExpandedSilverfishEntity
				|| entity instanceof SilverfishQueenEntity
				|| entity instanceof SilverfishLarvaEntity
				|| entity instanceof Silverfish;
	}

	public static void alertColony(ServerLevel level, LivingEntity wounded, LivingEntity attacker) {
		if (isColonyMember(attacker)) {
			return;
		}
		AABB box = wounded.getBoundingBox().inflate(ALERT_RANGE);
		for (ExpandedSilverfishEntity ally : level.getEntitiesOfClass(ExpandedSilverfishEntity.class, box)) {
			if (ally.getTarget() == null) {
				ally.setTarget(attacker);
			}
		}
		for (Silverfish ally : level.getEntitiesOfClass(Silverfish.class, box)) {
			if (ally.getTarget() == null) {
				ally.setTarget(attacker);
			}
		}
	}

	public static int countCombatants(Level level, BlockPos center) {
		return countCombatants(level, center, COMBATANT_RADIUS);
	}

	public static int countCombatants(Level level, BlockPos center, double radius) {
		AABB box = new AABB(center).inflate(radius);
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

	/**
	 * Rare underground scout: dark caves in the same band as breeding chambers.
	 * A pair of adults should make the player look around, not replace the nest.
	 */
	public static boolean canSpawnWildHint(LevelAccessor level, BlockPos pos) {
		if (pos.getY() < WILD_HINT_MIN_Y || pos.getY() > WILD_HINT_MAX_Y) {
			return false;
		}
		if (level.canSeeSky(pos)) {
			return false;
		}
		if (!(level instanceof Level realLevel)) {
			return true;
		}
		return countCombatants(realLevel, pos, WILD_HINT_RADIUS) < WILD_HINT_CAP;
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
