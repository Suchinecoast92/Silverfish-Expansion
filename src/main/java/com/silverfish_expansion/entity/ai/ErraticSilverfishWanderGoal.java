package com.silverfish_expansion.entity.ai;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Short, twitchy ground wander: frequent retargets, brief curious pauses, variable speed.
 * Mimics silverfish-like nervous exploration instead of long straight strolls.
 */
public class ErraticSilverfishWanderGoal extends Goal {
	private final PathfinderMob mob;
	private final double baseSpeed;
	private final java.util.function.BooleanSupplier canWander;

	private Vec3 wantedPos;
	private int repathTicks;
	private int pauseTicks;
	private double currentSpeed;

	public ErraticSilverfishWanderGoal(PathfinderMob mob, double baseSpeed) {
		this(mob, baseSpeed, () -> true);
	}

	public ErraticSilverfishWanderGoal(PathfinderMob mob, double baseSpeed, java.util.function.BooleanSupplier canWander) {
		this.mob = mob;
		this.baseSpeed = baseSpeed;
		this.canWander = canWander;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!canWander.getAsBoolean() || mob.isVehicle()) {
			return false;
		}
		if (mob.getRandom().nextInt(8) != 0) {
			return false;
		}
		return pickWantedPos();
	}

	@Override
	public boolean canContinueToUse() {
		if (!canWander.getAsBoolean()) {
			return false;
		}
		if (pauseTicks > 0) {
			return true;
		}
		return wantedPos != null && !mob.getNavigation().isDone();
	}

	@Override
	public void start() {
		repathTicks = 10 + mob.getRandom().nextInt(18);
		pauseTicks = 0;
		currentSpeed = rollSpeed();
		moveToWanted();
	}

	@Override
	public void stop() {
		wantedPos = null;
		pauseTicks = 0;
		mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (pauseTicks > 0) {
			pauseTicks--;
			mob.getNavigation().stop();
			if (pauseTicks == 0 && mob.getRandom().nextFloat() < 0.7F && pickWantedPos()) {
				currentSpeed = rollSpeed();
				repathTicks = 8 + mob.getRandom().nextInt(16);
				moveToWanted();
			}
			return;
		}

		repathTicks--;

		// Occasionally stop mid-path to "sniff" / look around.
		if (mob.getRandom().nextInt(55) == 0) {
			pauseTicks = 8 + mob.getRandom().nextInt(16);
			mob.getNavigation().stop();
			twitchLook();
			return;
		}

		// Abort early and dart somewhere else — erratic personality.
		if (repathTicks <= 0 || mob.getNavigation().isDone() || mob.getRandom().nextInt(40) == 0) {
			if (mob.getRandom().nextFloat() < 0.25F) {
				pauseTicks = 6 + mob.getRandom().nextInt(12);
				mob.getNavigation().stop();
				twitchLook();
				return;
			}
			if (pickWantedPos()) {
				currentSpeed = rollSpeed();
				repathTicks = 8 + mob.getRandom().nextInt(20);
				moveToWanted();
			}
		}
	}

	private boolean pickWantedPos() {
		// Prefer short hops; rarely a slightly longer scurry.
		int horizontal = mob.getRandom().nextFloat() < 0.2F ? 8 : 4;
		int vertical = 2;
		Vec3 pos = DefaultRandomPos.getPos(mob, horizontal, vertical);
		if (pos == null) {
			return false;
		}
		wantedPos = pos;
		return true;
	}

	private void moveToWanted() {
		if (wantedPos == null) {
			return;
		}
		mob.getNavigation().moveTo(wantedPos.x, wantedPos.y, wantedPos.z, currentSpeed);
	}

	private double rollSpeed() {
		// Bursts and crawls mixed together (~18% slower medium/fast).
		float roll = mob.getRandom().nextFloat();
		if (roll < 0.2F) {
			return baseSpeed * 0.55;
		}
		if (roll < 0.55F) {
			return baseSpeed * (0.70 + mob.getRandom().nextFloat() * 0.16);
		}
		return baseSpeed * (0.94 + mob.getRandom().nextFloat() * 0.29);
	}

	private void twitchLook() {
		float yaw = mob.getYRot() + (mob.getRandom().nextFloat() - 0.5F) * 120.0F;
		mob.getLookControl().setLookAt(
				mob.getX() + Mth.sin(-yaw * ((float) Math.PI / 180.0F)),
				mob.getEyeY(),
				mob.getZ() + Mth.cos(-yaw * ((float) Math.PI / 180.0F)),
				60.0F,
				30.0F
		);
	}
}
