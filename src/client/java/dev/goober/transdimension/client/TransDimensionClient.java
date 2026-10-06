package dev.goober.transdimension.client;

import java.util.List;
import java.util.Objects;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.object.boat.BoatModel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.WardenRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.client.entity.CloudyModel;
import dev.goober.transdimension.client.entity.CloudyRenderer;
import dev.goober.transdimension.client.entity.FairyLightModel;
import dev.goober.transdimension.client.entity.FairyRenderer;
import dev.goober.transdimension.client.entity.FairyCrystalSpikeRenderer;
import dev.goober.transdimension.client.entity.MaddieRenderer;
import dev.goober.transdimension.client.entity.PastelSlimeModel;
import dev.goober.transdimension.client.entity.PastelSlimeRenderer;
import dev.goober.transdimension.client.entity.SculkPersonRenderer;
import dev.goober.transdimension.client.entity.SeatRenderer;
import dev.goober.transdimension.client.entity.SillyCatModel;
import dev.goober.transdimension.client.entity.SillyCatRenderer;
import dev.goober.transdimension.client.entity.TransEndermanModel;
import dev.goober.transdimension.client.entity.TransEndermanRenderer;
import dev.goober.transdimension.client.entity.TransFairyModel;
import dev.goober.transdimension.client.entity.TransFairyRenderer;
import dev.goober.transdimension.client.entity.TransFishRenderer;
import dev.goober.transdimension.client.screen.MaddieDialogueScreen;
import dev.goober.transdimension.client.wings.TransWingsLayer;
import dev.goober.transdimension.client.wings.TransWingsModel;
import dev.goober.transdimension.client.wings.WingAnimations;
import dev.goober.transdimension.client.wings.WingPose;
import dev.goober.transdimension.client.wings.WingsController;
import dev.goober.transdimension.network.FairyCutscenePayload;
import dev.goober.transdimension.network.FairyRescuePayload;
import dev.goober.transdimension.network.OpenMaddieDialoguePayload;
import dev.goober.transdimension.network.WingFlapPayload;
import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.registry.ModEntities;
import dev.goober.transdimension.registry.ModFluids;
import dev.goober.transdimension.registry.ModItems;

public class TransDimensionClient implements ClientModInitializer {
	/** Trans pink, used for trans grass outside of a world (and as the fallback colour). */
	private static final int DEFAULT_GRASS = 0xFFF5A9B8;

	/** Tints trans grass blocks, grass and ferns with the biome's grass colour, so every biome has its own shade. */
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

		// Pink lava: vanilla lava's look, in pink.
		FluidRenderingRegistry.register(ModFluids.PINK_LAVA, ModFluids.FLOWING_PINK_LAVA, new FluidModel.Unbaked(
				new Material(TransDimension.id("block/pink_lava_still")), new Material(TransDimension.id("block/pink_lava_flow")), null, null));
		// Trans grass and ferns take the biome's grass colour, like the grass block they grow on.
		BlockColorRegistry.register(List.of(TRANS_GRASS_TINT), ModBlocks.TRANS_GRASS_BLOCK, ModBlocks.TRANS_SHORT_GRASS,
				ModBlocks.TALL_TRANS_GRASS, ModBlocks.TRANS_FERN, ModBlocks.LARGE_TRANS_FERN, ModBlocks.POTTED_TRANS_FERN);

		// The Silly Cat.
		ModelLayerRegistry.registerModelLayer(SillyCatRenderer.LAYER, SillyCatModel::createBodyLayer);
		EntityRenderers.register(ModEntities.SILLY_CAT, SillyCatRenderer::new);

