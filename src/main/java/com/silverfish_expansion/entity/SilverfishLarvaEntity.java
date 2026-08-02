package com.silverfish_expansion.entity;

import com.silverfish_expansion.colony.ColonyInfestationData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.state.AnimationTest;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Juvenile silverfish: flees, seeks stone, and burrows into vanilla infested stone.
 */
public class SilverfishLarvaEntity extends PathfinderMob implements GeoEntity {
	private static final EntityDataAccessor<Byte> DATA_BURROW_STATE =
			SynchedEntityData.defineId(SilverfishLarvaEntity.class, EntityDataSerializers.BYTE);

	private static final byte BURROW_NONE = 0;
	private static final byte BURROW_FRONT = 1;
	private static final byte BURROW_DOWN = 2;
	private static final int BURROW_ANIMATION_TICKS = 62;

	private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation BURROW_FRONT_ANIM = RawAnimation.begin().thenPlay("insertStone_Front");
	private static final RawAnimation BURROW_DOWN_ANIM = RawAnimation.begin().thenPlay("insertStone_Down");
	/** Must roam at least this far before burrowing into stone. */
	private static final double MIN_WALK_BEFORE_INFEST = 1.0;
	/** Fallback so a larva pressed against a wall still stops being a hatchling. */
	private static final int MIN_TICKS_BEFORE_INFEST = 30;
	/** One larva per stone: UUID of the larva that currently claims that block. */
	private static final Map<Long, UUID> CLAIMED_STONES = new ConcurrentHashMap<>();

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private double distanceWalked;
	private Vec3 lastWalkSample;
	private int burrowTicks;
	private BlockPos pendingInfestPos;
	private BlockState pendingInfestedState;
	@Nullable
	private Direction pendingBurrowFace;
	@Nullable
	private BlockPos claimedStone;

