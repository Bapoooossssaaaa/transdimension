package dev.goober.transdimension.client;

import java.util.List;
import java.util.Objects;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.object.boat.BoatModel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.client.entity.SillyCatModel;
import dev.goober.transdimension.client.entity.SillyCatRenderer;
import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.registry.ModEntities;

public class TransDimensionClient implements ClientModInitializer {
	/** Trans pink, used for trans grass outside of a world (and as the fallback colour). */
	private static final int DEFAULT_GRASS = 0xFFF5A9B8;

	/** Tints trans grass with the biome's grass colour, so every biome has its own shade. */
	private static final BlockTintSource TRANS_GRASS_TINT = new BlockTintSource() {
		@Override
		public int color(BlockState state) {
			return DEFAULT_GRASS;
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			return level.getBlockTint(pos, BiomeColors.GRASS_COLOR_RESOLVER);
		}
	};

	/** Boat model layers; BoatRenderer reads the texture from the layer id (textures/entity/boat/trans.png and so on). */
	public static final ModelLayerLocation TRANS_BOAT_LAYER = new ModelLayerLocation(TransDimension.id("boat/trans"), "main");
	public static final ModelLayerLocation TRANS_CHEST_BOAT_LAYER = new ModelLayerLocation(TransDimension.id("chest_boat/trans"), "main");

	private static ResourceKey<Level> lastDimension;

	@Override
	public void onInitializeClient() {
		TransRecolor.captureRenderThread();

		BlockColorRegistry.register(List.of(TRANS_GRASS_TINT), ModBlocks.TRANS_GRASS_BLOCK);

		// The Silly Cat.
		ModelLayerRegistry.registerModelLayer(SillyCatRenderer.LAYER, SillyCatModel::createBodyLayer);
		EntityRenderers.register(ModEntities.SILLY_CAT, SillyCatRenderer::new);

		// Trans boats use vanilla's boat models with our textures.
		ModelLayerRegistry.registerModelLayer(TRANS_BOAT_LAYER, BoatModel::createBoatModel);
		ModelLayerRegistry.registerModelLayer(TRANS_CHEST_BOAT_LAYER, BoatModel::createChestBoatModel);
		EntityRenderers.register(ModEntities.TRANS_BOAT, context -> new BoatRenderer(context, TRANS_BOAT_LAYER));
		EntityRenderers.register(ModEntities.TRANS_CHEST_BOAT, context -> new BoatRenderer(context, TRANS_CHEST_BOAT_LAYER));

		// Cat spit sits under the hotbar like the pumpkin overlay; the dimension intro draws on top of everything.
		HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR, TransDimension.id("saliva"), SalivaOverlay::extract);
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
