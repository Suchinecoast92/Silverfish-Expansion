package com.silverfish_expansion.entity;

import com.silverfish_expansion.block.ModBlocks;
import com.silverfish_expansion.block.SilverfishEggsBlock;
import com.silverfish_expansion.colony.ColonyPresence;
import com.silverfish_expansion.entity.ai.CuriousSilverfishLookGoal;
import com.silverfish_expansion.entity.ai.ErraticSilverfishWanderGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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

/**
 * Colony matriarch: stays near her chamber, lays eggs, and flees when hurt.
 * The workers fight; she does not. Without a living queen, no new eggs are laid.
 */
public class SilverfishQueenEntity extends Monster implements GeoEntity {
	private static final EntityDataAccessor<Boolean> DATA_WALKING =
			SynchedEntityData.defineId(SilverfishQueenEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Byte> DATA_IDLE_PICK =
			SynchedEntityData.defineId(SilverfishQueenEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_LAYING =
			SynchedEntityData.defineId(SilverfishQueenEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_CLIMBING =
			SynchedEntityData.defineId(SilverfishQueenEntity.class, EntityDataSerializers.BOOLEAN);

	private static final int WALK_WINDOW_TICKS = 5;
	private static final double WALK_NET_THRESHOLD_SQR = 0.01;
	private static final int WALK_GRACE_TICKS = 2;
	private static final float IDLE_CHANCE_ON_STOP = 0.35F;

	private static final int HOME_RADIUS = 14;
	/** putEgg animation length (~6s). */
	private static final int LAY_ANIM_TICKS = 120;
	private static final int LAY_COOLDOWN_MIN = 2400; // 2 min
	private static final int LAY_COOLDOWN_MAX = 4800; // 4 min
	/** Just past the scaled tail tip so the cluster sits behind her, not under her. */
	private static final double EGG_BEHIND_DISTANCE = 1.35;

	private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation NONE_ANIM = RawAnimation.begin().thenLoop("none");
	private static final RawAnimation IDLE_ANIM =
			RawAnimation.begin().thenPlay("idle").thenLoop("none");
	private static final RawAnimation IDLE2_ANIM =
			RawAnimation.begin().thenPlay("idle2").thenLoop("none");
	private static final RawAnimation IDLE3_ANIM =
			RawAnimation.begin().thenPlay("idle3").thenLoop("none");
	private static final RawAnimation PUT_EGG_ANIM = RawAnimation.begin().thenPlay("putEgg");

	private static final Direction[] EGG_SUPPORT_FACES = {
			Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
	};

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private final double[] walkHistoryX = new double[WALK_WINDOW_TICKS];
	private final double[] walkHistoryY = new double[WALK_WINDOW_TICKS];
	private final double[] walkHistoryZ = new double[WALK_WINDOW_TICKS];
	private int walkHistoryIndex;
	private int walkHistoryCount;
	private int walkGrace;
	private boolean wasServerMoving;

	private int layCooldown;
	private int layTicks;
	private boolean hasLayPose;
	private double layX;
	private double layY;
	private double layZ;
	private float layYRot;
	private float layXRot;
	private float layYHeadRot;
	private float layYBodyRot;

	public SilverfishQueenEntity(EntityType<? extends SilverfishQueenEntity> type, Level level) {
		super(type, level);
		this.moveControl = new MoveControl(this) {
			@Override
			public void tick() {
				if (!isLayingEggs()) {
					super.tick();
				}
			}
		};
		this.lookControl = new LookControl(this) {
			@Override
			public void tick() {
				if (!isLayingEggs()) {
					super.tick();
				}
			}
		};
		this.jumpControl = new JumpControl(this) {
			@Override
			public void tick() {
				if (!isLayingEggs()) {
					super.tick();
				}
			}
		};
		layCooldown = LAY_COOLDOWN_MIN / 2 + random.nextInt(Math.max(1, LAY_COOLDOWN_MIN / 2));
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.22)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.FOLLOW_RANGE, 20.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.35)
				.add(Attributes.SCALE, 1.5);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_WALKING, false);
		builder.define(DATA_IDLE_PICK, (byte) -1);
		builder.define(DATA_LAYING, false);
		builder.define(DATA_CLIMBING, false);
	}

	public boolean isWalking() {
		return entityData.get(DATA_WALKING);
	}

	public boolean isClimbing() {
		return entityData.get(DATA_CLIMBING);
	}