	public SilverfishLarvaEntity(EntityType<? extends SilverfishLarvaEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 2.0)
				.add(Attributes.MOVEMENT_SPEED, 0.13)
				.add(Attributes.FOLLOW_RANGE, 12.0)
				.add(Attributes.STEP_HEIGHT, 0.0)
				.add(Attributes.JUMP_STRENGTH, 0.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_BURROW_STATE, BURROW_NONE);
	}

	public boolean hasWalkedEnoughToInfest() {
		return distanceWalked >= MIN_WALK_BEFORE_INFEST || tickCount >= MIN_TICKS_BEFORE_INFEST;
	}

	private byte getBurrowState() {
		return entityData.get(DATA_BURROW_STATE);
	}

	private boolean isBurrowing() {
		return getBurrowState() != BURROW_NONE;
	}

	@Override
	protected float getJumpPower() {
		return 0.0F;
	}

	@Override
	public boolean canSprint() {
		return false;
	}

	private void beginBurrow(BlockPos pos, BlockState infestedState, byte burrowState, @Nullable Direction face) {
		if (!(level() instanceof ServerLevel serverLevel) || !claimStone(pos)) {
			return;
		}
		pendingBurrowFace = face;
		alignForBurrow(pos, burrowState, face);
		getNavigation().stop();
		setDeltaMovement(Vec3.ZERO);
		pendingInfestPos = pos.immutable();
		pendingInfestedState = infestedState;
		burrowTicks = BURROW_ANIMATION_TICKS;
		entityData.set(DATA_BURROW_STATE, burrowState);
		spawnBurrowDust(serverLevel, serverLevel.getBlockState(pos), 10);
	}

	/** Small stone crumbs around the larva while it digs in. */
	private void spawnBurrowDust(ServerLevel serverLevel, BlockState hostState, int count) {
		BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, hostState);
		double width = Math.max(0.15, getBbWidth() * 0.55);
		double height = Math.max(0.08, getBbHeight() * 0.55);
		for (int i = 0; i < count; i++) {
			double x = getX() + (random.nextDouble() - 0.5) * width * 2.0;
			double y = getY() + 0.05 + random.nextDouble() * height;
			double z = getZ() + (random.nextDouble() - 0.5) * width * 2.0;
			double vx = (random.nextDouble() - 0.5) * 0.04;
			double vy = 0.01 + random.nextDouble() * 0.03;
			double vz = (random.nextDouble() - 0.5) * 0.04;
			serverLevel.sendParticles(particle, x, y, z, 1, vx, vy, vz, 0.02);
		}
	}

	private boolean claimStone(BlockPos pos) {
		UUID self = getUUID();
		long key = pos.asLong();
		UUID previous = CLAIMED_STONES.putIfAbsent(key, self);
		if (previous != null && !previous.equals(self)) {
			return false;
		}
		if (claimedStone != null && !claimedStone.equals(pos)) {
			CLAIMED_STONES.remove(claimedStone.asLong(), self);
		}
		claimedStone = pos.immutable();
		return true;
	}

	private void releaseClaimedStone() {
		if (claimedStone == null) {
			return;
		}
		CLAIMED_STONES.remove(claimedStone.asLong(), getUUID());
		claimedStone = null;
	}

	private boolean isStoneFree(BlockPos pos) {
		UUID owner = CLAIMED_STONES.get(pos.asLong());
		return owner == null || owner.equals(getUUID());
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!level().isClientSide()) {
			releaseClaimedStone();
		}
		super.remove(reason);
	}

	private void alignForBurrow(BlockPos pos, byte burrowState, @Nullable Direction face) {
		if (burrowState == BURROW_DOWN || face == null) {
			return;
		}

		double snapDistance = 0.5 + (getBbWidth() * 0.5) + 0.02;
		double snapX = pos.getX() + 0.5 - (face.getStepX() * snapDistance);
		double snapZ = pos.getZ() + 0.5 - (face.getStepZ() * snapDistance);
		snapTo(snapX, getY(), snapZ, face.toYRot(), 0.0F);
		yBodyRot = getYRot();
		yHeadRot = getYRot();
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide()) {
			return;
		}
		if (burrowTicks > 0) {
			getNavigation().stop();
			setDeltaMovement(Vec3.ZERO);
			if (pendingBurrowFace != null && getBurrowState() == BURROW_FRONT && pendingInfestPos != null) {
				alignForBurrow(pendingInfestPos, BURROW_FRONT, pendingBurrowFace);
			}
			if (pendingInfestPos != null
					&& level() instanceof ServerLevel serverLevel
					&& burrowTicks % 6 == 0
					&& burrowTicks > 8) {
				spawnBurrowDust(serverLevel, serverLevel.getBlockState(pendingInfestPos), 2);
			}
			burrowTicks--;
			if (burrowTicks == 0) {
				finishBurrow();
			}
			return;
		}
		Vec3 current = position();
		if (lastWalkSample != null) {
			double step = current.distanceTo(lastWalkSample);
			if (step > 0.0 && step < 1.5) {
				distanceWalked += step;
			}
		}
		lastWalkSample = current;

		tryBurrowIntoTouchingStone();
	}

	/** Burrow the moment the larva is against valid stone, without waiting for the goal cycle. */
	private void tryBurrowIntoTouchingStone() {
		if (isBurrowing() || !hasWalkedEnoughToInfest()) {
			return;
		}

		BlockPos feet = blockPosition();
		BlockPos below = feet.below();
		BlockState belowInfested = InfestStoneGoal.infestedStateFor(level().getBlockState(below));
		if (belowInfested != null && isStoneFree(below)) {
			beginBurrow(below, belowInfested, BURROW_DOWN, null);
			return;
		}

		Direction facing = Direction.fromYRot(getYRot());
		BlockPos facingPos = feet.relative(facing);
		BlockState facingInfested = InfestStoneGoal.infestedStateFor(level().getBlockState(facingPos));
		if (facingInfested != null && isStoneFree(facingPos)) {
			beginBurrow(facingPos, facingInfested, BURROW_FRONT, facing);
			return;
		}

		for (Direction face : Direction.Plane.HORIZONTAL) {
			BlockPos stone = feet.relative(face);
			BlockState infested = InfestStoneGoal.infestedStateFor(level().getBlockState(stone));
			if (infested != null && isStoneFree(stone)) {
				beginBurrow(stone, infested, BURROW_FRONT, face);
				return;
			}
		}
	}

	private void finishBurrow() {
		if (!(level() instanceof ServerLevel serverLevel) || pendingInfestPos == null || pendingInfestedState == null) {
			entityData.set(DATA_BURROW_STATE, BURROW_NONE);
			pendingBurrowFace = null;
			releaseClaimedStone();
			return;
		}

		BlockState currentState = serverLevel.getBlockState(pendingInfestPos);
		if (InfestStoneGoal.infestedStateFor(currentState) == null) {
			pendingInfestPos = null;
			pendingInfestedState = null;
			pendingBurrowFace = null;
			entityData.set(DATA_BURROW_STATE, BURROW_NONE);
			releaseClaimedStone();
			return;
		}

		serverLevel.setBlock(pendingInfestPos, pendingInfestedState, Block.UPDATE_ALL);
		ColonyInfestationData.get(serverLevel).register(pendingInfestPos, serverLevel.getGameTime());
		discard();
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new PanicGoal(this, 1.25));
		goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 8.0F, 1.15, 1.25));
		goalSelector.addGoal(3, new InfestStoneGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.95));
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>("movement", 5, this::movementController));
	}

	private PlayState movementController(AnimationTest<SilverfishLarvaEntity> state) {
		byte burrowState = getBurrowState();
		if (burrowState == BURROW_FRONT) {
			state.setControllerSpeed(1.0F);
			return state.setAndContinue(BURROW_FRONT_ANIM);
		}
		if (burrowState == BURROW_DOWN) {
			state.setControllerSpeed(1.0F);
			return state.setAndContinue(BURROW_DOWN_ANIM);
		}
		if (state.isMoving()) {
			state.setControllerSpeed(2.0F);
			return state.setAndContinue(WALK_ANIM);
		}
		state.setControllerSpeed(1.0F);
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

	/**
	 * Seeks infestible stone. Adjacent blocks (below / horizontal) are taken immediately;
	 * farther stones are approached by walking to the open face in front of them.
	 */
	private static class InfestStoneGoal extends Goal {
		private static final int SCAN_RANGE = 6;
		/** Horizontal distance to the stone face that counts as "ready to burrow". */
		private static final double FACE_REACH = 0.85;

		private final SilverfishLarvaEntity larva;
		@Nullable
		private BurrowTarget target;
		private int pathRefresh;

		InfestStoneGoal(SilverfishLarvaEntity larva) {
			this.larva = larva;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (larva.level().isClientSide() || larva.isBurrowing() || !larva.hasWalkedEnoughToInfest()) {
				return false;
			}
			// Prefer an immediate adjacent target every time the goal starts.
			target = findImmediateTarget();
			if (target == null) {
				target = findDistantTarget();
			}
			return target != null && larva.claimStone(target.stonePos());
		}

		@Override
		public boolean canContinueToUse() {
			return !larva.isBurrowing()
					&& larva.hasWalkedEnoughToInfest()
					&& target != null
					&& targetStillValid()
					&& larva.isStoneFree(target.stonePos());
		}

		@Override
		public void start() {
			pathRefresh = 0;
			if (target != null && target.burrowState() == BURROW_FRONT && !isReadyToBurrowFront(target)) {
				moveToApproach();
			}
		}

		@Override
		public void stop() {
			if (!larva.isBurrowing()) {
				larva.releaseClaimedStone();
			}
			target = null;
			larva.getNavigation().stop();
		}

		@Override
		public void tick() {
			if (target == null) {
				return;
			}

			// Constantly prefer an adjacent free stone if one appears.
			BurrowTarget immediate = findImmediateTarget();
			if (immediate != null && larva.claimStone(immediate.stonePos())) {
				target = immediate;
			}

			if (!larva.isStoneFree(target.stonePos())) {
				target = null;
				return;
			}

			BlockState hostState = larva.level().getBlockState(target.stonePos());
			BlockState infestedState = infestedStateFor(hostState);
			if (infestedState == null) {
				larva.releaseClaimedStone();
				target = null;
				return;
			}

			if (target.burrowState() == BURROW_DOWN) {
				larva.beginBurrow(target.stonePos(), infestedState, BURROW_DOWN, null);
				target = null;
				return;
			}

			if (isReadyToBurrowFront(target)) {
				larva.beginBurrow(target.stonePos(), infestedState, BURROW_FRONT, target.face());
				target = null;
				return;
			}

			pathRefresh--;
			if (pathRefresh <= 0 || larva.getNavigation().isDone()) {
				moveToApproach();
				pathRefresh = 10;
			}
		}

		private void moveToApproach() {
			if (target == null || target.face() == null) {
				return;
			}
			// Aim slightly in front of the stone face, not at the stone center.
			Direction face = target.face();
			double x = target.stonePos().getX() + 0.5 - face.getStepX() * 0.75;
			double z = target.stonePos().getZ() + 0.5 - face.getStepZ() * 0.75;
			larva.getNavigation().moveTo(x, larva.getY(), z, 1.15);
		}

		private boolean targetStillValid() {
			if (target == null) {
				return false;
			}
			return infestedStateFor(larva.level().getBlockState(target.stonePos())) != null;
		}

		private boolean isReadyToBurrowFront(BurrowTarget burrowTarget) {
			BlockPos stone = burrowTarget.stonePos();
			Direction face = burrowTarget.face();
			if (face == null) {
				return false;
			}

			BlockPos feet = larva.blockPosition();
			// Already standing in the cell next to that face.
			if (feet.equals(stone.relative(face.getOpposite())) && feet.getY() == stone.getY()) {
				return true;
			}

			double faceX = stone.getX() + 0.5 - face.getStepX() * 0.5;
			double faceZ = stone.getZ() + 0.5 - face.getStepZ() * 0.5;
			double dx = larva.getX() - faceX;
			double dz = larva.getZ() - faceZ;
			boolean closeToFace = dx * dx + dz * dz <= FACE_REACH * FACE_REACH;
			boolean sameLevel = Math.abs(larva.getY() - stone.getY()) < 1.1;
			return closeToFace && sameLevel;
		}

		/** Stone under feet or directly touching a horizontal face. */
		@Nullable
		private BurrowTarget findImmediateTarget() {
			BlockPos feet = larva.blockPosition();
			BlockPos below = feet.below();
			if (infestedStateFor(larva.level().getBlockState(below)) != null && larva.isStoneFree(below)) {
				return new BurrowTarget(below, feet, BURROW_DOWN, null);
			}

			BurrowTarget best = null;
			double bestDist = Double.MAX_VALUE;
			for (Direction face : Direction.Plane.HORIZONTAL) {
				BlockPos stone = feet.relative(face);
				if (infestedStateFor(larva.level().getBlockState(stone)) == null || !larva.isStoneFree(stone)) {
					continue;
				}
				double dist = larva.distanceToSqr(stone.getX() + 0.5, larva.getY(), stone.getZ() + 0.5);
				if (dist < bestDist) {
					bestDist = dist;
					best = new BurrowTarget(stone, feet, BURROW_FRONT, face);
				}
			}
			return best;
		}

		@Nullable
		private BurrowTarget findDistantTarget() {
			BlockPos origin = larva.blockPosition();
			BurrowTarget best = null;
			double bestDist = Double.MAX_VALUE;
			BlockPos.MutableBlockPos stone = new BlockPos.MutableBlockPos();

			for (int dy = -1; dy <= 1; dy++) {
				for (int dx = -SCAN_RANGE; dx <= SCAN_RANGE; dx++) {
					for (int dz = -SCAN_RANGE; dz <= SCAN_RANGE; dz++) {
						stone.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
						if (infestedStateFor(larva.level().getBlockState(stone)) == null
								|| !larva.isStoneFree(stone)) {
							continue;
						}

						for (Direction face : Direction.Plane.HORIZONTAL) {
							BlockPos approach = stone.immutable().relative(face.getOpposite());
							if (approach.getY() != origin.getY() || !isOpenApproach(approach)) {
								continue;
							}
							double dist = larva.distanceToSqr(
									approach.getX() + 0.5,
									approach.getY(),
									approach.getZ() + 0.5
							);
							if (dist < bestDist) {
								bestDist = dist;
								best = new BurrowTarget(stone.immutable(), approach, BURROW_FRONT, face);
							}
						}
					}
				}
			}
			return best;
		}

		private boolean isOpenApproach(BlockPos pos) {
			BlockState state = larva.level().getBlockState(pos);
			return state.getCollisionShape(larva.level(), pos).isEmpty();
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

		private record BurrowTarget(
				BlockPos stonePos,
				BlockPos approachPos,
				byte burrowState,
				@Nullable Direction face
		) {
		}
	}
}
