package com.silverfish_expansion;

import com.silverfish_expansion.block.ModBlocks;
import com.silverfish_expansion.colony.ColonyInfestationData;
import com.silverfish_expansion.entity.ModEntities;
import com.silverfish_expansion.item.ModItems;
import com.silverfish_expansion.worldgen.ModStructures;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SilverfishExpansion implements ModInitializer {
	public static final String MOD_ID = "silverfish-expansion";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModEntities.registerAttributes();
		ModEntities.registerSpawnPlacements();
		ModItems.register();
		ModStructures.register();
		ServerTickEvents.END_WORLD_TICK.register(level -> {
			if (level.getGameTime() % 200L == 0L) {
				ColonyInfestationData.get(level).cleanup(level);
			}
		});
		LOGGER.info("Silverfish Expansion loaded.");
	}
}
