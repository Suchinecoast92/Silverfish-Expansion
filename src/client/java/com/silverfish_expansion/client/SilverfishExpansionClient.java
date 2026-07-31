package com.silverfish_expansion.client;

import com.silverfish_expansion.block.ModBlocks;
import com.silverfish_expansion.entity.ExpandedSilverfishEntity;
import com.silverfish_expansion.entity.ModEntities;
import com.silverfish_expansion.entity.SilverfishLarvaEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SilverfishExpansionClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockRenderLayerMap.putBlock(ModBlocks.SILVERFISH_EGGS, ChunkSectionLayer.CUTOUT);

		registerSilverfishRenderer(ModEntities.ARANEO_SILVERFISH);
		registerSilverfishRenderer(ModEntities.MANTIS_SILVERFISH);
		registerSilverfishRenderer(ModEntities.FLY_SILVERFISH);
		EntityRenderers.register(ModEntities.LARVA, context ->
				new GeoEntityRenderer<SilverfishLarvaEntity, LivingEntityRenderState>(context, ModEntities.LARVA));
	}

	private static void registerSilverfishRenderer(net.minecraft.world.entity.EntityType<ExpandedSilverfishEntity> type) {
		EntityRenderers.register(type, context ->
				new GeoEntityRenderer<ExpandedSilverfishEntity, LivingEntityRenderState>(context, type));
	}
}
