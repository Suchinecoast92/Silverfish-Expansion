package com.silverfish_expansion.entity;

import com.nyfaria.awcapi.entity.IAdvancedClimber;
import com.nyfaria.awcapi.entity.movement.ClimberPathNavigator;
import com.silverfish_expansion.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Shared AWCAPI wiring for terrestrial adults and the queen.
 * Fly and larva stay on vanilla / custom movement.
 */
public final class SilverfishClimber {
	private SilverfishClimber() {
	}

	public static boolean usesWallClimbing(Mob mob) {
		return mob.getType() != ModEntities.FLY_SILVERFISH && mob.getType() != ModEntities.LARVA;
	}

	public static void initStepHeight(Mob climber) {
		AttributeInstance stepHeight = climber.getAttribute(Attributes.STEP_HEIGHT);
		if (stepHeight != null) {
			stepHeight.setBaseValue(0.1);
		}
	}

	public static <T extends Mob & IAdvancedClimber> PathNavigation createNavigation(T climber, Level level) {
		ClimberPathNavigator<T> navigator = new ClimberPathNavigator<>(climber, level, false);
		navigator.setCanFloat(true);
		return navigator;
	}

	public static float blockSlipperiness(Mob mob, BlockPos pos) {
		BlockState state = mob.level().getBlockState(pos);
		return state.getBlock().getFriction() * 0.91F;
	}

	public static boolean canClimbOnBlock(Mob mob, BlockState state, BlockPos pos) {
		if (state.is(ModBlocks.SILVERFISH_EGGS) || state.isAir()) {
			return false;
		}
		return !state.getCollisionShape(mob.level(), pos).isEmpty();
	}

	public static float pathingMalus(
			IAdvancedClimber climber,
			BlockGetter cache,
			Mob entity,
			PathType nodeType,
			BlockPos pos,
			Vec3i direction,
			Predicate<Direction> sides
	) {
		if (direction.getY() != 0) {
			boolean hasClimbableNeighbor = false;
			BlockPos.MutableBlockPos offsetPos = new BlockPos.MutableBlockPos();
			for (Direction offset : Direction.values()) {
				if (!sides.test(offset)) {
					continue;
				}
				offsetPos.set(pos.getX() + offset.getStepX(), pos.getY() + offset.getStepY(), pos.getZ() + offset.getStepZ());
				if (climber.canClimbOnBlock(cache.getBlockState(offsetPos), offsetPos)) {
					hasClimbableNeighbor = true;
					break;
				}
			}
			if (!hasClimbableNeighbor) {
				return -1.0F;
			}
		}
		return entity.getPathfindingMalus(nodeType);
	}

	public static float bridgePathingMalus(Mob entity, BlockPos pos, @Nullable Node fallPathPoint) {
		return -1.0F;
	}
}
