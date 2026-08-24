package com.silverfish_expansion.entity;

import com.silverfish_expansion.colony.ColonyPresence;
import com.silverfish_expansion.entity.ai.CuriousSilverfishLookGoal;
import com.silverfish_expansion.entity.ai.ErraticSilverfishWanderGoal;
import com.silverfish_expansion.entity.ai.FlySilverfishAttackGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.state.AnimationTest;
import software.bernie.geckolib.util.GeckoLibUtil;

public class ExpandedSilverfishEntity extends Monster implements GeoEntity {
	/** 0 = ground, 1 = flying, 2 = approach ground, 3 = landing (stopFlying). */
	private static final EntityDataAccessor<Byte> DATA_FLIGHT_STATE =
			SynchedEntityData.defineId(ExpandedSilverfishEntity.class, EntityDataSerializers.BYTE);
	/** -1 = none (neutral), 0 = idle, 1 = idle2, 2 = idle3 (fly only). */
	private static final EntityDataAccessor<Byte> DATA_IDLE_PICK =
			SynchedEntityData.defineId(ExpandedSilverfishEntity.class, EntityDataSerializers.BYTE);
	/** True only when the entity actually changes position (walking, pushed, sliding). */
	private static final EntityDataAccessor<Boolean> DATA_WALKING =
			SynchedEntityData.defineId(ExpandedSilverfishEntity.class, EntityDataSerializers.BOOLEAN);
	/** Spider-style: colliding horizontally while using wall navigation. */
	private static final EntityDataAccessor<Boolean> DATA_CLIMBING =
			SynchedEntityData.defineId(ExpandedSilverfishEntity.class, EntityDataSerializers.BOOLEAN);

	private static final byte FLIGHT_GROUND = 0;
	private static final byte FLIGHT_FLYING = 1;
	private static final byte FLIGHT_APPROACH = 2;
	private static final byte FLIGHT_LANDING = 3;

	private static final int STOP_FLYING_TICKS = 40;
	/** Wait for startFlying to begin (controller blend) before lifting off the ground. */
	private static final int TAKEOFF_ANIM_DELAY = 10;
	/** Soft lift duration after the animation has started (~1.5s of startFlying). */
	private static final int TAKEOFF_LIFT_TICKS = 30;
	private static final int MIN_FLIGHT_TICKS = 140;
	private static final int MAX_FLIGHT_TICKS = 280;
	private static final double LAND_ANIM_HEIGHT = 0.85; // start stopFlying near the ground
	/** Need more than ~1 free block above the body to start flying. */
	private static final double MIN_TAKEOFF_HEADROOM = 1.05;
	/** Abort climb / force descent when this little free space remains above. */
	private static final double MIN_FLIGHT_HEADROOM = 0.35;
	private static final int LOCAL_FLOOR_SCAN_DOWN = 8;
	private static final int LOCAL_CEILING_SCAN_UP = 5;
	/** Chance to play a real idle when stopping; otherwise stay in neutral "none". */
	private static final float IDLE_CHANCE_ON_STOP = 0.35F;
	/** Window used to measure how far the entity actually travelled. */
	private static final int WALK_WINDOW_TICKS = 5;
	/** Net travel over the window needed to count as walking (0.1 blocks, squared). */
	private static final double WALK_NET_THRESHOLD_SQR = 0.01;
	/** Keeps walk playing briefly between steps so it doesn't flicker. */
	private static final int WALK_GRACE_TICKS = 2;

	private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation NONE_ANIM = RawAnimation.begin().thenLoop("none");
	/** Play once, then return to neutral so idles are not constant. */
	private static final RawAnimation IDLE_ANIM =
			RawAnimation.begin().thenPlay("idle").thenLoop("none");
	private static final RawAnimation IDLE2_ANIM =
			RawAnimation.begin().thenPlay("idle2").thenLoop("none");
	private static final RawAnimation IDLE3_ANIM =
			RawAnimation.begin().thenPlay("idle3").thenLoop("none");
	private static final RawAnimation TAKEOFF_ANIM =
			RawAnimation.begin().thenPlay("startFlying").thenLoop("flying");
	private static final RawAnimation LAND_ANIM =
			RawAnimation.begin().thenPlay("stopFlying");

	private final SilverfishVariant variant;
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private int flyTicks;
	private int flyCooldown;
	private int landingTicks;
	private boolean wasServerMoving;
	private final double[] walkHistoryX = new double[WALK_WINDOW_TICKS];
	private final double[] walkHistoryY = new double[WALK_WINDOW_TICKS];
	private final double[] walkHistoryZ = new double[WALK_WINDOW_TICKS];
	private int walkHistoryIndex;
	private int walkHistoryCount;
	private int walkGrace;

