package dev.goober.transdimension.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.FairyCrystalSpike;

/**
 * Draws a {@link FairyCrystalSpike}: a cluster of icy crystal shards (a tall one in the middle, four leaning outward)
 * that shoots up out of the ground, wobbles, holds and sinks back. See-through and full bright, like ice catching
 * the light. Texture: tools/generate_textures.py {@code fairy_crystal_spike()} (32x32).
 */
public class FairyCrystalSpikeRenderer extends EntityRenderer<FairyCrystalSpike, FairyCrystalSpikeRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(TransDimension.id("fairy_crystal_spike"), "main");
	private static final Identifier TEXTURE = TransDimension.id("textures/entity/trans_fairy/fairy_crystal_spike.png");
	private static final int FULL_BRIGHT = 0xF000F0;

	private final Model model;

	public FairyCrystalSpikeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new Model(context.bakeLayer(LAYER));
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition cluster = mesh.getRoot().addOrReplaceChild("cluster", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
		cluster.addOrReplaceChild("centre", CubeListBuilder.create()
						.texOffs(0, 0).addBox(-1.5F, -20.0F, -1.5F, 3.0F, 20.0F, 3.0F)
						.texOffs(12, 0).addBox(-1.0F, -23.0F, -1.0F, 2.0F, 3.0F, 2.0F)
						.texOffs(12, 5).addBox(-0.5F, -25.0F, -0.5F, 1.0F, 2.0F, 1.0F),
				PartPose.rotation(0.0F, Mth.PI / 4.0F, 0.0F));
		shard(cluster, "north_shard", 0.0F, -2.5F, -0.45F, 0.0F, 13.0F);
		shard(cluster, "south_shard", 0.0F, 2.5F, 0.5F, 0.0F, 11.0F);
		shard(cluster, "east_shard", 2.5F, 0.0F, 0.0F, -0.45F, 12.0F);
		shard(cluster, "west_shard", -2.5F, 0.0F, 0.0F, 0.55F, 10.0F);
		return LayerDefinition.create(mesh, 32, 32);
	}

	private static void shard(PartDefinition cluster, String name, float x, float z, float xRot, float zRot, float height) {
		cluster.addOrReplaceChild(name, CubeListBuilder.create()
						.texOffs(20, 0).addBox(-1.0F, -height, -1.0F, 2.0F, height, 2.0F)
						.texOffs(28, 0).addBox(-0.5F, -height - 2.0F, -0.5F, 1.0F, 2.0F, 1.0F),
				PartPose.offsetAndRotation(x, 0.0F, z, xRot, 0.7F, zRot));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FairyCrystalSpike spike, State state, float partialTick) {
		super.extractRenderState(spike, state, partialTick);
		state.eruption = spike.getEruption(partialTick);
		state.yRot = spike.getYRot();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState camera) {
		if (state.eruption > 0.0F) {
			poseStack.pushPose();
			poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
			// Model space has y down with the ground at 24, like a mob's.
			poseStack.scale(-1.0F, -1.0F, 1.0F);
			poseStack.translate(0.0F, -1.501F, 0.0F);
			nodeCollector.submitModel(this.model, state, poseStack, RenderTypes.entityTranslucent(TEXTURE), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
					state.outlineColor, null);
			poseStack.popPose();
		}
		super.submit(state, poseStack, nodeCollector, camera);
	}

	public static class State extends EntityRenderState {
		/** 0 hidden, 1 fully out (a little more while it wobbles). */
		public float eruption;
		public float yRot;
	}

	/** The shard cluster; it grows out of the ground from its base as the spike erupts. */
	static class Model extends EntityModel<State> {
		private final ModelPart cluster;

		Model(ModelPart root) {
			super(root);
			this.cluster = root.getChild("cluster");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float out = Math.min(1.0F, state.eruption);
			this.cluster.yScale = state.eruption;
			this.cluster.xScale = 0.6F + 0.4F * out;
			this.cluster.zScale = 0.6F + 0.4F * out;
			this.cluster.y = 24.0F + (1.0F - out) * 6.0F;
		}
	}
}
