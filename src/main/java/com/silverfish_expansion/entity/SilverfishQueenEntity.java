package com.silverfish_expansion.entity;

import com.nyfaria.awcapi.ClimberHelper;
import com.nyfaria.awcapi.entity.ClimberComponent;
import com.nyfaria.awcapi.entity.IAdvancedClimber;
import com.nyfaria.awcapi.entity.movement.ClimberJumpController;
import com.nyfaria.awcapi.entity.movement.ClimberLookController;
import com.nyfaria.awcapi.entity.movement.ClimberMoveController;
import com.silverfish_expansion.block.ModBlocks;
import com.silverfish_expansion.block.SilverfishEggsBlock;
import com.silverfish_expansion.colony.ColonyPresence;
import com.silverfish_expansion.entity.ai.CuriousSilverfishLookGoal;
import com.silverfish_expansion.entity.ai.ErraticSilverfishWanderGoal;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
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
 * Colony matriarch: stays near her chamber anchor, lays eggs, and alerts nearby silverfish when hurt.
 * Without a living queen, no new eggs are laid; existing eggs keep hatching.
 */
public class SilverfishQueenEntity extends Monster implements GeoEntity, IAdvancedClimber {
	private static final EntityDataAccessor<Boolean> DATA_WALKING =
			SynchedEntityData.defineId(SilverfishQueenEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Byte> DATA_IDLE_PICK =
			SynchedEntityData.defineId(SilverfishQueenEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_LAYING =
			SynchedEntityData.defineId(SilverfishQueenEntity.class, EntityDataSerializers.BOOLEAN);

	private static final int WALK_WINDOW_TICKS = 5;
	private static final double WALK_NET_THRESHOLD_SQR = 0.01;
	private static final int WALK_GRACE_TICKS = 2;
	private static final float IDLE_CHANCE_ON_STOP = 0.35F;

	private static final int HOME_RADIUS = 14;
	private static final int ALERT_RANGE = 20;
	/** putEgg animation length (~6s). */
	private static final int LAY_ANIM_TICKS = 120;
	private static final int LAY_COOLDOWN_MIN = 2400; // 2 min
	private static final int LAY_COOLDOWN_MAX = 4800; // 4 min
	private static final int LAY_SEARCH_RADIUS = 8;

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
	private final ClimberComponent climber = new ClimberComponent(this);

	private final double[] walkHistoryX = new double[WALK_WINDOW_TICKS];
	private final double[] walkHistoryY = new double[WALK_WINDOW_TICKS];
	private final double[] walkHistoryZ = new double[WALK_WINDOW_TICKS];
	private int walkHistoryIndex;
	private int walkHistoryCount;
	private int walkGrace;
	private boolean wasServerMoving;
	private double lerpXRot;
	private double lerpYRot;
	private double lerpYHeadRot;
	private int lerpHeadSteps;

	private int layCooldown;
	private int layTicks;

	public SilverfishQueenEntity(EntityType<? extends SilverfishQueenEntity> type, Level level) {
		super(type, level);
		this.moveControl = new ClimberMoveController<>(this);
		this.lookControl = new ClimberLookController<>(this);
		this.jumpControl = new ClimberJumpController<>(this);
		SilverfishClimber.initStepHeight(this);
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
	}

	public boolean isWalking() {
		return entityData.get(DATA_WALKING);
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
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new LayEggGoal());
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
		goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		goalSelector.addGoal(5, new ErraticSilverfishWanderGoal(this, 0.85, () -> !isLayingEggs()));
		goalSelector.addGoal(6, new CuriousSilverfishLookGoal(this, () -> !isLayingEggs()));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
	}

	@Override
	public void tick() {
		super.tick();
		ClimberHelper.tickClimber(this);
		if (!level().isClientSide()) {
			if (!hasHome()) {
				setColonyHome(blockPosition());
			}
			tickWalkState();
			if (layCooldown > 0 && !isLayingEggs()) {
				layCooldown--;
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
			Entity attacker = damageSource.getEntity();
			if (attacker instanceof LivingEntity living && !(living instanceof SilverfishQueenEntity)) {
				alertColony(level, living);
			}
		}
		return hurt;
	}

	private void alertColony(ServerLevel level, LivingEntity attacker) {
		AABB box = getBoundingBox().inflate(ALERT_RANGE);
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
		if (getTarget() == null) {
			setTarget(attacker);
		}
	}

	private boolean tryPlaceEggNearHome() {
		BlockPos origin = blockPosition();
		BlockPos home = getColonyHome();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int start = random.nextInt(EGG_SUPPORT_FACES.length);

		for (int attempt = 0; attempt < 48; attempt++) {
			int dx = random.nextInt(LAY_SEARCH_RADIUS * 2 + 1) - LAY_SEARCH_RADIUS;
			int dy = random.nextInt(5) - 2;
			int dz = random.nextInt(LAY_SEARCH_RADIUS * 2 + 1) - LAY_SEARCH_RADIUS;
			cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);

			if (cursor.distSqr(home) < 4L) {
				continue; // keep clear around matriarch seat
			}

			BlockState existing = level().getBlockState(cursor);
			if (existing.is(ModBlocks.SILVERFISH_EGGS)) {
				int eggs = existing.getValue(SilverfishEggsBlock.EGGS);
				if (eggs < SilverfishEggsBlock.MAX_EGGS) {
					level().setBlock(cursor, existing.setValue(SilverfishEggsBlock.EGGS, eggs + 1), 3);
					playEggPlaceSound(cursor);
					return true;
				}
				continue;
			}
			if (!existing.isAir()) {
				continue;
			}

			for (int i = 0; i < EGG_SUPPORT_FACES.length; i++) {
				Direction support = EGG_SUPPORT_FACES[(start + i) % EGG_SUPPORT_FACES.length];
				BlockPos supportPos = cursor.relative(support);
				BlockState supportState = level().getBlockState(supportPos);
				if (!supportState.isFaceSturdy(level(), supportPos, support.getOpposite())) {
					continue;
				}
				BlockState placed = ModBlocks.SILVERFISH_EGGS.defaultBlockState()
						.setValue(SilverfishEggsBlock.FACING, support.getOpposite())
						.setValue(SilverfishEggsBlock.EGGS, 1);
				if (!placed.canSurvive(level(), cursor)) {
					continue;
				}
				level().setBlock(cursor, placed, 3);
				playEggPlaceSound(cursor);
				return true;
			}
		}
		return false;
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
	public void aiStep() {
		ClimberHelper.livingTickClimber(this);
		super.aiStep();
	}

	@Override
	public void travel(Vec3 travelVector) {
		if (ClimberHelper.handleTravel(this, travelVector)) {
			ClimberHelper.postTravel(this, travelVector);
			return;
		}
		super.travel(travelVector);
		ClimberHelper.postTravel(this, travelVector);
	}

	@Override
	public void move(MoverType type, Vec3 movement) {
		ClimberHelper.handleMove(this, type, movement, true);
		super.move(type, movement);
		ClimberHelper.handleMove(this, type, movement, false);
	}

	@Override
	public void jumpFromGround() {
		if (!ClimberHelper.handleJump(this)) {
			super.jumpFromGround();
		}
	}

	@Override
	public BlockPos getOnPos() {
		return ClimberHelper.getAdjustedOnPosition(this, super.getOnPos());
	}

	@Override
	public void lookAt(EntityAnchorArgument.Anchor anchor, Vec3 target) {
		Vec3 dir = getOrientation().getLocal(target.subtract(position()));
		super.lookAt(anchor, position().add(dir));
	}

	@Override
	public boolean onClimbable() {
		return false;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource) {
		if (climber.getGroundSide() != Direction.DOWN) {
			return false;
		}
		return super.causeFallDamage(fallDistance, damageMultiplier, damageSource);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("LayCooldown", layCooldown);
		output.putInt("LayTicks", layTicks);
		output.putBoolean("Laying", isLayingEggs());
		climber.writeToNbt(output);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		layCooldown = input.getIntOr("LayCooldown", layCooldown);
		layTicks = input.getIntOr("LayTicks", 0);
		setLayingEggs(input.getBooleanOr("Laying", false));
		climber.readFromNbt(input);
	}

	@Override
	public ClimberComponent getClimberComponent() {
		return climber;
	}

	@Override
	public Mob asMob() {
		return this;
	}

	@Override
	public float getMovementSpeed() {
		return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
	}

	@Override
	public float getBlockSlipperiness(BlockPos pos) {
		return SilverfishClimber.blockSlipperiness(this, pos);
	}

	@Override
	public boolean canClimbOnBlock(BlockState state, BlockPos pos) {
		return SilverfishClimber.canClimbOnBlock(this, state, pos);
	}

	@Override
	public Direction getGroundSide() {
		return climber.getGroundSide();
	}

	@Override
	public float getBridgePathingMalus(Mob entity, BlockPos pos, @Nullable Node fallPathPoint) {
		return SilverfishClimber.bridgePathingMalus(entity, pos, fallPathPoint);
	}

	@Override
	public float getPathingMalus(
			BlockGetter cache,
			Mob entity,
			PathType nodeType,
			BlockPos pos,
			Vec3i direction,
			java.util.function.Predicate<Direction> sides
	) {
		return SilverfishClimber.pathingMalus(this, cache, entity, nodeType, pos, direction, sides);
	}

	@Override
	public void setLerpYRot(Float yRot) {
		this.lerpYRot = yRot != null ? yRot.doubleValue() : 0.0;
	}

	@Override
	public void setLerpXRot(Float xRot) {
		this.lerpXRot = xRot != null ? xRot.doubleValue() : 0.0;
	}

	@Override
	public void setLerpYHeadRot(Float yHeadRot) {
		this.lerpYHeadRot = yHeadRot != null ? yHeadRot.doubleValue() : 0.0;
	}

	@Override
	public void setLerpHeadSteps(int steps) {
		this.lerpHeadSteps = steps;
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
			if (layCooldown > 0 || isLayingEggs() || isAggressive()) {
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
			return isLayingEggs() && layTicks > 0;
		}

		@Override
		public void start() {
			getNavigation().stop();
			setLayingEggs(true);
			layTicks = LAY_ANIM_TICKS;
		}

		@Override
		public void stop() {
			setLayingEggs(false);
			layTicks = 0;
		}

		@Override
		public void tick() {
			getNavigation().stop();
			layTicks--;
			if (layTicks > 0) {
				return;
			}
			boolean placed = tryPlaceEggNearHome();
			setLayingEggs(false);
			RandomSource rand = random;
			layCooldown = LAY_COOLDOWN_MIN + rand.nextInt(Math.max(1, LAY_COOLDOWN_MAX - LAY_COOLDOWN_MIN + 1));
			if (!placed) {
				layCooldown = Math.min(layCooldown, 600 + rand.nextInt(400));
			}
		}
	}
}
