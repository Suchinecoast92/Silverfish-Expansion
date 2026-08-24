package com.silverfish_expansion.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.Level;

/**
 * Vanilla spider-style wall climbing for terrestrial adults and the queen.
 * Fly and larva do not climb.
 */
public final class SilverfishClimber {
	private SilverfishClimber() {
	}

	public static boolean usesWallClimbing(Mob mob) {
		return mob.getType() != ModEntities.FLY_SILVERFISH && mob.getType() != ModEntities.LARVA;
	}

	public static PathNavigation createNavigation(Mob mob, Level level) {
		return new WallClimberNavigation(mob, level);
	}
}