	private Vec3 flightWanderTarget = Vec3.ZERO;
	private int flightRetargetTicks;

	public ExpandedSilverfishEntity(SilverfishVariant variant, EntityType<? extends ExpandedSilverfishEntity> type, Level level) {
		super(type, level);
		this.variant = variant;
	}

	public boolean usesWallClimbing() {
		return SilverfishClimber.usesWallClimbing(this);
	}

	public SilverfishVariant getVariant() {
		return variant;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAttributes(SilverfishVariant.ARANEO);
	}

	public static AttributeSupplier.Builder createAttributes(SilverfishVariant variant) {
		return switch (variant) {
			case ARANEO -> Monster.createMonsterAttributes()
					.add(Attributes.MAX_HEALTH, 8.0)
					.add(Attributes.MOVEMENT_SPEED, 0.28)
					.add(Attributes.ATTACK_DAMAGE, 2.0)
					.add(Attributes.FOLLOW_RANGE, 16.0);
			case MANTIS -> Monster.createMonsterAttributes()
					.add(Attributes.MAX_HEALTH, 7.0)
					.add(Attributes.MOVEMENT_SPEED, 0.30)
					.add(Attributes.ATTACK_DAMAGE, 3.0)
					.add(Attributes.FOLLOW_RANGE, 16.0);
			case FLY -> Monster.createMonsterAttributes()
					.add(Attributes.MAX_HEALTH, 5.0)
					.add(Attributes.MOVEMENT_SPEED, 0.36)
					.add(Attributes.ATTACK_DAMAGE, 1.5)
					.add(Attributes.FOLLOW_RANGE, 18.0);
			case CRAB -> Monster.createMonsterAttributes()
					.add(Attributes.MAX_HEALTH, 14.0)
					.add(Attributes.MOVEMENT_SPEED, 0.22)
					.add(Attributes.ATTACK_DAMAGE, 2.0)
					.add(Attributes.FOLLOW_RANGE, 14.0)
					.add(Attributes.ARMOR, 3.0)
					.add(Attributes.KNOCKBACK_RESISTANCE, 0.45);
		};
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FLIGHT_STATE, FLIGHT_GROUND);
		builder.define(DATA_IDLE_PICK, (byte) -1);
		builder.define(DATA_WALKING, false);
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

	private byte getIdlePick() {
		return entityData.get(DATA_IDLE_PICK);
	}

	/** Server-side: usually none; sometimes a one-shot idle (synced to client). */
	private void rollIdleServer() {
		if (random.nextFloat() >= IDLE_CHANCE_ON_STOP) {
			entityData.set(DATA_IDLE_PICK, (byte) -1);
			return;
		}
		if (variant == SilverfishVariant.FLY) {
			// Equal among idle / idle2 / idle3
			entityData.set(DATA_IDLE_PICK, (byte) random.nextInt(3));
			return;
		}
		entityData.set(DATA_IDLE_PICK, (byte) random.nextInt(2));
	}

	private RawAnimation idleAnimationForPick(byte pick) {
		return switch (pick) {
			case 0 -> IDLE_ANIM;
			case 1 -> IDLE2_ANIM;
			case 2 -> IDLE3_ANIM;
			default -> NONE_ANIM;
		};
	}

	public boolean isFlying() {
		byte state = getFlightState();
		return getVariant() == SilverfishVariant.FLY
				&& (state == FLIGHT_FLYING || state == FLIGHT_APPROACH);
	}

	public boolean isLanding() {
		return getVariant() == SilverfishVariant.FLY && getFlightState() == FLIGHT_LANDING;
	}

	private byte getFlightState() {
		return entityData.get(DATA_FLIGHT_STATE);
	}

	private void setFlightState(byte state) {
		if (getVariant() != SilverfishVariant.FLY) {
			return;
		}
		entityData.set(DATA_FLIGHT_STATE, state);
	}