		// The realm's creatures: trans fish, trans endermen (with glowing eyes) and pastel slimes (with a jelly coat).
		EntityRenderers.register(ModEntities.TRANS_FISH, TransFishRenderer::new);
		ModelLayerRegistry.registerModelLayer(TransEndermanRenderer.LAYER, TransEndermanModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(TransEndermanRenderer.EYES_LAYER, TransEndermanModel::createEyesLayer);
		EntityRenderers.register(ModEntities.TRANS_ENDERMAN, TransEndermanRenderer::new);
		ModelLayerRegistry.registerModelLayer(PastelSlimeRenderer.LAYER, PastelSlimeModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(PastelSlimeRenderer.JELLY_LAYER, PastelSlimeModel::createJellyLayer);
		EntityRenderers.register(ModEntities.PASTEL_SLIME, PastelSlimeRenderer::new);

		// The Fairy Realm: the Trans Fairy (wings and wand star in a glowing layer), her ice crystals, and the wild fairies.
		ModelLayerRegistry.registerModelLayer(TransFairyRenderer.LAYER, TransFairyModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(TransFairyRenderer.GLOW_LAYER, TransFairyModel::createGlowLayer);
		EntityRenderers.register(ModEntities.TRANS_FAIRY, TransFairyRenderer::new);
		ModelLayerRegistry.registerModelLayer(FairyCrystalSpikeRenderer.LAYER, FairyCrystalSpikeRenderer::createLayer);
		EntityRenderers.register(ModEntities.FAIRY_CRYSTAL_SPIKE, FairyCrystalSpikeRenderer::new);
		ModelLayerRegistry.registerModelLayer(FairyLightModel.LAYER, FairyLightModel::createLayer);
		ModelLayerRegistry.registerModelLayer(FairyLightModel.HALO_LAYER, FairyLightModel::createHaloLayer);
		EntityRenderers.register(ModEntities.FAIRY, FairyRenderer::new);

		// The invisible seat you ride while sitting on furniture.
		EntityRenderers.register(ModEntities.SEAT, SeatRenderer::new);

		// The pink deep dark: the friendly warden (vanilla's renderer; TransRecolor makes it pink in the realm), the sculk
		// people, and vanilla's sculk effects drawn in pink in the realm.
		EntityRenderers.register(ModEntities.PINK_WARDEN, WardenRenderer::new);
		ModelLayerRegistry.registerModelLayer(SculkPersonRenderer.LAYER, SculkPersonRenderer::createLayer);
		EntityRenderers.register(ModEntities.SCULK_PERSON, SculkPersonRenderer::new);
		PinkSculkParticles.register();
		// The Cloud Realm's Cloudies.
		ModelLayerRegistry.registerModelLayer(CloudyRenderer.LAYER, CloudyModel::createLayer);
		EntityRenderers.register(ModEntities.CLOUDY, CloudyRenderer::new);

		// Trans boats use vanilla's boat models with our textures.
		ModelLayerRegistry.registerModelLayer(TRANS_BOAT_LAYER, BoatModel::createBoatModel);
		ModelLayerRegistry.registerModelLayer(TRANS_CHEST_BOAT_LAYER, BoatModel::createChestBoatModel);
		EntityRenderers.register(ModEntities.TRANS_BOAT, context -> new BoatRenderer(context, TRANS_BOAT_LAYER));
		EntityRenderers.register(ModEntities.TRANS_CHEST_BOAT, context -> new BoatRenderer(context, TRANS_CHEST_BOAT_LAYER));

		// Maddie, her wand's spell and her wings (a layer on every player renderer; it only draws for wearers).
		ModelLayerRegistry.registerModelLayer(MaddieRenderer.LAYER, MaddieRenderer::createLayer);
		EntityRenderers.register(ModEntities.MADDIE, MaddieRenderer::new);
		EntityRenderers.register(ModEntities.TRANS_MAGIC_BOLT, context -> new ThrownItemRenderer<>(context, 1.25F, true));
		EntityRenderers.register(ModEntities.CRYSTAL_EYE, context -> new ThrownItemRenderer<>(context, 1.0F, true));
		ModelLayerRegistry.registerModelLayer(TransWingsModel.LAYER, TransWingsModel::createLayer);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityRenderer instanceof AvatarRenderer<?> avatarRenderer) {
				registrationHelper.register(new TransWingsLayer(avatarRenderer, context));
			}
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR, TransDimension.id("trans_wings"), hiddenInCutscene(WingsController::extractHud));
		// A cape would poke through the folded feathers, so players wearing the wings don't show theirs.
		LivingEntityFeatureRenderEvents.ALLOW_CAPE_RENDER.register(state -> state.getData(WingPose.KEY) == null);

		ClientPlayNetworking.registerGlobalReceiver(OpenMaddieDialoguePayload.TYPE, (payload, context) ->
				context.client().gui.setScreen(new MaddieDialogueScreen(payload.entityId(), payload.gifted())));
		// The Fairy Realm cutscene: its subtitles, moving camera and hidden HUD.
		ClientPlayNetworking.registerGlobalReceiver(FairyCutscenePayload.TYPE, (payload, context) -> FairyCutsceneOverlay.handle(payload));
		// A Bottled Fairy saved you: it pops up on screen the way a totem does (the sparkles come from the server).
		ClientPlayNetworking.registerGlobalReceiver(FairyRescuePayload.TYPE, (payload, context) ->
				context.client().gameRenderer.displayItemActivation(new ItemStack(ModItems.BOTTLED_FAIRY)));
		ClientPlayNetworking.registerGlobalReceiver(WingFlapPayload.TYPE, (payload, context) -> {
			if (context.client().level != null) {
				WingAnimations.flap(payload.entityId(), payload.action(), context.client().level.getGameTime());
			}
		});

		// Cat spit sits under the hotbar like the pumpkin overlay; the dimension intro draws on top of everything.
		HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR, TransDimension.id("saliva"), hiddenInCutscene(SalivaOverlay::extract));
		// The Trans Fairy's own boss bar sits where vanilla's would; the Fairy Realm cutscene's subtitles go over everything.
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, TransDimension.id("trans_fairy_bar"),
				hiddenInCutscene(TransFairyBossBar::extract));
		HudElementRegistry.addLast(TransDimension.id("fairy_cutscene"), FairyCutsceneOverlay::extract);
		HudElementRegistry.addLast(TransDimension.id("trans_intro"), TransIntroOverlay::extract);
		// While the Fairy Realm cutscene plays, the HUD is hidden: every vanilla element but the sound subtitles.
		for (Identifier element : List.of(VanillaHudElements.MISC_OVERLAYS, VanillaHudElements.CROSSHAIR, VanillaHudElements.SPECTATOR_MENU,
				VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR,
				VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL,
				VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.SPECTATOR_TOOLTIP, VanillaHudElements.MOB_EFFECTS,
				VanillaHudElements.BOSS_BAR, VanillaHudElements.SLEEP, VanillaHudElements.DEMO_TIMER, VanillaHudElements.SCOREBOARD,
				VanillaHudElements.OVERLAY_MESSAGE, VanillaHudElements.TITLE_AND_SUBTITLE, VanillaHudElements.CHAT, VanillaHudElements.PLAYER_LIST)) {
			HudElementRegistry.replaceElement(element, TransDimensionClient::hiddenInCutscene);
		}
		// The candle ritual's beams and light, and the cutscene's moving camera and column of light.
		RitualEffects.register();
		FairyCutsceneCamera.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ResourceKey<Level> dimension = client.level != null ? client.level.dimension() : null;
			boolean inFairyRealm = TransDimension.FAIRY_REALM.equals(dimension);
			// The Fairy Realm floats high above the same pastel world, so it gets the realm's sky, clouds and colours too.
			boolean inRealm = TransDimension.TRANS_REALM.equals(dimension) || inFairyRealm;

			if (!Objects.equals(dimension, lastDimension)) {
				// The intro plays on arriving in the Trans Realm from outside, not on coming back from the Fairy or Cloud Realm.
				boolean cameFromRealms = TransDimension.TRANS_REALM.equals(lastDimension) || TransDimension.FAIRY_REALM.equals(lastDimension)
						|| TransDimension.CLOUD_REALM.equals(lastDimension);
				if (TransDimension.TRANS_REALM.equals(dimension) && !cameFromRealms) {
					TransIntroOverlay.start();
				}
				FairyCutsceneOverlay.reset();
				lastDimension = dimension;
			}

			WingsController.tick(client);
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

	/** A HUD element that draws as usual, except while the Fairy Realm cutscene plays (FairyCutsceneCamera). */
	private static HudElement hiddenInCutscene(HudElement element) {
		return (graphics, deltaTracker) -> {
			if (!FairyCutsceneCamera.isActive()) {
				element.extractRenderState(graphics, deltaTracker);
			}
		};
	}
}
