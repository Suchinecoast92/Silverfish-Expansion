package com.silverfish_expansion.entity.ai;

import com.silverfish_expansion.entity.ExpandedSilverfishEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;

/**
 * Bee-like aerial melee: take off, stay airborne, and dart at the current target.
 */
public class FlySilverfishAttackGoal extends Goal {
	private static final int ATTACK_INTERVAL = 16;
	/** Vanilla silverfish hitbox width; MeleeAttackGoal uses {@code width * 2}. */
	private static final float VANILLA_SILVERFISH_WIDTH = 0.4F;
	/** 10% extra reach beyond vanilla silverfish melee. */
	private static final double REACH_BONUS = 1.10;

	private final ExpandedSilverfishEntity fly;
	private int attackTicks;

	public FlySilverfishAttackGoal(ExpandedSilverfishEntity fly) {
		this.fly = fly;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = fly.getTarget();
		return target != null && target.isAlive() && fly.wantsAerialChase();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		attackTicks = 0;
		fly.getNavigation().stop();
		fly.requestCombatTakeoff();
	}

	@Override
	public void stop() {
		attackTicks = 0;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = fly.getTarget();
		if (target == null) {
			return;
		}

		fly.getLookControl().setLookAt(target, 40.0F, 40.0F);
		if (!fly.isFlying() && !fly.isLanding()) {
			fly.requestCombatTakeoff();
		}
		if (fly.isFlying()) {
			fly.getNavigation().stop();
		} else {
			// Tight cave: no headroom — close on foot like a grounded bee.
			fly.getNavigation().moveTo(target, 1.15);
		}

		if (isInVanillaSilverfishReach(target) && --attackTicks <= 0) {
			attackTicks = ATTACK_INTERVAL;
			if (fly.level() instanceof ServerLevel serverLevel) {
				fly.doHurtTarget(serverLevel, target);
			}
		}
	}

	/**
	 * Same formula as vanilla {@code MeleeAttackGoal} for a silverfish, plus 10% distance.
	 * Uses bounding-box overlap so a fly hovering slightly above still connects.
	 */
	private boolean isInVanillaSilverfishReach(LivingEntity target) {
		double reach = VANILLA_SILVERFISH_WIDTH * 2.0F;
		double reachSqr = reach * reach + target.getBbWidth();
		double allowed = Math.sqrt(reachSqr) * REACH_BONUS;
		double halves = fly.getBbWidth() * 0.5 + target.getBbWidth() * 0.5;
		double inflate = Math.max(0.05, allowed - halves);
		AABB attackBox = fly.getBoundingBox().inflate(inflate);
		return attackBox.intersects(target.getBoundingBox());
	}
}
