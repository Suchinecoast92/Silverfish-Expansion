package com.silverfish_expansion.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import com.silverfish_expansion.entity.ModEntities;
import com.silverfish_expansion.entity.SilverfishLarvaEntity;

/**
 * Stackable silverfish egg cluster (1–4) attachable to floor, walls, and ceiling.
 * Crushable like turtle eggs when on the floor. Spawns larvae on break.
 */
public class SilverfishEggsBlock extends Block {
	public static final MapCodec<SilverfishEggsBlock> CODEC = simpleCodec(SilverfishEggsBlock::new);

	public static final int MAX_EGGS = 4;
	private static final float LARVA_SPAWN_CHANCE = 0.6F;
	public static final IntegerProperty EGGS = IntegerProperty.create("eggs", 1, MAX_EGGS);
	/** Direction the eggs grow away from the support (like amethyst). */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	/**
	 * TURTLE_EGG_BREAK only when the block actually breaks.
	 * Vanilla turtle eggs use the metal place sound when placed by hand.
	 */
	public static final SoundType SOUND_TYPE = new SoundType(
			1.0F,
			1.0F,
			SoundEvents.TURTLE_EGG_BREAK,
			SoundEvents.SLIME_BLOCK_STEP,
			SoundEvents.METAL_PLACE,
			SoundEvents.SLIME_BLOCK_HIT,
			SoundEvents.SLIME_BLOCK_FALL
	);

	private static final VoxelShape SHAPE_UP = Block.box(2.0, 0.0, 2.0, 14.0, 5.0, 14.0);
	private static final VoxelShape SHAPE_DOWN = Block.box(2.0, 11.0, 2.0, 14.0, 16.0, 14.0);
	private static final VoxelShape SHAPE_NORTH = Block.box(2.0, 2.0, 11.0, 14.0, 14.0, 16.0);
	private static final VoxelShape SHAPE_SOUTH = Block.box(2.0, 2.0, 0.0, 14.0, 14.0, 5.0);
	private static final VoxelShape SHAPE_WEST = Block.box(11.0, 2.0, 2.0, 16.0, 14.0, 14.0);
	private static final VoxelShape SHAPE_EAST = Block.box(0.0, 2.0, 2.0, 5.0, 14.0, 14.0);

	public SilverfishEggsBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(EGGS, 1).setValue(FACING, Direction.UP));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(EGGS, FACING);
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return !context.isSecondaryUseActive()
				&& context.getItemInHand().is(asItem())
				&& state.getValue(EGGS) < MAX_EGGS;
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
		if (existing.is(this)) {
			return existing.setValue(EGGS, Math.min(MAX_EGGS, existing.getValue(EGGS) + 1));
		}

		Direction face = context.getClickedFace();
		BlockState placed = defaultBlockState().setValue(FACING, face);
		return placed.canSurvive(context.getLevel(), context.getClickedPos()) ? placed : null;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapeFor(state.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapeFor(state.getValue(FACING));
	}

	private static VoxelShape shapeFor(Direction facing) {
		return switch (facing) {
			case DOWN -> SHAPE_DOWN;
			case NORTH -> SHAPE_NORTH;
			case SOUTH -> SHAPE_SOUTH;
			case WEST -> SHAPE_WEST;
			case EAST -> SHAPE_EAST;
			default -> SHAPE_UP;
		};
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		BlockPos supportPos = pos.relative(facing.getOpposite());
		return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing);
	}

	@Override
	protected BlockState updateShape(
			BlockState state,
			LevelReader level,
			net.minecraft.world.level.ScheduledTickAccess scheduledTickAccess,
			BlockPos pos,
			Direction direction,
			BlockPos neighborPos,
			BlockState neighborState,
			net.minecraft.util.RandomSource random
	) {
		if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		// Only floor clusters crush when walked on (like turtle eggs).
		if (state.getValue(FACING) == Direction.UP && !entity.isSteppingCarefully()) {
			tryCrush(level, state, pos, entity, 100);
		}
		super.stepOn(level, pos, state, entity);
	}

	@Override
	public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
		if (state.getValue(FACING) == Direction.UP
				&& (!(entity instanceof LivingEntity living) || !living.isSteppingCarefully())) {
			tryCrush(level, state, pos, entity, 3);
		}
		super.fallOn(level, state, pos, entity, fallDistance);
	}

	/** Like turtle eggs: chance to lose one egg when walked/jumped on. */
	private void tryCrush(Level level, BlockState state, BlockPos pos, Entity entity, int inverseChance) {
		if (!canCrush(entity) || level.isClientSide()) {
			return;
		}
		if (level.random.nextInt(inverseChance) == 0) {
			decreaseEggs(level, pos, state);
		}
	}

	private static boolean canCrush(Entity entity) {
		if (entity instanceof ItemEntity) {
			return false;
		}
		return entity instanceof LivingEntity;
	}

	private void decreaseEggs(Level level, BlockPos pos, BlockState state) {
		int eggs = state.getValue(EGGS);
		trySpawnLarva(level, pos);
		level.playSound(
				null,
				pos,
				SoundEvents.TURTLE_EGG_BREAK,
				SoundSource.BLOCKS,
				0.7F,
				0.9F + level.random.nextFloat() * 0.2F
		);

		if (eggs <= 1) {
			level.removeBlock(pos, false);
		} else {
			level.setBlock(pos, state.setValue(EGGS, eggs - 1), Block.UPDATE_ALL);
		}
	}

	/** Some eggs are undeveloped, so not every broken egg releases a larva. */
	private static void trySpawnLarva(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (serverLevel.random.nextFloat() >= LARVA_SPAWN_CHANCE) {
			return;
		}
		SilverfishLarvaEntity larva = ModEntities.LARVA.create(serverLevel, EntitySpawnReason.TRIGGERED);
		if (larva != null) {
			larva.snapTo(
					pos.getX() + 0.5,
					pos.getY() + 0.1,
					pos.getZ() + 0.5,
					serverLevel.random.nextFloat() * 360.0F,
					0.0F
			);
			serverLevel.addFreshEntity(larva);
		}
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && !player.isCreative()) {
			int eggs = state.getValue(EGGS);
			for (int i = 0; i < eggs; i++) {
				trySpawnLarva(level, pos);
			}
			level.playSound(
					null,
					pos,
					SoundEvents.TURTLE_EGG_BREAK,
					SoundSource.BLOCKS,
					0.7F,
					0.9F + level.random.nextFloat() * 0.2F
			);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	public static Properties createProperties() {
		return Properties.of()
				.strength(0.5F)
				.sound(SOUND_TYPE)
				.noOcclusion()
				.pushReaction(PushReaction.DESTROY);
	}
}
