package com.silverfish_expansion.item;

import com.silverfish_expansion.entity.LarvaSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Larva spawn egg that hatches with the same logic as {@link com.silverfish_expansion.block.SilverfishEggsBlock}.
 */
public class LarvaSpawnEggItem extends SpawnEggItem {
	public LarvaSpawnEggItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		BlockPos clickedPos = context.getClickedPos();
		Direction face = context.getClickedFace();
		BlockState clickedState = level.getBlockState(clickedPos);
		BlockPos spawnPos = clickedState.getCollisionShape(level, clickedPos).isEmpty()
				? clickedPos
				: clickedPos.relative(face);

		if (spawnLarva(context.getPlayer(), context.getItemInHand(), (ServerLevel) level, clickedPos, spawnPos)) {
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.FAIL;
	}

	private boolean spawnLarva(
			@Nullable Player player,
			ItemStack stack,
			ServerLevel level,
			BlockPos eventPos,
			BlockPos spawnPos
	) {
		if (LarvaSpawner.spawnAt(level, spawnPos) == null) {
			return false;
		}

		level.playSound(
				null,
				spawnPos,
				SoundEvents.TURTLE_EGG_BREAK,
				SoundSource.NEUTRAL,
				0.7F,
				0.9F + level.random.nextFloat() * 0.2F
		);
		level.gameEvent(player, GameEvent.ENTITY_PLACE, eventPos);
		stack.consume(1, player);
		if (player != null) {
			player.awardStat(Stats.ITEM_USED.get(this));
		}
		return true;
	}
}
