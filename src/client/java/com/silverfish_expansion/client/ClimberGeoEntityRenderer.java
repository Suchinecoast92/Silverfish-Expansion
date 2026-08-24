package com.silverfish_expansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nyfaria.awcapi.ClientClimberHelper;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * GeckoLib renderer that applies AWCAPI wall/ceiling orientation.
 */
public class ClimberGeoEntityRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T, ClimberEntityRenderState> {
	public ClimberGeoEntityRenderer(EntityRendererProvider.Context context, EntityType<? extends T> entityType) {
		super(context, entityType);
	}

	@Override
	public ClimberEntityRenderState createRenderState(T animatable, @Nullable Void relatedObject) {
		return new ClimberEntityRenderState();
	}

	@Override
	public void extractRenderState(T entity, ClimberEntityRenderState entityRenderState, float partialTick) {
		super.extractRenderState(entity, entityRenderState, partialTick);
		if (entity instanceof LivingEntity living) {
			ClientClimberHelper.extractClimbingRenderState(living, entityRenderState, partialTick);
		}
	}

	@Override
	public void submit(
			ClimberEntityRenderState renderState,
			PoseStack poseStack,
			SubmitNodeCollector renderTasks,
			CameraRenderState cameraState
	) {
		ClientClimberHelper.preRenderClimber(renderState, poseStack);
		super.submit(renderState, poseStack, renderTasks, cameraState);
		ClientClimberHelper.postRenderClimber(renderState, poseStack);
	}
}