	public void setFlying(boolean flying) {
		if (getVariant() != SilverfishVariant.FLY) {
			return;
		}
		if (flying) {
			if (!hasTakeoffClearance()) {
				flyCooldown = 80 + random.nextInt(80);
				return;
			}
			setFlightState(FLIGHT_FLYING);
			flyTicks = 0;
			flightRetargetTicks = 0;
			// Don't enable no-gravity yet — wait until lift phase so it doesn't float early
			setNoGravity(false);
			getNavigation().stop();
			setDeltaMovement(getDeltaMovement().multiply(0.5, 0.0, 0.5));
		} else if (getFlightState() == FLIGHT_FLYING) {
			// Soft approach first — stopFlying only starts near the ground
			setFlightState(FLIGHT_APPROACH);
			fallDistance = 0.0F;
			setNoGravity(true);
		}
	}

	private void beginLandingAnim() {
		setFlightState(FLIGHT_LANDING);
		landingTicks = STOP_FLYING_TICKS;
		fallDistance = 0.0F;
		setNoGravity(true);
	}

	private double heightAboveGround() {
		return getY() - localFloorY(getX(), getY(), getZ());
	}

	private double availableHeadroom() {
		return localCeilingY(getX(), getY(), getZ()) - (getY() + getBbHeight());
	}

	private boolean hasTakeoffClearance() {
		// Enough open air above, and room to rise off the floor a bit.
		return availableHeadroom() >= MIN_TAKEOFF_HEADROOM
				&& hasCollisionFreeOffset(0.0, 0.55, 0.0);
	}

	private boolean hasCollisionFreeOffset(double dx, double dy, double dz) {
		return level().noCollision(this, getBoundingBox().move(dx, dy, dz));
	}

	private double localFloorY(double x, double y, double z) {
		int blockX = Mth.floor(x);
		int blockZ = Mth.floor(z);
		int startY = Mth.floor(y);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int dy = 0; dy <= LOCAL_FLOOR_SCAN_DOWN; dy++) {
			cursor.set(blockX, startY - dy, blockZ);
			if (!level().getBlockState(cursor).getCollisionShape(level(), cursor).isEmpty()) {
				return cursor.getY() + 1.0;
			}
		}

