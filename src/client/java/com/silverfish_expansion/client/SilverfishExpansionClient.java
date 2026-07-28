package com.silverfish_expansion.client;

import com.silverfish_expansion.entity.ExpandedSilverfishEntity;
import com.silverfish_expansion.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SilverfishExpansionClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		registerRenderer(ModEntities.ARANEO_SILVERFISH);
		registerRenderer(ModEntities.MANTIS_SILVERFISH);
		registerRenderer(ModEntities.FLY_SILVERFISH);
	}

	private static void registerRenderer(net.minecraft.world.entity.EntityType<ExpandedSilverfishEntity> type) {
		EntityRenderers.register(type, context ->
				new GeoEntityRenderer<ExpandedSilverfishEntity, LivingEntityRenderState>(context, type));
	}
}
