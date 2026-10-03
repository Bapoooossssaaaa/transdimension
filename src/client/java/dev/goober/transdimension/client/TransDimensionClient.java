package dev.goober.transdimension.client;

import java.util.Objects;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.Level;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.registry.ModFluids;

public class TransDimensionClient implements ClientModInitializer {
	private static ResourceKey<Level> lastDimension;

	@Override
	public void onInitializeClient() {
		TransRecolor.captureRenderThread();

		// Animated pink/blue/white Trans Water. No tint, so the texture's own colours show.
		FluidRenderingRegistry.register(ModFluids.TRANS_WATER, ModFluids.FLOWING_TRANS_WATER, new FluidModel.Unbaked(
				new Material(TransDimension.id("block/trans_water_still"), true),
				new Material(TransDimension.id("block/trans_water_flow"), true),
				null,
				null));

		// The dimension intro animation draws on top of the whole HUD.
		HudElementRegistry.addLast(TransDimension.id("trans_intro"), TransIntroOverlay::extract);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ResourceKey<Level> dimension = client.level != null ? client.level.dimension() : null;
			boolean inRealm = TransDimension.TRANS_REALM.equals(dimension);

			if (!Objects.equals(dimension, lastDimension)) {
				if (inRealm) {
					TransIntroOverlay.start();
				}
				lastDimension = dimension;
			}

			TransRecolor.setActive(inRealm);
			TransIntroOverlay.tick(client);
			HeartClouds.tick(client, inRealm);
		});

		// After vanilla reloads its clouds (F3+T, resource pack changes), re-apply the hearts if needed.
		Identifier reloaderId = TransDimension.id("trans_visuals");
		ResourceLoader loader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
		loader.registerReloadListener(reloaderId, (ResourceManagerReloadListener) manager -> {
			HeartClouds.onResourcesReloaded();
			TransRecolor.clearCache();
		});
		loader.addListenerOrdering(ResourceReloaderKeys.Client.CLOUD_RENDERER, reloaderId);
	}
}
