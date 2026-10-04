package dev.goober.transdimension.client.block;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.block.FairyJarBlock;
import dev.goober.transdimension.block.entity.FairyJarBlockEntity;
import dev.goober.transdimension.client.entity.FairyLightModel;

/**
 * The Fairy Jar's dancing fairy: the same glowing winged light as a wild fairy ({@link FairyLightModel}), looping about
 * inside the jar, turning slowly, inside a soft halo. It glows pink, then white, then blue (in step with
 * {@link FairyJarBlock#COLOURS}); its wings stay swept back a little so they fit inside the glass. Each jar dances out
 * of step with its neighbours. Textures: tools/generate_textures.py {@code fairy_light()}.
 */
public class FairyJarRenderer implements BlockEntityRenderer<FairyJarBlockEntity, FairyJarRenderer.State> {
	private static final Identifier[] TEXTURES = {TransDimension.id("textures/entity/fairy_light/pink.png"),
			TransDimension.id("textures/entity/fairy_light/white.png"), TransDimension.id("textures/entity/fairy_light/blue.png")};
	private static final int FULL_BRIGHT = 0xF000F0;

	private final LightModel light;
	private final LightModel halo;

	public FairyJarRenderer(BlockEntityRendererProvider.Context context) {
		this.light = new LightModel(context.bakeLayer(FairyLightModel.LAYER));
		this.halo = new LightModel(context.bakeLayer(FairyLightModel.HALO_LAYER));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FairyJarBlockEntity jar, State state, float partialTick, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(jar, state, partialTick, cameraPosition, breakProgress);
		Level level = jar.getLevel();
		long gameTime = level != null ? level.getGameTime() : 0L;
		state.colour = (int) (gameTime / FairyJarBlock.COLOUR_TICKS % TEXTURES.length);
		state.time = gameTime % 24000L + partialTick + (jar.getBlockPos().hashCode() & 1023);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState camera) {
		float t = state.time;
		poseStack.pushPose();
		// A lazy little loop inside the jar, bobbing up and down, turning as it goes.
		poseStack.translate(0.5F + 0.04F * Mth.sin(t * 0.07F), 0.36F + 0.05F * Mth.sin(t * 0.13F), 0.5F + 0.04F * Mth.sin(t * 0.14F));
		poseStack.mulPose(Axis.YP.rotation(t * 0.06F));
		poseStack.mulPose(Axis.XP.rotation(Mth.sin(t * 0.09F) * 0.15F));
		// Model space is upside down (like an entity's) with the light 20 pixels down; flip it and centre the light here.
		float size = 1.0F / 16.0F;
		poseStack.scale(-size, -size, size);
		poseStack.translate(0.0F, -FairyLightModel.LIGHT_Y, 0.0F);
		Identifier texture = TEXTURES[state.colour];
		nodeCollector.submitModel(this.light, state, poseStack, RenderTypes.entityTranslucent(texture), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
				0, null);
		nodeCollector.submitModel(this.halo, state, poseStack, RenderTypes.entityTranslucent(texture), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
				0, null);
		poseStack.popPose();
	}

	public static class State extends BlockEntityRenderState {
		/** Index into the light's colours (pink, white, blue). */
		public int colour;
		/** Ticks, offset per jar, for the dance. */
		public float time;
	}

	/** The fairy light as a block entity model; its wings never open wider than the jar. */
	static class LightModel extends Model<State> {
		private final FairyLightModel.Parts parts;

		LightModel(ModelPart root) {
			super(root, RenderTypes::entityTranslucent);
			this.parts = new FairyLightModel.Parts(root);
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			this.parts.flutter(state.time, 0.75F);
		}
	}
}
