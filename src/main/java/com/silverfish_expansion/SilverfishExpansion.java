package com.silverfish_expansion;

import com.silverfish_expansion.entity.ModEntities;
import com.silverfish_expansion.item.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SilverfishExpansion implements ModInitializer {
	public static final String MOD_ID = "silverfish-expansion";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModEntities.registerAttributes();
		ModItems.register();
		LOGGER.info("Silverfish Expansion loaded with {} new silverfish variants.", 3);
	}
}