	private void setClimbing(boolean climbing) {
		entityData.set(DATA_CLIMBING, climbing);
	}

	public boolean isLayingEggs() {
		return entityData.get(DATA_LAYING);
	}

	private void setLayingEggs(boolean laying) {
		entityData.set(DATA_LAYING, laying);
	}

	/** Structure / spawn-egg: bind the queen to a colony focus. */
	public void setColonyHome(BlockPos pos) {
		setHomeTo(pos.immutable(), HOME_RADIUS);
	}

	public BlockPos getColonyHome() {
		return hasHome() ? getHomePosition() : blockPosition();
	}

	@Nullable
	@Override
	public SpawnGroupData finalizeSpawn(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			@Nullable SpawnGroupData spawnGroupData
	) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
		if (!hasHome()) {
			setColonyHome(blockPosition());
		}
		return data;
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return SilverfishClimber.createNavigation(this, level);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this) {
			@Override
			public boolean canUse() {
				return !isLayingEggs() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !isLayingEggs() && super.canContinueToUse();
			}
		});
		goalSelector.addGoal(1, new PanicGoal(this, 1.45));
		goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 12.0F, 1.15, 1.4, this::shouldAvoidAttacker));
		goalSelector.addGoal(3, new LayEggGoal());
		goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		goalSelector.addGoal(5, new ErraticSilverfishWanderGoal(this, 0.85, () -> !isLayingEggs() && !isRecentlyHurt()));
		goalSelector.addGoal(6, new CuriousSilverfishLookGoal(this, () -> !isLayingEggs() && !isRecentlyHurt()));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F) {
			@Override
			public boolean canUse() {
				return !isLayingEggs() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !isLayingEggs() && super.canContinueToUse();
			}
		});
		goalSelector.addGoal(8, new RandomLookAroundGoal(this) {
			@Override
			public boolean canUse() {
				return !isLayingEggs() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !isLayingEggs() && super.canContinueToUse();
			}
		});
	}

	private boolean isRecentlyHurt() {
		return getLastHurtByMob() != null && tickCount - getLastHurtByMobTimestamp() < 80;
	}

	private boolean shouldAvoidAttacker(LivingEntity entity) {
		return isRecentlyHurt() && entity == getLastHurtByMob();
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return false;
	}

	@Override
	public void tick() {
		if (isLayingEggs()) {
			applyLayPose();
		}
		super.tick();
		if (!level().isClientSide()) {
			setClimbing(!isLayingEggs() && horizontalCollision);
		}
		if (isLayingEggs()) {
			applyLayPose();
		}
		if (!level().isClientSide()) {
			if (!hasHome()) {
				setColonyHome(blockPosition());
			}
			tickWalkState();
			tickLaying();
			if (isLayingEggs()) {
				applyLayPose();
			}
			if (layCooldown > 0 && !isLayingEggs()) {
				layCooldown--;
			}
		}
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_LAYING.equals(accessor)) {
			if (isLayingEggs()) {
				snapshotLayPose();
			} else {
				hasLayPose = false;
			}
		}
	}

	private void tickWalkState() {
		if (isLayingEggs()) {
			resetWalkTracking();
			if (isWalking()) {
				entityData.set(DATA_WALKING, false);
			}
			return;
		}

		boolean windowReady = walkHistoryCount >= WALK_WINDOW_TICKS;
		double netX = getX() - walkHistoryX[walkHistoryIndex];
		double netY = getY() - walkHistoryY[walkHistoryIndex];
		double netZ = getZ() - walkHistoryZ[walkHistoryIndex];

		walkHistoryX[walkHistoryIndex] = getX();
		walkHistoryY[walkHistoryIndex] = getY();
		walkHistoryZ[walkHistoryIndex] = getZ();
		walkHistoryIndex = (walkHistoryIndex + 1) % WALK_WINDOW_TICKS;
		if (!windowReady) {
			walkHistoryCount++;
		}

		double netSqr = netX * netX + netY * netY + netZ * netZ;
		boolean moving = windowReady && netSqr > WALK_NET_THRESHOLD_SQR && netSqr <= 4.0;
		if (moving) {
			walkGrace = WALK_GRACE_TICKS;
		} else if (walkGrace > 0) {
			walkGrace--;
			moving = true;
		}

		if (moving != isWalking()) {
			entityData.set(DATA_WALKING, moving);
		}

		boolean serverMoving = getDeltaMovement().horizontalDistanceSqr() > 1.0E-6 || !getNavigation().isDone();
		if (wasServerMoving && !serverMoving && !moving) {
			rollIdleServer();
		}
		wasServerMoving = serverMoving;
	}

	private void resetWalkTracking() {
		walkHistoryCount = 0;
		walkHistoryIndex = 0;
		walkGrace = 0;
	}

	private void rollIdleServer() {
		if (random.nextFloat() >= IDLE_CHANCE_ON_STOP) {
			entityData.set(DATA_IDLE_PICK, (byte) -1);
			return;
		}
		entityData.set(DATA_IDLE_PICK, (byte) random.nextInt(3));
	}

	private RawAnimation idleAnimationForPick(byte pick) {
		return switch (pick) {
			case 0 -> IDLE_ANIM;
			case 1 -> IDLE2_ANIM;
			case 2 -> IDLE3_ANIM;
			default -> NONE_ANIM;
		};
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
		boolean hurt = super.hurtServer(level, damageSource, amount);
		if (hurt) {
			setTarget(null);
			if (isLayingEggs()) {
				stopLaying();
			}
			Entity attacker = damageSource.getEntity();
			if (attacker instanceof LivingEntity living) {
				ColonyPresence.alertColony(level, this, living);
			}
		}
		return hurt;
	}

	@Nullable
	private BlockPos tryPlaceEggNearHome() {
		Vec3 back = backwardAlongSurface();
		Direction attach = Direction.DOWN;
		BlockPos self = blockPosition();
		int y = self.getY();
		Vec3 right = new Vec3(-back.z, 0.0, back.x);
		if (right.lengthSqr() > 1.0E-6) {
			right = right.normalize();
		}

		double[] distances = {EGG_BEHIND_DISTANCE, EGG_BEHIND_DISTANCE + 0.7, EGG_BEHIND_DISTANCE + 1.4};
		double[] laterals = {0.0, 0.7, -0.7};
		for (double dist : distances) {
			for (double lat : laterals) {
				Vec3 at = position().add(back.scale(dist)).add(right.scale(lat));
				BlockPos pos = BlockPos.containing(at.x, y, at.z);
				if (pos.equals(self)) {
					continue;
				}
				BlockPos placed = tryPlaceEggAt(pos, attach);
				if (placed != null) {
					return placed;
				}
			}
		}
		return null;
	}

	/** Opposite of heading, flattened onto the floor. */
	private Vec3 backwardAlongSurface() {
		Vec3 look = getLookAngle();
		if (look.horizontalDistanceSqr() > 1.0E-6) {
			look = new Vec3(look.x, 0.0, look.z).normalize();
		}
		return look.scale(-1.0);
	}

	@Nullable
	private BlockPos tryPlaceEggAt(BlockPos pos, Direction preferredSupport) {
		BlockState existing = level().getBlockState(pos);
		if (existing.is(ModBlocks.SILVERFISH_EGGS)) {
			int eggs = existing.getValue(SilverfishEggsBlock.EGGS);
			if (eggs < SilverfishEggsBlock.MAX_EGGS) {
				level().setBlock(pos, existing.setValue(SilverfishEggsBlock.EGGS, eggs + 1), 3);
				playEggPlaceSound(pos);
				return pos.immutable();
			}
			return null;
		}
		if (!existing.isAir()) {
			return null;
		}

		Direction[] supports = new Direction[EGG_SUPPORT_FACES.length];
		supports[0] = preferredSupport;
		int index = 1;
		for (Direction support : EGG_SUPPORT_FACES) {
			if (support != preferredSupport) {
				supports[index++] = support;
			}
		}

		for (Direction support : supports) {
			BlockPos supportPos = pos.relative(support);
			BlockState supportState = level().getBlockState(supportPos);
			if (!supportState.isFaceSturdy(level(), supportPos, support.getOpposite())) {
				continue;
			}
			BlockState placed = ModBlocks.SILVERFISH_EGGS.defaultBlockState()
					.setValue(SilverfishEggsBlock.FACING, support.getOpposite())
					.setValue(SilverfishEggsBlock.EGGS, 1);
			if (!placed.canSurvive(level(), pos)) {
				continue;
			}
			level().setBlock(pos, placed, 3);
			playEggPlaceSound(pos);
			return pos.immutable();
		}
		return null;
	}

	private void beginLaying() {
		getNavigation().stop();
		setDeltaMovement(Vec3.ZERO);
		snapshotLayPose();
		setLayingEggs(true);
		layTicks = LAY_ANIM_TICKS;
		applyLayPose();
	}

	private void stopLaying() {
		setLayingEggs(false);
		layTicks = 0;
		hasLayPose = false;
	}

	private void snapshotLayPose() {
		layX = getX();
		layY = getY();
		layZ = getZ();
		layYRot = getYRot();
		layXRot = getXRot();
		layYHeadRot = getYHeadRot();
		layYBodyRot = yBodyRot;
		hasLayPose = true;
	}

	private void applyLayPose() {
		if (!hasLayPose) {
			snapshotLayPose();
		}
		getNavigation().stop();
		xxa = 0.0F;
		yya = 0.0F;
		zza = 0.0F;
		setDeltaMovement(Vec3.ZERO);
		setPos(layX, layY, layZ);
		setYRot(layYRot);
		setXRot(layXRot);
		setYHeadRot(layYHeadRot);
		yBodyRot = layYBodyRot;
		yHeadRot = layYHeadRot;
		yRotO = layYRot;
		xRotO = layXRot;
		yBodyRotO = layYBodyRot;
		yHeadRotO = layYHeadRot;
		setOldPosAndRot();
	}

	private void tickLaying() {
		if (!isLayingEggs()) {
			return;
		}
		applyLayPose();
		if (isRecentlyHurt()) {
			stopLaying();
			return;
		}
		layTicks--;
		if (layTicks > 0) {
			return;
		}
		boolean placed = tryPlaceEggNearHome() != null;
		stopLaying();
		layCooldown = LAY_COOLDOWN_MIN + random.nextInt(Math.max(1, LAY_COOLDOWN_MAX - LAY_COOLDOWN_MIN + 1));
		if (!placed) {
			layCooldown = Math.min(layCooldown, 600 + random.nextInt(400));
		}
	}

	private void playEggPlaceSound(BlockPos pos) {
		level().playSound(
				null,
				pos,
				SoundEvents.METAL_PLACE,
				SoundSource.BLOCKS,
				0.6F,
				0.9F + random.nextFloat() * 0.2F
		);
	}

	@Override
	public void travel(Vec3 travelVector) {
		if (isLayingEggs()) {
			setDeltaMovement(Vec3.ZERO);
			return;
		}
		super.travel(travelVector);
	}

	@Override
	public boolean onClimbable() {
		return isClimbing();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("LayCooldown", layCooldown);
		output.putInt("LayTicks", layTicks);
		output.putBoolean("Laying", isLayingEggs());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		layCooldown = input.getIntOr("LayCooldown", layCooldown);
		layTicks = input.getIntOr("LayTicks", 0);
		setLayingEggs(input.getBooleanOr("Laying", false));
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>("movement", 8, this::movementController));
	}

	private PlayState movementController(AnimationTest<SilverfishQueenEntity> state) {
		SilverfishQueenEntity entity = state.animatable();
		if (entity.isLayingEggs()) {
			return state.setAndContinue(PUT_EGG_ANIM);
		}
		if (entity.isWalking()) {
			return state.setAndContinue(WALK_ANIM);
		}
		return state.setAndContinue(entity.idleAnimationForPick(entity.entityData.get(DATA_IDLE_PICK)));
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
	protected void playStepSound(BlockPos pos, BlockState blockState) {
		playSound(SoundEvents.SILVERFISH_STEP, 0.15F, 0.9F);
	}

	private class LayEggGoal extends Goal {
		LayEggGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (layCooldown > 0 || isLayingEggs() || isRecentlyHurt()) {
				return false;
			}
			if (!(level() instanceof ServerLevel serverLevel)) {
				return false;
			}
			BlockPos home = getColonyHome();
			if (!ColonyPresence.canLayMoreEggs(serverLevel, home)) {
				return false;
			}
			if (!ColonyPresence.canHatchLarva(serverLevel, home)) {
				return false;
			}
			return random.nextInt(20) == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return isLayingEggs() && layTicks > 0 && !isRecentlyHurt();
		}

		@Override
		public void start() {
			beginLaying();
		}

		@Override
		public void stop() {
			getNavigation().stop();
		}

		@Override
		public void tick() {
			getNavigation().stop();
		}
	}
}
