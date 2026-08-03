package com.silverfish_expansion.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Quick curious head twitches toward nearby air points / walls.
 */
public class CuriousSilverfishLookGoal extends Goal {
	private final Mob mob;
	private final java.util.function.BooleanSupplier canLook;
	private double relX;
	private double relZ;
	private int lookTime;

	public CuriousSilverfishLookGoal(Mob mob) {
		this(mob, () -> true);
	}

	public CuriousSilverfishLookGoal(Mob mob, java.util.function.BooleanSupplier canLook) {
		this.mob = mob;
		this.canLook = canLook;
		setFlags(EnumSet.of(Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return canLook.getAsBoolean() && mob.getRandom().nextFloat() < 0.08F;
	}

	@Override
	public boolean canContinueToUse() {
		return canLook.getAsBoolean() && lookTime > 0;
	}

	@Override
	public void start() {
		double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
		relX = Math.cos(angle);
		relZ = Math.sin(angle);
		lookTime = 12 + mob.getRandom().nextInt(20);
	}

	@Override
	public void stop() {
		lookTime = 0;
	}

	@Override
	public void tick() {
		lookTime--;
		mob.getLookControl().setLookAt(
				mob.getX() + relX,
				mob.getEyeY() + (mob.getRandom().nextDouble() - 0.35) * 0.6,
				mob.getZ() + relZ,
				50.0F,
				40.0F
		);
	}
}
