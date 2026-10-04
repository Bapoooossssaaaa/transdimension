package dev.goober.transdimension.client.block;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
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

/**
 * The Fairy Jar's dancing light: a little glowing cube with fluttering wings that loops about inside the jar, spinning
 * slowly, inside a soft halo. It glows pink, then white, then blue (in step with {@link FairyJarBlock#COLOURS}).
 * Each jar dances out of step with its neighbours. Textures: tools/generate_textures.py {@code fairy_light()}.
 */
public class FairyJarRenderer implements BlockEntityRenderer<FairyJarBlockEntity, FairyJarRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(TransDimension.id("fairy_light"), "main");
	public static final ModelLayerLocation HALO_LAYER = new ModelLayerLocation(TransDimension.id("fairy_light"), "halo");
	private static final Identifier[] TEXTURES = {TransDimension.id("textures/entity/fairy_light/pink.png"),
			TransDimension.id("textures/entity/fairy_light/white.png"), TransDimension.id("textures/entity/fairy_light/blue.png")};
	private static final int FULL_BRIGHT = 0xF000F0;

	private final LightModel light;
	private final LightModel halo;

	public FairyJarRenderer(BlockEntityRendererProvider.Context context) {
		this.light = new LightModel(context.bakeLayer(LAYER));
		this.halo = new LightModel(context.bakeLayer(HALO_LAYER));
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition light = mesh.getRoot().addOrReplaceChild("light", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.ZERO);
		light.addOrReplaceChild("right_wing", CubeListBuilder.create()
				.texOffs(0, 6).mirror().addBox(-3.0F, -2.0F, 0.0F, 3.0F, 3.0F, 0.0F), PartPose.offset(-0.8F, -0.8F, 1.6F));
		light.addOrReplaceChild("left_wing", CubeListBuilder.create()
				.texOffs(0, 6).addBox(0.0F, -2.0F, 0.0F, 3.0F, 3.0F, 0.0F), PartPose.offset(0.8F, -0.8F, 1.6F));
		return LayerDefinition.create(mesh, 16, 16);
	}

	public static LayerDefinition createHaloLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition light = mesh.getRoot().addOrReplaceChild("light", CubeListBuilder.create()
				.texOffs(0, 9).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(1.0F)), PartPose.ZERO);
		light.addOrReplaceChild("right_wing", CubeListBuilder.create(), PartPose.ZERO);
		light.addOrReplaceChild("left_wing", CubeListBuilder.create(), PartPose.ZERO);
		return LayerDefinition.create(mesh, 16, 16);
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
		// A lazy figure-of-eight loop inside the jar, bobbing up and down, turning as it goes.
		poseStack.translate(0.5F + 0.13F * Mth.sin(t * 0.07F), 0.42F + 0.11F * Mth.sin(t * 0.13F), 0.5F + 0.13F * Mth.sin(t * 0.14F));
		poseStack.mulPose(Axis.YP.rotation(t * 0.06F));
		poseStack.mulPose(Axis.XP.rotation(Mth.sin(t * 0.09F) * 0.3F));
		float size = 1.0F / 16.0F;
		poseStack.scale(size, size, size);
		Identifier texture = TEXTURES[state.colour];
		nodeCollector.submitModel(this.light, state, poseStack, RenderTypes.entityCutoutNoCull(texture), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
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

	/** The light: a cube with two tiny wings that flap fast. */
	static class LightModel extends Model<State> {
		private final ModelPart rightWing;
		private final ModelPart leftWing;

		LightModel(ModelPart root) {
			super(root, RenderTypes::entityCutoutNoCull);
			ModelPart light = root.getChild("light");
			this.rightWing = light.getChild("right_wing");
			this.leftWing = light.getChild("left_wing");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float flap = 0.5F + 0.45F * Mth.sin(state.time * 1.6F);
			this.rightWing.yRot = flap;
			this.leftWing.yRot = -flap;
		}
	}
}