		return y - 0.5;
	}

	private double localCeilingY(double x, double y, double z) {
		int blockX = Mth.floor(x);
		int blockZ = Mth.floor(z);
		int startY = Mth.floor(y + getBbHeight());
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int dy = 0; dy <= LOCAL_CEILING_SCAN_UP; dy++) {
			cursor.set(blockX, startY + dy, blockZ);
			if (!level().getBlockState(cursor).getCollisionShape(level(), cursor).isEmpty()) {
				return cursor.getY();
			}
		}

		return y + getBbHeight() + LOCAL_CEILING_SCAN_UP;
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		if (!SilverfishClimber.usesWallClimbing(this)) {
			return super.createNavigation(level);
		}
		return SilverfishClimber.createNavigation(this, level);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		if (variant == SilverfishVariant.FLY) {
			goalSelector.addGoal(2, new FlySilverfishAttackGoal(this));
		} else {
			goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
		}
		// Nervous short hops + curious pauses; Fly only while grounded and not chasing.
		goalSelector.addGoal(5, new ErraticSilverfishWanderGoal(this, 0.95, this::canWanderOnGround));
		goalSelector.addGoal(6, new CuriousSilverfishLookGoal(this, this::canWanderOnGround));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
		boolean hurt = super.hurtServer(level, damageSource, amount);
		if (hurt) {
			Entity attacker = damageSource.getEntity();
			if (attacker instanceof LivingEntity living) {
				ColonyPresence.alertColony(level, this, living);
			}
		}
		return hurt;
	}

	/** Fly variant should stay airborne while it has a living target. */
	public boolean wantsAerialChase() {
		return getVariant() == SilverfishVariant.FLY;
	}

	public void requestCombatTakeoff() {
		if (getVariant() != SilverfishVariant.FLY || isFlying() || isLanding()) {
			return;
		}
		if (hasTakeoffClearance()) {
			setFlying(true);
			flyCooldown = 0;
		}
	}

	private boolean canWanderOnGround() {
		return getVariant() != SilverfishVariant.FLY || getFlightState() == FLIGHT_GROUND;
	}

	@Override
	public void tick() {
		super.tick();
		if (usesWallClimbing() && !level().isClientSide()) {
			setClimbing(horizontalCollision);
		}
		if (getVariant() == SilverfishVariant.FLY) {
			fallDistance = 0.0F;
		}

		if (!level().isClientSide()) {
			tickWalkState();
			tickIdleRoll();
		}

		if (level().isClientSide() || getVariant() != SilverfishVariant.FLY) {
			return;
		}

		byte state = getFlightState();
		if (state == FLIGHT_LANDING) {
			tickLanding();
			return;
		}

		if (state == FLIGHT_APPROACH) {
			tickApproachGround();
			return;
		}

		if (state == FLIGHT_FLYING) {
			tickFlying();
			return;
		}

		setNoGravity(false);

		if (getTarget() != null && getTarget().isAlive() && hasTakeoffClearance()) {
			setFlying(true);
			return;
		}

		if (flyCooldown > 0) {
			flyCooldown--;
			return;
		}

		// Prefers flying over long ground travel
		if (onGround() && random.nextInt(70) == 0 && hasTakeoffClearance()) {
			setFlying(true);
		}
	}

	/**
	 * Walk is driven by net travel over a short window, not by per-tick speed: an entity
	 * shoving against a wall or another mob keeps returning to the same spot, so it must
	 * not animate. Real movement (own pathing or being pushed away) always does.
	 */
	private void tickWalkState() {
		if (getVariant() == SilverfishVariant.FLY && getFlightState() != FLIGHT_GROUND) {
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
		if (windowReady && netSqr > 4.0) {
			// Faster than this mob can ever walk: teleport or dimension move, not a step
			resetWalkTracking();
		} else if (windowReady && netSqr > WALK_NET_THRESHOLD_SQR) {
			walkGrace = WALK_GRACE_TICKS;
		} else if (walkGrace > 0) {
			walkGrace--;
		}

		boolean walking = walkGrace > 0;
		if (walking != isWalking()) {
			entityData.set(DATA_WALKING, walking);
		}
	}

	/** Drop stale positions so teleports and landings can't look like walking. */
	private void resetWalkTracking() {
		walkHistoryIndex = 0;
		walkHistoryCount = 0;
		walkGrace = 0;
	}

	/** Detect walk → stop on server and roll a new idle (works even if fly rarely walks). */
	private void tickIdleRoll() {
		if (getVariant() == SilverfishVariant.FLY) {
			byte flight = getFlightState();
			if (flight != FLIGHT_GROUND) {
				wasServerMoving = false;
				return;
			}
		}

		boolean moving = isWalking();
		if (wasServerMoving && !moving) {
			rollIdleServer();
		}
		wasServerMoving = moving;
	}

	/** Phase 3: stopFlying plays only when already near the ground. Soft touchdown. */
	private void tickLanding() {
		landingTicks--;
		Vec3 motion = getDeltaMovement();
		double sink = Math.max(motion.y, -0.06);
		if (onGround()) {
			setDeltaMovement(motion.x * 0.4, 0.0, motion.z * 0.4);
		} else {
			setDeltaMovement(motion.x * 0.92, sink, motion.z * 0.92);
		}
		fallDistance = 0.0F;

		if (landingTicks <= 0 || (onGround() && landingTicks <= STOP_FLYING_TICKS - 20)) {
			setFlightState(FLIGHT_GROUND);
			setNoGravity(false);
			setDeltaMovement(getDeltaMovement().multiply(0.3, 0.0, 0.3));
			flyCooldown = 60 + random.nextInt(80);
			// Prefer none / occasional idle on touchdown (fly rarely walks)
			rollIdleServer();
		}
	}

	/**
	 * Between free flight and stopFlying: descend gently until close to the ground,
	 * then start the landing animation so stopFlying looks natural.
	 */
	private void tickApproachGround() {
		fallDistance = 0.0F;
		setNoGravity(true);
		getNavigation().stop();

		Vec3 motion = getDeltaMovement();
		double sink = Math.max(motion.y - 0.015, -0.08);
		setDeltaMovement(motion.x * 0.88, sink, motion.z * 0.88);

		if (onGround() || heightAboveGround() <= LAND_ANIM_HEIGHT) {
			beginLandingAnim();
		}
	}

	private void tickFlying() {
		flyTicks++;
		fallDistance = 0.0F;
		getNavigation().stop();

		Vec3 motion = getDeltaMovement();

		if (flyTicks < TAKEOFF_ANIM_DELAY) {
			// Phase 1a — stay on / near ground until startFlying is visibly playing
			setNoGravity(false);
			setDeltaMovement(motion.x * 0.55, onGround() ? 0.0 : Math.min(motion.y, 0.0), motion.z * 0.55);
		} else if (flyTicks < TAKEOFF_ANIM_DELAY + TAKEOFF_LIFT_TICKS) {
			// Phase 1b — soft takeoff lift (same feel as before, synced with startFlying)
			setNoGravity(true);
			double headroom = availableHeadroom();
			// Do NOT use verticalCollision here: standing on the floor also sets it and aborted flight instantly.
			if (headroom < MIN_FLIGHT_HEADROOM) {
				setFlying(false);
				return;
			}
			double maxLift = Math.min(0.11, Math.max(0.02, headroom * 0.25));
			double lift = Math.min(motion.y + 0.035, maxLift);
			if (!hasCollisionFreeOffset(0.0, Math.max(0.05, lift), 0.0)) {
				lift = Math.min(0.0, motion.y);
				if (headroom < 0.7) {
					setFlying(false);
					return;
				}
			}
			setDeltaMovement(motion.x * 0.96, lift, motion.z * 0.96);
		} else {
			// Phase 2 — chase the attacker or wander like a bee
			setNoGravity(true);
			if (getTarget() != null && getTarget().isAlive()) {
				tickChaseFlight();
			} else {
				tickErraticFlight();
			}
		}

		boolean chasing = getTarget() != null && getTarget().isAlive();
		int flightLimit = MIN_FLIGHT_TICKS + random.nextInt(MAX_FLIGHT_TICKS - MIN_FLIGHT_TICKS + 1);
		if (!chasing && flyTicks >= flightLimit) {
			setFlying(false);
		}
	}

	/**
	 * Bee-like pursuit: dart toward the target's upper body, stay off the floor,
	 * and keep cave headroom so the hive ceiling is not punched through.
	 */
	private void tickChaseFlight() {
		LivingEntity target = getTarget();
		if (target == null) {
			return;
		}

		double hoverY = target.getY() + Math.max(0.25, target.getBbHeight() * 0.35);
		double floorY = localFloorY(target.getX(), hoverY, target.getZ());
		double ceilingY = localCeilingY(target.getX(), hoverY, target.getZ());
		double maxY = ceilingY - getBbHeight() - 0.15;
		double minY = floorY + 0.35;
		if (maxY > minY) {
			hoverY = Mth.clamp(hoverY, minY, maxY);
		}

		Vec3 desiredPos = new Vec3(target.getX(), hoverY, target.getZ());
		Vec3 toTarget = desiredPos.subtract(position());
		double dist = toTarget.length();
		if (dist < 0.001) {
			return;
		}

		Vec3 dir = toTarget.scale(1.0 / dist);
		dir = dir.add(
				(random.nextDouble() - 0.5) * 0.18,
				(random.nextDouble() - 0.5) * 0.10,
				(random.nextDouble() - 0.5) * 0.18
		).normalize();

		double speed = dist < 2.0 ? 0.22 : 0.32;
		Vec3 desired = dir.scale(speed);
		Vec3 motion = getDeltaMovement();
		double headroom = availableHeadroom();
		if (headroom < MIN_FLIGHT_HEADROOM) {
			desired = new Vec3(desired.x, Math.min(desired.y, -0.05), desired.z);
		}
		if (!hasCollisionFreeOffset(desired.x, desired.y, desired.z)) {
			Vec3 fallback = new Vec3(desired.x * 0.35, Math.min(desired.y, -0.04), desired.z * 0.35);
			desired = hasCollisionFreeOffset(fallback.x, fallback.y, fallback.z)
					? fallback
					: new Vec3(-motion.x * 0.25, -0.04, -motion.z * 0.25);
		}

		setDeltaMovement(
				Mth.lerp(0.42, motion.x, desired.x),
				Mth.lerp(0.42, motion.y, desired.y),
				Mth.lerp(0.42, motion.z, desired.z)
		);

		if (desired.horizontalDistanceSqr() > 1.0E-4) {
			float yaw = (float) (Mth.atan2(desired.z, desired.x) * (180.0 / Math.PI)) - 90.0F;
			setYRot(Mth.rotLerp(0.45F, getYRot(), yaw));
			yBodyRot = getYRot();
		}
	}

	/**
	 * Bee-like free flight: short erratic hops between nearby air points,
	 * low altitude, no straight fixed paths.
	 */
	private void tickErraticFlight() {
		if (flightRetargetTicks <= 0 || flightWanderTarget.distanceToSqr(position()) < 1.2) {
			flightWanderTarget = pickErraticAirTarget();
			flightRetargetTicks = 12 + random.nextInt(18);
		}
		flightRetargetTicks--;

		Vec3 pos = position();
		Vec3 toTarget = flightWanderTarget.subtract(pos);
		double dist = toTarget.length();
		if (dist < 0.001) {
			return;
		}

		Vec3 dir = toTarget.scale(1.0 / dist);
		// Silverfish-with-wings jitter: twist sideways / up / down often
		dir = dir.add(
				(random.nextDouble() - 0.5) * 0.55,
				(random.nextDouble() - 0.5) * 0.35,
				(random.nextDouble() - 0.5) * 0.55
		).normalize();

		double speed = 0.17 + random.nextDouble() * 0.09;
		Vec3 desired = dir.scale(speed);
		Vec3 motion = getDeltaMovement();
		double headroom = availableHeadroom();
		if (headroom < MIN_FLIGHT_HEADROOM) {
			desired = new Vec3(desired.x, Math.min(desired.y, -0.04), desired.z);
			if (headroom < 0.2) {
				setFlying(false);
				return;
			}
		}
		if (!hasCollisionFreeOffset(desired.x, desired.y, desired.z)) {
			flightRetargetTicks = 0;
			Vec3 fallback = new Vec3(desired.x * 0.4, Math.min(desired.y, -0.05), desired.z * 0.4);
			desired = hasCollisionFreeOffset(fallback.x, fallback.y, fallback.z)
					? fallback
					: new Vec3(-motion.x * 0.3, -0.05, -motion.z * 0.3);
		}
		setDeltaMovement(
				Mth.lerp(0.35, motion.x, desired.x),
				Mth.lerp(0.35, motion.y, desired.y),
				Mth.lerp(0.35, motion.z, desired.z)
		);

		if (horizontalCollision || (verticalCollision && !onGround())) {
			flightRetargetTicks = 0;
		}

		// Face travel direction
		if (desired.horizontalDistanceSqr() > 1.0E-4) {
			float yaw = (float) (Mth.atan2(desired.z, desired.x) * (180.0 / Math.PI)) - 90.0F;
			setYRot(Mth.rotLerp(0.3F, getYRot(), yaw));
			yBodyRot = getYRot();
		}
	}

	private Vec3 pickErraticAirTarget() {
		BlockPos origin = blockPosition();
		double currentFloorY = localFloorY(getX(), getY(), getZ());

		for (int attempt = 0; attempt < 12; attempt++) {
			double dx = (random.nextDouble() - 0.5) * 7.0;
			double dz = (random.nextDouble() - 0.5) * 7.0;
			double hover = 0.45 + random.nextDouble() * 1.35; // stay low in caves
			double x = getX() + dx;
			double z = getZ() + dz;
			double floorY = localFloorY(x, getY(), z);
			double ceilingY = localCeilingY(x, floorY, z);
			double maxY = Math.min(floorY + 2.0, ceilingY - getBbHeight() - 0.15);
			if (maxY <= floorY + 0.25) {
				continue;
			}
			double y = Mth.clamp(floorY + hover, floorY + 0.25, maxY);
			// Prefer staying near current cave layer, not hopping through ceilings/floors.
			y = Mth.clamp(y, currentFloorY + 0.25, currentFloorY + 2.0);
			if (hasCollisionFreeOffset(x - getX(), y - getY(), z - getZ())) {
				return new Vec3(x, y, z);
			}
		}

		double fallbackMaxY = localCeilingY(getX(), getY(), getZ()) - getBbHeight() - 0.15;
		double fallbackMinY = currentFloorY + 0.25;
		double safeY = fallbackMaxY <= fallbackMinY
				? getY()
				: Mth.clamp(getY() - 0.15, fallbackMinY, fallbackMaxY);
		return new Vec3(getX(), safeY, getZ());
	}

	@Override
	public boolean onClimbable() {
		return usesWallClimbing() ? isClimbing() : super.onClimbable();
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource) {
		if (getVariant() == SilverfishVariant.FLY) {
			return false;
		}
		return super.causeFallDamage(fallDistance, damageMultiplier, damageSource);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>("movement", 8, this::movementController));
	}

	private PlayState movementController(AnimationTest<ExpandedSilverfishEntity> state) {
		ExpandedSilverfishEntity entity = state.animatable();

		if (entity.getVariant() == SilverfishVariant.FLY) {
			if (entity.isFlying()) {
				return state.setAndContinue(TAKEOFF_ANIM);
			}
			if (entity.isLanding()) {
				return state.setAndContinue(LAND_ANIM);
			}
		}

		if (entity.isWalking()) {
			return state.setAndContinue(WALK_ANIM);
		}

		return state.setAndContinue(entity.idleAnimationForPick(entity.getIdlePick()));
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
	protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState blockState) {
		playSound(SoundEvents.SILVERFISH_STEP, 0.15F, 1.0F);
	}
}
