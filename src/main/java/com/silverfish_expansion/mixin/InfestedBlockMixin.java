package com.silverfish_expansion.mixin;

import com.silverfish_expansion.colony.ColonyInfestationData;
import com.silverfish_expansion.colony.ColonyInfestationSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InfestedBlock.class)
public class InfestedBlockMixin {
	@Inject(method = "spawnInfestation", at = @At("HEAD"), cancellable = true)
	private void silverfishExpansion$colonySpawn(ServerLevel level, BlockPos pos, CallbackInfo ci) {
		ColonyInfestationData data = ColonyInfestationData.get(level);
		if (!data.contains(pos)) {
			return;
		}
		long infestTime = data.remove(pos);
		ColonyInfestationData.Maturity maturity = ColonyInfestationData.stageFor(infestTime, level.getGameTime());
		ColonyInfestationSpawner.spawn(level, pos, maturity);
		ci.cancel();
	}

	@Inject(method = "spawnAfterBreak", at = @At("RETURN"))
	private void silverfishExpansion$cleanupTracking(
			BlockState state,
			ServerLevel level,
			BlockPos pos,
			ItemStack stack,
			boolean dropExperience,
			CallbackInfo ci
	) {
		// Silk Touch (and any other path that skips spawnInfestation) still clears tracking.
		ColonyInfestationData.get(level).remove(pos);
	}
}
