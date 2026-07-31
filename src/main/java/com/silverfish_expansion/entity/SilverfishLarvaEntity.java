package com.silverfish_expansion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.state.AnimationTest;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * Juvenile silverfish: flees, seeks stone, and burrows into vanilla infested stone.
 */
public class SilverfishLarvaEntity extends PathfinderMob implements GeoEntity {
	private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public SilverfishLarvaEntity(EntityType<? extends SilverfishLarvaEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 2.0)
				.add(Attributes.MOVEMENT_SPEED, 0.07)
				.add(Attributes.FOLLOW_RANGE, 12.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new PanicGoal(this, 1.35));
		goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 8.0F, 1.2, 1.35));
		goalSelector.addGoal(3, new InfestStoneGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>("movement", 5, this::movementController));
	}

	private PlayState movementController(AnimationTest<SilverfishLarvaEntity> state) {
		if (state.isMoving()) {
			return state.setAndContinue(WALK_ANIM);
		}
		return PlayState.STOP;
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SILVERFISH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SILVERFISH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SILVERFISH_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(SoundEvents.SILVERFISH_STEP, 0.1F, 1.4F);
	}

	/** Path to a nearby infestible stone and burrow into it. */
	private static class InfestStoneGoal extends Goal {
		private static final int SCAN_RANGE = 8;

		private final SilverfishLarvaEntity larva;
		private BlockPos targetPos;
		private int recalcCooldown;

		InfestStoneGoal(SilverfishLarvaEntity larva) {
			this.larva = larva;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (larva.level().isClientSide()) {
				return false;
			}
			if (recalcCooldown > 0) {
				recalcCooldown--;
				return targetPos != null;
			}
			recalcCooldown = 15 + larva.getRandom().nextInt(15);
			targetPos = findInfestibleStone();
			return targetPos != null;
		}

		@Override
		public boolean canContinueToUse() {
			return targetPos != null
					&& infestedStateFor(larva.level().getBlockState(targetPos)) != null;
		}

		@Override
		public void start() {
			if (targetPos != null) {
				larva.getNavigation().moveTo(
						targetPos.getX() + 0.5,
						targetPos.getY(),
						targetPos.getZ() + 0.5,
						1.15
				);
			}
		}

		@Override
		public void stop() {
			targetPos = null;
			larva.getNavigation().stop();
		}

		@Override
		public void tick() {
			if (targetPos == null) {
				return;
			}
			BlockState hostState = larva.level().getBlockState(targetPos);
			BlockState infestedState = infestedStateFor(hostState);
			if (infestedState == null) {
				targetPos = null;
				return;
			}
			if (larva.distanceToSqr(
					targetPos.getX() + 0.5,
					targetPos.getY() + 0.5,
					targetPos.getZ() + 0.5) < 2.25) {
				if (larva.level() instanceof ServerLevel serverLevel) {
					serverLevel.levelEvent(2001, targetPos, Block.getId(hostState));
					serverLevel.setBlock(targetPos, infestedState, Block.UPDATE_ALL);
				}
				larva.discard();
				return;
			}
			if (larva.getNavigation().isDone()) {
				larva.getNavigation().moveTo(
						targetPos.getX() + 0.5,
						targetPos.getY(),
						targetPos.getZ() + 0.5,
						1.15
				);
			}
		}

		private BlockPos findInfestibleStone() {
			BlockPos origin = larva.blockPosition();
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			BlockPos best = null;
			double bestDist = Double.MAX_VALUE;

			for (int dy = -2; dy <= 2; dy++) {
				for (int dx = -SCAN_RANGE; dx <= SCAN_RANGE; dx++) {
					for (int dz = -SCAN_RANGE; dz <= SCAN_RANGE; dz++) {
						cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
						BlockState state = larva.level().getBlockState(cursor);
						if (infestedStateFor(state) == null) {
							continue;
						}
						double dist = larva.distanceToSqr(
								cursor.getX() + 0.5,
								cursor.getY() + 0.5,
								cursor.getZ() + 0.5
						);
						if (dist < bestDist) {
							bestDist = dist;
							best = cursor.immutable();
						}
					}
				}
			}
			return best;
		}

		private static BlockState infestedStateFor(BlockState hostState) {
			Block host = hostState.getBlock();
			if (host == Blocks.STONE) {
				return Blocks.INFESTED_STONE.defaultBlockState();
			}
			if (host == Blocks.COBBLESTONE) {
				return Blocks.INFESTED_COBBLESTONE.defaultBlockState();
			}
			if (host == Blocks.STONE_BRICKS) {
				return Blocks.INFESTED_STONE_BRICKS.defaultBlockState();
			}
			if (host == Blocks.MOSSY_STONE_BRICKS) {
				return Blocks.INFESTED_MOSSY_STONE_BRICKS.defaultBlockState();
			}
			if (host == Blocks.CRACKED_STONE_BRICKS) {
				return Blocks.INFESTED_CRACKED_STONE_BRICKS.defaultBlockState();
			}
			if (host == Blocks.CHISELED_STONE_BRICKS) {
				return Blocks.INFESTED_CHISELED_STONE_BRICKS.defaultBlockState();
			}
			if (host == Blocks.DEEPSLATE) {
				return Blocks.INFESTED_DEEPSLATE.defaultBlockState();
			}
			return null;
		}
	}
}
